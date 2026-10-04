package dev.mellow.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.ErrorContent
import dev.mellow.core.designsystem.component.LoadingContent
import kotlinx.coroutines.flow.flowOf

/**
 * A list with no items and nothing to load, for a screen the caller has no list for. The load states say so: without
 * them the list would look like it's still loading.
 */
@Composable
internal fun <T : Any> emptyPagingItems(): LazyPagingItems<T> =
    remember {
        val loaded = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
        flowOf(PagingData.empty<T>(loaded))
    }.collectAsLazyPagingItems()

/** A paged list's [content] once it has items, otherwise loading, an error to retry, or why it's empty. */
@Composable
internal fun <T : Any> PagedContent(
    items: LazyPagingItems<T>,
    emptyMessage: String,
    errorMessage: String,
    content: @Composable () -> Unit,
) {
    val refresh = items.loadState.refresh
    when {
        items.itemCount > 0 -> content()
        refresh is LoadState.Error -> ErrorContent(message = errorMessage, onRetry = items::retry)
        refresh is LoadState.Loading -> LoadingContent()
        else -> EmptyContent(emptyMessage)
    }
}
