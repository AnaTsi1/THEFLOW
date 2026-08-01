package com.ana.theflow.data.repository

import android.util.Log
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.messaging.Conversation
import com.ana.theflow.data.model.messaging.ConversationParticipant
import com.ana.theflow.data.model.messaging.Message
import com.ana.theflow.data.model.messaging.PartyRef
import com.ana.theflow.data.model.messaging.toPartyRef
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.settings.MessageSettings
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.session.ActiveAccountHolder
import com.ana.theflow.utilities.Constants
import com.ana.theflow.utilities.MessagingPrivacyUtils
import com.ana.theflow.utilities.UnreadCountUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

// Handles private conversations and messages - both person-to-person chats and a person
// messaging into a studio's shared inbox.
class MessagingRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val notificationRepository = NotificationRepository()

    // The doc id scheme: user<->user keeps the original sorted-uid join; a studio side always
    // resolves to "{customerUid}__studio_{studioId}" regardless of who initiated it, since
    // studio<->studio conversations are not supported.
    fun conversationIdFor(a: PartyRef, b: PartyRef): String {
        val studio = listOf(a, b).firstOrNull { it.type == Constants.EntityType.STUDIO }
        if (studio != null) {
            val customer = listOf(a, b).first { it.type != Constants.EntityType.STUDIO }
            return "${customer.id}__${studio.key}"
        }
        return listOf(a.id, b.id).sorted().joinToString("_")
    }

    // Live-listens to the conversation list for whichever account is active.
    fun listenToConversations(
        account: ActiveAccount = ActiveAccountHolder.current(),
        onUpdate: (List<Conversation>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("User is not logged in")
            return null
        }
        val query = if (account is ActiveAccount.StudioAccount) {
            db.collection(Constants.Collections.CONVERSATIONS).whereEqualTo("studioId", account.studioId)
        } else {
            db.collection(Constants.Collections.CONVERSATIONS).whereArrayContains("participantIds", uid)
        }
        return query
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load conversations")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.map { it.toConversation() }.orEmpty())
            }
    }

    // Live-listens to the total unread message count for the chat icon's badge.
    fun listenToUnreadCount(
        account: ActiveAccount = ActiveAccountHolder.current(),
        onUpdate: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        val partyKey = account.toPartyRef().key
        val query = if (account is ActiveAccount.StudioAccount) {
            db.collection(Constants.Collections.CONVERSATIONS).whereEqualTo("studioId", account.studioId)
        } else {
            db.collection(Constants.Collections.CONVERSATIONS).whereArrayContains("participantIds", uid)
        }
        return query
            .limit(99)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load unread messages")
                    return@addSnapshotListener
                }
                val total = snapshot?.documents.orEmpty().sumOf { document ->
                    val unread = document.get("unreadCounts") as? Map<*, *> ?: emptyMap<Any, Any>()
                    (unread[partyKey] as? Number)?.toLong() ?: 0L
                }
                onUpdate(total)
            }
    }

    // Live-listens to the last 100 messages in a conversation.
    fun listenToMessages(
        conversationId: String,
        onUpdate: (List<Message>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        if (conversationId.isBlank()) return null
        return db.collection(Constants.Collections.CONVERSATIONS)
            .document(conversationId)
            .collection(Constants.Collections.MESSAGES)
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load messages")
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { document ->
                    document.toObject(Message::class.java)?.copy(messageId = document.id)
                }.orEmpty())
            }
    }

    // Finds the existing conversation between two parties, or creates it if it doesn't exist yet.
    // Checks the recipient's messaging permissions first, since a conversation shouldn't get
    // created at all if they don't allow messages from this sender.
    fun resolveOrCreateConversation(
        target: PartyRef,
        from: ActiveAccount = ActiveAccountHolder.current(),
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        val fromParty = from.toPartyRef()
        if (fromParty.type == target.type && fromParty.id == target.id) {
            onFailure("You cannot message yourself.")
            return
        }

        val conversationId = conversationIdFor(fromParty, target)
        val conversationRef = db.collection(Constants.Collections.CONVERSATIONS).document(conversationId)
        Log.d("ConversationCreateDebug", "stage=start fromParty=$fromParty target=$target conversationId=$conversationId path=${conversationRef.path}")
        checkMessagingAllowed(
            senderUid = currentUid,
            recipient = target,
            onAllowed = {
                loadPartyInfo(
                    party = fromParty,
                    onSuccess = { fromInfo ->
                        loadPartyInfo(
                            party = target,
                            onSuccess = { targetInfo ->
                                val humanIds = listOf(fromParty, target)
                                    .filter { it.type != Constants.EntityType.STUDIO }
                                    .map { it.id }
                                    .distinct()
                                    .sorted()
                                val studioId = listOf(fromParty, target).firstOrNull { it.type == Constants.EntityType.STUDIO }?.id.orEmpty()
                                Log.d("ConversationCreateDebug", "stage=upsert conversationId=$conversationId humanIds=$humanIds studioId=$studioId path=${conversationRef.path}")
                                conversationRef.set(
                                    mapOf(
                                        "conversationId" to conversationId,
                                        "participantIds" to humanIds,
                                        "studioId" to studioId,
                                        "partyKeys" to listOf(fromParty.key, target.key),
                                        "participantInfo" to mapOf(
                                            fromParty.key to fromInfo.toMap(),
                                            target.key to targetInfo.toMap()
                                        ),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    ),
                                    SetOptions.merge()
                                )
                                    .addOnSuccessListener {
                                        Log.d("ConversationCreateDebug", "stage=upsert_success conversationId=$conversationId")
                                        onSuccess(conversationId)
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e("ConversationCreateDebug", "stage=upsert_failed conversationId=$conversationId code=${error.firestoreCode()} message=${error.message}", error)
                                        onFailure(messageError(error, "open"))
                                    }
                            },
                            onFailure = onFailure
                        )
                    },
                    onFailure = onFailure
                )
            },
            onDenied = onFailure
        )
    }

    // Sends a message. If sending as a studio, grabs the actual manager's name first so it can
    // show "via <name>" to the other managers, without exposing that to the recipient.
    fun sendMessage(
        conversationId: String,
        text: String,
        from: ActiveAccount = ActiveAccountHolder.current(),
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        val cleanText = text.trim()
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        if (cleanText.isBlank()) {
            onFailure("Message cannot be empty")
            return
        }
        val fromParty = from.toPartyRef()
        if (fromParty.type == Constants.EntityType.STUDIO) {
            userRepository.getUserByUid(
                uid = currentUid,
                onSuccess = { actor ->
                    val actorName = "${actor.firstName} ${actor.lastName}".trim().ifBlank { "Manager" }
                    sendMessageInternal(conversationId, cleanText, currentUid, fromParty, actorName, onSuccess, onFailure)
                },
                onFailure = { sendMessageInternal(conversationId, cleanText, currentUid, fromParty, "", onSuccess, onFailure) }
            )
        } else {
            sendMessageInternal(conversationId, cleanText, currentUid, fromParty, "", onSuccess, onFailure)
        }
    }

    // Does the actual write, in a transaction so the new message and the conversation's
    // last-message/unread-count updates always land together, never half-applied.
    private fun sendMessageInternal(
        conversationId: String,
        cleanText: String,
        currentUid: String,
        fromParty: PartyRef,
        actorName: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val conversationRef = db.collection(Constants.Collections.CONVERSATIONS).document(conversationId)
        val messageRef = conversationRef.collection(Constants.Collections.MESSAGES).document()
        Log.d("MessageSendDebug", "stage=start currentUid=$currentUid conversationId=$conversationId messagePath=${messageRef.path}")
        db.runTransaction { transaction ->
            val conversation = transaction.get(conversationRef)
            val participants = (conversation.get("participantIds") as? List<*>).orEmpty().mapNotNull { it as? String }
            val partyKeys = (conversation.get("partyKeys") as? List<*>).orEmpty().mapNotNull { it as? String }.ifEmpty { participants }
            Log.d("MessageSendDebug", "stage=transaction_read currentUid=$currentUid conversationId=$conversationId partyKeys=$partyKeys")
            if (!partyKeys.contains(fromParty.key)) error("You are not part of this conversation")
            val unread = (conversation.get("unreadCounts") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
                val partyKey = key as? String ?: return@mapNotNull null
                val count = value as? Number ?: return@mapNotNull null
                partyKey to count.toLong()
            }.toMap()
            transaction.set(
                messageRef,
                mapOf(
                    "messageId" to messageRef.id,
                    "senderId" to currentUid,
                    "senderPartyKey" to fromParty.key,
                    "senderEntityType" to fromParty.type,
                    "senderEntityId" to fromParty.id,
                    "actorName" to actorName,
                    "text" to cleanText,
                    "type" to Message.TYPE_TEXT,
                    "sentAt" to FieldValue.serverTimestamp(),
                    "readBy" to mapOf(currentUid to FieldValue.serverTimestamp())
                )
            )
            transaction.set(
                conversationRef,
                mapOf(
                    "lastMessage" to cleanText,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to currentUid,
                    "lastSenderPartyKey" to fromParty.key,
                    "unreadCounts" to UnreadCountUtils.incrementForRecipients(partyKeys, fromParty.key, unread),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            partyKeys.firstOrNull { it != fromParty.key }.orEmpty()
        }
            .addOnSuccessListener { recipientPartyKey ->
                Log.d("MessageSendDebug", "stage=send_success currentUid=$currentUid conversationId=$conversationId recipientPartyKey=$recipientPartyKey")
                createMessageNotification(conversationId, recipientPartyKey, cleanText)
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e("MessageSendDebug", "stage=send_failed currentUid=$currentUid conversationId=$conversationId code=${error.firestoreCode()} message=${error.message}", error)
                onFailure(messageError(error, "send"))
            }
    }

    // Checks up front whether this sender is even allowed to message this target, with a reason if not.
    fun canStartConversationWith(
        target: PartyRef,
        from: ActiveAccount = ActiveAccountHolder.current(),
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onResult(false, "Please log in to send messages.")
            return
        }
        val fromParty = from.toPartyRef()
        if (fromParty.type == target.type && fromParty.id == target.id) {
            onResult(false, "You cannot message yourself.")
            return
        }
        checkMessagingAllowed(
            senderUid = currentUid,
            recipient = target,
            onAllowed = { onResult(true, "") },
            onDenied = { reason -> onResult(false, reason) }
        )
    }

    // Zeroes out this account's own unread count on a conversation.
    fun markConversationRead(conversationId: String, account: ActiveAccount = ActiveAccountHolder.current()) {
        if (auth.currentUser?.uid == null) return
        db.collection(Constants.Collections.CONVERSATIONS)
            .document(conversationId)
            .update("unreadCounts.${account.toPartyRef().key}", 0L)
    }

    // Marks any messages the signed-in user hasn't read yet as read, then zeroes their unread count too.
    fun markMessagesRead(conversationId: String, messages: List<Message>) {
        val uid = auth.currentUser?.uid ?: return
        val unreadMessages = messages.filter { it.senderId != uid && !it.readBy.containsKey(uid) }
        if (unreadMessages.isEmpty()) return
        val batch = db.batch()
        unreadMessages.take(50).forEach { message ->
            val ref = db.collection(Constants.Collections.CONVERSATIONS)
                .document(conversationId)
                .collection(Constants.Collections.MESSAGES)
                .document(message.messageId)
            batch.update(ref, "readBy.$uid", FieldValue.serverTimestamp())
        }
        batch.commit()
        markConversationRead(conversationId)
    }

    // Loads one conversation by id.
    fun loadConversation(
        conversationId: String,
        onSuccess: (Conversation) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection(Constants.Collections.CONVERSATIONS)
            .document(conversationId)
            .get()
            .addOnSuccessListener { onSuccess(it.toConversation()) }
            .addOnFailureListener { error -> onFailure(error.message ?: "Failed to load conversation") }
    }

    // A studio has no users/{id} settings doc to read - businesses accept messages from anyone,
    // so the recipient-side settings/follow gate only ever applies to a personal recipient.
    private fun checkMessagingAllowed(
        senderUid: String,
        recipient: PartyRef,
        onAllowed: () -> Unit,
        onDenied: (String) -> Unit
    ) {
        if (recipient.type == Constants.EntityType.STUDIO) {
            onAllowed()
            return
        }
        val recipientUid = recipient.id
        SettingsRepository().loadSettings(
            uid = recipientUid,
            onSuccess = { _, messageSettings ->
                if (messageSettings.receiveMessagesFrom == MessageSettings.RECEIVE_EVERYONE) {
                    onAllowed()
                    return@loadSettings
                }
                isSenderFollowedByRecipient(
                    senderUid = senderUid,
                    recipientUid = recipientUid,
                    onResult = { followsSender ->
                        if (MessagingPrivacyUtils.canStartConversation(messageSettings, followsSender)) {
                            onAllowed()
                        } else {
                            onDenied(MessagingPrivacyUtils.blockedReason(messageSettings))
                        }
                    }
                )
            },
            onFailure = onDenied
        )
    }

    // Checks the three "following" subcollections to see if the recipient follows this sender back.
    private fun isSenderFollowedByRecipient(
        senderUid: String,
        recipientUid: String,
        onResult: (Boolean) -> Unit
    ) {
        val collections = listOf("followingDancers", "followingTeachers", "followingStudios")
        var pending = collections.size
        var found = false
        collections.forEach { collection ->
            val ref = db.collection(Constants.Collections.USERS)
                .document(recipientUid)
                .collection(collection)
                .document(senderUid)
            Log.d("MessagingPermissionDebug", "stage=check_follow senderUid=$senderUid recipientUid=$recipientUid path=${ref.path}")
            ref
                .get()
                .addOnSuccessListener { document ->
                    found = found || document.exists()
                    pending -= 1
                    if (pending == 0) onResult(found)
                }
                .addOnFailureListener { error ->
                    Log.e("MessagingPermissionDebug", "stage=check_follow_failed senderUid=$senderUid recipientUid=$recipientUid collection=$collection code=${error.firestoreCode()} message=${error.message}", error)
                    pending -= 1
                    if (pending == 0) onResult(found)
                }
        }
    }

    // Pulls the Firestore error code out of an exception, for logging.
    private fun Exception.firestoreCode(): String {
        return (this as? FirebaseFirestoreException)?.code?.name ?: this::class.java.simpleName
    }

    // Turns a raw Firestore error into a message that actually makes sense to show someone.
    private fun messageError(error: Exception, action: String): String {
        val code = (error as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                if (action == "send") "We couldn't send this message. Please try again." else "We couldn't open this conversation."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "The network is unavailable right now. Please try again."
            else -> error.message ?: if (action == "send") "We couldn't send this message." else "We couldn't open this conversation."
        }
    }

    // Loads display info (name, photo) for whichever party this is - a studio or a person.
    private fun loadPartyInfo(
        party: PartyRef,
        onSuccess: (ConversationParticipant) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (party.type == Constants.EntityType.STUDIO) {
            StudioRepository().loadStudio(
                studioId = party.id,
                onSuccess = { studio ->
                    onSuccess(
                        ConversationParticipant(
                            uid = studio.id,
                            name = studio.displayName.ifBlank { "Studio" },
                            profileImageUrl = studio.profileImageUrl,
                            type = Constants.EntityType.STUDIO
                        )
                    )
                },
                onFailure = onFailure
            )
        } else {
            userRepository.getUserByUid(party.id, onSuccess = { user -> onSuccess(user.toParticipant()) }, onFailure = onFailure)
        }
    }

    // recipientPartyKey may be a studio key ("studio_<id>") when the customer messaged first -
    // that lands in the studio's shared notifications subcollection instead of a personal one.
    private fun createMessageNotification(conversationId: String, recipientPartyKey: String, text: String) {
        if (recipientPartyKey.isBlank()) return
        val currentUid = auth.currentUser?.uid ?: return
        val recipient = if (recipientPartyKey.startsWith(STUDIO_PARTY_KEY_PREFIX)) {
            PartyRef.studio(recipientPartyKey.removePrefix(STUDIO_PARTY_KEY_PREFIX))
        } else {
            PartyRef.user(recipientPartyKey)
        }
        userRepository.getUserByUid(
            uid = currentUid,
            onSuccess = { sender ->
                notificationRepository.createNotification(
                    recipient = recipient,
                    type = if (recipient.type == Constants.EntityType.STUDIO) InAppNotification.Types.STUDIO_MESSAGE else InAppNotification.Types.PRIVATE_MESSAGE,
                    actorId = currentUid,
                    actorName = "${sender.firstName} ${sender.lastName}".trim().ifBlank { "Dancer" },
                    actorProfileImageUrl = sender.profileImageUrl,
                    conversationId = conversationId,
                    title = "New message",
                    message = text
                )
            },
            onFailure = {}
        )
    }

    // Turns a User into the display info stored on a conversation.
    private fun User.toParticipant(): ConversationParticipant {
        return ConversationParticipant(
            uid = uid,
            name = "${firstName} ${lastName}".trim().ifBlank { "Dancer" },
            profileImageUrl = profileImageUrl,
            type = Constants.EntityType.USER
        )
    }

    // Converts a participant into a plain map for storing on the conversation document.
    private fun ConversationParticipant.toMap(): Map<String, String> {
        return mapOf(
            "uid" to uid,
            "name" to name,
            "profileImageUrl" to profileImageUrl,
            "type" to type
        )
    }

    // Rebuilds a Conversation from a raw Firestore document.
    private fun DocumentSnapshot.toConversation(): Conversation {
        val participantInfo = (get("participantInfo") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val partyKey = key as? String ?: return@mapNotNull null
            val data = value as? Map<*, *> ?: return@mapNotNull null
            partyKey to ConversationParticipant(
                uid = data["uid"] as? String ?: partyKey,
                name = data["name"] as? String ?: "",
                profileImageUrl = data["profileImageUrl"] as? String ?: "",
                type = data["type"] as? String ?: Constants.EntityType.USER
            )
        }.toMap()
        val unreadCounts = (get("unreadCounts") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val partyKey = key as? String ?: return@mapNotNull null
            val count = value as? Number ?: return@mapNotNull null
            partyKey to count.toLong()
        }.toMap()
        return Conversation(
            conversationId = id,
            participantIds = (get("participantIds") as? List<*>).orEmpty().mapNotNull { it as? String },
            studioId = getString("studioId").orEmpty(),
            partyKeys = (get("partyKeys") as? List<*>).orEmpty().mapNotNull { it as? String },
            participantInfo = participantInfo,
            lastMessage = getString("lastMessage").orEmpty(),
            lastMessageAt = getTimestamp("lastMessageAt"),
            lastSenderId = getString("lastSenderId").orEmpty(),
            lastSenderPartyKey = getString("lastSenderPartyKey").orEmpty(),
            unreadCounts = unreadCounts,
            createdAt = getTimestamp("createdAt"),
            updatedAt = getTimestamp("updatedAt")
        )
    }

    private companion object {
        const val STUDIO_PARTY_KEY_PREFIX = "studio_"
    }
}
