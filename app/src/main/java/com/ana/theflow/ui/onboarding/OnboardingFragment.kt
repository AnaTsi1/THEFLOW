package com.ana.theflow.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.databinding.FragmentOnboardingBinding
import com.ana.theflow.prototype.RecommendationEngine

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.onboardingBTNContinue.setOnClickListener {
            RecommendationEngine.savePreferences(
                styles = selectedStyles(),
                level = selectedLevel(),
                location = binding.onboardingEDTLocation.text.toString().ifBlank { "Tel Aviv" }
            )
            (requireActivity() as MainActivity).completeOnboarding()
        }
    }

    private fun selectedStyles(): Set<String> {
        val styles = mutableSetOf<String>()
        if (binding.onboardingCHKHiphop.isChecked) styles.add("Hip Hop")
        if (binding.onboardingCHKHeels.isChecked) styles.add("Heels")
        if (binding.onboardingCHKSalsa.isChecked) styles.add("Salsa")
        if (binding.onboardingCHKContemporary.isChecked) styles.add("Contemporary")
        if (binding.onboardingCHKAfro.isChecked) styles.add("Afro")
        if (binding.onboardingCHKBallet.isChecked) styles.add("Ballet")
        return styles.ifEmpty { setOf("Hip Hop") }
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
