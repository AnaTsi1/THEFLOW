// Everything to do with reading and managing a studio's business profile - loading, searching,
// following/unfollowing, and editing the profile fields a manager is allowed to change.
package com.ana.theflow.data.repository

import com.ana.theflow.data.model.messaging.PartyRef
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class StudioRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationRepository = NotificationRepository()

    // Loads one studio by id.
    fun loadStudio(studioId: String, onSuccess: (Studio) -> Unit, onFailure: (String) -> Unit) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        db.collection(Constants.Collections.STUDIOS).document(studioId).get()
            .addOnSuccessListener { document ->
                val studio = document.toObject(Studio::class.java)?.copy(id = document.id)
                if (studio != null) onSuccess(studio) else onFailure("Studio was not found")
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load studio") }
    }

    // Listens for live changes to one studio's business profile.
    fun listenToStudio(studioId: String, onUpdate: (Studio) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        if (studioId.isBlank()) return null
        return db.collection(Constants.Collections.STUDIOS).document(studioId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load studio")
                    return@addSnapshotListener
                }
                val studio = snapshot?.toObject(Studio::class.java)?.copy(id = snapshot.id)
                if (studio != null) onUpdate(studio)
            }
    }

    // Loads a known set of studios while preserving the caller's order.
    fun loadStudiosByIds(ids: List<String>, onSuccess: (List<Studio>) -> Unit, onFailure: (String) -> Unit) {
        val clean = ids.distinct().filter { it.isNotBlank() }
        if (clean.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        val order = clean.withIndex().associate { it.value to it.index }
        val chunks = clean.chunked(FIRESTORE_WHERE_IN_LIMIT)
        val studios = mutableListOf<Studio>()
        var pending = chunks.size
        var completed = false

        chunks.forEach { chunk ->
            db.collection(Constants.Collections.STUDIOS)
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (completed) return@addOnSuccessListener
                    studios.addAll(snapshot.documents.mapNotNull { document ->
                        document.toObject(Studio::class.java)?.copy(id = document.id)
                    })
                    pending -= 1
                    if (pending == 0) {
                        completed = true
                        onSuccess(studios.sortedBy { order[it.id] ?: Int.MAX_VALUE })
                    }
                }
                .addOnFailureListener { error ->
                    if (!completed) {
                        completed = true
                        onFailure(error.message ?: "Failed to load studios")
                    }
                }
        }
    }

    // Loads every studio a user currently manages.
    fun loadManagedStudios(uid: String, onSuccess: (List<Studio>) -> Unit, onFailure: (String) -> Unit) {
        if (uid.isBlank()) {
            onSuccess(emptyList())
            return
        }
        db.collection(Constants.Collections.STUDIOS)
            .whereArrayContains("managerUids", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { document ->
                    document.toObject(Studio::class.java)?.copy(id = document.id)
                })
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load managed studios") }
    }

    // Name search for admin tooling (e.g. assigning a manager) - unlike searchStudios() below,
    // this isn't limited to verified studios, since an admin may need to find any studio
    // regardless of its verification/claim state.
    fun searchStudiosByName(
        query: String,
        onSuccess: (List<Studio>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val normalizedQuery = query.trim()
        db.collection(Constants.Collections.STUDIOS)
            .limit(80)
            .get()
            .addOnSuccessListener { snapshot ->
                val studios = snapshot.documents.mapNotNull { document ->
                    document.toObject(Studio::class.java)?.copy(id = document.id)
                }.filter { studio ->
                    normalizedQuery.isBlank() ||
                        studio.displayName.contains(normalizedQuery, ignoreCase = true) ||
                        studio.city.contains(normalizedQuery, ignoreCase = true)
                }.sortedBy { it.displayName }
                onSuccess(studios)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to search studios")
            }
    }

    // Searches verified studios by location and style.
    fun searchStudios(
        location: String,
        danceStyle: String,
        onSuccess: (List<Studio>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var query = db.collection(Constants.Collections.STUDIOS)
            .whereEqualTo("verified", true)

        if (location.isNotBlank()) {
            query = query.whereEqualTo("city", location.trim())
        }

        if (danceStyle.isNotBlank()) {
            query = query.whereArrayContains("danceStyles", danceStyle.trim())
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val studios = snapshot.documents.mapNotNull { document ->
                    document.toObject(Studio::class.java)?.copy(id = document.id)
                }
                onSuccess(studios)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to search studios")
            }
    }

    // Toggles the signed-in user's follow relationship with a studio's business account.
    fun toggleFollowStudio(
        studioId: String,
        viewer: User,
        onSuccess: (Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onFailure("User is not logged in")
            return
        }
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        val followerRef = db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .collection("followers")
            .document(uid)
        val followingRef = db.collection(Constants.Collections.USERS)
            .document(uid)
            .collection("followingStudios")
            .document(studioId)

        followerRef.get()
            .addOnSuccessListener { snapshot ->
                val isFollowing = snapshot.exists()
                val batch = db.batch()
                if (isFollowing) {
                    batch.delete(followerRef)
                    batch.delete(followingRef)
                    batch.update(
                        db.collection(Constants.Collections.STUDIOS).document(studioId),
                        "followersCount", FieldValue.increment(-1)
                    )
                } else {
                    batch.set(
                        followerRef,
                        mapOf("userId" to uid, "name" to viewer.fullName(), "profileImageUrl" to viewer.profileImageUrl, "followedAt" to FieldValue.serverTimestamp())
                    )
                    batch.set(
                        followingRef,
                        mapOf("targetId" to studioId, "studioId" to studioId, "followedAt" to FieldValue.serverTimestamp())
                    )
                    batch.update(
                        db.collection(Constants.Collections.STUDIOS).document(studioId),
                        "followersCount", FieldValue.increment(1)
                    )
                }
                batch.commit()
                    .addOnSuccessListener {
                        if (!isFollowing) createStudioFollowNotification(studioId, viewer)
                        onSuccess(!isFollowing)
                    }
                    .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update follow state") }
            }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load follow state") }
    }

    // Checks if the signed-in user already follows this studio.
    fun isFollowingStudio(studioId: String, onSuccess: (Boolean) -> Unit, onFailure: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid
        if (uid == null || studioId.isBlank()) {
            onSuccess(false)
            return
        }
        db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .collection("followers")
            .document(uid)
            .get()
            .addOnSuccessListener { document -> onSuccess(document.exists()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load follow state") }
    }

    // Counts how many followers a studio has.
    fun loadStudioFollowers(studioId: String, onSuccess: (Int) -> Unit, onFailure: (String) -> Unit = {}) {
        if (studioId.isBlank()) {
            onSuccess(0)
            return
        }
        db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .collection("followers")
            .get()
            .addOnSuccessListener { snapshot -> onSuccess(snapshot.size()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load followers") }
    }

    // Updates editable business-profile fields. The whitelist here mirrors (but does not
    // replace) the Firestore rules guard on ownerUid/managerUids/verified/status/claim fields.
    fun updateStudioProfile(
        studioId: String,
        updates: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        val safeUpdates = updates.filterKeys { it in EDITABLE_PROFILE_FIELDS } + mapOf("updatedAt" to FieldValue.serverTimestamp())
        db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .set(safeUpdates, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update studio profile") }
    }

    // Updates a studio's teacher roster - both the plain uid list and the richer profile info shown on the studio page.
    fun updateStudioTeachers(
        studioId: String,
        teacherUids: List<String>,
        profiles: List<Map<String, Any>>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        db.collection(Constants.Collections.STUDIOS)
            .document(studioId)
            .set(mapOf("teacherUids" to teacherUids, "teacherProfiles" to profiles, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to update teachers") }
    }

    // Notifies a studio's inbox that someone new started following it.
    private fun createStudioFollowNotification(studioId: String, viewer: User) {
        val actorName = viewer.fullName()
        notificationRepository.createNotification(
            recipient = PartyRef.studio(studioId),
            type = InAppNotification.Types.STUDIO_FOLLOW,
            actorId = viewer.uid,
            actorName = actorName,
            actorProfileImageUrl = viewer.profileImageUrl,
            title = "New follower",
            message = "$actorName started following your studio.",
            dedupeId = "studio_follow_${studioId}_${viewer.uid}"
        )
    }

    private fun User.fullName(): String = "$firstName $lastName".trim().ifBlank { "Dancer" }

    private companion object {
        const val FIRESTORE_WHERE_IN_LIMIT = 10
        val EDITABLE_PROFILE_FIELDS = setOf(
            "displayName", "searchName", "handle", "bio", "address", "city", "location",
            "latitude", "longitude", "danceStyles", "profileImageUrl", "coverImageUrl",
            "socialLinks", "websiteUrl", "contactPhone", "contactEmail", "openingHours",
            "teacherUids", "teacherProfiles"
        )
    }
}
