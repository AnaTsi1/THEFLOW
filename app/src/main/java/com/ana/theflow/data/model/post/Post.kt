// Firestore models for the social feed: a post (regular, activity, or collaboration), its media,
// and its comment/reply thread.
package com.ana.theflow.data.model.post

import com.google.firebase.Timestamp

// One feed post - could be a regular text/media post, a dance activity announcement, or a collaboration request.
data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileImageUrl: String = "",
    val authorType: String = "dancer",
    // Always the real signed-in person who performed the action, even when posting as a studio.
    val actorUserId: String = "",
    // Which account owns this post: "user" (default, and every pre-existing post) or "studio".
    val authorEntityType: String = "",
    val authorEntityId: String = "",
    val authorEntityName: String = "",
    val authorEntityImageUrl: String = "",
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
    val activityCapacity: Long = 0,
    val registrationsCount: Long = 0,
    val waitlistCount: Long = 0,
    val collaborationLookingFor: String = "",
    val collaborationStyle: String = "",
    val collaborationLocation: String = "",
    val collaborationDate: String = "",
    val collaborationPaid: String = "",
    val collaborationDescription: String = "",
    val createdAt: Timestamp? = null,
    val visibility: String = "public",
    val likesCount: Long = 0,
    val commentsCount: Long = 0,
    val originalPostId: String = "",
    val originalAuthorId: String = "",
    val originalAuthorName: String = "",
    val originalAuthorEntityType: String = "",
    val originalAuthorEntityId: String = "",
    val originalAuthorEntityName: String = "",
    val originalAuthorEntityImageUrl: String = ""
)

// One photo or video attached to a post.
data class PostMediaItem(
    val id: String = "",
    val url: String = "",
    val mediaType: String = "photo",
    val visibleInMedia: Boolean = true,
    val pinned: Boolean = false,
    val uploadedAt: Long = 0L
)

// A top-level comment on a post, with its own thread of replies.
data class PostComment(
    val commentId: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileImageUrl: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val likesCount: Long = 0,
    val isLikedByCurrentUser: Boolean = false,
    val replies: List<PostCommentReply> = emptyList()
)

// A reply to a comment.
data class PostCommentReply(
    val replyId: String = "",
    val postId: String = "",
    val commentId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorProfileImageUrl: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)
