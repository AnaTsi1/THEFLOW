package com.ana.theflow.ui.messaging

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.messaging.Conversation

class ConversationsViewModel : ViewModel() {
    var conversations: List<Conversation> = emptyList()
    var error: String = ""
}
