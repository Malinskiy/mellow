package dev.mellow.core.data

data class SyncProgress(
    val phase: String,
    val current: Int,
    val total: Int,
)
