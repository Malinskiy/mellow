package dev.mellow.core.database.paging

import android.database.Cursor
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** A position hint, or the values of a row whose place should survive inserts and deletes above it. */
sealed interface KeysetPagingKey {
    val position: Int

    data class Position(override val position: Int) : KeysetPagingKey

    data class Row(override val position: Int, val values: List<Any?>) : KeysetPagingKey
}

enum class KeysetDirection {
    ASCENDING,
    DESCENDING,
}

/** One term of a total SQL order. The last term of every [KeysetQuery] is its unique ID. */
class KeysetColumn<T : Any>(
    val expression: String,
    val resultColumn: String,
    val direction: KeysetDirection,
    val nullable: Boolean = false,
    val valueOf: (T) -> Any?,
)

/**
 * Everything needed to seek through one filtered, totally ordered SQL result. Keeping this independent of entities
 * lets albums and artists use the same paging source when their tabs are converted.
 */
class KeysetQuery<T : Any>(
    val select: String,
    val from: String,
    val seekFrom: String = from,
    val where: String,
    val arguments: List<Any?>,
    val order: List<KeysetColumn<T>>,
    val observedTables: Array<String>,
    val mapRow: (Cursor) -> T,
) {
    init {
        require(order.isNotEmpty()) { "A keyset order must include a unique final column" }
    }

    fun keyOf(row: T): List<Any?> = order.map { it.valueOf(row) }
}

/**
 * A PagingSource that seeks from page-edge values instead of walking OFFSET rows. Refreshes count the filtered rows
 * once so placeholders stay exact. A position-only refresh performs one indexed OFFSET lookup to find its seek key;
 * Paging can request that while jumping through placeholders, and the following page reads are keyset queries.
 */
class KeysetPagingSource<T : Any>(
    private val database: RoomDatabase,
    private val query: KeysetQuery<T>,
) : PagingSource<KeysetPagingKey, T>() {

    private val runner = KeysetQueryRunner(database, query)
    private val observerRegistered = AtomicBoolean(false)
    private var totalCount: Int? = null

    private val observer = object : InvalidationTracker.Observer(query.observedTables) {
        override fun onInvalidated(tables: Set<String>) {
            database.invalidationTracker.removeObserver(this)
            invalidate()
        }
    }

    override val jumpingSupported: Boolean = true

    override suspend fun load(params: LoadParams<KeysetPagingKey>): LoadResult<KeysetPagingKey, T> {
        registerObserver()
        return try {
            withContext(Dispatchers.IO) {
                when (params) {
                    is LoadParams.Refresh -> refresh(params)
                    is LoadParams.Append -> append(params)
                    is LoadParams.Prepend -> prepend(params)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<KeysetPagingKey, T>): KeysetPagingKey? {
        val position = state.anchorPosition ?: return null
        val row = state.pages.firstNotNullOfOrNull { page ->
            val index = position - page.itemsBefore
            page.data.getOrNull(index)
        }
        return if (row == null) KeysetPagingKey.Position(position) else KeysetPagingKey.Row(position, query.keyOf(row))
    }

    private fun refresh(params: LoadParams.Refresh<KeysetPagingKey>): LoadResult.Page<KeysetPagingKey, T> {
        val total = runner.count()
        totalCount = total
        if (total == 0) return page(emptyList(), 0, 0)

        val requestedKey = params.key
        if (requestedKey == null) {
            val data = runner.loadFirst(params.loadSize)
            return page(data, 0, total - data.size)
        }

        val key: List<Any?>
        val anchorPosition: Int
        when (requestedKey) {
            is KeysetPagingKey.Position -> {
                anchorPosition = requestedKey.position.coerceIn(0, total - 1)
                key = runner.keyAt(anchorPosition) ?: return page(emptyList(), total, 0)
            }
            is KeysetPagingKey.Row -> {
                key = requestedKey.values
                anchorPosition = runner.countBefore(key).coerceIn(0, total)
            }
        }

        val window = runner.loadWindow(key, params.loadSize / 2, params.loadSize)
        val itemsBefore = (anchorPosition - window.beforeAnchor).coerceAtLeast(0)
        val itemsAfter = (total - itemsBefore - window.items.size).coerceAtLeast(0)
        return page(window.items, itemsBefore, itemsAfter)
    }

    private fun append(params: LoadParams.Append<KeysetPagingKey>): LoadResult.Page<KeysetPagingKey, T> {
        val key = params.key as? KeysetPagingKey.Row
            ?: error("Append needs the row key returned by the previous page")
        val total = totalCount ?: error("Append was requested before refresh")
        val data = runner.loadAfter(key.values, params.loadSize)
        val itemsBefore = (key.position + 1).coerceAtMost(total)
        val itemsAfter = (total - itemsBefore - data.size).coerceAtLeast(0)
        return page(data, itemsBefore, itemsAfter)
    }

    private fun prepend(params: LoadParams.Prepend<KeysetPagingKey>): LoadResult.Page<KeysetPagingKey, T> {
        val key = params.key as? KeysetPagingKey.Row
            ?: error("Prepend needs the row key returned by the previous page")
        val total = totalCount ?: error("Prepend was requested before refresh")
        val data = runner.loadBefore(key.values, params.loadSize)
        val itemsBefore = (key.position - data.size).coerceAtLeast(0)
        val itemsAfter = (total - itemsBefore - data.size).coerceAtLeast(0)
        return page(data, itemsBefore, itemsAfter)
    }

    private fun page(data: List<T>, itemsBefore: Int, itemsAfter: Int): LoadResult.Page<KeysetPagingKey, T> {
        val previous = data.firstOrNull()?.takeIf { itemsBefore > 0 }?.let {
            KeysetPagingKey.Row(itemsBefore, query.keyOf(it))
        }
        val next = data.lastOrNull()?.takeIf { itemsAfter > 0 }?.let {
            KeysetPagingKey.Row(itemsBefore + data.lastIndex, query.keyOf(it))
        }
        return LoadResult.Page(data, previous, next, itemsBefore, itemsAfter)
    }

    private fun registerObserver() {
        if (!observerRegistered.compareAndSet(false, true)) return
        database.invalidationTracker.addObserver(observer)
        registerInvalidatedCallback { database.invalidationTracker.removeObserver(observer) }
    }
}

/** A keyed window with the number of its rows that precede the anchor. */
data class KeysetWindow<T : Any>(val items: List<T>, val beforeAnchor: Int)

/** Direct SQL operations shared by paging and queue-window reads. */
class KeysetQueryRunner<T : Any>(
    private val database: RoomDatabase,
    private val query: KeysetQuery<T>,
) {
    fun count(): Int = scalar("SELECT COUNT(*) ${query.from} WHERE ${query.where}", query.arguments)

    fun countBefore(key: List<Any?>): Int {
        val predicate = predicate(key, Relation.BEFORE)
        return scalar(
            "SELECT COUNT(*) ${query.seekFrom} WHERE (${query.where}) AND (${predicate.sql})",
            query.arguments + predicate.arguments,
        )
    }

    fun keyAt(position: Int): List<Any?>? {
        val selected = query.order.joinToString { "${it.expression} AS ${it.resultColumn}" }
        val sql = "SELECT $selected ${query.seekFrom} WHERE ${query.where} " +
            "ORDER BY ${orderBy(false)} LIMIT 1 OFFSET ?"
        return database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery(sql, (query.arguments + position).toTypedArray()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            query.order.map { column -> cursor.value(cursor.getColumnIndexOrThrow(column.resultColumn)) }
        }
    }

    fun findById(id: Any): T? {
        val idColumn = query.order.last()
        val sql = "${query.select} ${query.from} WHERE (${query.where}) AND ${idColumn.expression} = ? LIMIT 1"
        return rows(sql, query.arguments + id).singleOrNull()
    }

    fun loadFirst(limit: Int): List<T> = load(null, null, reversed = false, limit)

    fun loadAfter(key: List<Any?>, limit: Int): List<T> = load(key, Relation.AFTER, reversed = false, limit)

    fun loadBefore(key: List<Any?>, limit: Int): List<T> =
        load(key, Relation.BEFORE, reversed = true, limit).asReversed()

    fun loadWindow(key: List<Any?>, before: Int, size: Int): KeysetWindow<T> {
        if (size <= 0) return KeysetWindow(emptyList(), 0)
        var earlier = loadBefore(key, before.coerceAtMost(size - 1))
        val fromAnchor = load(key, Relation.AT_OR_AFTER, reversed = false, size - earlier.size)
        val missing = size - earlier.size - fromAnchor.size
        if (missing > 0 && earlier.isNotEmpty()) {
            earlier = loadBefore(query.keyOf(earlier.first()), missing) + earlier
        }
        return KeysetWindow(earlier + fromAnchor, earlier.size)
    }

    fun loadWindowById(id: Any, before: Int, size: Int): KeysetWindow<T>? {
        val anchor = findById(id) ?: return null
        return loadWindow(query.keyOf(anchor), before, size)
    }

    private fun load(key: List<Any?>?, relation: Relation?, reversed: Boolean, limit: Int): List<T> {
        if (limit <= 0) return emptyList()
        val seek = if (key == null || relation == null) null else predicate(key, relation)
        val seekSql = seek?.let { " AND (${it.sql})" }.orEmpty()
        val sql = "${query.select} ${query.seekFrom} WHERE (${query.where})$seekSql " +
            "ORDER BY ${orderBy(reversed)} LIMIT ?"
        return rows(sql, query.arguments + seek.orEmptyArguments() + limit)
    }

    private fun rows(sql: String, arguments: List<Any?>): List<T> =
        database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, arguments.toTypedArray())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(query.mapRow(cursor))
            }
        }

    private fun scalar(sql: String, arguments: List<Any?>): Int =
        database.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, arguments.toTypedArray())).use { cursor ->
            check(cursor.moveToFirst()) { "COUNT query returned no row" }
            cursor.getLong(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    private fun orderBy(reversed: Boolean): String = query.order.joinToString { column ->
        val direction = if (reversed) column.direction.reverse() else column.direction
        "${column.expression} ${direction.sql}"
    }

    /**
     * SQLite row comparisons cannot express mixed directions. This is the equivalent ordered OR chain. Nullable
     * columns include the explicit NULL arm that SQLite's ordinary comparisons leave unknown.
     */
    private fun predicate(key: List<Any?>, relation: Relation): SqlPart {
        require(key.size == query.order.size) { "Key has ${key.size} values for ${query.order.size} columns" }
        if (relation == Relation.AT_OR_AFTER) {
            val after = predicate(key, Relation.AFTER)
            val equal = equality(key)
            return SqlPart("(${after.sql}) OR (${equal.sql})", after.arguments + equal.arguments)
        }

        val terms = mutableListOf<SqlPart>()
        for (index in query.order.indices) {
            val prefix = query.order.indices.take(index).map { equality(query.order[it], key[it]) }
            val strict = strict(query.order[index], key[index], relation) ?: continue
            val parts = prefix + strict
            terms += SqlPart(
                parts.joinToString(prefix = "(", postfix = ")", separator = " AND ") { it.sql },
                parts.flatMap { it.arguments },
            )
        }
        return if (terms.isEmpty()) SqlPart("0", emptyList()) else SqlPart(
            terms.joinToString(prefix = "(", postfix = ")", separator = " OR ") { it.sql },
            terms.flatMap { it.arguments },
        )
    }

    private fun equality(key: List<Any?>): SqlPart {
        val parts = query.order.mapIndexed { index, column -> equality(column, key[index]) }
        return SqlPart(parts.joinToString(" AND ") { it.sql }, parts.flatMap { it.arguments })
    }

    private fun equality(column: KeysetColumn<T>, value: Any?): SqlPart =
        if (value == null) SqlPart("${column.expression} IS NULL", emptyList())
        else SqlPart("${column.expression} = ?", listOf(value))

    private fun strict(column: KeysetColumn<T>, value: Any?, relation: Relation): SqlPart? {
        val after = relation == Relation.AFTER
        return when (column.direction) {
            KeysetDirection.ASCENDING -> when {
                value == null && after -> SqlPart("${column.expression} IS NOT NULL", emptyList())
                value == null -> null
                after -> SqlPart("${column.expression} > ?", listOf(value))
                column.nullable -> SqlPart("(${column.expression} < ? OR ${column.expression} IS NULL)", listOf(value))
                else -> SqlPart("${column.expression} < ?", listOf(value))
            }
            KeysetDirection.DESCENDING -> when {
                value == null && after -> null
                value == null -> SqlPart("${column.expression} IS NOT NULL", emptyList())
                after && column.nullable ->
                    SqlPart("(${column.expression} < ? OR ${column.expression} IS NULL)", listOf(value))
                after -> SqlPart("${column.expression} < ?", listOf(value))
                else -> SqlPart("${column.expression} > ?", listOf(value))
            }
        }
    }

    private enum class Relation {
        BEFORE,
        AFTER,
        AT_OR_AFTER,
    }

    private data class SqlPart(val sql: String, val arguments: List<Any?>)

    private fun SqlPart?.orEmptyArguments(): List<Any?> = this?.arguments.orEmpty()
}

private val KeysetDirection.sql: String
    get() = if (this == KeysetDirection.ASCENDING) "ASC" else "DESC"

private fun KeysetDirection.reverse(): KeysetDirection =
    if (this == KeysetDirection.ASCENDING) KeysetDirection.DESCENDING else KeysetDirection.ASCENDING

private fun Cursor.value(index: Int): Any? = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> null
    Cursor.FIELD_TYPE_INTEGER -> getLong(index)
    Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
    Cursor.FIELD_TYPE_STRING -> getString(index)
    Cursor.FIELD_TYPE_BLOB -> getBlob(index)
    else -> error("Unknown cursor field type ${getType(index)}")
}
