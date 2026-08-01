// All the Firestore paths the recommendation engine reads/writes, kept in one place so we're not
// hardcoding the same path shape all over the codebase.
package com.ana.theflow.data.recommendation

object RecommendationFirestorePaths {
    // Where one user's recommendation profile document lives.
    fun profile(uid: String): String = "users/$uid/recommendationProfile/main"

    // Where we track "have we already counted this signal" so a repeat like/save doesn't
    // double-count.
    fun signalDedupe(uid: String, dedupeKey: String): String {
        return "${profile(uid)}/signalDedupe/$dedupeKey"
    }

    // Where we log what's been shown to a user, for recommendation impressions.
    fun impressions(uid: String): String = "users/$uid/recommendationImpressions"
}
