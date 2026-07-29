package com.ana.theflow.ui.home

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment

class HomeViewModel : ViewModel() {
    var selectedFeed: HomeFeedTab = HomeFeedTab.FOR_YOU
    var forYouItems: List<HomeFeedItem> = emptyList()
    var followingItems: List<HomeFeedItem> = emptyList()
    var isLoading: Boolean = false
    var isRefreshing: Boolean = false
    var lastForYouLoadedAt: Long = 0L
    var lastFollowingLoadedAt: Long = 0L
    var requestId: Long = 0L
    var forYouRequestId: Long = 0L
    var followingRequestId: Long = 0L
    var forYouScrollY: Int = 0
    var followingScrollY: Int = 0
    var error: String = ""

    fun items(): List<HomeFeedItem> {
        return when (selectedFeed) {
            HomeFeedTab.FOR_YOU -> forYouItems
            HomeFeedTab.FOLLOWING -> followingItems
        }
    }

    fun setItems(items: List<HomeFeedItem>) {
        setItems(selectedFeed, items)
    }

    fun setItems(feed: HomeFeedTab, items: List<HomeFeedItem>) {
        when (feed) {
            HomeFeedTab.FOR_YOU -> forYouItems = items
            HomeFeedTab.FOLLOWING -> followingItems = items
        }
        markLoaded(feed)
    }

    fun nextRequestId(feed: HomeFeedTab): Long {
        requestId += 1
        when (feed) {
            HomeFeedTab.FOR_YOU -> forYouRequestId = requestId
            HomeFeedTab.FOLLOWING -> followingRequestId = requestId
        }
        return requestId
    }

    fun requestIdFor(feed: HomeFeedTab): Long {
        return when (feed) {
            HomeFeedTab.FOR_YOU -> forYouRequestId
            HomeFeedTab.FOLLOWING -> followingRequestId
        }
    }

    fun updateItem(postId: String, transform: (HomeFeedItem) -> HomeFeedItem) {
        fun update(items: List<HomeFeedItem>): List<HomeFeedItem> {
            return items.map { item -> if (item.post.postId == postId) transform(item) else item }
        }
        forYouItems = update(forYouItems)
        followingItems = update(followingItems)
    }

    fun removeItem(postId: String) {
        forYouItems = forYouItems.filterNot { it.post.postId == postId }
        followingItems = followingItems.filterNot { it.post.postId == postId }
    }

    fun prependToCurrent(item: HomeFeedItem) {
        when (selectedFeed) {
            HomeFeedTab.FOR_YOU -> forYouItems = listOf(item) + forYouItems
            HomeFeedTab.FOLLOWING -> followingItems = listOf(item) + followingItems
        }
    }

    fun hasCache(): Boolean = items().isNotEmpty()

    fun isStale(now: Long = System.currentTimeMillis()): Boolean {
        val loadedAt = when (selectedFeed) {
            HomeFeedTab.FOR_YOU -> lastForYouLoadedAt
            HomeFeedTab.FOLLOWING -> lastFollowingLoadedAt
        }
        return loadedAt == 0L || now - loadedAt > STALE_AFTER_MS
    }

    fun scrollY(): Int {
        return when (selectedFeed) {
            HomeFeedTab.FOR_YOU -> forYouScrollY
            HomeFeedTab.FOLLOWING -> followingScrollY
        }
    }

    fun saveScroll(scrollY: Int) {
        when (selectedFeed) {
            HomeFeedTab.FOR_YOU -> forYouScrollY = scrollY
            HomeFeedTab.FOLLOWING -> followingScrollY = scrollY
        }
    }

    private fun markLoaded(feed: HomeFeedTab = selectedFeed) {
        val now = System.currentTimeMillis()
        when (feed) {
            HomeFeedTab.FOR_YOU -> lastForYouLoadedAt = now
            HomeFeedTab.FOLLOWING -> lastFollowingLoadedAt = now
        }
        isLoading = false
        isRefreshing = false
    }

    companion object {
        private const val STALE_AFTER_MS = 3 * 60 * 1000L
    }
}

data class HomeFeedItem(
    val post: Post,
    val comments: List<PostComment> = emptyList(),
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isEventRegistered: Boolean = false
)

enum class HomeFeedTab {
    FOR_YOU,
    FOLLOWING
}
