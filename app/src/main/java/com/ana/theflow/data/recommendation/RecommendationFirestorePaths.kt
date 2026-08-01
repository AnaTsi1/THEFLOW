package com.ana.theflow.data.recommendation

object RecommendationFirestorePaths {
    fun profile(uid: String): String = "users/$uid/recommendationProfile/main"

    fun signalDedupe(uid: String, dedupeKey: String): String {
        return "${profile(uid)}/signalDedupe/$dedupeKey"
    }

    fun impressions(uid: String): String = "users/$uid/recommendationImpressions"
}
