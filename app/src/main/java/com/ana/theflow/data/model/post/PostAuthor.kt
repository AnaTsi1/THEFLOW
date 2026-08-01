package com.ana.theflow.data.model.post

import com.ana.theflow.utilities.Constants

// Who a post should be attributed to for display/routing purposes. `authorId` on Post always
// stays the acting human's uid (for backward compatibility with every existing query); this
// resolves the "owning account" - the personal user, or the studio they posted as.
data class AuthorRef(
    val type: String,
    val id: String,
    val name: String,
    val imageUrl: String
)

// Falls back to plain user attribution for every post written before this field existed.
fun Post.authorRef(): AuthorRef {
    val type = authorEntityType.ifBlank { Constants.EntityType.USER }
    return AuthorRef(
        type = type,
        id = authorEntityId.ifBlank { authorId },
        name = authorEntityName.ifBlank { authorName },
        imageUrl = authorEntityImageUrl.ifBlank { authorProfileImageUrl }
    )
}

fun Post.isStudioAuthored(): Boolean {
    return authorEntityType.equals(Constants.EntityType.STUDIO, ignoreCase = true)
}

// Same fallback resolution as authorRef(), but for the original post a repost points to.
fun Post.originalAuthorRef(): AuthorRef {
    val type = originalAuthorEntityType.ifBlank { Constants.EntityType.USER }
    return AuthorRef(
        type = type,
        id = originalAuthorEntityId.ifBlank { originalAuthorId },
        name = originalAuthorEntityName.ifBlank { originalAuthorName },
        imageUrl = originalAuthorEntityImageUrl
    )
}
