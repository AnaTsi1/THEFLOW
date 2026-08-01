package com.ana.theflow.data.repository

import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.recommendation.DiscoverRankingStrategy
import com.ana.theflow.data.recommendation.ForYouRankingStrategy
import com.ana.theflow.data.recommendation.RecommendationContext
import com.ana.theflow.data.recommendation.RecommendationProfile
import com.ana.theflow.data.recommendation.RecommendationScoreExplanation
import com.ana.theflow.data.recommendation.RecommendationSurface
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// Powers the admin recommendation-insights screen: builds a real RecommendationContext for any
// chosen user and runs it through the actual ForYouRankingStrategy/DiscoverRankingStrategy, so
// an admin sees a genuine preview of that user's feed rather than a guess. Kept separate from
// DiscoveryRepository's singleton, which is scoped to whichever person is signed in on the
// device - here the admin needs to inspect an arbitrary other user's profile instead.
class AdminRecommendationRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val discoveryRepository = DiscoveryRepository

    // One ranked Home feed post, plus the score breakdown behind why it landed where it did.
    data class RankedPost(val post: Post, val explanation: RecommendationScoreExplanation)

    // Same idea for a ranked Discover item.
    data class RankedItem(val item: DiscoveryItem, val explanation: RecommendationScoreExplanation)

    // Everything the insights screen shows for one user - their profile plus a live preview of both feeds.
    data class Snapshot(
        val user: User,
        val profile: RecommendationProfile,
        val homeForYou: List<RankedPost>,
        val discoverRecommended: List<RankedItem>
    )

    // Loads a full snapshot for the given user, for an admin to look at. Fails if whoever's calling this isn't an admin.
    fun loadSnapshot(
        targetUid: String,
        onSuccess: (Snapshot) -> Unit,
        onFailure: (String) -> Unit
    ) {
        ensureAdmin(
            onSuccess = {
                userRepository.loadRecommendationProfileFor(
                    uid = targetUid,
                    onSuccess = { user, profile -> loadCandidatesAndRank(user, profile, onSuccess, onFailure) },
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    // Loads the posts and discovery items we'll rank, then builds the snapshot from them.
    private fun loadCandidatesAndRank(
        user: User,
        profile: RecommendationProfile,
        onSuccess: (Snapshot) -> Unit,
        onFailure: (String) -> Unit
    ) {
        loadCandidatePosts(
            onSuccess = { posts ->
                loadCandidateDiscoveryItems(
                    onSuccess = { items ->
                        onSuccess(buildSnapshot(user, profile, posts, items))
                    },
                    onFailure = onFailure
                )
            },
            onFailure = onFailure
        )
    }

    // Runs the real ranking strategies for both feeds and packages up the results with their score explanations.
    private fun buildSnapshot(
        user: User,
        profile: RecommendationProfile,
        posts: List<Post>,
        items: List<DiscoveryItem>
    ): Snapshot {
        val forYouContext = RecommendationContext(
            userId = user.uid,
            surface = RecommendationSurface.FOR_YOU,
            profileLocation = profile.profileLocation,
            preferredRecommendationArea = profile.preferredRecommendationArea,
            danceStyles = profile.danceStyles,
            danceLevel = profile.danceLevel,
            recommendationProfile = profile
        )
        val discoverContext = RecommendationContext(
            userId = user.uid,
            surface = RecommendationSurface.DISCOVER,
            profileLocation = profile.profileLocation,
            preferredRecommendationArea = profile.preferredRecommendationArea,
            danceStyles = profile.danceStyles,
            danceLevel = profile.danceLevel,
            recommendationProfile = profile
        )

        val rankedPosts = ForYouRankingStrategy.rank(posts, forYouContext).take(MAX_RESULTS)
        val homeForYou = rankedPosts.map { RankedPost(it, ForYouRankingStrategy.score(it, forYouContext)) }

        val rankedItems = DiscoverRankingStrategy.rank(items, discoverContext).take(MAX_RESULTS)
        val discoverRecommended = rankedItems.map { RankedItem(it, DiscoverRankingStrategy.score(it, discoverContext)) }

        return Snapshot(user, profile, homeForYou, discoverRecommended)
    }

    // Loads public posts to rank for the target user. We don't apply the admin's own
    // hidden/blocked-author filters here, since those belong to whoever's actually using the app
    // right now, not the person we're previewing.
    private fun loadCandidatePosts(onSuccess: (List<Post>) -> Unit, onFailure: (String) -> Unit) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .limit(150)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }
                onSuccess(posts)
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load candidate posts") }
    }

    // Loads studios and published activities to rank for Discover (we leave teachers out of this preview).
    private fun loadCandidateDiscoveryItems(onSuccess: (List<DiscoveryItem>) -> Unit, onFailure: (String) -> Unit) {
        discoveryRepository.loadApprovedStudios(
            onSuccess = { studios ->
                discoveryRepository.loadPublishedActivities(
                    onSuccess = { activities -> onSuccess(studios + activities) },
                    onFailure = { onSuccess(studios) }
                )
            },
            onFailure = onFailure
        )
    }

    // Checks that whoever's signed in is actually an admin before we let them look at someone else's profile.
    private fun ensureAdmin(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            onFailure("User is not logged in")
            return
        }
        db.collection(Constants.Collections.USERS)
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role").orEmpty()
                if (role.equals("admin", ignoreCase = true) || role.equals("ADMIN", ignoreCase = true)) {
                    onSuccess()
                } else {
                    onFailure("Admin access required")
                }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to verify admin access") }
    }

    private companion object {
        const val MAX_RESULTS = 12
    }
}
