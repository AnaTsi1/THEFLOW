package com.ana.theflow.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.onboardingBTNContinue.setOnClickListener {
            saveOnboarding()
        }
    }

    private fun saveOnboarding() {
        binding.onboardingBTNContinue.isEnabled = false
        userRepository.saveOnboardingPreferences(
            styles = selectedStyles(),
            level = selectedLevel(),
            location = binding.onboardingEDTLocation.text.toString().ifBlank { "Tel Aviv" },
            onSuccess = {
                (requireActivity() as MainActivity).completeOnboarding()
            },
            onFailure = { error ->
                binding.onboardingBTNContinue.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun selectedStyles(): List<String> {
        val styles = mutableListOf<String>()
        if (binding.onboardingCHKHiphop.isChecked) styles.add("Hip Hop")
        if (binding.onboardingCHKHeels.isChecked) styles.add("Heels")
        if (binding.onboardingCHKSalsa.isChecked) styles.add("Salsa")
        if (binding.onboardingCHKContemporary.isChecked) styles.add("Contemporary")
        if (binding.onboardingCHKAfro.isChecked) styles.add("Afro")
        if (binding.onboardingCHKBallet.isChecked) styles.add("Ballet")
        return styles.ifEmpty { listOf("Hip Hop") }
    }

    private fun selectedLevel(): String {
        return when (binding.onboardingRADIOLevel.checkedRadioButtonId) {
            binding.onboardingRDBBeginner.id -> "Beginner"
            binding.onboardingRDBAdvanced.id -> "Advanced"
            else -> "Intermediate"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
