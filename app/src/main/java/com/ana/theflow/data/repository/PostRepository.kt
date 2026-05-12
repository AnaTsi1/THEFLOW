package com.ana.theflow.data.repository

import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.utilities.Constants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PostRepository {

    private val db = FirebaseFirestore.getInstance()

    fun createTextPost(
        author: User,
        text: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (author.uid.isBlank()) {
            onFailure("Missing author id")
            return
        }
        if (text.isBlank()) {
            onFailure("Post text cannot be empty")
            return
        }

        val docRef = db.collection(Constants.Collections.POSTS).document()
        val authorName = "${author.firstName} ${author.lastName}".trim().ifBlank { "Dancer" }
        val post = mapOf(
            "postId" to docRef.id,
            "authorId" to author.uid,
            "authorName" to authorName,
            "authorProfileImageUrl" to author.profileImageUrl,
            "authorType" to author.role.ifBlank { "dancer" },
            "text" to text.trim(),
            "mediaUrls" to emptyList<String>(),
            "mediaType" to "none",
            "createdAt" to FieldValue.serverTimestamp(),
            "visibility" to "public",
            "likesCount" to 0,
            "commentsCount" to 0
        )

        docRef.set(post)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to create post")
            }
    }

    fun loadFeed(
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("visibility", "public")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                })
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load posts")
            }
    }

    fun loadPostsByAuthor(
        authorId: String,
        onSuccess: (List<Post>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.POSTS)
            .whereEqualTo("authorId", authorId)
            .get()
            .addOnSuccessListener { snapshot ->
                val posts = snapshot.documents.mapNotNull { document ->
                    document.toObject(Post::class.java)?.copy(postId = document.id)
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
                onSuccess(posts)
            }
            .addOnFailureListener { error ->
                onFailure(error.message ?: "Failed to load profile posts")
            }
    }
}
