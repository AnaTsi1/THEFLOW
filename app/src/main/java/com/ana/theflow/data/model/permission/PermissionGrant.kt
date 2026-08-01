package com.ana.theflow.data.model.permission

import com.google.firebase.Timestamp

// Admin-only audit record of every permission mutation (grant, revoke, verify, ownership change).
data class PermissionGrant(
    val grantId: String = "",
    val adminUid: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val action: String = "",
    val studioId: String = "",
    val note: String = "",
    val createdAt: Timestamp? = null
) {
    object Actions {
        const val GRANT_TEACHER = "GRANT_TEACHER"
        const val REVOKE_TEACHER = "REVOKE_TEACHER"
        const val GRANT_CHOREOGRAPHER = "GRANT_CHOREOGRAPHER"
        const val REVOKE_CHOREOGRAPHER = "REVOKE_CHOREOGRAPHER"
        const val ADD_MANAGER = "ADD_MANAGER"
        const val REMOVE_MANAGER = "REMOVE_MANAGER"
        const val SET_OWNER = "SET_OWNER"
        const val VERIFY_STUDIO = "VERIFY_STUDIO"
        const val UNVERIFY_STUDIO = "UNVERIFY_STUDIO"
        const val APPROVE_STUDIO_REQUEST = "APPROVE_STUDIO_REQUEST"
        const val REJECT_STUDIO_REQUEST = "REJECT_STUDIO_REQUEST"
    }
}
