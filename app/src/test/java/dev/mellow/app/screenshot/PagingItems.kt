package dev.mellow.app.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.flowOf

/** [items] as a fully loaded paged list, the way screens get the library's long lists. */
@Composable
fun <T : Any> pagingItemsOf(items: List<T>): LazyPagingItems<T> =
    remember(items) {
        val loaded = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
        flowOf(PagingData.from(items, loaded))
    }.collectAsLazyPagingItems()
