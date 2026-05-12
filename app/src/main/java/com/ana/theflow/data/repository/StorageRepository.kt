package com.ana.theflow.data.repository

import android.net.Uri
import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

class StorageRepository {

    private val storage = FirebaseStorage.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun uploadProfileImage(
        uid: String,
        imageUri: Uri,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (uid.isBlank()) {
            onFailure("Missing user id")
            return
        }

        uploadAndSaveUrl(
            path = "users/$uid/profile/profile.jpg",
            fileUri = imageUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                db.collection(Constants.Collections.USERS)
                    .document(uid)
                    .update("profileImageUrl", url)
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error ->
                        failure(error.message ?: "Failed to save profile image URL")
                    }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    fun uploadPostMedia(
        postId: String,
        mediaUri: Uri,
        fileName: String,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        val cleanFileName = sanitizeFileName(fileName)
        uploadAndSaveUrl(
            path = "posts/$postId/media/$cleanFileName",
            fileUri = mediaUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                val updates = mapOf(
                    "mediaUrls" to FieldValue.arrayUnion(url),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(Constants.Collections.POSTS)
                    .document(postId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error ->
                        failure(error.message ?: "Failed to save post media URL")
                    }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    fun uploadStudioApplicationDocument(
        applicationId: String,
        documentUri: Uri,
        fileName: String,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (applicationId.isBlank()) {
            onFailure("Missing studio application id")
            return
        }

        val cleanFileName = sanitizeFileName(fileName)
        val path = "studioApplications/$applicationId/documents/$cleanFileName"
        uploadAndSaveUrl(
            path = path,
            fileUri = documentUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                val documentMetadata = mapOf(
                    "fileName" to cleanFileName,
                    "url" to url,
                    "storagePath" to path,
                    "uploadedAt" to System.currentTimeMillis()
                )
                val updates = mapOf(
                    "documents" to FieldValue.arrayUnion(documentMetadata),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection(Constants.Collections.STUDIO_APPLICATIONS)
                    .document(applicationId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error ->
                        failure(error.message ?: "Failed to save studio document URL")
                    }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    private fun uploadAndSaveUrl(
        path: String,
        fileUri: Uri,
        onLoading: (Boolean) -> Unit,
        saveUrl: (url: String, success: () -> Unit, failure: (String) -> Unit) -> Unit,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        onLoading(true)
        val ref = storage.reference.child(path)
        ref.putFile(fileUri)
            .continueWithTask { task ->
                if (!task.isSuccessful) {
                    task.exception?.let { throw it }
                }
                ref.downloadUrl
            }
            .addOnSuccessListener { downloadUri ->
                val url = downloadUri.toString()
                saveUrl(
                    url,
                    {
                        onLoading(false)
                        onSuccess(url)
                    },
                    { error ->
                        onLoading(false)
                        onFailure(error)
                    }
                )
            }
            .addOnFailureListener { error ->
                onLoading(false)
                onFailure(error.message ?: "Upload failed")
            }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .trim()
            .ifBlank { "upload" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
