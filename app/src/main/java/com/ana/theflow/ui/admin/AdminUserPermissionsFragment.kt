package com.ana.theflow.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AdminRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.AccountPermissions

// Admin-only screen for granting/revoking additive permissions on one user at a time:
// verified teacher, choreographer, and studio-manager membership.
class AdminUserPermissionsFragment : Fragment() {

    private val adminRepository = AdminRepository()
    private val userRepository = UserRepository()
    private lateinit var content: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var resultsList: LinearLayout
    private lateinit var detailPanel: LinearLayout
    private var selectedUid: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "User Permissions")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        render()
        return root
    }

    private fun render() {
        content.removeAllViews()
        content.addView(SettingsUi.message(requireContext(), "Search a user, then grant or revoke their permissions. Every change is logged."))

        searchField = EditText(requireContext()).apply {
            hint = "Search by name"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 52.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            setOnEditorActionListener { _, _, _ -> search(); true }
        }
        content.addView(searchField)

        content.addView(Button(requireContext()).apply {
            text = "Search"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                bottomMargin = 12.dp()
            }
            setOnClickListener { search() }
        })

        resultsList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(resultsList)

        detailPanel = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(detailPanel)
    }

    private fun search() {
        val query = searchField.text.toString().trim()
        resultsList.removeAllViews()
        if (query.isBlank()) return
        userRepository.searchUsers(
            query = query,
            dancersOnly = false,
            onSuccess = { users ->
                if (!isAdded) return@searchUsers
                resultsList.removeAllViews()
                if (users.isEmpty()) {
                    resultsList.addView(SettingsUi.message(requireContext(), "No users found."))
                    return@searchUsers
                }
                users.take(15).forEach { user -> resultsList.addView(userRow(user)) }
            },
            onFailure = { error ->
                if (!isAdded) return@searchUsers
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun userRow(user: User): View {
        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        return SettingsUi.row(
            context = requireContext(),
            title = name,
            description = AccountPermissions.badges(user).ifEmpty { listOf("Dancer") }.joinToString(", "),
            onClick = {
                selectedUid = user.uid
                loadDetail(user.uid)
            }
        )
    }

    private fun loadDetail(uid: String) {
        detailPanel.removeAllViews()
        detailPanel.addView(SettingsUi.message(requireContext(), "Loading..."))
        adminRepository.loadUserPermissions(
            uid = uid,
            onSuccess = { user, studios -> if (isAdded) renderDetail(user, studios) },
            onFailure = { error ->
                if (!isAdded) return@loadUserPermissions
                detailPanel.removeAllViews()
                detailPanel.addView(SettingsUi.message(requireContext(), error))
            }
        )
    }

    private fun renderDetail(user: User, studios: List<Studio>) {
        detailPanel.removeAllViews()
        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        detailPanel.addView(TextView(requireContext()).apply {
            text = name
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16.dp(), 0, 6.dp())
        })

        detailPanel.addView(
            permissionRow(
                title = "Verified Teacher",
                granted = user.verifiedTeacher,
                onGrant = { adminRepository.grantTeacher(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError) },
                onRevoke = { adminRepository.revokeTeacher(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError) }
            )
        )
        detailPanel.addView(
            permissionRow(
                title = "Choreographer",
                granted = user.verifiedChoreographer,
                onGrant = { adminRepository.grantChoreographer(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError) },
                onRevoke = { adminRepository.revokeChoreographer(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError) }
            )
        )

        detailPanel.addView(TextView(requireContext()).apply {
            text = "Managed studios"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 14.dp(), 0, 6.dp())
        })
        if (studios.isEmpty()) {
            detailPanel.addView(SettingsUi.message(requireContext(), "Not a manager of any studio."))
        } else {
            studios.forEach { studio -> detailPanel.addView(studioRow(user, studio)) }
        }

        detailPanel.addView(Button(requireContext()).apply {
            text = "Add to a studio (by studio id)"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                topMargin = 6.dp()
            }
            setOnClickListener { showAddToStudioDialog(user) }
        })
    }

    private fun permissionRow(title: String, granted: Boolean, onGrant: () -> Unit, onRevoke: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = "$title ${if (granted) "(granted)" else ""}"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = if (granted) "Revoke" else "Grant"
                isAllCaps = false
                minWidth = 0
                setTextColor(context.getColor(if (granted) R.color.flow_error else R.color.flow_surface))
                setBackgroundResource(if (granted) R.drawable.bg_flow_button_secondary else R.drawable.bg_flow_button_primary)
                setOnClickListener { confirmAndRun(if (granted) "Revoke $title?" else "Grant $title?") { if (granted) onRevoke() else onGrant() } }
            })
        }
    }

    private fun studioRow(user: User, studio: Studio): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = studio.displayName.ifBlank { studio.id }.let { if (studio.ownerUid == user.uid) "$it (owner)" else it }
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(context).apply {
                text = "Remove"
                isAllCaps = false
                minWidth = 0
                setTextColor(context.getColor(R.color.flow_error))
                setBackgroundResource(R.drawable.bg_flow_button_secondary)
                setOnClickListener {
                    confirmAndRun("Remove ${studio.displayName} manager access?") {
                        adminRepository.removeStudioManager(
                            studioId = studio.id, targetUid = user.uid,
                            onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError
                        )
                    }
                }
            })
        }
    }

    private fun showAddToStudioDialog(user: User) {
        val input = EditText(requireContext()).apply {
            hint = "Studio document id"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add as manager")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add") { _, _ ->
                val studioId = input.text.toString().trim()
                if (studioId.isBlank()) return@setPositiveButton
                adminRepository.addStudioManager(
                    studioId = studioId, targetUid = user.uid,
                    onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError
                )
            }
            .show()
    }

    private fun confirmAndRun(message: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> action() }
            .show()
    }

    private fun onPermissionChanged(uid: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), "Updated", Toast.LENGTH_SHORT).show()
        loadDetail(uid)
    }

    private fun showError(error: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }
}
