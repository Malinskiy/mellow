package dev.mellow.core.database

import androidx.room.withTransaction

/** Runs a block of DAO calls as one database transaction: all of it is saved, or none of it. */
interface DatabaseTransactionRunner {
    suspend operator fun <T> invoke(block: suspend () -> T): T
}

class RoomTransactionRunner(private val database: MellowDatabase) : DatabaseTransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T = database.withTransaction(block)
}
