package com.ana.theflow.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.databinding.ActivityLoginBinding
import com.ana.theflow.utilities.Constants

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    // Sets up the activity when it is created.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            return
        }

        if (authViewModel.getCurrentUserUid() == null) {
            showLogin()
        } else {
            routeSignedInUser()
        }
    }

    // Shows the login screen.
    fun showLogin() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.auth_fragment_container, LoginFragment())
            .commit()
    }

    // Shows the registration screen.
    fun showRegister() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.auth_fragment_container, RegisterFragment())
            .addToBackStack(null)
            .commit()
    }

    // Sends a signed-in user to the correct app screen.
    fun routeSignedInUser() {
        binding.authFragmentContainer.visibility = View.GONE
        authViewModel.loadCurrentUser(
            onSuccess = { user ->
                val destination = if (user.role.isAdminRole() || user.onboardingCompleted) {
                    MainActivity.START_DESTINATION_HOME
                } else {
                    MainActivity.START_DESTINATION_ONBOARDING
                }
                openMainApp(destination)
            },
            onFailure = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                binding.authFragmentContainer.visibility = View.VISIBLE
                showLogin()
            }
        )
    }

    // Opens the main app after authentication.
    fun openMainApp(startDestination: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_START_DESTINATION, startDestination)
        }
        startActivity(intent)
        finish()
    }

    private fun String.isAdminRole(): Boolean {
        return equals(Constants.UserRole.ADMIN.name, ignoreCase = true) ||
            equals(Constants.UserRole.ADMIN.firestoreValue, ignoreCase = true)
    }
}
