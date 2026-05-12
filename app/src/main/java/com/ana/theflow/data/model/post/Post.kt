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
    val mediaType: String = "none",
    val createdAt: Timestamp? = null,
    val visibility: String = "public",
    val likesCount: Long = 0,
    val commentsCount: Long = 0
)
