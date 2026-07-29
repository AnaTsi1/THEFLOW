package com.ana.theflow.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText

class EditProfileFragment : Fragment() {
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val storageRepository = StorageRepository()
    private var user: User? = null
    private var pendingProfilePhotoUri: Uri? = null
    private var pendingCoverImageUri: Uri? = null
    private lateinit var bio: EditText
    private lateinit var background: EditText
    private lateinit var skills: EditText
    private lateinit var progress: ProgressBar
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmDiscardOrClose()
            }
        })
    }

    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingProfilePhotoUri = uri
        Toast.makeText(requireContext(), if (uri == null) "No profile photo selected" else "Profile photo selected", Toast.LENGTH_SHORT).show()
    }

    private val coverImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingCoverImageUri = uri
        Toast.makeText(requireContext(), if (uri == null) "No cover image selected" else "Cover image selected", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Edit public profile", onBack = { confirmDiscardOrClose() })
        val scroll = SettingsUi.contentScroll(requireContext())
        val content = SettingsUi.contentColumn(requireContext())
        bio = field("Bio", 108.dp())
        background = field("Professional background", 120.dp())
        skills = field("Dance styles and skills, comma separated", 54.dp())
        content.addView(SettingsUi.message(requireContext(), "Only public profile fields are edited here. Private account settings remain in Account."))
        content.addView(Button(requireContext()).apply {
            text = "Choose profile photo"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply { bottomMargin = 10.dp() }
            setOnClickListener { profilePhotoPicker.launch("image/*") }
        })
        content.addView(Button(requireContext()).apply {
            text = "Choose cover image"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_brand))
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply { bottomMargin = 12.dp() }
            setOnClickListener { coverImagePicker.launch("image/*") }
        })
        content.addView(bio)
        content.addView(background)
        content.addView(skills)
        progress = ProgressBar(requireContext()).apply { visibility = View.GONE }
        content.addView(progress)
        saveButton = Button(requireContext()).apply {
            text = "Save"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp()).apply { topMargin = 14.dp() }
            setOnClickListener { save() }
        }
        content.addView(saveButton)
        scroll.addView(content)
        root.addView(scroll)
        load()
        return root
    }

    private fun load() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "Please log in to edit your profile.", Toast.LENGTH_SHORT).show()
            return
        }
        progress.visibility = View.VISIBLE
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = {
                if (!isAdded) return@getUserByUid
                user = it
                bio.setText(it.bio)
                background.setText(it.professionalBackground)
                skills.setText(it.skills.joinToString(", "))
                progress.visibility = View.GONE
            },
            onFailure = { error ->
                if (!isAdded) return@getUserByUid
                progress.visibility = View.GONE
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load your profile."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun save() {
        val uid = authRepository.getCurrentUserUid() ?: return
        val current = user ?: return
        setLoading(true)
        userRepository.updateUserProfile(
            uid = uid,
            firstName = current.firstName,
            lastName = current.lastName,
            birthDate = current.birthDate,
            age = current.age,
            headline = current.headline,
            bio = bio.text.toString().trim(),
            professionalBackground = background.text.toString().trim(),
            skills = skills.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() },
            yearsOfExperience = current.yearsOfExperience,
            studiosTrainedAt = current.studiosTrainedAt,
            teachersLearnedFrom = current.teachersLearnedFrom,
            performancesCompetitions = current.performancesCompetitions,
            availability = current.availability,
            instagramUrl = current.instagramUrl,
            tiktokUrl = current.tiktokUrl,
            youtubeUrl = current.youtubeUrl,
            onSuccess = { uploadImages(uid) },
            onFailure = { error ->
                if (!isAdded) return@updateUserProfile
                setLoading(false)
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not save your profile."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun uploadImages(uid: String) {
        val profileUri = pendingProfilePhotoUri
        val coverUri = pendingCoverImageUri
        if (profileUri != null) {
            storageRepository.uploadProfileImage(
                uid = uid,
                imageUri = profileUri,
                onSuccess = { uploadCover(uid, coverUri) },
                onFailure = { failUpload(it) }
            )
        } else {
            uploadCover(uid, coverUri)
        }
    }

    private fun uploadCover(uid: String, uri: Uri?) {
        if (uri == null) {
            finishSave()
            return
        }
        storageRepository.uploadCoverImage(
            uid = uid,
            imageUri = uri,
            onSuccess = { finishSave() },
            onFailure = { failUpload(it) }
        )
    }

    private fun finishSave() {
        if (!isAdded) return
        setLoading(false)
        requireContext()
            .getSharedPreferences(PROFILE_EDIT_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PROFILE_EDIT_UPDATED, true)
            .apply()
        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    private fun failUpload(error: String) {
        if (!isAdded) return
        setLoading(false)
        Toast.makeText(requireContext(), UiText.friendlyError(error, "Your profile was saved, but media upload failed."), Toast.LENGTH_LONG).show()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        saveButton.isEnabled = !loading
    }

    private fun field(hintValue: String, height: Int): EditText {
        return EditText(requireContext()).apply {
            hint = hintValue
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = height
            gravity = android.view.Gravity.TOP
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 10.dp()
            }
        }
    }

    private fun confirmDiscardOrClose() {
        if (!hasUnsavedInput()) {
            parentFragmentManager.popBackStack()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Discard changes?")
            .setMessage("Unsaved profile edits will be lost.")
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton("Discard") { _, _ -> parentFragmentManager.popBackStack() }
            .show()
    }

    private fun hasUnsavedInput(): Boolean {
        val current = user ?: return false
        return bio.text.toString() != current.bio ||
            background.text.toString() != current.professionalBackground ||
            skills.text.toString() != current.skills.joinToString(", ") ||
            pendingProfilePhotoUri != null ||
            pendingCoverImageUri != null
    }

    companion object {
        const val PROFILE_EDIT_PREFS = "profile_edit_state"
        const val PROFILE_EDIT_UPDATED = "profile_updated"
    }
}
