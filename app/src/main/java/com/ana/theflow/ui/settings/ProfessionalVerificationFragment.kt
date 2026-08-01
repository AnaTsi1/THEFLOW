package com.ana.theflow.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.ProfessionalApplicationRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.databinding.FragmentProfessionalVerificationBinding
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.Constants

class ProfessionalVerificationFragment : Fragment() {

    private var _binding: FragmentProfessionalVerificationBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val applicationRepository = ProfessionalApplicationRepository()
    private val storageRepository = StorageRepository()
    private var selectedType: Constants.ProfessionalApplicationType? = null
    private var pendingDocumentUri: Uri? = null
    private var pendingDocumentName: String = ""

    private val documentPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingDocumentUri = uri
        pendingDocumentName = uri?.let { documentFileName(it) }.orEmpty()
        binding.verificationLBLDocument.text = pendingDocumentName.ifBlank { "" }
        binding.verificationLBLDocument.visibility = if (uri == null) View.GONE else View.VISIBLE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfessionalVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.verificationBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.verificationCARDTeacher.setOnClickListener {
            selectType(Constants.ProfessionalApplicationType.VERIFIED_TEACHER)
        }
        binding.verificationCARDChoreographer.setOnClickListener {
            selectType(Constants.ProfessionalApplicationType.CHOREOGRAPHER)
        }
        binding.verificationBTNDocument.setOnClickListener {
            documentPicker.launch("*/*")
        }
        binding.verificationBTNSubmit.setOnClickListener { submit() }
    }

    // Resolves a display name for the picked document from its content Uri.
    private fun documentFileName(uri: Uri): String {
        var name = ""
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) name = cursor.getString(nameIndex).orEmpty()
        }
        return name.ifBlank { "document" }
    }

    private fun selectType(type: Constants.ProfessionalApplicationType) {
        selectedType = type
        binding.verificationCARDTeacher.setBackgroundResource(
            if (type == Constants.ProfessionalApplicationType.VERIFIED_TEACHER) R.drawable.bg_flow_event_panel else R.drawable.bg_flow_card
        )
        binding.verificationIMGTeacherCheck.visibility = if (type == Constants.ProfessionalApplicationType.VERIFIED_TEACHER) View.VISIBLE else View.INVISIBLE
        binding.verificationCARDChoreographer.setBackgroundResource(
            if (type == Constants.ProfessionalApplicationType.CHOREOGRAPHER) R.drawable.bg_flow_event_panel else R.drawable.bg_flow_card
        )
        binding.verificationIMGChoreographerCheck.visibility = if (type == Constants.ProfessionalApplicationType.CHOREOGRAPHER) View.VISIBLE else View.INVISIBLE
        hideMessage()
    }

    // Submits the selected professional application type.
    private fun submit() {
        val type = selectedType
        if (type == null) {
            showMessage("Choose Verified Teacher or Choreographer above.")
            return
        }
        val displayName = binding.verificationEDTDisplayName.text.toString().trim()
        if (displayName.isBlank()) {
            showMessage("Add the display name you'd like your badge to show.")
            return
        }
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            showMessage("User is not logged in")
            return
        }

        setLoading(true)
        applicationRepository.submitApplication(
            applicantUid = uid,
            applicationType = type,
            requestedDisplayName = displayName,
            experienceDetails = binding.verificationEDTExperience.text.toString().trim(),
            onSuccess = { applicationId ->
                if (_binding == null) return@submitApplication
                uploadDocumentIfNeeded(applicationId)
            },
            onFailure = { error ->
                if (_binding == null) return@submitApplication
                setLoading(false)
                showMessage(UiText.friendlyError(error, "We could not submit this application."))
            }
        )
    }

    // The application document must exist before a document can be attached to it (the upload
    // path and the arrayUnion write both target professionalApplications/{applicationId}).
    private fun uploadDocumentIfNeeded(applicationId: String) {
        val uri = pendingDocumentUri
        if (uri == null) {
            finishSubmit()
            return
        }
        storageRepository.uploadVerificationDocument(
            applicationId = applicationId,
            fileUri = uri,
            fileName = pendingDocumentName,
            onSuccess = { finishSubmit() },
            onFailure = {
                // The application itself was already submitted successfully - a document upload
                // hiccup shouldn't look like the whole submission failed.
                finishSubmit(documentWarning = true)
            }
        )
    }

    private fun finishSubmit(documentWarning: Boolean = false) {
        if (_binding == null) return
        setLoading(false)
        binding.verificationBTNSubmit.text = "Submitted for review"
        binding.verificationBTNSubmit.isEnabled = false
        if (documentWarning) {
            showMessage("Application submitted, but the document could not be uploaded. You can try again later.")
        }
    }

    private fun showMessage(text: String) {
        binding.verificationLBLMessage.text = text
        binding.verificationLBLMessage.visibility = View.VISIBLE
    }

    private fun hideMessage() {
        binding.verificationLBLMessage.visibility = View.GONE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.verificationBTNSubmit.isEnabled = !isLoading
        binding.verificationBTNSubmit.text = if (isLoading) "Submitting..." else "Submit Application"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
