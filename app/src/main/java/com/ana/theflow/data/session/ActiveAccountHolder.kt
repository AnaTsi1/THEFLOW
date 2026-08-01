package com.ana.theflow.data.session

import android.content.Context
import android.content.SharedPreferences
import com.ana.theflow.data.model.account.ActiveAccount
import com.ana.theflow.data.model.user.User
import com.google.firebase.auth.FirebaseAuth

// Process-wide source of truth for which account (personal or a managed studio) is currently
// active. Backed by SharedPreferences so the choice survives process death, keyed by Firebase
// uid so switching signed-in users never leaks a studio selection across accounts.
object ActiveAccountHolder {
    private const val PREFS_NAME = "flow_active_account"
    private const val KEY_PREFIX = "active_"

    private var prefs: SharedPreferences? = null
    private val listeners = mutableListOf<(ActiveAccount) -> Unit>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Returns the active account, always falling back to the signed-in user's personal account.
    fun current(): ActiveAccount {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return ActiveAccount.Personal(userUid = "")
        val raw = prefs?.getString(KEY_PREFIX + uid, null) ?: return ActiveAccount.Personal(userUid = uid)
        return ActiveAccount.parse(raw, uid)
    }

    fun currentPersonalUid(): String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun isStudio(): Boolean = current() is ActiveAccount.StudioAccount

    fun currentStudioId(): String = (current() as? ActiveAccount.StudioAccount)?.studioId.orEmpty()

    fun set(account: ActiveAccount) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        prefs?.edit()?.putString(KEY_PREFIX + uid, account.serialize())?.apply()
        listeners.toList().forEach { it(account) }
    }

    // Falls back to the personal account if the active studio is no longer managed by this user
    // (e.g. the manager was just removed by an admin).
    fun reconcile(user: User) {
        val active = current()
        if (active is ActiveAccount.StudioAccount && active.studioId !in user.managedStudioIds) {
            set(ActiveAccount.Personal(userUid = active.userUid))
        }
    }

    // Clears the cached selection on sign-out so the next signed-in user starts on Personal.
    fun clear() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) prefs?.edit()?.remove(KEY_PREFIX + uid)?.apply()
    }

    fun addListener(listener: (ActiveAccount) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ActiveAccount) -> Unit) {
        listeners.remove(listener)
    }
}
