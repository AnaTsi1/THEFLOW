package com.ana.theflow.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.ana.theflow.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by activityViewModels()

    // Creates and returns the fragment view.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        observeViewModel()
        setupClickListeners()
    }

    // Observes UI state changes from the view model.
    private fun observeViewModel() {
        authViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.loginProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.loginBTNLogin.isEnabled = !state.isLoading
            binding.loginBTNRegister.isEnabled = !state.isLoading

            binding.loginLBLMessage.text = state.errorMessage.orEmpty()
            binding.loginLBLMessage.visibility =
                if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    // Connects buttons to their click actions.
    private fun setupClickListeners() {
        binding.loginBTNLogin.setOnClickListener {
            loginUser()
        }

        binding.loginBTNRegister.setOnClickListener {
            authViewModel.clearError()
            (requireActivity() as LoginActivity).showRegister()
        }
    }

    // Starts login with the entered credentials.
    private fun loginUser() {
        authViewModel.login(
            email = binding.loginEDTEmail.text.toString().trim(),
            password = binding.loginEDTPassword.text.toString(),
            onSuccess = {
                Toast.makeText(requireContext(), "Signed in successfully", Toast.LENGTH_SHORT).show()
                (requireActivity() as LoginActivity).routeSignedInUser()
            }
        )
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
