package com.ana.theflow.data.repository

import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FirebaseFirestore

class StudioRepository {

    private val db = FirebaseFirestore.getInstance()

    fun createStudioPage(
        studio: Studio,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val docRef = db.collection(Constants.Collections.STUDIOS).document()
        val studioWithId = studio.copy(
            id = docRef.id,
            status = Constants.StudioStatus.PENDING.name
        )

        docRef.set(studioWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to save studio page")
            }
    }

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
}
