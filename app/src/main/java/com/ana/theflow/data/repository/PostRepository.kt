package com.ana.theflow.data.repository

import com.ana.theflow.data.model.post.Post
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

    fun createTextPost(
        author: User,
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (author.uid.isBlank()) {
            onFailure("Missing author id")
            return
        }
        if (text.isBlank()) {
            onFailure("Post text cannot be empty")
            return
        }

        val docRef = db.collection(Constants.Collections.POSTS).document()
        val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
        val post = mapOf(
            "postId" to docRef.id,
            "authorId" to author.uid,
            "authorName" to authorName,
            "authorProfileImageUrl" to author.profileImageUrl,
            "authorType" to author.role.ifBlank { "dancer" },
            "text" to text.trim(),
            "mediaUrls" to emptyList<String>(),
            "mediaType" to "none",
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

    fun loadFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadForYouFeed(onSuccess = onSuccess, onFailure = onFailure)
    }

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

    private fun loadFollowedAuthorIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var pendingLoads = 3
        val followedIds = linkedSetOf<String>()
        var completed = false

        fun completeWith(ids: List<String>) {
            if (completed) return
            followedIds.addAll(ids)
            pendingLoads -= 1
            if (pendingLoads == 0) {
                completed = true
                onSuccess(followedIds.toList())
            }
        }

        fun fail(message: String) {
            if (completed) return
            completed = true
            onFailure(message)
        }

        loadFollowedDancerIds(onSuccess = ::completeWith, onFailure = ::fail)
        loadFollowedTeacherIds(onSuccess = ::completeWith, onFailure = ::fail)
        loadFollowedStudioIds(onSuccess = ::completeWith, onFailure = ::fail)
    }

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

    private fun DocumentSnapshot.toPostRecommendationProfile(): PostRecommendationProfile {
        val targetTypeScores = numericMap("targetTypeScores")
        return PostRecommendationProfile(targetTypeScores = targetTypeScores)
    }

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
