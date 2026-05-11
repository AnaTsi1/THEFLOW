package com.ana.theflow.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ana.theflow.databinding.ActivityRegisterChoiceBinding
import com.ana.theflow.utilities.Constants

class RegisterChoiceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterChoiceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRegisterChoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.registerChoiceBTNDancer.setOnClickListener {
            openRegisterActivity(Constants.UserRole.DANCER)
        }

        binding.registerChoiceBTNStudioOwner.setOnClickListener {
            openRegisterActivity(Constants.UserRole.STUDIO_OWNER)
        }

        binding.registerChoiceBTNBack.setOnClickListener {
            finish()
        }
    }

    private fun openRegisterActivity(role: Constants.UserRole) {
        val intent = Intent(this, RegisterActivity::class.java)
        intent.putExtra(RegisterActivity.EXTRA_USER_ROLE, role.name)
        startActivity(intent)
    }
}
