// Login form: email/password fields, sign-in, forgot-password, and a link over to registration.
package com.ana.theflow.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

        binding.loginLBLForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // Shows a dialog to collect (or confirm) the email to send a password reset link to.
    private fun showForgotPasswordDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT
            setText(binding.loginEDTEmail.text)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()

        AlertDialog.Builder(context)
            .setTitle("Reset password")
            .setMessage("Enter your account email and we'll send you a link to reset your password.")
            .setView(input.apply { setPadding(padding, 0, padding, 0) })
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                authViewModel.sendPasswordReset(email) { message ->
                    if (!isAdded) return@sendPasswordReset
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
