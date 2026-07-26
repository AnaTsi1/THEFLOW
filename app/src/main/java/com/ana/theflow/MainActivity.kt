package com.ana.theflow

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.DiscoveryRepository
import com.ana.theflow.data.repository.MessagingRepository
import com.ana.theflow.data.repository.NotificationRepository
import com.ana.theflow.databinding.ActivityMainBinding
import com.ana.theflow.ui.admin.AdminReviewFragment
import com.ana.theflow.ui.detail.DetailFragment
import com.ana.theflow.ui.detail.PostDetailFragment
import com.ana.theflow.ui.discover.DiscoverFragment
import com.ana.theflow.ui.home.HomeFragment
import com.ana.theflow.ui.media.MediaViewerFragment
import com.ana.theflow.ui.messaging.ChatFragment
import com.ana.theflow.ui.messaging.ConversationsFragment
import com.ana.theflow.ui.notifications.NotificationsFragment
import com.ana.theflow.ui.onboarding.OnboardingFragment
import com.ana.theflow.ui.profile.ProfileMediaFragment
import com.ana.theflow.ui.profile.ProfileFragment
import com.ana.theflow.ui.profile.SavedItemsFragment
import com.ana.theflow.ui.search.SearchFragment
import com.ana.theflow.ui.settings.ProfessionalVerificationFragment
import com.ana.theflow.ui.settings.SettingsFragment
import com.google.android.libraries.places.api.Places
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedTab = AppTab.HOME
    private val messagingRepository = MessagingRepository()
    private val notificationRepository = NotificationRepository()
    private var messageBadgeListener: ListenerRegistration? = null
    private var notificationBadgeListener: ListenerRegistration? = null

    // Sets up the activity when it is created.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializePlaces()

        setupBottomNavigation()
        setupBackNavigation()
        setupBadges()
        supportFragmentManager.addOnBackStackChangedListener {
            syncNavigationState()
        }

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

    // Connects the bottom navigation buttons to their screens.
    private fun setupBottomNavigation() {
        binding.mainNavHome.setOnClickListener {
            openHome()
        }

        binding.mainNavDiscover.setOnClickListener {
            openDiscover()
        }

        binding.mainBOXMessages.setOnClickListener {
            openConversations()
        }

        binding.mainBOXNotifications.setOnClickListener {
            openNotifications()
        }

        binding.mainNavProfile.setOnClickListener {
            openProfile()
        }

    }

    private fun initializePlaces() {
        val apiKey = BuildConfig.PLACES_API_KEY
        if (apiKey.isBlank() || Places.isInitialized()) return
        Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
    }

    private fun setupBadges() {
        messageBadgeListener?.remove()
        notificationBadgeListener?.remove()
        messageBadgeListener = messagingRepository.listenToUnreadCount(
            onUpdate = { count -> renderBadge(binding.mainBadgeMessages, count) }
        )
        notificationBadgeListener = notificationRepository.listenToUnreadCount(
            onUpdate = { count -> renderBadge(binding.mainBadgeNotifications, count) }
        )
    }

    // Connects Android back presses to app navigation.
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                // Handles the Android back button.
                override fun handleOnBackPressed() {
                    navigateBack()
                }
            }
        )
    }

    // Finishes onboarding and opens the home screen.
    fun completeOnboarding() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(HomeFragment())
        markSelectedTab(AppTab.HOME)
    }

    // Opens the onboarding screen.
    private fun openOnboarding() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(OnboardingFragment())
    }

    // Opens the home tab.
    fun openHome() {
        openRootTab(HomeFragment(), AppTab.HOME)
    }

    // Opens the discover tab.
    fun openDiscover() {
        openRootTab(DiscoverFragment(), AppTab.DISCOVER)
    }

    // Opens the profile tab.
    fun openProfile() {
        openRootTab(ProfileFragment(), AppTab.PROFILE)
    }

    fun openUserProfile(uid: String) {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(ProfileFragment.newInstance(uid), addToBackStack = true)
    }

    fun openConversations() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(ConversationsFragment(), addToBackStack = true)
    }

    fun openNotifications() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(NotificationsFragment(), addToBackStack = true)
    }

    fun openChat(conversationId: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ChatFragment.newInstance(conversationId), addToBackStack = true)
    }

    fun openPost(postId: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(PostDetailFragment.newInstance(postId), addToBackStack = true)
    }

    // Opens the search screen.
    fun openSearch() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(SearchFragment(), addToBackStack = true)
    }

    // Opens the settings screen.
    fun openSettings() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(SettingsFragment(), addToBackStack = true)
    }

    // Opens the preferences editor from Settings.
    fun openEditPreferences() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(OnboardingFragment.newEditInstance(), addToBackStack = true)
    }

    // Opens the professional verification screen from Settings.
    fun openProfessionalVerification() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(ProfessionalVerificationFragment(), addToBackStack = true)
    }

    // Opens the admin review screen from Settings.
    fun openAdminReview() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(AdminReviewFragment(), addToBackStack = true)
    }

    // Opens the full media screen for the current profile.
    fun openProfileMedia() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ProfileMediaFragment(), addToBackStack = true)
    }

    // Opens the saved discovery items screen.
    fun openSavedItems() {
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(SavedItemsFragment(), addToBackStack = true)
    }

    // Opens one photo or video in a larger viewer.
    fun openMediaViewer(url: String, mediaType: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(MediaViewerFragment.newInstance(url, mediaType), addToBackStack = true)
    }

    // Opens the detail screen for a selected item.
    fun openDetail(item: DiscoveryItem) {
        DiscoveryRepository.trackOpenItem(item)
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(DetailFragment.newInstance(item.id), addToBackStack = true)
    }

    // Closes the detail screen and returns to the previous screen.
    fun closeDetail() {
        navigateBack()
    }

    // Switches to a main tab and clears inner navigation.
    private fun openRootTab(fragment: Fragment, tab: AppTab) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        if (
            selectedTab == tab &&
            supportFragmentManager.backStackEntryCount == 0 &&
            currentFragment?.javaClass == fragment.javaClass
        ) {
            return
        }

        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        selectedTab = tab
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        openFragment(fragment)
        markSelectedTab(tab)
    }

    // Shows a fragment in the main container.
    private fun openFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
            .replace(R.id.main_fragment_container, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(fragment::class.java.simpleName)
        }

        transaction
            .commit()
    }

    // Moves back through the app navigation stack.
    private fun navigateBack() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return
        }

        if (selectedTab != AppTab.HOME) {
            openHome()
            return
        }

        finish()
    }

    // Keeps the bottom navigation state in sync with the visible screen.
    private fun syncNavigationState() {
        when (supportFragmentManager.findFragmentById(R.id.main_fragment_container)) {
            is DetailFragment, is PostDetailFragment, is OnboardingFragment, is ProfileMediaFragment, is MediaViewerFragment, is ChatFragment -> {
                binding.mainLAYBottomNav.visibility = View.GONE
            }
            else -> binding.mainLAYBottomNav.visibility = View.VISIBLE
        }

        when (supportFragmentManager.findFragmentById(R.id.main_fragment_container)) {
            is HomeFragment -> {
                selectedTab = AppTab.HOME
                markSelectedTab(AppTab.HOME)
            }
            is DiscoverFragment -> {
                selectedTab = AppTab.DISCOVER
                markSelectedTab(AppTab.DISCOVER)
            }
            is ProfileFragment -> {
                selectedTab = AppTab.PROFILE
                markSelectedTab(AppTab.PROFILE)
            }
        }
    }

    // Highlights the selected bottom navigation tab.
    private fun markSelectedTab(tab: AppTab) {
        binding.mainNavHome.setTextColor(getTabColor(tab == AppTab.HOME))
        binding.mainNavDiscover.setTextColor(getTabColor(tab == AppTab.DISCOVER))
        binding.mainNavProfile.setTextColor(getTabColor(tab == AppTab.PROFILE))
        binding.mainNavHome.alpha = if (tab == AppTab.HOME) 1f else 0.72f
        binding.mainNavDiscover.alpha = if (tab == AppTab.DISCOVER) 1f else 0.72f
        binding.mainNavProfile.alpha = if (tab == AppTab.PROFILE) 1f else 0.72f
        binding.mainNavHome.setTypeface(null, if (tab == AppTab.HOME) Typeface.BOLD else Typeface.NORMAL)
        binding.mainNavDiscover.setTypeface(null, if (tab == AppTab.DISCOVER) Typeface.BOLD else Typeface.NORMAL)
        binding.mainNavProfile.setTypeface(null, if (tab == AppTab.PROFILE) Typeface.BOLD else Typeface.NORMAL)
        selectedTabView(tab).animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(90)
            .withEndAction {
                selectedTabView(tab).animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }

    // Returns the color for a selected or unselected tab.
    private fun getTabColor(isSelected: Boolean): Int {
        val colorRes = if (isSelected) R.color.neon_pink else R.color.text_muted
        return getColor(colorRes)
    }

    private fun selectedTabView(tab: AppTab): View {
        return when (tab) {
            AppTab.HOME -> binding.mainNavHome
            AppTab.DISCOVER -> binding.mainNavDiscover
            AppTab.PROFILE -> binding.mainNavProfile
        }
    }

    private fun renderBadge(badge: android.widget.TextView, count: Long) {
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        badge.text = count.coerceAtMost(99).toString()
    }

    override fun onDestroy() {
        messageBadgeListener?.remove()
        notificationBadgeListener?.remove()
        super.onDestroy()
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
