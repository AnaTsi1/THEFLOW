// Handles uploading files to Firebase Storage (profile photos, covers, post media, verification
// documents) and saving the resulting download URL onto the right Firestore document.
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

    // Uploads a profile image and saves its URL.
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

    // Uploads a cover image and saves its URL.
    fun uploadCoverImage(
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
            path = "users/$uid/profile/cover.jpg",
            fileUri = imageUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                db.collection(Constants.Collections.USERS)
                    .document(uid)
                    .update("coverImageUrl", url)
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error ->
                        failure(error.message ?: "Failed to save cover image URL")
                    }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Uploads a studio's logo image and saves its URL.
    fun uploadStudioLogo(
        studioId: String,
        imageUri: Uri,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        uploadAndSaveUrl(
            path = "studios/$studioId/profile/logo.jpg",
            fileUri = imageUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                db.collection(Constants.Collections.STUDIOS)
                    .document(studioId)
                    .set(mapOf("profileImageUrl" to url, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error -> failure(error.message ?: "Failed to save logo URL") }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Uploads a studio's cover image and saves its URL.
    fun uploadStudioCover(
        studioId: String,
        imageUri: Uri,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (studioId.isBlank()) {
            onFailure("Missing studio id")
            return
        }
        uploadAndSaveUrl(
            path = "studios/$studioId/profile/cover.jpg",
            fileUri = imageUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                db.collection(Constants.Collections.STUDIOS)
                    .document(studioId)
                    .set(mapOf("coverImageUrl" to url, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error -> failure(error.message ?: "Failed to save cover URL") }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Uploads post media and saves its URL.
    fun uploadPostMedia(
        postId: String,
        mediaUri: Uri,
        fileName: String,
        mediaType: String = "photo",
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (postId.isBlank()) {
            onFailure("Missing post id")
            return
        }

        val cleanFileName = sanitizeFileName(fileName)
        val mediaId = "media_${System.currentTimeMillis()}"
        uploadAndSaveUrl(
            path = "posts/$postId/media/$cleanFileName",
            fileUri = mediaUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                val mediaItem = mapOf(
                    "id" to mediaId,
                    "url" to url,
                    "mediaType" to mediaType.ifBlank { "photo" },
                    "visibleInMedia" to true,
                    "pinned" to false,
                    "uploadedAt" to System.currentTimeMillis()
                )
                val updates = mapOf(
                    "mediaUrls" to FieldValue.arrayUnion(url),
                    "mediaItems" to FieldValue.arrayUnion(mediaItem),
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

    // Uploads a supporting document (certificate, credential) for a professional verification
    // application and appends its URL - the application document already exists by this point
    // (submitApplication runs first), so this is an append, not a create.
    fun uploadVerificationDocument(
        applicationId: String,
        fileUri: Uri,
        fileName: String,
        onLoading: (Boolean) -> Unit = {},
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (applicationId.isBlank()) {
            onFailure("Missing application id")
            return
        }

        val cleanFileName = sanitizeFileName(fileName)
        uploadAndSaveUrl(
            path = "professionalApplications/$applicationId/documents/$cleanFileName",
            fileUri = fileUri,
            onLoading = onLoading,
            saveUrl = { url, success, failure ->
                db.collection(Constants.Collections.PROFESSIONAL_APPLICATIONS)
                    .document(applicationId)
                    .update("documents", FieldValue.arrayUnion(url))
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { error ->
                        failure(error.message ?: "Failed to save document URL")
                    }
            },
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Uploads a file and then saves its download URL.
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

    // Cleans a file name for storage paths.
    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .trim()
            .ifBlank { "upload" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
