// Identifies one side of a conversation or authorship relationship - a person or a studio
// business account - independent of which real Firebase user is acting.
package com.ana.theflow.data.model.messaging

import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.post.AuthorRef
import com.ana.theflow.utilities.Constants

// Identifies one side of a conversation - a person or a studio. Unlike ActiveAccount, this can
// also describe the OTHER party, who might not be the signed-in user at all.
data class PartyRef(
    val type: String,
    val id: String,
    val key: String
) {
    companion object {
        // Builds a PartyRef for a person from their uid.
        fun user(uid: String): PartyRef = PartyRef(Constants.EntityType.USER, uid, uid)

        // Builds a PartyRef for a studio from its studio id.
        fun studio(studioId: String): PartyRef = PartyRef(Constants.EntityType.STUDIO, studioId, "studio_$studioId")
    }
}

// Turns whichever account is currently active into its PartyRef.
fun ActiveAccount.toPartyRef(): PartyRef = PartyRef(type = entityType, id = entityId, key = partyKey)

// Turns a post's author reference into its PartyRef.
fun AuthorRef.toPartyRef(): PartyRef = if (type == Constants.EntityType.STUDIO) PartyRef.studio(id) else PartyRef.user(id)
