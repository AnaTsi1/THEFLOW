// This defines which identity - personal or a studio - is currently "active" for whoever's
// signed in, plus the small helpers for saving and showing that choice.
package com.ana.theflow.data.model.account

import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.utilities.Constants

// Everyone always has a Personal account. StudioAccount only shows up if they actually manage
// that studio. Whichever one is active decides who new posts/events/jobs/messages get credited to.
sealed class ActiveAccount {
    abstract val userUid: String

    // Just the signed-in person, acting as themselves.
    data class Personal(override val userUid: String) : ActiveAccount()

    // The signed-in person, but posting/acting as one specific studio they manage.
    data class StudioAccount(override val userUid: String, val studioId: String) : ActiveAccount()

    // "user" or "studio" - whichever this account counts as.
    val entityType: String
        get() = if (this is StudioAccount) Constants.EntityType.STUDIO else Constants.EntityType.USER

    // The id that new content should be credited to - the studio's id, or just the user's own uid.
    val entityId: String
        get() = if (this is StudioAccount) studioId else userUid

    // Key we use for shared map fields (like unread counts) since a studio and a user need
    // different-looking keys to avoid clashing.
    val partyKey: String
        get() = if (this is StudioAccount) "studio_$studioId" else userUid

    // Turns this into a plain string so we can stash it in SharedPreferences.
    fun serialize(): String {
        return when (this) {
            is Personal -> VALUE_PERSONAL
            is StudioAccount -> "$PREFIX_STUDIO$studioId"
        }
    }

    companion object {
        private const val VALUE_PERSONAL = "personal"
        private const val PREFIX_STUDIO = "studio:"

        // Reverses serialize() - takes the saved string back and rebuilds the account.
        fun parse(raw: String, userUid: String): ActiveAccount {
            if (raw.startsWith(PREFIX_STUDIO)) {
                val studioId = raw.removePrefix(PREFIX_STUDIO)
                if (studioId.isNotBlank()) return StudioAccount(userUid = userUid, studioId = studioId)
            }
            return Personal(userUid = userUid)
        }
    }
}

// One row in the account switcher - either the personal account or one of the studios someone manages.
data class AccountSummary(
    val account: ActiveAccount,
    val displayName: String,
    val subtitle: String,
    val imageUrl: String,
    val isVerified: Boolean = false
)

// Quick helper to turn a Studio into the StudioAccount version of it.
fun Studio.toActiveAccount(userUid: String): ActiveAccount.StudioAccount {
    return ActiveAccount.StudioAccount(userUid = userUid, studioId = id)
}
