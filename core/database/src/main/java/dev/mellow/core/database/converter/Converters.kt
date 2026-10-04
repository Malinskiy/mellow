package dev.mellow.core.database.converter

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    companion object {
        /** Joins the items of a stored list; queries that look inside a list column use it too. */
        internal const val SEPARATOR = "|||"
    }
}
