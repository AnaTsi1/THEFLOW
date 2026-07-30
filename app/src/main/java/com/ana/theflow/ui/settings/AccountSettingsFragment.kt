package com.ana.theflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.auth.LoginActivity
import com.ana.theflow.ui.common.UiText

class AccountSettingsFragment : Fragment() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Account")
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        content.addView(SettingsUi.row(requireContext(), "Personal information", "Name, email, and account basics.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Email and password", "Credential changes require an account-management backend.", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Account status", "Signed in", value = "Active", enabled = false))
        content.addView(SettingsUi.row(requireContext(), "Log out", "Sign out on this device.", onClick = { confirmLogout() }))
        content.addView(SettingsUi.row(requireContext(), "Delete account", "Request account deletion for admin/backend processing.", destructive = true, onClick = { confirmDelete() }))
        scroll.addView(content)
        root.addView(scroll)
        return root
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log out?")
            .setMessage("You will need to sign in again to use THE FLOW.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Log out") { _, _ ->
                authRepository.logout()
                startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_delete_account_title)
            .setMessage(R.string.settings_delete_account_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.settings_delete_account) { _, _ ->
                userRepository.requestAccountDeletion(
                    onSuccess = { if (isAdded) Toast.makeText(requireContext(), R.string.settings_delete_account_confirmed, Toast.LENGTH_SHORT).show() },
                    onFailure = { error -> if (isAdded) Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not request account deletion."), Toast.LENGTH_SHORT).show() }
                )
            }
            .show()
    }
}
