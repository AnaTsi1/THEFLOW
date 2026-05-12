package com.ana.theflow.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.ProfessionalApplicationRepository
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.utilities.Constants

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val applicationRepository = ProfessionalApplicationRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNTeacher.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.VERIFIED_TEACHER)
        }
        binding.settingsBTNChoreographer.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.CHOREOGRAPHER)
        }
        binding.settingsBTNStudio.setOnClickListener {
            submit(Constants.ProfessionalApplicationType.STUDIO)
        }
    }

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
            requestedDisplayName = binding.settingsEDTDisplayName.text.toString(),
            onSuccess = {
                setLoading(false)
                binding.settingsLBLMessage.text = "Application submitted for review."
                binding.settingsLBLMessage.visibility = View.VISIBLE
            },
            onFailure = { error ->
                setLoading(false)
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setLoading(isLoading: Boolean) {
        binding.settingsBTNTeacher.isEnabled = !isLoading
        binding.settingsBTNChoreographer.isEnabled = !isLoading
        binding.settingsBTNStudio.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
