package com.ana.theflow.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ana.theflow.databinding.FragmentRegisterBinding
import com.ana.theflow.utilities.Constants

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by activityViewModels()
    private var selectedRole = Constants.UserRole.DANCER

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupScreenTitle()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupScreenTitle() {
        binding.registerLBLTitle.text = when (selectedRole) {
            Constants.UserRole.STUDIO_MANAGER -> "Create Studio Manager Account"
            else -> "Create Dancer Account"
        }
    }

    private fun observeViewModel() {
        authViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.registerProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.registerBTNSubmit.isEnabled = !state.isLoading

            binding.registerLBLMessage.text = state.errorMessage.orEmpty()
            binding.registerLBLMessage.visibility =
                if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.registerBTNDancer.setOnClickListener {
            selectedRole = Constants.UserRole.DANCER
            renderRoleSelection()
        }

        binding.registerBTNStudioManager.setOnClickListener {
            selectedRole = Constants.UserRole.STUDIO_MANAGER
            renderRoleSelection()
        }

        binding.registerBTNSubmit.setOnClickListener {
            registerUser()
        }

        binding.registerBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun registerUser() {
        authViewModel.register(
            firstName = binding.registerEDTFirstName.text.toString().trim(),
            lastName = binding.registerEDTLastName.text.toString().trim(),
            email = binding.registerEDTEmail.text.toString().trim(),
            password = binding.registerEDTPassword.text.toString(),
            role = selectedRole,
            onSuccess = {
                Toast.makeText(requireContext(), "Account created successfully", Toast.LENGTH_SHORT).show()
                (requireActivity() as LoginActivity).openMainApp(
                    com.ana.theflow.MainActivity.START_DESTINATION_ONBOARDING
                )
            }
        )
    }

    private fun renderRoleSelection() {
        val dancerSelected = selectedRole == Constants.UserRole.DANCER
        setupScreenTitle()
        binding.registerBTNDancer.setBackgroundResource(
            if (dancerSelected) com.ana.theflow.R.drawable.bg_button_primary else com.ana.theflow.R.drawable.bg_button_secondary
        )
        binding.registerBTNStudioManager.setBackgroundResource(
            if (dancerSelected) com.ana.theflow.R.drawable.bg_button_secondary else com.ana.theflow.R.drawable.bg_button_primary
        )
        binding.registerBTNDancer.setTypeface(null, if (dancerSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        binding.registerBTNStudioManager.setTypeface(null, if (dancerSelected) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
