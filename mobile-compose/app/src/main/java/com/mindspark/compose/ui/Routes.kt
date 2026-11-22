package com.mindspark.compose.ui

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val ROOM = "room/{roomId}"

    fun room(roomId: String) = "room/$roomId"
}
