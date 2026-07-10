package com.ana.theflow.data.repository

import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.post.PostMediaItem
import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PostRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Creates a new text post for the author.
    fun createTextPost(
        author: User,
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        createPost(
            author = author,
            text = text,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Creates a post with optional typed composer fields.
    fun createPost(
        author: User,
        text: String,
        mediaType: String = "none",
        postType: String = POST_TYPE_REGULAR,
        activityType: String = "",
        activityLocation: String = "",
        activityDate: String = "",
        activityTime: String = "",
        activityPrice: String = "",
        activityLevel: String = "",
        activityDescription: String = "",
        collaborationLookingFor: String = "",
        collaborationStyle: String = "",
        collaborationLocation: String = "",
        collaborationDate: String = "",
        collaborationPaid: String = "",
        collaborationDescription: String = "",
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        if (author.uid.isNotBlank() && author.uid != currentUid) {
            onFailure("Post author does not match the signed-in user")
            return
        }
        val hasBody = listOf(
            text,
            activityDescription,
            collaborationDescription,
            activityType,
            collaborationLookingFor
        ).any { it.isNotBlank() }
        if (!hasBody && mediaType == "none") {
            onFailure("Add text, media, or post details")
            return
        }

        val docRef = db.collection(Constants.Collections.POSTS).document()
        val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
        val post = mapOf(
            "postId" to docRef.id,
            "authorId" to currentUid,
            "authorName" to authorName,
            "authorProfileImageUrl" to author.profileImageUrl,
            "authorType" to author.role.ifBlank { "dancer" },
            "text" to text.trim(),
            "mediaUrls" to emptyList<String>(),
            "mediaType" to mediaType.ifBlank { "none" },
            "postType" to postType.ifBlank { POST_TYPE_REGULAR },
            "activityType" to activityType.trim(),
            "activityLocation" to activityLocation.trim(),
            "activityDate" to activityDate.trim(),
            "activityTime" to activityTime.trim(),
            "activityPrice" to activityPrice.trim(),
            "activityLevel" to activityLevel.trim(),
            "activityDescription" to activityDescription.trim(),
            "collaborationLookingFor" to collaborationLookingFor.trim(),
            "collaborationStyle" to collaborationStyle.trim(),
            "collaborationLocation" to collaborationLocation.trim(),
            "collaborationDate" to collaborationDate.trim(),
            "collaborationPaid" to collaborationPaid.trim(),
            "collaborationDescription" to collaborationDescription.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "visibility" to "public",
            "likesCount" to 0,
            "commentsCount" to 0
        )

        docRef.set(post)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to create post")
            }
    }

    // Loads posts for the current feed.
    fun loadFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadForYouFeed(onSuccess = onSuccess, onFailure = onFailure)
    }

    // Loads public posts for the personalized feed.
    fun loadForYouFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }
                rankForYouFeed(
                    posts = posts,
                    onSuccess = onSuccess
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load posts")
            }
    }

    // Loads posts from followed authors.
    fun loadFollowingFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadFollowedAuthorIds(
            onSuccess = { followedAuthorIds ->
                if (followedAuthorIds.isEmpty()) {
                    onSuccess(emptyList())
                    return@loadFollowedAuthorIds
                }

                loadPostsByFollowedAuthors(
                    followedAuthorIds = followedAuthorIds,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    // Loads dancer ids followed by the current user.
    fun loadFollowedDancerIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        loadFollowedIds(
            followType = FollowType.DANCER,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Loads teacher ids followed by the current user.
    fun loadFollowedTeacherIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        loadFollowedIds(
            followType = FollowType.TEACHER,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Loads studio ids followed by the current user.
    fun loadFollowedStudioIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        loadFollowedIds(
            followType = FollowType.STUDIO,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Loads posts written by one author.
    fun loadPostsByAuthor(
        authorId: String,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("authorId", authorId)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(posts)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load profile posts")
            }
    }

    // Loads one post by id.
    fun loadPostById(
        postId: String,
        onSuccess: (Post) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .get()
            .addOnSuccessListener { document ->
                val post = document.toObject(Post::class.java)?.copy(postId = document.id)
                if (post == null) {
                    onFailure("Post was not found")
                } else {
                    onSuccess(post)
                }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load post")
            }
    }

    // Updates the editable text of an existing post.
    fun updatePostText(
        postId: String,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .update(
                mapOf(
                    "text" to text.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update post")
            }
    }

    // Deletes one post document.
    fun deletePost(
        postId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to delete post")
            }
    }

    // Saves the editable media metadata for one post.
    fun updatePostMediaItems(
        postId: String,
        mediaItems: List<PostMediaItem>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        val mediaMaps = mediaItems.map { item ->
            mapOf(
                "id" to item.id,
                "url" to item.url,
                "mediaType" to item.mediaType,
                "visibleInMedia" to item.visibleInMedia,
                "pinned" to item.pinned,
                "uploadedAt" to item.uploadedAt
            )
        }
        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .update(
                mapOf(
                    "mediaItems" to mediaMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update media")
            }
    }

    // Toggles the current user's like on one post.
    fun toggleLike(
        postId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        val likeRef = postRef.collection(LIKES_COLLECTION).document(uid)
        db.runTransaction { transaction ->
            val likeSnapshot = transaction.get(likeRef)
            val isLiked = likeSnapshot.exists()
            if (isLiked) {
                transaction.delete(likeRef)
                transaction.update(postRef, "likesCount", FieldValue.increment(-1))
            } else {
                transaction.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(postRef, "likesCount", FieldValue.increment(1))
            }
            !isLiked
        }
            .addOnSuccessListener { isLiked -> onSuccess(isLiked) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update like")
            }
    }

    // Checks whether the current user liked one post.
    fun isPostLikedByCurrentUser(
        postId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(false)
            return
        }

        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(LIKES_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load like state")
            }
    }

    // Adds a comment to one post.
    fun addComment(
        postId: String,
        author: User,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        if (author.uid.isNotBlank() && author.uid != currentUid) {
            onFailure("Comment author does not match the signed-in user")
            return
        }
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onFailure("Comment cannot be empty")
            return
        }

        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        val commentRef = postRef.collection(COMMENTS_COLLECTION).document()
        val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
        val comment = mapOf(
            "commentId" to commentRef.id,
            "postId" to postId,
            "authorId" to currentUid,
            "authorName" to authorName,
            "authorProfileImageUrl" to author.profileImageUrl,
            "text" to cleanText,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.runBatch { batch ->
            batch.set(commentRef, comment)
            batch.update(postRef, "commentsCount", FieldValue.increment(1))
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to add comment")
            }
    }

    // Loads recent comments for one post.
    fun loadComments(
        postId: String,
        onSuccess: (List<PostComment>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(COMMENTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(30)
            .get()
            .addOnSuccessListener { snapshot ->
                val comments = snapshot.documents.mapNotNull { document ->
                    document.toObject(PostComment::class.java)?.copy(commentId = document.id)
                }
                onSuccess(comments)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load comments")
            }
    }

    // Ranks posts using the current user recommendation profile.
    private fun rankForYouFeed(
        posts: List<Post>,
        onSuccess: (List<Post>) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(RecommendationEngine.rankPosts(posts, PostRecommendationProfile()))
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(RECOMMENDATION_PROFILE_COLLECTION)
            .document(RECOMMENDATION_PROFILE_DOCUMENT)
            .get()
            .addOnSuccessListener { document ->
                onSuccess(
                    RecommendationEngine.rankPosts(
                        posts = posts,
                        profile = document.toPostRecommendationProfile()
                    )
                )
            }
            .addOnFailureListener {
                onSuccess(RecommendationEngine.rankPosts(posts, PostRecommendationProfile()))
            }
    }

    // Loads all followed author ids for the following feed.
    private fun loadFollowedAuthorIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var pendingLoads = 3
        val followedIds = linkedSetOf<String>()
        var completed = false

        // Adds one completed follow list to the combined result.
        fun completeWith(ids: List<String>) {
            if (completed) return
            followedIds.addAll(ids)
            pendingLoads -= 1
            if (pendingLoads == 0) {
                completed = true
                onSuccess(followedIds.toList())
            }
        }

        // Stops loading and returns an error.
        fun fail(message: String) {
            if (completed) return
            completed = true
            onFailure(message)
        }

        loadFollowedDancerIds(onSuccess = ::completeWith, onFailure = ::fail)
        loadFollowedTeacherIds(onSuccess = ::completeWith, onFailure = ::fail)
        loadFollowedStudioIds(onSuccess = ::completeWith, onFailure = ::fail)
    }

    // Loads public posts written by followed authors.
    private fun loadPostsByFollowedAuthors(
        followedAuthorIds: List<String>,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val chunks = followedAuthorIds.chunked(FIRESTORE_WHERE_IN_LIMIT)
        var pendingLoads = chunks.size
        val posts = mutableListOf<Post>()
        var completed = false

        chunks.forEach { authorIds ->
            db.collection(Constants.Collections.POSTS)
                .whereEqualTo("visibility", "public")
                .whereIn("authorId", authorIds)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    posts.addAll(snapshot.documents.mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(postId = document.id)
                    })
                    pendingLoads -= 1
                    if (pendingLoads == 0) {
                        completed = true
                        onSuccess(posts.sortedByDescending { it.createdAt?.seconds ?: 0L })
                    }
                }
                .addOnFailureListener { error ->
                    if (completed) return@addOnFailureListener
                    completed = true
                    onFailure(error.message ?: "Failed to load following feed")
                }
        }
    }

    // Loads followed ids from one follow collection.
    private fun loadFollowedIds(
        followType: FollowType,
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(emptyList())
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(followType.collectionName)
            .get()
            .addOnSuccessListener { snapshot ->
                val followedIds = snapshot.documents.mapNotNull { document ->
                    document.getString("targetId")
                        ?: document.getString("userId")
                        ?: document.getString("studioId")
                        ?: document.id
                }.filter { it.isNotBlank() }
                onSuccess(followedIds)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load followed ${followType.label}")
            }
    }

    // Converts a Firestore document into a post recommendation profile.
    private fun DocumentSnapshot.toPostRecommendationProfile(): PostRecommendationProfile {
        val targetTypeScores = numericMap("targetTypeScores")
        return PostRecommendationProfile(targetTypeScores = targetTypeScores)
    }

    // Reads a numeric map field from a Firestore document.
    private fun DocumentSnapshot.numericMap(field: String): Map<String, Int> {
        val value = get(field) as? Map<*, *> ?: return emptyMap()
        return value.mapNotNull { (key, score) ->
            val name = key as? String ?: return@mapNotNull null
            val number = score as? Number ?: return@mapNotNull null
            name to number.toInt()
        }.toMap()
    }

    private companion object {
        const val FIRESTORE_WHERE_IN_LIMIT = 10
        const val RECOMMENDATION_PROFILE_COLLECTION = "recommendationProfile"
        const val RECOMMENDATION_PROFILE_DOCUMENT = "main"
        const val POST_TYPE_REGULAR = "regular"
        const val LIKES_COLLECTION = "likes"
        const val COMMENTS_COLLECTION = "comments"
    }

    private enum class FollowType(
        val collectionName: String,
        val label: String
    ) {
        DANCER("followingDancers", "dancers"),
        TEACHER("followingTeachers", "teachers"),
        STUDIO("followingStudios", "studios")
    }
}
