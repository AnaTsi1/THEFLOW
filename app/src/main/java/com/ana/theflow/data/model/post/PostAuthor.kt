// Resolves who a post (or the original post behind a repost) should be attributed to for
// display/routing purposes, independent of Post's own raw authorId field.
package com.ana.theflow.data.model.post

import com.ana.theflow.utilities.Constants

// Who a post should actually be credited to. Post.authorId always stays the real human's uid (so
// old queries keep working), but this figures out the "owning account" - is it just them, or are
// they posting as a studio?
data class AuthorRef(
    val type: String,
    val id: String,
    val name: String,
    val imageUrl: String
)

// Works out who a post is credited to, falling back to plain user info for older posts that
// don't have the entity fields set.
fun Post.authorRef(): AuthorRef {
    val type = authorEntityType.ifBlank { Constants.EntityType.USER }
    return AuthorRef(
        type = type,
        id = authorEntityId.ifBlank { authorId },
        name = authorEntityName.ifBlank { authorName },
        imageUrl = authorEntityImageUrl.ifBlank { authorProfileImageUrl }
    )
}

// True if this was posted as a studio, not a personal account.
fun Post.isStudioAuthored(): Boolean {
    return authorEntityType.equals(Constants.EntityType.STUDIO, ignoreCase = true)
}

// Same idea as authorRef() above, but for the original post behind a repost.
fun Post.originalAuthorRef(): AuthorRef {
    val type = originalAuthorEntityType.ifBlank { Constants.EntityType.USER }
    return AuthorRef(
        type = type,
        id = originalAuthorEntityId.ifBlank { originalAuthorId },
        name = originalAuthorEntityName.ifBlank { originalAuthorName },
        imageUrl = originalAuthorEntityImageUrl
    )
}
