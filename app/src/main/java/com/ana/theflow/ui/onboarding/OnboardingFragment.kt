package com.ana.theflow.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentOnboardingBinding
import com.ana.theflow.utilities.CityOptions

class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()
    private val isEditMode get() = arguments?.getBoolean(ARG_EDIT_MODE) == true

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        CityOptions.configureCitySelector(requireContext(), binding.onboardingEDTLocation)
        configureMode()
        binding.onboardingBTNContinue.setOnClickListener {
            savePreferences()
        }
    }

    // Configures the screen for onboarding or editing existing preferences.
    private fun configureMode() {
        if (!isEditMode) return

        binding.onboardingLBLTitle.text = "Edit Preferences"
        binding.onboardingLBLSubtitle.text = "Update your city, interests, and recommendation preferences."
        binding.onboardingBTNContinue.text = "Save Preferences"
        loadExistingPreferences()
    }

    // Loads existing preferences into the edit form.
    private fun loadExistingPreferences() {
        userRepository.loadPreferenceSettings(
            onSuccess = { preferences ->
                if (_binding == null) return@loadPreferenceSettings
                populatePreferences(preferences)
            },
            onFailure = { error ->
                if (_binding == null) return@loadPreferenceSettings
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Fills the preference form from private feed preferences.
    private fun populatePreferences(preferences: UserRepository.PreferenceSettings) {
        binding.onboardingCHKHiphop.isChecked = preferences.styles.any { it.equals("Hip Hop", ignoreCase = true) }
        binding.onboardingCHKHeels.isChecked = preferences.styles.any { it.equals("Heels", ignoreCase = true) }
        binding.onboardingCHKSalsa.isChecked = preferences.styles.any { it.equals("Salsa", ignoreCase = true) }
        binding.onboardingCHKContemporary.isChecked = preferences.styles.any { it.equals("Contemporary", ignoreCase = true) }
        binding.onboardingCHKAfro.isChecked = preferences.styles.any { it.equals("Afro", ignoreCase = true) }
        binding.onboardingCHKBallet.isChecked = preferences.styles.any { it.equals("Ballet", ignoreCase = true) }
        when (preferences.level) {
            "Beginner" -> binding.onboardingRADIOLevel.check(binding.onboardingRDBBeginner.id)
            "Advanced" -> binding.onboardingRADIOLevel.check(binding.onboardingRDBAdvanced.id)
            else -> binding.onboardingRADIOLevel.check(binding.onboardingRDBIntermediate.id)
        }
        binding.onboardingEDTLocation.setText(CityOptions.normalizeCity(preferences.location), false)
        binding.onboardingEDTPreferredStudios.setText(preferences.preferredStudios.joinToString(", "))
        binding.onboardingEDTPreferredTeachers.setText(preferences.preferredTeachers.joinToString(", "))
        binding.onboardingEDTPreferredDancers.setText(preferences.preferredDancers.joinToString(", "))
    }

    // Saves onboarding or edited preferences.
    private fun savePreferences() {
        binding.onboardingBTNContinue.isEnabled = false
        if (isEditMode) {
            saveEditedPreferences()
        } else {
            saveOnboardingPreferences()
        }
    }

    // Saves first-time onboarding preferences.
    private fun saveOnboardingPreferences() {
        userRepository.saveOnboardingPreferences(
            styles = selectedStyles(),
            level = selectedLevel(),
            location = selectedCity(),
            preferredStudios = commaList(binding.onboardingEDTPreferredStudios.text.toString()),
            preferredTeachers = commaList(binding.onboardingEDTPreferredTeachers.text.toString()),
            preferredDancers = commaList(binding.onboardingEDTPreferredDancers.text.toString()),
            onSuccess = {
                DiscoveryRepository.hydratePreferences(
                    styles = selectedStyles(),
                    level = selectedLevel(),
                    location = selectedCity(),
                    preferredStudios = commaList(binding.onboardingEDTPreferredStudios.text.toString()),
                    preferredTeachers = commaList(binding.onboardingEDTPreferredTeachers.text.toString()),
                    preferredDancers = commaList(binding.onboardingEDTPreferredDancers.text.toString())
                )
                (requireActivity() as MainActivity).completeOnboarding()
            },
            onFailure = { error ->
                binding.onboardingBTNContinue.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Saves updated preferences from Settings.
    private fun saveEditedPreferences() {
        userRepository.updatePreferenceSettings(
            styles = selectedStyles(),
            level = selectedLevel(),
            location = selectedCity(),
            preferredStudios = commaList(binding.onboardingEDTPreferredStudios.text.toString()),
            preferredTeachers = commaList(binding.onboardingEDTPreferredTeachers.text.toString()),
            preferredDancers = commaList(binding.onboardingEDTPreferredDancers.text.toString()),
            onSuccess = {
                DiscoveryRepository.hydratePreferences(
                    styles = selectedStyles(),
                    level = selectedLevel(),
                    location = selectedCity(),
                    preferredStudios = commaList(binding.onboardingEDTPreferredStudios.text.toString()),
                    preferredTeachers = commaList(binding.onboardingEDTPreferredTeachers.text.toString()),
                    preferredDancers = commaList(binding.onboardingEDTPreferredDancers.text.toString())
                )
                Toast.makeText(requireContext(), "Preferences updated", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            },
            onFailure = { error ->
                binding.onboardingBTNContinue.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Returns the selected dance styles.
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

    // Returns the selected dance level.
    private fun selectedLevel(): String {
        return when (binding.onboardingRADIOLevel.checkedRadioButtonId) {
            binding.onboardingRDBBeginner.id -> "Beginner"
            binding.onboardingRDBAdvanced.id -> "Advanced"
            else -> "Intermediate"
        }
    }

    // Returns the selected city as a normalized value.
    private fun selectedCity(): String {
        return CityOptions.normalizeCity(binding.onboardingEDTLocation.text.toString())
    }

    // Splits comma-separated text into a list.
    private fun commaList(value: String): List<String> {
        return value.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EDIT_MODE = "ARG_EDIT_MODE"

        // Creates an onboarding fragment configured for editing preferences.
        fun newEditInstance(): OnboardingFragment {
            return OnboardingFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_EDIT_MODE, true)
                }
            }
        }
    }
}
