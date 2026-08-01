package com.ana.theflow.utilities

import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User

// Single place permission logic lives. Permissions are additive: a user can simultaneously be
// a dancer, a verified teacher/choreographer, and a manager of any number of studios. Nothing
// here should ever brand a user with one exclusive role except admin.
object AccountPermissions {
    fun isAdmin(user: User): Boolean {
        return user.role.equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true) ||
            user.role.equals(Constants.UserRole.ADMIN.name, ignoreCase = true)
    }

    fun isVerifiedTeacher(user: User): Boolean = user.verifiedTeacher

    fun isVerifiedChoreographer(user: User): Boolean = user.verifiedChoreographer

    fun managedStudioIds(user: User): List<String> {
        return user.managedStudioIds.filter { it.isNotBlank() }
    }

    fun managesAnyStudio(user: User): Boolean = managedStudioIds(user).isNotEmpty()

    fun manages(user: User, studioId: String): Boolean {
        return studioId.isNotBlank() && studioId in managedStudioIds(user)
    }

    fun canRequestStudio(user: User): Boolean = true

    fun canPublishJobs(user: User, studioId: String): Boolean {
        return isAdmin(user) || manages(user, studioId)
    }

    fun canEditStudio(user: User, studio: Studio): Boolean {
        return isAdmin(user) || studio.ownerUid == user.uid || user.uid in studio.managerUids
    }

    // Display-only badges. Never used for authorization - authorization always reads the
    // underlying verifiedTeacher/verifiedChoreographer/managedStudioIds fields directly.
    fun badges(user: User): List<String> {
        val badges = mutableListOf<String>()
        if (user.verifiedTeacher) badges.add("Verified Teacher")
        if (user.verifiedChoreographer) badges.add("Choreographer")
        if (managesAnyStudio(user)) badges.add("Studio Manager")
        return badges
    }
}
