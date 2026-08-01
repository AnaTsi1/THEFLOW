// Repository for creating, loading, and updating social posts and their engagement state.
package com.ana.theflow.data.repository

import android.util.Log
import com.ana.theflow.BuildConfig
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostComment
import com.ana.theflow.data.model.post.PostCommentReply
import com.ana.theflow.data.model.post.PostMediaItem
import com.ana.theflow.data.model.post.authorRef
import com.ana.theflow.data.model.post.isStudioAuthored
import com.ana.theflow.data.model.messaging.toPartyRef
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.recommendation.ForYouRankingStrategy
import com.ana.theflow.data.recommendation.RecommendationContext
import com.ana.theflow.data.recommendation.RecommendationProfile
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PostRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationRepository = NotificationRepository()
    private val userRepository = UserRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()

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

    // Creates a post with optional typed composer fields. When `account` is a studio account,
    // `studio` must be supplied and the signed-in user must currently manage it - the post is
    // then attributed to the studio while `actorUserId` still records who really wrote it.
    fun createPost(
        author: User,
        text: String,
        account: ActiveAccount = ActiveAccountHolder.current(),
        studio: Studio? = null,
        mediaType: String = "none",
        postType: String = POST_TYPE_REGULAR,
        visibility: String = "public",
        activityType: String = "",
        activityLocation: String = "",
        activityDate: String = "",
        activityTime: String = "",
        activityPrice: String = "",
        activityLevel: String = "",
        activityDescription: String = "",
        activityCapacity: Long = 0,
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
        // Auto-resolve the active studio when the caller didn't already have it loaded, so every
        // composer screen posts as the correct account with no extra wiring required.
        if (account is ActiveAccount.StudioAccount && studio == null) {
            StudioRepository().loadStudio(
                studioId = account.studioId,
                onSuccess = { loadedStudio ->
                    createPost(
                        author = author, text = text, account = account, studio = loadedStudio,
                        mediaType = mediaType, postType = postType, visibility = visibility,
                        activityType = activityType, activityLocation = activityLocation, activityDate = activityDate,
                        activityTime = activityTime, activityPrice = activityPrice, activityLevel = activityLevel,
                        activityDescription = activityDescription, activityCapacity = activityCapacity,
                        collaborationLookingFor = collaborationLookingFor, collaborationStyle = collaborationStyle,
                        collaborationLocation = collaborationLocation, collaborationDate = collaborationDate,
                        collaborationPaid = collaborationPaid, collaborationDescription = collaborationDescription,
                        onSuccess = onSuccess, onFailure = onFailure
                    )
                },
                onFailure = { onFailure("You do not manage this studio") }
            )
            return
        }
        if (account is ActiveAccount.StudioAccount &&
            (studio == null || studio.id != account.studioId || currentUid !in (studio.managerUids + studio.ownerUid))
        ) {
            onFailure("You do not manage this studio")
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
            "authorType" to if (account is ActiveAccount.StudioAccount) "studio" else author.role.ifBlank { "dancer" },
            "actorUserId" to currentUid,
            "authorEntityType" to account.entityType,
            "authorEntityId" to account.entityId,
            "authorEntityName" to (studio?.displayName?.ifBlank { authorName } ?: authorName),
            "authorEntityImageUrl" to (studio?.profileImageUrl ?: author.profileImageUrl),
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
            "activityCapacity" to activityCapacity.coerceAtLeast(0),
            "registrationsCount" to 0,
            "waitlistCount" to 0,
            "collaborationLookingFor" to collaborationLookingFor.trim(),
            "collaborationStyle" to collaborationStyle.trim(),
            "collaborationLocation" to collaborationLocation.trim(),
            "collaborationDate" to collaborationDate.trim(),
            "collaborationPaid" to collaborationPaid.trim(),
            "collaborationDescription" to collaborationDescription.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "visibility" to visibility.ifBlank { "public" },
            "likesCount" to 0,
            "commentsCount" to 0,
            "originalPostId" to "",
            "originalAuthorId" to "",
            "originalAuthorName" to ""
        )

        docRef.set(post)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to create post")
            }
    }

    // Creates a repost pointing back at the original - refuses to repost your own post (unless
    // it's a dance activity, which people do share to their own feed) and refuses a duplicate
    // repost of the same source post by the same person.
    fun createRepost(
        originalPost: Post,
        author: User,
        account: ActiveAccount = ActiveAccountHolder.current(),
        studio: Studio? = null,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        if (originalPost.postId.isBlank() || originalPost.visibility != "public") {
            onFailure("This post is not available to repost")
            return
        }
        if (originalPost.authorId == currentUid && originalPost.postType != POST_TYPE_DANCE_ACTIVITY) {
            onFailure("You already created this post")
            return
        }
        if (account is ActiveAccount.StudioAccount && studio == null) {
            StudioRepository().loadStudio(
                studioId = account.studioId,
                onSuccess = { loadedStudio ->
                    createRepost(originalPost, author, account, loadedStudio, onSuccess, onFailure)
                },
                onFailure = { onFailure("You do not manage this studio") }
            )
            return
        }
        if (account is ActiveAccount.StudioAccount &&
            (studio == null || studio.id != account.studioId || currentUid !in (studio.managerUids + studio.ownerUid))
        ) {
            onFailure("You do not manage this studio")
            return
        }

        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("authorId", currentUid)
            .whereEqualTo("originalPostId", originalPost.postId)
            .limit(1)
            .get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    onFailure("You already reposted this")
                    return@addOnSuccessListener
                }
                val docRef = db.collection(Constants.Collections.POSTS).document()
                val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
                val repost = mapOf(
                    "postId" to docRef.id,
                    "authorId" to currentUid,
                    "authorName" to authorName,
                    "authorProfileImageUrl" to author.profileImageUrl,
                    "authorType" to if (account is ActiveAccount.StudioAccount) "studio" else author.role.ifBlank { "dancer" },
                    "actorUserId" to currentUid,
                    "authorEntityType" to account.entityType,
                    "authorEntityId" to account.entityId,
                    "authorEntityName" to (studio?.displayName?.ifBlank { authorName } ?: authorName),
                    "authorEntityImageUrl" to (studio?.profileImageUrl ?: author.profileImageUrl),
                    "text" to "",
                    "mediaUrls" to emptyList<String>(),
                    "mediaItems" to emptyList<Map<String, Any>>(),
                    "mediaType" to "none",
                    "postType" to POST_TYPE_REPOST,
                    "activityType" to originalPost.activityType,
                    "activityLocation" to originalPost.activityLocation,
                    "activityDate" to originalPost.activityDate,
                    "activityTime" to originalPost.activityTime,
                    "activityPrice" to originalPost.activityPrice,
                    "activityLevel" to originalPost.activityLevel,
                    "activityDescription" to originalPost.activityDescription,
                    "activityCapacity" to originalPost.activityCapacity,
                    "registrationsCount" to originalPost.registrationsCount,
                    "waitlistCount" to originalPost.waitlistCount,
                    "visibility" to "public",
                    "likesCount" to 0,
                    "commentsCount" to 0,
                    "originalPostId" to originalPost.postId,
                    "originalAuthorId" to originalPost.authorId,
                    "originalAuthorName" to originalPost.authorName,
                    "originalAuthorEntityType" to originalPost.authorRef().type,
                    "originalAuthorEntityId" to originalPost.authorRef().id,
                    "originalAuthorEntityName" to originalPost.authorRef().name,
                    "originalAuthorEntityImageUrl" to originalPost.authorRef().imageUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(repost)
                    .addOnSuccessListener { onSuccess(docRef.id) }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to repost") }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to check repost state") }
    }

    // Loads posts for the current feed.
    fun loadFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadForYouFeed(onSuccess = onSuccess, onFailure = onFailure)
    }

    // Loads public posts for the personalized feed. The post query, hidden-post ids, blocked-
    // author ids, and the recommendation profile are four independent reads - none of them
    // depends on any other's result - so they're all fired off at once here instead of one after
    // another. Doing 4 round trips in parallel instead of in sequence is what keeps Home feeling
    // fast to open.
    fun loadForYouFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        var posts: List<Post>? = null
        var hiddenIds: Set<String> = emptySet()
        var blockedIds: Set<String> = emptySet()
        var profile: RecommendationProfile? = null
        var failed = false
        var pending = 4

        fun finishIfReady() {
            if (failed) return
            pending -= 1
            if (pending > 0) return
            val candidates = posts.orEmpty()
                .filterNot { hiddenIds.contains(it.postId) || blockedIds.contains(it.authorId) }
                .take(50)
            val loadedProfile = profile
            if (uid == null || loadedProfile == null) {
                // This is a real, silent personalization cliff, not a graceful degradation: the
                // fallback below has zero data plugged into it (PostRecommendationProfile() is
                // always constructed empty at every call site in this file) - not even explicit
                // style/level, only popularity+freshness. It fires on ANY recommendation-profile
                // load failure, which is a real possibility (two Firestore reads, either can
                // fail), not just the rare signed-out case. Logging when it's the latter, since a
                // signed-in user unexpectedly losing all personalization for a request is worth
                // being able to find in logs - unlike uid == null, which is an expected, unlogged
                // state (no user to personalize for).
                if (uid != null) {
                    Log.w(TAG_PERMISSION, "loadForYouFeed falling back to non-personalized ranking uid=$uid (recommendation profile failed to load)")
                }
                onSuccess(RecommendationEngine.rankPosts(candidates, PostRecommendationProfile()))
            } else {
                onSuccess(applyForYouRanking(candidates, uid, loadedProfile))
            }
        }

        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.filter { post ->
                    post.postType != POST_TYPE_DANCE_ACTIVITY || !post.isPastActivityDate()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                finishIfReady()
            }
            .addOnFailureListener { error ->
                if (!failed) {
                    failed = true
                    onFailure(error.message ?: "Failed to load posts")
                }
            }

        loadHiddenPostIds(
            onSuccess = { ids -> hiddenIds = ids; finishIfReady() },
            onFailure = { finishIfReady() }
        )

        loadBlockedAuthorIds(
            onSuccess = { ids -> blockedIds = ids; finishIfReady() },
            onFailure = { finishIfReady() }
        )

        if (uid == null) {
            profile = null
            finishIfReady()
        } else {
            userRepository.loadRecommendationProfile(
                onSuccess = { p -> profile = p; finishIfReady() },
                onFailure = { finishIfReady() }
            )
        }
    }

    // Loads posts from followed authors - both followed users and followed studios.
    fun loadFollowingFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadFollowedUserIds(
            onSuccess = { followedUserIds ->
                loadFollowedStudioIds(
                    onSuccess = { followedStudioIds ->
                        if (followedUserIds.isEmpty() && followedStudioIds.isEmpty()) {
                            onSuccess(emptyList())
                            return@loadFollowedStudioIds
                        }
                        loadPostsByFollowedAuthors(
                            followedUserIds = followedUserIds,
                            followedStudioIds = followedStudioIds,
                            onSuccess = onSuccess,
                            onFailure = onFailure
                        )
                    },
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

    // Loads posts written by one author's personal account. Posts made on behalf of a studio
    // this person manages belong to the studio's profile, not their personal one, so they're
    // filtered out client-side - querying authorEntityType directly would also silently exclude
    // every pre-existing post, since Firestore equality never matches a missing field.
    fun loadPostsByAuthor(
        authorId: String,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        val query = db.collection(Constants.Collections.POSTS)
            .whereEqualTo("authorId", authorId)

        val visibleQuery = if (currentUid == authorId) {
            query
        } else {
            query.whereEqualTo("visibility", "public")
        }

        visibleQuery
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.filterNot { it.isStudioAuthored() }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(posts)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load profile posts")
            }
    }

    // Loads posts published on behalf of a studio's business account.
    fun loadPostsByStudio(
        studioId: String,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("authorEntityId", studioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.filter { it.isStudioAuthored() }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(posts)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load studio posts")
            }
    }

    // Searches recent public posts and event posts by text and structured activity fields.
    fun searchPosts(
        query: String,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            onSuccess(emptyList())
            return
        }

        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.filter { post ->
                    post.matchesQuery(normalizedQuery)
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }

                loadBlockedAuthorIds(
                    onSuccess = { blockedIds ->
                        onSuccess(posts.filterNot { blockedIds.contains(it.authorId) }.take(25))
                    },
                    onFailure = { onSuccess(posts.take(25)) }
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to search posts")
            }
    }

    // Hides a post from the current user's feeds without deleting the source post.
    fun hidePostForCurrentUser(
        post: Post,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (post.postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(HIDDEN_POSTS_COLLECTION)
            .document(post.postId)
            .set(
                mapOf(
                    "postId" to post.postId,
                    "authorId" to post.authorId,
                    "authorName" to post.authorName,
                    "postType" to post.postType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                activityTrackingRepository.trackPostHidden(post)
                onSuccess()
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to hide post")
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
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        val likeRef = postRef.collection(LIKES_COLLECTION).document(uid)
        Log.d(TAG_LIKE, "toggleLike entry uid=$uid postId=$postId likePath=${likeRef.path}")
        likeRef.get()
            .addOnSuccessListener { likeSnapshot ->
                val isLiked = likeSnapshot.exists()
                val write = if (isLiked) {
                    likeRef.delete()
                } else {
                    likeRef.set(
                        mapOf(
                            "userId" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
                write
                    .addOnSuccessListener {
                        Log.d(TAG_LIKE, "like doc write success uid=$uid postId=$postId isNowLiked=${!isLiked}")
                        syncLikeCountBestEffort(postId)
                        if (!isLiked) {
                            createLikeNotification(postId, uid)
                            trackPostSignal(postId) { activityTrackingRepository.trackPostLiked(it) }
                        }
                        onSuccess(!isLiked)
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG_PERMISSION, "like doc write failed uid=$uid postId=$postId path=${likeRef.path}", error)
                        onFailure(error.message ?: "Failed to update like")
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG_PERMISSION, "like state read failed uid=$uid postId=$postId path=${likeRef.path}", error)
                onFailure(error.message ?: "Failed to load like state")
            }
    }

    // Recounts the likes subcollection and writes the result onto the post's likesCount field, so
    // feed cards can show a count without reading the whole subcollection every time. "Best
    // effort" because if this write fails, we just log it rather than surfacing an error - the
    // like itself already went through, only the displayed count would be briefly stale.
    private fun syncLikeCountBestEffort(postId: String) {
        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        postRef.collection(LIKES_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                postRef.update(
                    mapOf(
                        "likesCount" to snapshot.size().toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { error ->
                    Log.w(TAG_PERMISSION, "like count sync failed postId=$postId path=${postRef.path}", error)
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG_LIKE, "like count read failed postId=$postId", error)
            }
    }

    // Loads the full post first so the activity tracker has real post data (author, style, type)
    // to attach to the signal, rather than just an id.
    private fun trackPostSignal(postId: String, tracker: (Post) -> Unit) {
        loadPostById(
            postId = postId,
            onSuccess = tracker,
            onFailure = {}
        )
    }

    // Counts the likes subcollection directly, for callers that want a fresh count rather than
    // the post's possibly-stale denormalized likesCount field.
    fun loadLikeCount(
        postId: String,
        onSuccess: (Long) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (postId.isBlank()) {
            onSuccess(0L)
            return
        }
        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(LIKES_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.size().toLong()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load like count") }
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

    // Toggles the current user's saved state for one post in both user and post subcollections.
    fun toggleSave(
        post: Post,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (post.postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        val userSavedRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(SAVED_POSTS_COLLECTION)
            .document(post.postId)
        val postSaveRef = db.collection(Constants.Collections.POSTS)
            .document(post.postId)
            .collection(SAVES_COLLECTION)
            .document(uid)

        userSavedRef.get()
            .addOnSuccessListener { snapshot ->
                val isSaved = snapshot.exists()
                val batch = db.batch()
                if (isSaved) {
                    batch.delete(userSavedRef)
                    batch.delete(postSaveRef)
                } else {
                    batch.set(userSavedRef, post.savedPostSummary())
                    batch.set(
                        postSaveRef,
                        mapOf(
                            "userId" to uid,
                            "postId" to post.postId,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit()
                    .addOnSuccessListener {
                        if (!isSaved) activityTrackingRepository.trackPostSaved(post)
                        onSuccess(!isSaved)
                    }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to update saved post")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load saved post state")
            }
    }

    // Checks whether the signed-in user has saved one post.
    fun isPostSavedByCurrentUser(
        postId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || postId.isBlank()) {
            onSuccess(false)
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(SAVED_POSTS_COLLECTION)
            .document(postId)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load saved post state")
            }
    }

    // Loads saved posts for the signed-in user by resolving saved ids back to source post documents.
    fun loadSavedPosts(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(SAVED_POSTS_COLLECTION)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(SAVED_POSTS_LIMIT)
            .get()
            .addOnSuccessListener { snapshot ->
                val postIds = snapshot.documents.map { document ->
                    document.getString("postId").orEmpty().ifBlank { document.id }
                }.filter { it.isNotBlank() }
                loadPostsByIds(postIds, excludeDanceActivity = true, onSuccess = onSuccess, onFailure = onFailure)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load saved posts")
            }
    }

    // Loads event posts the signed-in user registered for.
    fun loadRegisteredEventPosts(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(REGISTERED_EVENTS_COLLECTION)
            .orderBy("registeredAt", Query.Direction.DESCENDING)
            .limit(REGISTERED_EVENTS_LIMIT)
            .get()
            .addOnSuccessListener { snapshot ->
                val postIds = snapshot.documents.map { document ->
                    document.getString("postId").orEmpty().ifBlank { document.id }
                }.filter { it.isNotBlank() }
                loadPostsByIds(postIds, excludeDanceActivity = false, onSuccess = onSuccess, onFailure = onFailure)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load registered events")
            }
    }

    // Loads upcoming public dance activity posts for the dedicated Events entry point.
    fun loadUpcomingEventPosts(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.filter { post ->
                    post.postType == POST_TYPE_DANCE_ACTIVITY && !post.isPastActivityDate()
                }.sortedBy { it.activityDate.ifBlank { "9999-99-99" } }

                loadBlockedAuthorIds(
                    onSuccess = { blockedIds -> onSuccess(posts.filterNot { blockedIds.contains(it.authorId) }.take(30)) },
                    onFailure = { onSuccess(posts.take(30)) }
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load events")
            }
    }

    // Loads upcoming event posts ordered by the same recommendation profile used for the home feed.
    fun loadRecommendedEventPosts(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadUpcomingEventPosts(
            onSuccess = { posts ->
                rankForYouFeed(
                    posts = posts,
                    onSuccess = { ranked -> onSuccess(ranked.take(30)) }
                )
            },
            onFailure = onFailure
        )
    }

    // Toggles registration for a dance activity post.
    fun toggleEventRegistration(
        post: Post,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (post.postId.isBlank() || post.postType != POST_TYPE_DANCE_ACTIVITY) {
            onFailure("Event is not available")
            return
        }

        val userRegistrationRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(REGISTERED_EVENTS_COLLECTION)
            .document(post.postId)
        val postRegistrationRef = db.collection(Constants.Collections.POSTS)
            .document(post.postId)
            .collection(REGISTRATIONS_COLLECTION)
            .document(uid)

        db.runTransaction { transaction ->
            val userRegistrationSnapshot = transaction.get(userRegistrationRef)
            val postSnapshot = transaction.get(db.collection(Constants.Collections.POSTS).document(post.postId))
            val currentRegistrations = postSnapshot.getLong("registrationsCount") ?: 0L
            val currentWaitlist = postSnapshot.getLong("waitlistCount") ?: 0L
            val capacity = postSnapshot.getLong("activityCapacity") ?: 0L
            val isRegistered = userRegistrationSnapshot.exists()
            val wasWaitlisted = userRegistrationSnapshot.getBoolean("waitlisted") == true

            if (isRegistered) {
                transaction.delete(userRegistrationRef)
                transaction.delete(postRegistrationRef)
                transaction.update(
                    postSnapshot.reference,
                    if (wasWaitlisted) {
                        mapOf(
                            "registrationsCount" to currentRegistrations,
                            "waitlistCount" to (currentWaitlist - 1).coerceAtLeast(0)
                        )
                    } else {
                        mapOf(
                            "registrationsCount" to (currentRegistrations - 1).coerceAtLeast(0),
                            "waitlistCount" to currentWaitlist
                        )
                    }
                )
                false
            } else {
                val waitlisted = capacity > 0 && currentRegistrations >= capacity
                transaction.set(userRegistrationRef, post.eventRegistrationSummary(waitlisted))
                transaction.set(
                    postRegistrationRef,
                    mapOf(
                        "userId" to uid,
                        "postId" to post.postId,
                        "waitlisted" to waitlisted,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.update(
                    postSnapshot.reference,
                    if (waitlisted) {
                        mapOf(
                            "registrationsCount" to currentRegistrations,
                            "waitlistCount" to currentWaitlist + 1
                        )
                    } else {
                        mapOf(
                            "registrationsCount" to currentRegistrations + 1,
                            "waitlistCount" to currentWaitlist
                        )
                    }
                )
                true
            }
        }
            .addOnSuccessListener { isRegistered ->
                activityTrackingRepository.trackEventRegistration(post, isRegistered)
                if (isRegistered) createEventRegistrationNotification(post, uid)
                onSuccess(isRegistered)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update event registration")
            }
    }

    // Checks whether the signed-in user is registered to one dance activity post.
    fun isEventRegisteredByCurrentUser(
        post: Post,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null || post.postId.isBlank() || post.postType != POST_TYPE_DANCE_ACTIVITY) {
            onSuccess(false)
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(REGISTERED_EVENTS_COLLECTION)
            .document(post.postId)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load event registration")
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

        Log.d(TAG_COMMENT, "addComment entry uid=$currentUid postId=$postId commentPath=${commentRef.path}")
        commentRef.set(comment)
            .addOnSuccessListener {
                Log.d(TAG_COMMENT, "comment write success uid=$currentUid postId=$postId commentId=${commentRef.id}")
                syncCommentCountBestEffort(postId)
                createCommentNotification(postId, currentUid, author, cleanText, commentRef.id)
                trackPostSignal(postId) { activityTrackingRepository.trackPostCommented(it) }
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e(TAG_PERMISSION, "comment write failed uid=$currentUid postId=$postId path=${commentRef.path}", error)
                onFailure(error.message ?: "Failed to add comment")
            }
    }

    // Same idea as syncLikeCountBestEffort - recounts the comments subcollection and writes it
    // onto the post, logging rather than failing if the write doesn't go through.
    private fun syncCommentCountBestEffort(postId: String) {
        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        postRef.collection(COMMENTS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                postRef.update(
                    mapOf(
                        "commentsCount" to snapshot.size().toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { error ->
                    Log.w(TAG_PERMISSION, "comment count sync failed postId=$postId path=${postRef.path}", error)
                }
            }
            .addOnFailureListener { error ->
                Log.w(TAG_COMMENT, "comment count read failed postId=$postId", error)
            }
    }

    // Updates the current user's comment text.
    fun updateComment(
        postId: String,
        commentId: String,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        val cleanText = text.trim()
        if (postId.isBlank() || commentId.isBlank()) {
            onFailure("Missing comment id")
            return
        }
        if (cleanText.isBlank()) {
            onFailure("Comment cannot be empty")
            return
        }

        val commentRef = db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(COMMENTS_COLLECTION)
            .document(commentId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(commentRef)
            if (!snapshot.exists()) error("Comment was not found")
            if (snapshot.getString("authorId") != uid) error("You can only edit your own comments")
            transaction.update(
                commentRef,
                mapOf(
                    "text" to cleanText,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to update comment")
            }
    }

    // Deletes the current user's comment and keeps the parent post count aligned.
    fun deleteComment(
        postId: String,
        commentId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (postId.isBlank() || commentId.isBlank()) {
            onFailure("Missing comment id")
            return
        }

        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        val commentRef = postRef.collection(COMMENTS_COLLECTION).document(commentId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(commentRef)
            val postSnapshot = transaction.get(postRef)
            if (!snapshot.exists()) error("Comment was not found")
            if (!postSnapshot.exists()) error("Post was not found")
            if (snapshot.getString("authorId") != uid) error("You can only delete your own comments")
            val currentComments = postSnapshot.getLong("commentsCount") ?: 0L
            transaction.delete(commentRef)
            transaction.update(
                postRef,
                mapOf(
                    "commentsCount" to (currentComments - 1).coerceAtLeast(0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to delete comment")
            }
    }

    // Toggles the current user's like on one comment.
    fun toggleCommentLike(
        postId: String,
        commentId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (postId.isBlank() || commentId.isBlank()) {
            onFailure("Missing comment id")
            return
        }

        val likeRef = db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(COMMENTS_COLLECTION)
            .document(commentId)
            .collection(LIKES_COLLECTION)
            .document(uid)
        likeRef.get()
            .addOnSuccessListener { snapshot ->
                val isLiked = snapshot.exists()
                val write = if (isLiked) {
                    likeRef.delete()
                } else {
                    likeRef.set(
                        mapOf(
                            "userId" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                write.addOnSuccessListener { onSuccess(!isLiked) }
                    .addOnFailureListener { error ->
                        onFailure(error.message ?: "Failed to update comment like")
                    }
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load comment like")
            }
    }

    // Adds a reply to one comment and increments the parent post discussion count.
    fun addCommentReply(
        postId: String,
        commentId: String,
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
            onFailure("Reply author does not match the signed-in user")
            return
        }
        val cleanText = text.trim()
        if (postId.isBlank() || commentId.isBlank()) {
            onFailure("Missing comment id")
            return
        }
        if (cleanText.isBlank()) {
            onFailure("Reply cannot be empty")
            return
        }

        val postRef = db.collection(Constants.Collections.POSTS).document(postId)
        val replyRef = postRef.collection(COMMENTS_COLLECTION)
            .document(commentId)
            .collection(REPLIES_COLLECTION)
            .document()
        val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
        val reply = mapOf(
            "replyId" to replyRef.id,
            "postId" to postId,
            "commentId" to commentId,
            "authorId" to currentUid,
            "authorName" to authorName,
            "authorProfileImageUrl" to author.profileImageUrl,
            "text" to cleanText,
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.runTransaction { transaction ->
            val postSnapshot = transaction.get(postRef)
            if (!postSnapshot.exists()) error("Post was not found")
            val currentComments = postSnapshot.getLong("commentsCount") ?: 0L
            transaction.set(replyRef, reply)
            transaction.update(
                postRef,
                mapOf(
                    "commentsCount" to currentComments + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to add reply")
            }
    }

    // Notifies whoever owns the post - the studio's shared inbox when it was published as one,
    // otherwise the personal author - not necessarily whichever manager happened to write it.
    private fun createLikeNotification(postId: String, actorUid: String) {
        loadPostById(
            postId = postId,
            onSuccess = { post ->
                if (post.authorId == actorUid) return@loadPostById
                val authorRef = post.authorRef()
                UserRepository().getUserByUid(
                    uid = actorUid,
                    onSuccess = { actor ->
                        val actorName = "${actor.firstName} ${actor.lastName}".trim().ifBlank { "Dancer" }
                        notificationRepository.createNotification(
                            recipient = authorRef.toPartyRef(),
                            type = if (post.isStudioAuthored()) InAppNotification.Types.STUDIO_POST_LIKE else InAppNotification.Types.LIKE,
                            actorId = actorUid,
                            actorName = actorName,
                            actorProfileImageUrl = actor.profileImageUrl,
                            postId = post.postId,
                            title = "New like",
                            message = "$actorName liked your post.",
                            dedupeId = "like_${post.postId}_$actorUid"
                        )
                    },
                    onFailure = {}
                )
            },
            onFailure = {}
        )
    }

    // Notifies whoever owns the post that a new comment came in - same recipient logic as
    // createLikeNotification (the studio's inbox for studio posts, the personal author otherwise).
    private fun createCommentNotification(
        postId: String,
        actorUid: String,
        actor: User,
        text: String,
        commentId: String
    ) {
        loadPostById(
            postId = postId,
            onSuccess = { post ->
                if (post.authorId == actorUid) return@loadPostById
                val authorRef = post.authorRef()
                val actorName = "${actor.firstName} ${actor.lastName}".trim().ifBlank { "Dancer" }
                notificationRepository.createNotification(
                    recipient = authorRef.toPartyRef(),
                    type = if (post.isStudioAuthored()) InAppNotification.Types.STUDIO_POST_COMMENT else InAppNotification.Types.COMMENT,
                    actorId = actorUid,
                    actorName = actorName,
                    actorProfileImageUrl = actor.profileImageUrl,
                    postId = post.postId,
                    title = "New comment",
                    message = "$actorName commented: ${text.take(80)}",
                    dedupeId = "comment_${post.postId}_$commentId"
                )
            },
            onFailure = {}
        )
    }

    // Notifies the event organizer - the studio's shared inbox when it was published as one.
    private fun createEventRegistrationNotification(post: Post, actorUid: String) {
        if (post.authorId == actorUid) return
        val authorRef = post.authorRef()
        UserRepository().getUserByUid(
            uid = actorUid,
            onSuccess = { actor ->
                val actorName = "${actor.firstName} ${actor.lastName}".trim().ifBlank { "Dancer" }
                notificationRepository.createNotification(
                    recipient = authorRef.toPartyRef(),
                    type = InAppNotification.Types.EVENT_REGISTRATION,
                    actorId = actorUid,
                    actorName = actorName,
                    actorProfileImageUrl = actor.profileImageUrl,
                    postId = post.postId,
                    title = "New registration",
                    message = "$actorName registered for ${post.activityType.ifBlank { "your event" }}.",
                    dedupeId = "event_registration_${post.postId}_$actorUid"
                )
            },
            onFailure = {}
        )
    }

    // Loads recent comments for one post. commentLimit defaults to the full detail-screen amount,
    // but feed/list callers (HomeFragment, ProfileFragment's own-posts list) pass a much smaller
    // limit, since PostCardRenderer's inline preview only ever shows
    // PostCardRenderer.COMMENTS_PREVIEW_LIMIT comments anyway - there's no point fully
    // engagement-hydrating (2 extra reads per comment, for likes + replies) 30 comments on every
    // post in a ~50-post feed just to display 3 of them.
    fun loadComments(
        postId: String,
        commentLimit: Long = 30L,
        onSuccess: (List<PostComment>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        db.collection(Constants.Collections.POSTS)
            .document(postId)
            .collection(COMMENTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(commentLimit)
            .get()
            .addOnSuccessListener { snapshot ->
                val comments = snapshot.documents.mapNotNull { document ->
                    document.toObject(PostComment::class.java)?.copy(commentId = document.id, postId = postId)
                }
                loadBlockedAuthorIds(
                    onSuccess = { blockedIds ->
                        hydrateCommentEngagement(
                            comments = comments.filterNot { blockedIds.contains(it.authorId) },
                            onSuccess = onSuccess,
                            onFailure = { onSuccess(comments.filterNot { blockedIds.contains(it.authorId) }) }
                        )
                    },
                    onFailure = {
                        hydrateCommentEngagement(
                            comments = comments,
                            onSuccess = onSuccess,
                            onFailure = { onSuccess(comments) }
                        )
                    }
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load comments")
            }
    }

    // Loads likes and replies for each comment before rendering the comment sheet.
    private fun hydrateCommentEngagement(
        comments: List<PostComment>,
        onSuccess: (List<PostComment>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (comments.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val uid = auth.currentUser?.uid.orEmpty()
        val hydrated = MutableList<PostComment?>(comments.size) { null }
        var pendingLoads = comments.size
        var completed = false

        fun finish(index: Int, comment: PostComment) {
            if (completed) return
            hydrated[index] = comment
            pendingLoads -= 1
            if (pendingLoads == 0) {
                completed = true
                onSuccess(hydrated.filterNotNull())
            }
        }

        comments.forEachIndexed { index, comment ->
            val commentRef = db.collection(Constants.Collections.POSTS)
                .document(comment.postId)
                .collection(COMMENTS_COLLECTION)
                .document(comment.commentId)
            var likesCount: Long? = null
            var isLiked: Boolean? = null
            var replies: List<PostCommentReply>? = null

            fun finishIfReady() {
                val loadedLikes = likesCount
                val loadedIsLiked = isLiked
                val loadedReplies = replies
                if (loadedLikes != null && loadedIsLiked != null && loadedReplies != null) {
                    finish(
                        index,
                        comment.copy(
                            likesCount = loadedLikes,
                            isLikedByCurrentUser = loadedIsLiked,
                            replies = loadedReplies
                        )
                    )
                }
            }

            commentRef.collection(LIKES_COLLECTION)
                .get()
                .addOnSuccessListener { snapshot ->
                    likesCount = snapshot.size().toLong()
                    isLiked = uid.isNotBlank() && snapshot.documents.any { it.id == uid }
                    finishIfReady()
                }
                .addOnFailureListener {
                    likesCount = 0
                    isLiked = false
                    finishIfReady()
                }

            commentRef.collection(REPLIES_COLLECTION)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(COMMENT_REPLIES_LIMIT)
                .get()
                .addOnSuccessListener { snapshot ->
                    replies = snapshot.documents.mapNotNull { document ->
                        document.toObject(PostCommentReply::class.java)
                            ?.copy(replyId = document.id, postId = comment.postId, commentId = comment.commentId)
                    }
                    finishIfReady()
                }
                .addOnFailureListener {
                    replies = emptyList()
                    finishIfReady()
                }
        }
    }

    // Ranks posts using the current user's recommendation profile. Used by
    // loadRecommendedEventPosts, which has its own small, already-loaded candidate list and so
    // can just load the profile and rank afterward - loadForYouFeed has a bigger candidate set
    // and loads its profile in parallel with other independent fetches instead, so it calls
    // applyForYouRanking directly rather than going through this.
    private fun rankForYouFeed(
        posts: List<Post>,
        onSuccess: (List<Post>) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(RecommendationEngine.rankPosts(posts, PostRecommendationProfile()))
            return
        }

        userRepository.loadRecommendationProfile(
            onSuccess = { profile -> onSuccess(applyForYouRanking(posts, uid, profile)) },
            onFailure = { error ->
                Log.w(TAG_PERMISSION, "rankForYouFeed falling back to non-personalized ranking uid=$uid (recommendation profile failed to load): $error")
                onSuccess(RecommendationEngine.rankPosts(posts, PostRecommendationProfile()))
            }
        )
    }

    // Builds the ranking context from an already-loaded profile and runs ForYouRankingStrategy
    // over the candidates - the shared final step behind both loadForYouFeed and rankForYouFeed.
    private fun applyForYouRanking(posts: List<Post>, uid: String, profile: RecommendationProfile): List<Post> {
        val context = RecommendationContext(
            userId = uid,
            surface = RecommendationSurface.FOR_YOU,
            profileLocation = profile.profileLocation,
            preferredRecommendationArea = profile.preferredRecommendationArea,
            danceStyles = profile.danceStyles,
            danceLevel = profile.danceLevel,
            recommendationProfile = profile
        )
        val ranked = ForYouRankingStrategy.rank(items = posts, context = context)
        logForYouExplanations(ranked.take(10), context)
        return ranked
    }

    // Debug-only logging of how the For You feed scored its top posts, so ranking behavior can be
    // checked from Logcat during development. Compiled out of release builds.
    private fun logForYouExplanations(posts: List<Post>, context: RecommendationContext) {
        if (!BuildConfig.DEBUG) return
        posts.forEach { post ->
            val explanation = ForYouRankingStrategy.score(post, context)
            Log.d(
                "RecommendationDebug",
                buildString {
                    append("ForYou item=${post.postId} score=${"%.1f".format(explanation.finalScore)}")
                    append(" mode=${explanation.exploreOrExploit}")
                    append(" strategy=${explanation.rankingStrategy}")
                    if (explanation.diversityAdjustments.isNotEmpty()) {
                        append(" diversity=${explanation.diversityAdjustments.joinToString("; ")}")
                    }
                    append(" reasons=${explanation.reasons.joinToString("; ")}")
                }
            )
        }
    }

    // Loads followed user ids (dancers + teachers) for the following feed. Followed studios are
    // loaded separately since their posts are queried by authorEntityId, not authorId.
    private fun loadFollowedUserIds(
        onSuccess: (List<String>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var pendingLoads = 2
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
    }

    // Loads public posts written by followed users' personal accounts or published by followed
    // studios' business accounts.
    private fun loadPostsByFollowedAuthors(
        followedUserIds: List<String>,
        followedStudioIds: List<String>,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userChunks = followedUserIds.chunked(FIRESTORE_WHERE_IN_LIMIT)
        val studioChunks = followedStudioIds.chunked(FIRESTORE_WHERE_IN_LIMIT)
        var pendingLoads = userChunks.size + studioChunks.size
        if (pendingLoads == 0) {
            onSuccess(emptyList())
            return
        }
        val posts = mutableListOf<Post>()
        var completed = false

        fun finishIfReady() {
            if (completed) return
            pendingLoads -= 1
            if (pendingLoads > 0) return
            completed = true
            val userSet = followedUserIds.toSet()
            val studioSet = followedStudioIds.toSet()
            val relevant = posts.distinctBy { it.postId }.filter { post ->
                (!post.isStudioAuthored() && post.authorId in userSet) ||
                    (post.isStudioAuthored() && post.authorEntityId in studioSet)
            }
            loadHiddenPostIds(
                onSuccess = { hiddenIds ->
                    loadBlockedAuthorIds(
                        onSuccess = { blockedIds ->
                            onSuccess(
                                relevant
                                    .filterNot { hiddenIds.contains(it.postId) || blockedIds.contains(it.authorId) }
                                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                            )
                        },
                        onFailure = {
                            onSuccess(
                                relevant
                                    .filterNot { hiddenIds.contains(it.postId) }
                                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
                            )
                        }
                    )
                },
                onFailure = {
                    onSuccess(relevant.sortedByDescending { it.createdAt?.seconds ?: 0L })
                }
            )
        }

        userChunks.forEach { authorIds ->
            db.collection(Constants.Collections.POSTS)
                .whereEqualTo("visibility", "public")
                .whereIn("authorId", authorIds)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    posts.addAll(snapshot.documents.mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(postId = document.id)
                    })
                    finishIfReady()
                }
                .addOnFailureListener { error ->
                    if (completed) return@addOnFailureListener
                    completed = true
                    onFailure(error.message ?: "Failed to load following feed")
                }
        }

        studioChunks.forEach { studioIds ->
            db.collection(Constants.Collections.POSTS)
                .whereEqualTo("visibility", "public")
                .whereIn("authorEntityId", studioIds)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    posts.addAll(snapshot.documents.mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(postId = document.id)
                    })
                    finishIfReady()
                }
                .addOnFailureListener { error ->
                    if (completed) return@addOnFailureListener
                    completed = true
                    onFailure(error.message ?: "Failed to load following feed")
                }
        }
    }

    // Loads post ids the current user asked not to see in feeds.
    private fun loadHiddenPostIds(
        onSuccess: (Set<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onSuccess(emptySet())
            return
        }

        db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection(HIDDEN_POSTS_COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(
                    snapshot.documents.map { document ->
                        document.getString("postId").orEmpty().ifBlank { document.id }
                    }.filter { it.isNotBlank() }.toSet()
                )
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load hidden posts")
            }
    }

    // Loads blocked author ids so feeds and comments can omit content from blocked profiles.
    private fun loadBlockedAuthorIds(
        onSuccess: (Set<String>) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        userRepository.loadBlockedUserIds(onSuccess = onSuccess, onFailure = onFailure)
    }

    // Loads a known set of post ids while preserving the caller's order.
    private fun loadPostsByIds(
        postIds: List<String>,
        excludeDanceActivity: Boolean,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postIds.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        val order = postIds.withIndex().associate { it.value to it.index }
        val chunks = postIds.distinct().chunked(FIRESTORE_WHERE_IN_LIMIT)
        var pendingLoads = chunks.size
        val posts = mutableListOf<Post>()
        var completed = false

        chunks.forEach { ids ->
            db.collection(Constants.Collections.POSTS)
                .whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    posts.addAll(snapshot.documents.mapNotNull { document ->
                        document.toObject(Post::class.java)?.copy(postId = document.id)
                    }.filter { post -> !excludeDanceActivity || post.postType != POST_TYPE_DANCE_ACTIVITY })
                    pendingLoads -= 1
                    if (pendingLoads == 0) {
                        completed = true
                        onSuccess(posts.sortedBy { order[it.postId] ?: Int.MAX_VALUE })
                    }
                }
                .addOnFailureListener { error ->
                    if (completed) return@addOnFailureListener
                    completed = true
                    onFailure(error.message ?: "Failed to load saved posts")
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

    // Stores enough denormalized metadata for a saved-post list without replacing the source post.
    private fun Post.savedPostSummary(): Map<String, Any> {
        return mapOf(
            "postId" to postId,
            "authorId" to authorId,
            "authorName" to authorName,
            "authorProfileImageUrl" to authorProfileImageUrl,
            "authorType" to authorType,
            "text" to text.take(240),
            "mediaType" to mediaType,
            "postType" to postType,
            "activityType" to activityType,
            "activityLocation" to activityLocation,
            "activityDate" to activityDate,
            "activityTime" to activityTime,
            "savedAt" to FieldValue.serverTimestamp()
        )
    }

    // Stores enough event details to show a user's registered events later.
    private fun Post.eventRegistrationSummary(waitlisted: Boolean): Map<String, Any> {
        return mapOf(
            "postId" to postId,
            "eventName" to activityType.ifBlank { text.take(80) },
            "authorId" to authorId,
            "authorName" to authorName,
            "activityLocation" to activityLocation,
            "activityDate" to activityDate,
            "activityTime" to activityTime,
            "waitlisted" to waitlisted,
            "registeredAt" to FieldValue.serverTimestamp()
        )
    }

    // Checks query text against regular, collaboration, and dance activity post fields.
    private fun Post.matchesQuery(query: String): Boolean {
        return listOf(
            authorName,
            authorType,
            text,
            postType,
            activityType,
            activityLocation,
            activityDate,
            activityDescription,
            collaborationLookingFor,
            collaborationStyle,
            collaborationLocation,
            collaborationDescription
        ).any { it.contains(query, ignoreCase = true) }
    }

    // Checks whether a dance activity's date has passed, trying a few known date formats since
    // older posts and newer ones don't all store the date the same way. Gives a day of grace
    // before treating something as past, so an event happening later today doesn't disappear
    // from feeds first thing in the morning.
    private fun Post.isPastActivityDate(): Boolean {
        val rawDate = activityDate.trim()
        if (rawDate.isBlank()) return false
        val parsed = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MMM d, yyyy").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(rawDate) }.getOrNull()
        } ?: return false
        return parsed.before(Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L))
    }

    private companion object {
        const val TAG_LIKE = "PostLikeDebug"
        const val TAG_COMMENT = "PostCommentDebug"
        const val TAG_PERMISSION = "FirestorePermissionDebug"
        const val FIRESTORE_WHERE_IN_LIMIT = 10
        const val POST_TYPE_REGULAR = "regular"
        const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
        const val POST_TYPE_REPOST = "repost"
        const val LIKES_COLLECTION = "likes"
        const val COMMENTS_COLLECTION = "comments"
        const val SAVES_COLLECTION = "saves"
        const val REPLIES_COLLECTION = "replies"
        const val SAVED_POSTS_COLLECTION = "savedPosts"
        const val HIDDEN_POSTS_COLLECTION = "hiddenPosts"
        const val REGISTRATIONS_COLLECTION = "registrations"
        const val REGISTERED_EVENTS_COLLECTION = "registeredEvents"
        const val SAVED_POSTS_LIMIT = 50L
        const val COMMENT_REPLIES_LIMIT = 8L
        const val REGISTERED_EVENTS_LIMIT = 50L
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
