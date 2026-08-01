// Admin-only screen for granting/revoking additive permissions on one user at a time:
// verified teacher, choreographer, and studio-manager membership.
package com.ana.theflow.ui.admin

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AdminRepository
import com.ana.theflow.data.repository.StudioRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.AccountPermissions
import com.bumptech.glide.Glide

class AdminUserPermissionsFragment : Fragment() {

    private val adminRepository = AdminRepository()
    private val userRepository = UserRepository()
    private val studioRepository = StudioRepository()
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

    // Builds the search box and results area; the detail panel stays empty until a user is picked.
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

    // Runs the name search and lists matching users to pick from.
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

    // The app has no @username concept - email is the closest stable secondary identifier a user
    // has on their profile, so it stands in for one here instead of a raw uid.
    private fun userRow(user: User): View {
        val context = requireContext()
        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }

            addView(ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(46.dp(), 46.dp()).apply { rightMargin = 10.dp() }
                if (user.profileImageUrl.isNotBlank()) {
                    Glide.with(this@AdminUserPermissionsFragment).load(user.profileImageUrl).circleCrop().into(this)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = name
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = user.email.ifBlank { "No email on file" }
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 2.dp(), 0, 0)
                })
                addView(TextView(context).apply {
                    text = AccountPermissions.badges(user).ifEmpty { listOf("Dancer") }.joinToString(", ")
                    setTextColor(context.getColor(R.color.flow_brand))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 3.dp(), 0, 0)
                })
            })
            setOnClickListener {
                selectedUid = user.uid
                loadDetail(user.uid)
            }
        }
    }

    // Loads the selected user plus every studio they manage, then renders the permissions panel.
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

    // Renders the name header, the three toggle rows (Teacher/Choreographer/Studio Manager),
    // and the list of studios this user currently manages.
    private fun renderDetail(user: User, studios: List<Studio>) {
        detailPanel.removeAllViews()
        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        detailPanel.addView(TextView(requireContext()).apply {
            text = name
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 16.dp(), 0, 6.dp())
        })

        // Permissions are additive, not mutually exclusive - a user can be Verified Teacher AND
        // Choreographer AND a studio manager all at once. Each row below reflects its own
        // independent state; nothing here forces picking just one.
        detailPanel.addView(
            toggleBadgeRow("Verified Teacher", user.verifiedTeacher) {
                confirmAndRun(if (user.verifiedTeacher) "Revoke Verified Teacher?" else "Grant Verified Teacher?") {
                    if (user.verifiedTeacher) {
                        adminRepository.revokeTeacher(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError)
                    } else {
                        adminRepository.grantTeacher(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError)
                    }
                }
            }
        )
        detailPanel.addView(
            toggleBadgeRow("Choreographer", user.verifiedChoreographer) {
                confirmAndRun(if (user.verifiedChoreographer) "Revoke Choreographer?" else "Grant Choreographer?") {
                    if (user.verifiedChoreographer) {
                        adminRepository.revokeChoreographer(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError)
                    } else {
                        adminRepository.grantChoreographer(user.uid, onSuccess = { onPermissionChanged(user.uid) }, onFailure = ::showError)
                    }
                }
            }
        )
        // Studio Manager isn't a single boolean - it's per-studio, so this row can't "revoke" in
        // one tap the way the other two can. Tapping it while ungranted jumps straight to
        // assigning a studio (mirrors a one-tap grant); while granted, it points at the per-studio
        // list below, where removal is unambiguous about which studio is affected.
        detailPanel.addView(
            toggleBadgeRow("Studio Manager", studios.isNotEmpty()) {
                if (studios.isEmpty()) {
                    showAssignToStudioDialog(user)
                } else {
                    Toast.makeText(requireContext(), "Manage individual studios below", Toast.LENGTH_SHORT).show()
                }
            }
        )

        detailPanel.addView(TextView(requireContext()).apply {
            text = "Managed studios"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 14.dp(), 0, 6.dp())
        })
        if (studios.isEmpty()) {
            detailPanel.addView(SettingsUi.message(requireContext(), "Not a manager of any studio."))
        } else {
            studios.forEach { studio -> detailPanel.addView(studioRow(user, studio)) }
        }

        detailPanel.addView(Button(requireContext()).apply {
            text = "Assign to Studio"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                topMargin = 6.dp()
            }
            setOnClickListener { showAssignToStudioDialog(user) }
        })
    }

    // One tappable permission row, showing a filled checkmark when granted and a hollow circle
    // when not.
    private fun toggleBadgeRow(title: String, granted: Boolean, onToggle: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = if (granted) "✓" else "○"
                setTextColor(context.getColor(if (granted) R.color.flow_brand else R.color.flow_text_muted))
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(28.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(if (granted) R.color.flow_ink else R.color.flow_text_secondary))
                textSize = 15f
                setTypeface(typeface, if (granted) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            setOnClickListener { onToggle() }
        }
    }

    // One managed-studio row with a Remove button. Labels the owner explicitly, since
    // AdminRepository.removeStudioManager refuses to remove an owner outright - that needs an
    // ownership transfer first, so this row makes it clear which studios that restriction applies to.
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
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 36.dp())
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

    // Searchable studio picker by name, replacing the old raw-document-id paste flow. Results
    // update as the admin types (debounce isn't necessary here - name search is a cheap
    // client-side filter over an already-fetched batch, see StudioRepository.searchStudiosByName).
    private fun showAssignToStudioDialog(user: User) {
        val context = requireContext()
        lateinit var dialog: AlertDialog
        val resultsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        fun runSearch(query: String) {
            resultsContainer.removeAllViews()
            studioRepository.searchStudiosByName(
                query = query,
                onSuccess = { studios ->
                    if (!isAdded) return@searchStudiosByName
                    resultsContainer.removeAllViews()
                    if (studios.isEmpty()) {
                        resultsContainer.addView(SettingsUi.message(context, "No studios found."))
                        return@searchStudiosByName
                    }
                    studios.take(20).forEach { studio ->
                        resultsContainer.addView(
                            SettingsUi.row(
                                context = context,
                                title = studio.displayName.ifBlank { "Untitled studio" },
                                description = studio.city,
                                onClick = {
                                    adminRepository.addStudioManager(
                                        studioId = studio.id,
                                        targetUid = user.uid,
                                        onSuccess = {
                                            dialog.dismiss()
                                            onPermissionChanged(user.uid)
                                        },
                                        onFailure = ::showError
                                    )
                                }
                            )
                        )
                    }
                },
                onFailure = { error ->
                    if (!isAdded) return@searchStudiosByName
                    resultsContainer.removeAllViews()
                    resultsContainer.addView(SettingsUi.message(context, error))
                }
            )
        }

        val searchInput = EditText(context).apply {
            hint = "Search studios by name"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 50.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { runSearch(s?.toString().orEmpty()) }
            })
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 12.dp(), 24.dp(), 4.dp())
            addView(searchInput)
            addView(resultsContainer)
        }

        dialog = AlertDialog.Builder(context)
            .setTitle("Assign to Studio")
            .setView(ScrollView(context).apply { addView(container) })
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        runSearch("")
    }

    // Shared confirm-before-you-act dialog for every permission change on this screen.
    private fun confirmAndRun(message: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ -> action() }
            .show()
    }

    // Confirms the change with a toast and reloads the detail panel so it reflects the new state.
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
