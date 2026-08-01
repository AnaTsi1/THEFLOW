// Activity-scoped session state: the signed-in user, every studio they manage, and which
// account is currently active. Mirrors the DiscoverViewModel/ConversationsViewModel pattern
// already used for other MainActivity-shared state.
package com.ana.theflow.ui.account

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.account.AccountSummary
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.StudioRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.data.session.ActiveAccountHolder

class ActiveAccountViewModel : ViewModel() {

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val studioRepository = StudioRepository()

    var currentUser: User? = null
        private set
    var managedStudios: List<Studio> = emptyList()
        private set
    var accounts: List<AccountSummary> = emptyList()
        private set
    var isLoading: Boolean = false
        private set
    var error: String = ""
        private set

    val active: ActiveAccount get() = ActiveAccountHolder.current()

    // Reloads the signed-in user and every studio they manage, then rebuilds the account list
    // shown in the switcher. Also reconciles the active account in case a manager was just
    // removed while this studio was selected.
    fun refresh(onDone: () -> Unit = {}) {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            currentUser = null
            managedStudios = emptyList()
            accounts = emptyList()
            onDone()
            return
        }

        isLoading = true
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                currentUser = user
                ActiveAccountHolder.reconcile(user)
                val studioIds = user.managedStudioIds.filter { it.isNotBlank() }
                if (studioIds.isEmpty()) {
                    managedStudios = emptyList()
                    rebuildAccounts()
                    isLoading = false
                    onDone()
                    return@getUserByUid
                }
                studioRepository.loadStudiosByIds(
                    ids = studioIds,
                    onSuccess = { studios ->
                        managedStudios = studios
                        rebuildAccounts()
                        isLoading = false
                        onDone()
                    },
                    onFailure = { message ->
                        managedStudios = emptyList()
                        error = message
                        rebuildAccounts()
                        isLoading = false
                        onDone()
                    }
                )
            },
            onFailure = { message ->
                error = message
                isLoading = false
                onDone()
            }
        )
    }

    // Switches the active account. Refuses to switch into a studio the user does not currently
    // manage - the source of truth is always the freshly-loaded managedStudios list.
    fun switchTo(account: ActiveAccount, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        if (account is ActiveAccount.StudioAccount && managedStudios.none { it.id == account.studioId }) {
            onFailure("You no longer manage this studio")
            return
        }
        ActiveAccountHolder.set(account)
        onSuccess()
    }

    // The account summary matching whichever account is currently active, for showing its name
    // and avatar in the header. Falls back to the first summary if the active account somehow
    // isn't in the list yet (e.g. right after sign-in, before refresh() has run).
    fun activeSummary(): AccountSummary? {
        val activeAccount = active
        return accounts.firstOrNull { it.account == activeAccount } ?: accounts.firstOrNull()
    }

    // Rebuilds the switchable account list: the personal account first, then one entry per
    // managed studio.
    private fun rebuildAccounts() {
        val user = currentUser ?: run { accounts = emptyList(); return }
        val personalName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        val personal = AccountSummary(
            account = ActiveAccount.Personal(userUid = user.uid),
            displayName = personalName,
            subtitle = "Personal account",
            imageUrl = user.profileImageUrl
        )
        val studioSummaries = managedStudios.map { studio ->
            AccountSummary(
                account = ActiveAccount.StudioAccount(userUid = user.uid, studioId = studio.id),
                displayName = studio.displayName.ifBlank { "Studio" },
                subtitle = "Business account",
                imageUrl = studio.profileImageUrl,
                isVerified = studio.verified
            )
        }
        accounts = listOf(personal) + studioSummaries
    }
}
