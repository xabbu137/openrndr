package org.openrndr.internal.gl3

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glFinish
import org.lwjgl.opengl.GL11.glFlush
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.DrawThread
import org.openrndr.draw.Drawer
import org.openrndr.internal.Driver
import kotlin.concurrent.thread

class NullRenderTargetGL3:RenderTargetGL3(0, 640, 480, 1.0) {

}

class DrawThreadGL3(private val contextWindow: Long) : DrawThread {

    companion object {
        fun create(): DrawThreadGL3 {
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GL11.GL_TRUE)
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
            GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, 8)
            GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, 8)
            GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, 8)
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, 8)
            GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, 24)
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            val contextWindow = GLFW.glfwCreateWindow(1,
                    1,
                    "", MemoryUtil.NULL, primaryWindow)

            return DrawThreadGL3(contextWindow)
        }
    }

    lateinit var drawer:Drawer

    val jobs = mutableListOf<(Drawer) -> Any>()

    override fun job(job: (Drawer) -> Unit) {
        synchronized(jobs) {
            jobs.add(job)
        }
    }

    val results = mutableMapOf<(Drawer)->Any, Any>()

    override fun <T:Any> block(job: (Drawer) -> T): T {
        println("adding blocking job")
        synchronized(jobs) {
            jobs.add(job)
        }

        while (true) {
            val result = synchronized(results) {
                results[job]
            }
            if (result != null) {
                results.remove(job)
                return result as T
            }

        }
    }
    init {
        thread(isDaemon = true, name = "DrawThreadGL3") {
            GLFW.glfwMakeContextCurrent(contextWindow)
            GL.createCapabilities()

            drawer = Drawer(Driver.driver)
            val vaos = IntArray(1)
            GL30.glGenVertexArrays(vaos)
            GL30.glBindVertexArray(vaos[0])

            val renderTarget = NullRenderTargetGL3()

            renderTarget.bind()

            while (true) {
                val job = synchronized(jobs) {
                    if (jobs.isNotEmpty()) {
                        jobs.removeAt(0)
                    } else {
                        null
                    }
                }
                if (job != null) {
                    println("running job $job on ${Thread.currentThread()}")
                    Driver.instance.clear(ColorRGBa.BLACK)
                    drawer.reset()
                    val result = job(drawer)
                    if (result != null && result !is Unit) {
                        println("storing result $result")
                        results[job] = result
                    }
                    println("job completed")
                    glFlush()
                    glFinish()
                } else {
                    Thread.sleep(100)
                }
            }
        }
    }
}