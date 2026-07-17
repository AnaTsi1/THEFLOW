package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.ProfessionalApplicationRepository
import com.ana.theflow.databinding.FragmentProfessionalVerificationBinding
import com.ana.theflow.utilities.Constants

class ProfessionalVerificationFragment : Fragment() {

    private var _binding: FragmentProfessionalVerificationBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val applicationRepository = ProfessionalApplicationRepository()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfessionalVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.verificationBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.verificationBTNTeacher.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.VERIFIED_TEACHER)
        }
        binding.verificationBTNChoreographer.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.CHOREOGRAPHER)
        }
        binding.verificationBTNStudio.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.STUDIO)
        }
    }

    // Submits the selected professional application type.
    private fun submit(type: Constants.ProfessionalApplicationType) {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        applicationRepository.submitApplication(
            applicantUid = uid,
            applicationType = type,
            requestedDisplayName = binding.verificationEDTDisplayName.text.toString(),
            onSuccess = {
                setLoading(false)
                binding.verificationLBLMessage.text = "Application submitted for review."
                binding.verificationLBLMessage.visibility = View.VISIBLE
            },
            onFailure = { error ->
                setLoading(false)
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Enables or disables verification buttons during loading.
    private fun setLoading(isLoading: Boolean) {
        binding.verificationBTNTeacher.isEnabled = !isLoading
        binding.verificationBTNChoreographer.isEnabled = !isLoading
        binding.verificationBTNStudio.isEnabled = !isLoading
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
