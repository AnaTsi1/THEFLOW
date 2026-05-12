package com.ana.theflow

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.databinding.ActivityMainBinding
import com.ana.theflow.ui.detail.DetailFragment
import com.ana.theflow.ui.discover.DiscoverFragment
import com.ana.theflow.ui.home.HomeFragment
import com.ana.theflow.ui.onboarding.OnboardingFragment
import com.ana.theflow.ui.profile.ProfileFragment
import com.ana.theflow.ui.search.SearchFragment
import com.ana.theflow.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            if (AuthRepository().getCurrentUserUid() == null) {
                startActivity(Intent(this, com.ana.theflow.ui.auth.LoginActivity::class.java))
                finish()
                return
            }

            when (intent.getStringExtra(EXTRA_START_DESTINATION)) {
                START_DESTINATION_ONBOARDING -> openOnboarding()
                else -> openHome()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.mainNavHome.setOnClickListener {
            openFragment(HomeFragment())
            markSelectedTab(AppTab.HOME)
        }

        binding.mainNavDiscover.setOnClickListener {
            openFragment(DiscoverFragment())
            markSelectedTab(AppTab.DISCOVER)
        }

        binding.mainNavProfile.setOnClickListener {
            openFragment(ProfileFragment())
            markSelectedTab(AppTab.PROFILE)
        }

    }

    fun completeOnboarding() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(HomeFragment())
        markSelectedTab(AppTab.HOME)
    }

    private fun openOnboarding() {
        binding.mainLAYBottomNav.visibility = android.view.View.GONE
        openFragment(OnboardingFragment())
    }

    fun openHome() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(HomeFragment())
        markSelectedTab(AppTab.HOME)
    }

    fun openSearch() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(SearchFragment())
    }

    fun openSettings() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(SettingsFragment())
    }

    fun openDetail(item: DiscoveryItem) {
        binding.mainLAYBottomNav.visibility = android.view.View.GONE
        openFragment(DetailFragment.newInstance(item.id))
    }

    fun closeDetail() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(DiscoverFragment())
        markSelectedTab(AppTab.DISCOVER)
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)
            .commit()
    }

    private fun markSelectedTab(tab: AppTab) {
        binding.mainNavHome.setTextColor(getTabColor(tab == AppTab.HOME))
        binding.mainNavDiscover.setTextColor(getTabColor(tab == AppTab.DISCOVER))
        binding.mainNavProfile.setTextColor(getTabColor(tab == AppTab.PROFILE))
    }

    private fun getTabColor(isSelected: Boolean): Int {
        val colorRes = if (isSelected) R.color.neon_pink else R.color.text_muted
        return getColor(colorRes)
    }

    enum class AppTab {
        HOME,
        DISCOVER,
        PROFILE
    }

    companion object {
        const val EXTRA_START_DESTINATION = "EXTRA_START_DESTINATION"
        const val START_DESTINATION_ONBOARDING = "onboarding"
        const val START_DESTINATION_HOME = "home"
    }
}
