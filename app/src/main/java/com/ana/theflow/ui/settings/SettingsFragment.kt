package com.ana.theflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.ui.auth.LoginActivity

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNEditPreferences.setOnClickListener {
            (requireActivity() as MainActivity).openEditPreferences()
        }
        binding.settingsBTNProfessionalVerification.setOnClickListener {
            (requireActivity() as MainActivity).openProfessionalVerification()
        }
        binding.settingsBTNLogout.setOnClickListener {
            logout()
        }
    }

    // Signs out the current user and returns to login.
    private fun logout() {
        authRepository.logout()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
