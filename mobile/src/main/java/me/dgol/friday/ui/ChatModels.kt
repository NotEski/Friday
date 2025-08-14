package me.dgol.friday.ui

enum class ChatRole { User, Assistant }

data class ChatItem (
    val role: ChatRole,
    val text: String,
    val id: Long = System.nanoTime()
)