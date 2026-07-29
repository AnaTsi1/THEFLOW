package com.ana.theflow.data.repository

import android.util.Log
import com.ana.theflow.data.model.messaging.Conversation
import com.ana.theflow.data.model.messaging.ConversationParticipant
import com.ana.theflow.data.model.messaging.Message
import com.ana.theflow.data.model.notification.InAppNotification
import com.ana.theflow.data.model.settings.MessageSettings
import com.ana.theflow.data.model.user.User
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

class MessagingRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val userRepository = UserRepository()
    private val notificationRepository = NotificationRepository()

    fun conversationIdFor(firstUid: String, secondUid: String): String {
        return listOf(firstUid, secondUid).sorted().joinToString("_")
    }

    fun listenToConversations(
        onUpdate: (List<Conversation>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("User is not logged in")
            return null
        }
        return db.collection(Constants.Collections.CONVERSATIONS)
            .whereArrayContains("participantIds", uid)
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

    fun listenToUnreadCount(
        onUpdate: (Long) -> Unit,
        onError: (String) -> Unit = {}
    ): ListenerRegistration? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection(Constants.Collections.CONVERSATIONS)
            .whereArrayContains("participantIds", uid)
            .limit(99)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Failed to load unread messages")
                    return@addSnapshotListener
                }
                val total = snapshot?.documents.orEmpty().sumOf { document ->
                    val unread = document.get("unreadCounts") as? Map<*, *> ?: emptyMap<Any, Any>()
                    (unread[uid] as? Number)?.toLong() ?: 0L
                }
                onUpdate(total)
            }
    }

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

    fun resolveOrCreateConversation(
        otherUserId: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onFailure("User is not logged in")
            return
        }
        if (currentUid == otherUserId) {
            onFailure("You cannot message yourself.")
            return
        }

        val conversationId = conversationIdFor(currentUid, otherUserId)
        val conversationRef = db.collection(Constants.Collections.CONVERSATIONS).document(conversationId)
        Log.d("ConversationCreateDebug", "stage=start currentUid=$currentUid otherUserId=$otherUserId conversationId=$conversationId path=${conversationRef.path}")
        checkMessagingAllowed(
            senderUid = currentUid,
            recipientUid = otherUserId,
            onAllowed = {
                loadParticipants(
                    currentUid = currentUid,
                    otherUserId = otherUserId,
                    onSuccess = { currentUser, otherUser ->
                        val participants = listOf(currentUid, otherUserId).sorted()
                        val participantInfo = mapOf(
                            currentUid to currentUser.toParticipant(),
                            otherUserId to otherUser.toParticipant()
                        )
                        Log.d("ConversationCreateDebug", "stage=upsert currentUid=$currentUid otherUserId=$otherUserId conversationId=$conversationId participantIds=$participants path=${conversationRef.path}")
                        conversationRef.set(
                            mapOf(
                                "conversationId" to conversationId,
                                "participantIds" to participants,
                                "participantInfo" to participantInfo.mapValues { it.value.toMap() },
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                            .addOnSuccessListener {
                                Log.d("ConversationCreateDebug", "stage=upsert_success conversationId=$conversationId")
                                onSuccess(conversationId)
                            }
                            .addOnFailureListener { error ->
                                Log.e("ConversationCreateDebug", "stage=upsert_failed currentUid=$currentUid otherUserId=$otherUserId conversationId=$conversationId code=${error.firestoreCode()} message=${error.message}", error)
                                onFailure(messageError(error, "open"))
                            }
                    },
                    onFailure = onFailure
                )
            },
            onDenied = onFailure
        )
    }

    fun sendMessage(
        conversationId: String,
        text: String,
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

        val conversationRef = db.collection(Constants.Collections.CONVERSATIONS).document(conversationId)
        val messageRef = conversationRef.collection(Constants.Collections.MESSAGES).document()
        Log.d("MessageSendDebug", "stage=start currentUid=$currentUid conversationId=$conversationId messagePath=${messageRef.path}")
        db.runTransaction { transaction ->
            val conversation = transaction.get(conversationRef)
            val participants = (conversation.get("participantIds") as? List<*>).orEmpty().mapNotNull { it as? String }
            Log.d("MessageSendDebug", "stage=transaction_read currentUid=$currentUid conversationId=$conversationId participantIds=$participants")
            if (!participants.contains(currentUid)) error("You are not part of this conversation")
            val unread = (conversation.get("unreadCounts") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
                val uid = key as? String ?: return@mapNotNull null
                val count = value as? Number ?: return@mapNotNull null
                uid to count.toLong()
            }.toMap()
            transaction.set(
                messageRef,
                mapOf(
                    "messageId" to messageRef.id,
                    "senderId" to currentUid,
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
                    "unreadCounts" to UnreadCountUtils.incrementForRecipients(participants, currentUid, unread),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            participants.firstOrNull { it != currentUid }.orEmpty()
        }
            .addOnSuccessListener { recipientUid ->
                Log.d("MessageSendDebug", "stage=send_success currentUid=$currentUid conversationId=$conversationId recipientUid=$recipientUid")
                createMessageNotification(conversationId, recipientUid, cleanText)
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e("MessageSendDebug", "stage=send_failed currentUid=$currentUid conversationId=$conversationId code=${error.firestoreCode()} message=${error.message}", error)
                onFailure(messageError(error, "send"))
            }
    }

    fun canStartConversationWith(
        otherUserId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            onResult(false, "Please log in to send messages.")
            return
        }
        if (currentUid == otherUserId) {
            onResult(false, "You cannot message yourself.")
            return
        }
        checkMessagingAllowed(
            senderUid = currentUid,
            recipientUid = otherUserId,
            onAllowed = { onResult(true, "") },
            onDenied = { reason -> onResult(false, reason) }
        )
    }

    fun markConversationRead(conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(Constants.Collections.CONVERSATIONS)
            .document(conversationId)
            .update("unreadCounts.$uid", 0L)
    }

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

    private fun checkMessagingAllowed(
        senderUid: String,
        recipientUid: String,
        onAllowed: () -> Unit,
        onDenied: (String) -> Unit
    ) {
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

    private fun Exception.firestoreCode(): String {
        return (this as? FirebaseFirestoreException)?.code?.name ?: this::class.java.simpleName
    }

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

    private fun loadParticipants(
        currentUid: String,
        otherUserId: String,
        onSuccess: (User, User) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var currentUser: User? = null
        var otherUser: User? = null
        fun finish() {
            val current = currentUser
            val other = otherUser
            if (current != null && other != null) onSuccess(current, other)
        }
        userRepository.getUserByUid(currentUid, { currentUser = it; finish() }, onFailure)
        userRepository.getUserByUid(otherUserId, { otherUser = it; finish() }, onFailure)
    }

    private fun createMessageNotification(conversationId: String, recipientUid: String, text: String) {
        val currentUid = auth.currentUser?.uid ?: return
        userRepository.getUserByUid(
            uid = currentUid,
            onSuccess = { sender ->
                notificationRepository.createNotification(
                    recipientUid = recipientUid,
                    type = InAppNotification.Types.PRIVATE_MESSAGE,
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

    private fun User.toParticipant(): ConversationParticipant {
        return ConversationParticipant(
            uid = uid,
            name = "${firstName} ${lastName}".trim().ifBlank { "Dancer" },
            profileImageUrl = profileImageUrl
        )
    }

    private fun ConversationParticipant.toMap(): Map<String, String> {
        return mapOf(
            "uid" to uid,
            "name" to name,
            "profileImageUrl" to profileImageUrl
        )
    }

    private fun DocumentSnapshot.toConversation(): Conversation {
        val participantInfo = (get("participantInfo") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val data = value as? Map<*, *> ?: return@mapNotNull null
            uid to ConversationParticipant(
                uid = uid,
                name = data["name"] as? String ?: "",
                profileImageUrl = data["profileImageUrl"] as? String ?: ""
            )
        }.toMap()
        val unreadCounts = (get("unreadCounts") as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val uid = key as? String ?: return@mapNotNull null
            val count = value as? Number ?: return@mapNotNull null
            uid to count.toLong()
        }.toMap()
        return Conversation(
            conversationId = id,
            participantIds = (get("participantIds") as? List<*>).orEmpty().mapNotNull { it as? String },
            participantInfo = participantInfo,
            lastMessage = getString("lastMessage").orEmpty(),
            lastMessageAt = getTimestamp("lastMessageAt"),
            lastSenderId = getString("lastSenderId").orEmpty(),
            unreadCounts = unreadCounts,
            createdAt = getTimestamp("createdAt"),
            updatedAt = getTimestamp("updatedAt")
        )
    }
}
