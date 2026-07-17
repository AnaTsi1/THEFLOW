package com.ana.theflow.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentSettingsBinding
import com.ana.theflow.ui.auth.LoginActivity
import com.ana.theflow.utilities.Constants

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.settingsBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.settingsBTNEditPreferences.setOnClickListener {
            (requireActivity() as MainActivity).openEditPreferences()
        }
        binding.settingsBTNProfessionalVerification.setOnClickListener {
            (requireActivity() as MainActivity).openProfessionalVerification()
        }
        binding.settingsBTNAdminReview.setOnClickListener {
            (requireActivity() as MainActivity).openAdminReview()
        }
        binding.settingsBTNLogout.setOnClickListener {
            logout()
        }
        loadAdminAccess()
    }

    private fun loadAdminAccess() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = success@ { user ->
                if (_binding == null) return@success
                binding.settingsBTNAdminReview.visibility =
                    if (user.role.isAdminRole()) View.VISIBLE else View.GONE
            },
            onFailure = failure@ {
                if (_binding == null) return@failure
                binding.settingsBTNAdminReview.visibility = View.GONE
            }
        )
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

    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }
}
