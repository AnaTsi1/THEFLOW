package com.ana.theflow.data.model.user

import com.ana.theflow.utilities.Constants

data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val age: Int = 0,
    val email: String = "",
    val role: String = Constants.UserRole.DANCER.firestoreValue,
    val verifiedTeacher: Boolean = false,
    val verifiedChoreographer: Boolean = false,
    val professionalBadges: List<String> = emptyList(),
    val managedStudioIds: List<String> = emptyList(),
    val onboardingCompleted: Boolean = false,
    val coverImageUrl: String = "",
    val danceStyles: List<String> = emptyList(),
    val danceLevel: String = "",
    val location: String = "",
    val profileImageUrl: String = "",
    val headline: String = "",
    val bio: String = "",
    val yearsOfExperience: String = "",
    val studiosTrainedAt: List<String> = emptyList(),
    val teachersLearnedFrom: List<String> = emptyList(),
    val performancesCompetitions: List<String> = emptyList(),
    val availability: String = "",
    val instagramUrl: String = "",
    val tiktokUrl: String = "",
    val youtubeUrl: String = "",
    val portfolioMediaUrls: List<String> = emptyList()
)
