package org.openrndr.draw

interface DrawThread {

    fun job(job:(Drawer)->Unit)
    fun <T:Any> block(job:(Drawer)->T):T
}