package dev.mellow.core.model

data class Server(
    val id: String,
    val name: String,
    val url: String,
    val userId: String,
    val accessToken: String,
)
