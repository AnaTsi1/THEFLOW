package com.ana.theflow.data.model.account

import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.utilities.Constants

// The account currently "active" in the app. Every real person always has a Personal account;
// a StudioAccount is only ever available when the signed-in user manages that studio. Switching
// which one is active drives who posts/events/jobs/messages are attributed to.
sealed class ActiveAccount {
    abstract val userUid: String

    data class Personal(override val userUid: String) : ActiveAccount()
    data class StudioAccount(override val userUid: String, val studioId: String) : ActiveAccount()

    val entityType: String
        get() = if (this is StudioAccount) Constants.EntityType.STUDIO else Constants.EntityType.USER

    val entityId: String
        get() = if (this is StudioAccount) studioId else userUid

    // Key used for map-based fields shared by multiple parties (unread counts, party info).
    val partyKey: String
        get() = if (this is StudioAccount) "studio_$studioId" else userUid

    fun serialize(): String {
        return when (this) {
            is Personal -> VALUE_PERSONAL
            is StudioAccount -> "$PREFIX_STUDIO$studioId"
        }
    }

    companion object {
        private const val VALUE_PERSONAL = "personal"
        private const val PREFIX_STUDIO = "studio:"

        fun parse(raw: String, userUid: String): ActiveAccount {
            if (raw.startsWith(PREFIX_STUDIO)) {
                val studioId = raw.removePrefix(PREFIX_STUDIO)
                if (studioId.isNotBlank()) return StudioAccount(userUid = userUid, studioId = studioId)
            }
            return Personal(userUid = userUid)
        }
    }
}

// Display model for the account switcher.
data class AccountSummary(
    val account: ActiveAccount,
    val displayName: String,
    val subtitle: String,
    val imageUrl: String,
    val isVerified: Boolean = false
)

// Convenience wrapper pairing a studio with its ActiveAccount representation.
fun Studio.toActiveAccount(userUid: String): ActiveAccount.StudioAccount {
    return ActiveAccount.StudioAccount(userUid = userUid, studioId = id)
}
