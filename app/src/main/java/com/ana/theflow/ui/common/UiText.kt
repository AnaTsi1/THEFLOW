// Turns a raw Firestore/network error message into something a user can actually read, instead
// of showing internal error codes or collection paths straight from an exception.
package com.ana.theflow.ui.common

object UiText {
    // Maps known error signatures (permission, missing index, offline) to a plain sentence, and
    // falls back to a generic message for anything that still looks like it leaked internal
    // details (a Firestore/Firebase term, or a path with a slash in it).
    fun friendlyError(raw: String?, fallback: String = "Something went wrong. Please try again."): String {
        val message = raw.orEmpty()
        if (message.isBlank()) return fallback
        val lower = message.lowercase()
        return when {
            "permission_denied" in lower || "permission denied" in lower -> "You do not have permission to do that."
            "failed_precondition" in lower || "index" in lower -> "We could not load this yet. Please try again soon."
            "unavailable" in lower || "network" in lower -> "Connection problem. Please check your internet and try again."
            "firebase" in lower || "firestore" in lower || "/" in message || "collection" in lower -> fallback
            else -> message
        }
    }
}
