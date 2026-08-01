// Every tunable number the recommendation engine uses to turn a raw behavioral signal (a like, a
// save, a hide) into a score change - kept in one file so the actual weighting can be reasoned
// about and adjusted without hunting through the scoring logic itself.
package com.ana.theflow.data.recommendation

// How much each user action should move their learned recommendation score. The first block is
// the base weight per action (a like moves it less than a save, a hide moves it a lot in the
// other direction); the "*_FACTOR" constants below control how much of that weight actually
// applies to each dimension - style, creator, location, and so on - for a given action.
object RecommendationSignalWeights {
    const val IMPRESSION = 0.0
    const val FAST_SKIP = -0.25
    const val SHORT_VIEW = 0.5
    const val MEANINGFUL_VIEW = 1.5
    const val NEAR_COMPLETE_VIEW = 2.5
    const val OPEN_POST = 2.0
    const val VIEW_PROFILE = 3.0
    const val LIKE = 3.0
    const val COMMENT = 4.0
    const val SAVE = 5.0
    const val SHARE = 5.0
    const val OPEN_EVENT = 3.0
    const val REGISTER_EVENT = 8.0
    const val START_NAVIGATION = 8.0
    const val HIDE = -8.0
    const val NOT_INTERESTED = -8.0
    const val UNFOLLOW = -6.0
    const val CANCEL_REGISTRATION = -2.0
    const val FOLLOW = 4.0
    const val SEARCH = 1.5

    const val STYLE_FACTOR = 1.0
    const val CREATOR_FACTOR = 0.8
    const val TEACHER_FACTOR = 0.7
    const val STUDIO_FACTOR = 0.6
    const val CONTENT_TYPE_FACTOR = 0.5
    const val MEDIA_TYPE_FACTOR = 0.4
    const val LOCATION_FACTOR = 0.25
    const val LEVEL_FACTOR = 0.3
    const val CREATOR_TYPE_FACTOR = 0.5

    const val OPEN_PROFILE_CREATOR_FACTOR = 1.0
    const val OPEN_PROFILE_CREATOR_TYPE_FACTOR = 0.8
    const val OPEN_PROFILE_STYLE_FACTOR = 0.2
    const val OPEN_PROFILE_LOCATION_FACTOR = 0.1

    const val REGISTRATION_STYLE_FACTOR = 1.0
    const val REGISTRATION_LOCATION_FACTOR = 0.9
    const val REGISTRATION_TEACHER_FACTOR = 0.8
    const val REGISTRATION_STUDIO_FACTOR = 0.8
    const val REGISTRATION_LEVEL_FACTOR = 0.7
    const val REGISTRATION_CONTENT_TYPE_FACTOR = 0.9

    const val MAX_DIMENSION_SCORE = 100.0
}
