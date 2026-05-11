package com.ana.theflow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ana.theflow.MainActivity
import com.ana.theflow.databinding.ActivityRegisterBinding
import com.ana.theflow.utilities.Constants

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val authViewModel: AuthViewModel by viewModels()

    private val role: Constants.UserRole by lazy {
        val roleName = intent.getStringExtra(EXTRA_USER_ROLE)
        Constants.UserRole.valueOf(roleName ?: Constants.UserRole.DANCER.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupScreenTitle()
        observeViewModel()
        setupClickListeners()
    }

    private fun setupScreenTitle() {
        binding.registerLBLTitle.text = when (role) {
            Constants.UserRole.DANCER -> "Create Dancer Account"
            Constants.UserRole.STUDIO_OWNER -> "Create Studio Owner Account"
            Constants.UserRole.ADMIN -> "Create Admin Account"
        }
    }

    private fun observeViewModel() {
        authViewModel.uiState.observe(this) { state ->
            binding.registerProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            binding.registerBTNSubmit.isEnabled = !state.isLoading

            binding.registerLBLMessage.text = state.errorMessage.orEmpty()
            binding.registerLBLMessage.visibility =
                if (state.errorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.registerBTNSubmit.setOnClickListener {
            registerUser()
        }

        binding.registerBTNBack.setOnClickListener {
            finish()
        }
    }

    private fun registerUser() {
        authViewModel.register(
            firstName = binding.registerEDTFirstName.text.toString().trim(),
            lastName = binding.registerEDTLastName.text.toString().trim(),
            email = binding.registerEDTEmail.text.toString().trim(),
            password = binding.registerEDTPassword.text.toString(),
            role = role,
            onSuccess = {
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        )
    }

    companion object {
        const val EXTRA_USER_ROLE = "EXTRA_USER_ROLE"
    }
}
