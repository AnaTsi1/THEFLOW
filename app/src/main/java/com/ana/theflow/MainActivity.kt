package com.ana.theflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ana.theflow.databinding.ActivityMainBinding
import com.ana.theflow.prototype.PrototypeItem
import com.ana.theflow.ui.detail.DetailFragment
import com.ana.theflow.ui.discover.DiscoverFragment
import com.ana.theflow.ui.home.HomeFragment
import com.ana.theflow.ui.onboarding.OnboardingFragment
import com.ana.theflow.ui.profile.ProfileFragment
import com.ana.theflow.ui.search.SearchFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            binding.mainLAYBottomNav.visibility = android.view.View.GONE
            openFragment(OnboardingFragment())
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

        binding.mainNavSearch.setOnClickListener {
            openFragment(SearchFragment())
            markSelectedTab(AppTab.SEARCH)
        }

        binding.mainNavProfile.setOnClickListener {
            openFragment(ProfileFragment())
            markSelectedTab(AppTab.PROFILE)
        }
    }

    fun completeOnboarding() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(DiscoverFragment())
        markSelectedTab(AppTab.DISCOVER)
    }

    fun openHome() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(HomeFragment())
        markSelectedTab(AppTab.HOME)
    }

    fun openSearch() {
        binding.mainLAYBottomNav.visibility = android.view.View.VISIBLE
        openFragment(SearchFragment())
        markSelectedTab(AppTab.SEARCH)
    }

    fun openDetail(item: PrototypeItem) {
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
        binding.mainNavSearch.setTextColor(getTabColor(tab == AppTab.SEARCH))
        binding.mainNavProfile.setTextColor(getTabColor(tab == AppTab.PROFILE))
    }

    private fun getTabColor(isSelected: Boolean): Int {
        val colorRes = if (isSelected) R.color.neon_pink else R.color.text_muted
        return getColor(colorRes)
    }

    enum class AppTab {
        HOME,
        DISCOVER,
        SEARCH,
        PROFILE
    }
}
