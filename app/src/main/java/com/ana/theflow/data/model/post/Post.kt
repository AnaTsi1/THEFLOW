package com.ana.theflow.data.model.post

import com.google.firebase.Timestamp

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileImageUrl: String = "",
    val authorType: String = "dancer",
    val text: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaItems: List<PostMediaItem> = emptyList(),
    val mediaType: String = "none",
    val postType: String = "regular",
    val activityType: String = "",
    val activityLocation: String = "",
    val activityDate: String = "",
    val activityTime: String = "",
    val activityPrice: String = "",
    val activityLevel: String = "",
    val activityDescription: String = "",
    val collaborationLookingFor: String = "",
    val collaborationStyle: String = "",
    val collaborationLocation: String = "",
    val collaborationDate: String = "",
    val collaborationPaid: String = "",
    val collaborationDescription: String = "",
    val createdAt: Timestamp? = null,
    val visibility: String = "public",
    val likesCount: Long = 0,
    val commentsCount: Long = 0
)

data class PostMediaItem(
    val id: String = "",
    val url: String = "",
    val mediaType: String = "photo",
    val visibleInMedia: Boolean = true,
    val pinned: Boolean = false,
    val uploadedAt: Long = 0L
)

data class PostComment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileImageUrl: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)
