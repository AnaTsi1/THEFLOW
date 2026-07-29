package com.ana.theflow

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
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
import com.ana.theflow.ui.creation.CollaborationCreationFragment
import com.ana.theflow.ui.creation.EventCreationFragment
import com.ana.theflow.ui.creation.PostCreationFragment
import com.ana.theflow.ui.detail.DetailFragment
import com.ana.theflow.ui.detail.PostDetailFragment
import com.ana.theflow.ui.discover.DiscoverFragment
import com.ana.theflow.ui.events.EventsFragment
import com.ana.theflow.ui.home.HomeFragment
import com.ana.theflow.ui.jobs.JobsFragment
import com.ana.theflow.ui.media.MediaViewerFragment
import com.ana.theflow.ui.messaging.ChatFragment
import com.ana.theflow.ui.messaging.ConversationsFragment
import com.ana.theflow.ui.messaging.MessageUserPickerFragment
import com.ana.theflow.ui.notifications.NotificationsFragment
import com.ana.theflow.ui.onboarding.OnboardingFragment
import com.ana.theflow.ui.profile.FollowListFragment
import com.ana.theflow.ui.profile.MyEventsFragment
import com.ana.theflow.ui.profile.ProfileMediaFragment
import com.ana.theflow.ui.profile.ProfileFragment
import com.ana.theflow.ui.profile.SavedItemsFragment
import com.ana.theflow.ui.search.SearchFragment
import com.ana.theflow.ui.settings.AccountSettingsFragment
import com.ana.theflow.ui.settings.AppearanceSettingsFragment
import com.ana.theflow.ui.settings.EditProfileFragment
import com.ana.theflow.ui.settings.FeedDiscoverySettingsFragment
import com.ana.theflow.ui.settings.HelpAboutSettingsFragment
import com.ana.theflow.ui.settings.NotificationSettingsFragment
import com.ana.theflow.ui.settings.PrivacySafetySettingsFragment
import com.ana.theflow.ui.settings.ProfessionalVerificationFragment
import com.ana.theflow.ui.settings.ProfileSettingsFragment
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
    private val rootTabTags = mapOf(
        AppTab.HOME to "root_home",
        AppTab.DISCOVER to "root_discover",
        AppTab.PROFILE to "root_profile"
    )

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
            toggleConversations()
        }

        binding.mainBOXNotifications.setOnClickListener {
            toggleNotifications()
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
        openHome()
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

    fun openJobs() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(JobsFragment(), addToBackStack = true)
    }

    fun openEvents() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(EventsFragment(), addToBackStack = true)
    }

    // Opens the profile tab.
    fun openProfile() {
        openRootTab(ProfileFragment(), AppTab.PROFILE)
    }

    fun openUserProfile(uid: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ProfileFragment.newInstance(uid), addToBackStack = true)
    }

    fun openFollowers(uid: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(FollowListFragment.followers(uid), addToBackStack = true)
    }

    fun openFollowing(uid: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(FollowListFragment.following(uid), addToBackStack = true)
    }

    fun openConversations() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ConversationsFragment(), addToBackStack = true)
    }

    fun openNotifications() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(NotificationsFragment(), addToBackStack = true)
    }

    fun openNewMessage() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(MessageUserPickerFragment(), addToBackStack = true)
    }

    private fun toggleConversations() {
        when (supportFragmentManager.findFragmentById(R.id.main_fragment_container)) {
            is ConversationsFragment -> navigateBack()
            is NotificationsFragment -> {
                supportFragmentManager.popBackStack()
                findViewById<View>(R.id.main_fragment_container).post { openConversations() }
            }
            else -> openConversations()
        }
    }

    private fun toggleNotifications() {
        when (supportFragmentManager.findFragmentById(R.id.main_fragment_container)) {
            is NotificationsFragment -> navigateBack()
            is ConversationsFragment -> {
                supportFragmentManager.popBackStack()
                findViewById<View>(R.id.main_fragment_container).post { openNotifications() }
            }
            else -> openNotifications()
        }
    }

    fun openChat(conversationId: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ChatFragment.newInstance(conversationId), addToBackStack = true)
    }

    fun openPost(postId: String) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(PostDetailFragment.newInstance(postId), addToBackStack = true)
    }

    fun openCreationMenu() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 8.dp())
            addView(TextView(this@MainActivity).apply {
                text = "What would you like to create?"
                setTextColor(getColor(R.color.flow_ink))
                textSize = 19f
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        val dialog = AlertDialog.Builder(this).setView(content).create()
        content.addView(creationChoice("Post", "Share a thought, media, music, feeling, or dance moment.") {
            dialog.dismiss()
            openPostCreation()
        })
        content.addView(creationChoice("Event", "Create a class, social, workshop, audition, or dance gathering.") {
            dialog.dismiss()
            openEventCreation()
        })
        content.addView(creationChoice("Collaboration", "Find dancers, teachers, creators, studios, or project partners.") {
            dialog.dismiss()
            openCollaborationCreation()
        })
        dialog.show()
    }

    fun openPostCreation() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(PostCreationFragment(), addToBackStack = true)
    }

    fun openEventCreation() {
        if (supportFragmentManager.findFragmentByTag(EventCreationFragment.TAG) != null) return
        EventCreationFragment().show(supportFragmentManager, EventCreationFragment.TAG)
    }

    fun openCollaborationCreation() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(CollaborationCreationFragment(), addToBackStack = true)
    }

    private fun creationChoice(title: String, subtitle: String, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            isClickable = true
            isFocusable = true
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(getColor(R.color.flow_ink))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = subtitle
                setTextColor(getColor(R.color.flow_text_secondary))
                textSize = 13f
                setPadding(0, 3.dp(), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    // Opens the search screen.
    fun openSearch(mapMode: Boolean = false) {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(SearchFragment.newInstance(mapMode), addToBackStack = true)
    }

    // Opens the settings screen.
    fun openSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(SettingsFragment(), addToBackStack = true)
    }

    fun openAccountSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(AccountSettingsFragment(), addToBackStack = true)
    }

    fun openProfileSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(ProfileSettingsFragment(), addToBackStack = true)
    }

    fun openFeedDiscoverySettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(FeedDiscoverySettingsFragment(), addToBackStack = true)
    }

    fun openNotificationSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(NotificationSettingsFragment(), addToBackStack = true)
    }

    fun openPrivacySafetySettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(PrivacySafetySettingsFragment(), addToBackStack = true)
    }

    fun openAppearanceSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(AppearanceSettingsFragment(), addToBackStack = true)
    }

    fun openHelpAboutSettings() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(HelpAboutSettingsFragment(), addToBackStack = true)
    }

    fun openEditProfile() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(EditProfileFragment(), addToBackStack = true)
    }

    // Opens the preferences editor from Settings.
    fun openEditPreferences() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(OnboardingFragment.newEditInstance(), addToBackStack = true)
    }

    // Opens the professional verification screen from Settings.
    fun openProfessionalVerification() {
        binding.mainLAYBottomNav.visibility = View.GONE
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
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(SavedItemsFragment(), addToBackStack = true)
    }

    fun openMyEvents() {
        binding.mainLAYBottomNav.visibility = View.GONE
        openFragment(MyEventsFragment(), addToBackStack = true)
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

        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        selectedTab = tab
        binding.mainLAYBottomNav.visibility = View.VISIBLE
        showRootFragment(fragment, tab)
        markSelectedTab(tab)
    }

    private fun showRootFragment(newFragment: Fragment, tab: AppTab) {
        val tag = rootTabTags.getValue(tab)
        val transaction = supportFragmentManager.beginTransaction()
        rootTabTags.values.forEach { rootTag ->
            supportFragmentManager.findFragmentByTag(rootTag)?.let { transaction.hide(it) }
        }
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.main_fragment_container, newFragment, tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()
    }

    // Shows a fragment in the main container.
    private fun openFragment(fragment: Fragment, addToBackStack: Boolean = false) {
        val transaction = supportFragmentManager.beginTransaction()
        val current = supportFragmentManager.findFragmentById(R.id.main_fragment_container)
        if (
            addToBackStack &&
            current != null &&
            current::class.java == fragment::class.java &&
            current !is ProfileFragment
        ) {
            return
        }
        if (addToBackStack && current != null) {
            transaction.hide(current)
            transaction.add(R.id.main_fragment_container, fragment)
        } else {
            transaction.replace(R.id.main_fragment_container, fragment)
        }

        if (addToBackStack) {
            transaction.addToBackStack(fragment::class.java.simpleName)
        }

        transaction
            .commit()
    }

    // Moves back through the app navigation stack.
    private fun navigateBack() {
        (supportFragmentManager.findFragmentById(R.id.main_fragment_container) as? HomeFragment)
            ?.let { if (it.closeDrawerIfOpen()) return }

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
            is DetailFragment,
            is PostDetailFragment,
            is SettingsFragment,
            is AccountSettingsFragment,
            is ProfileSettingsFragment,
            is FeedDiscoverySettingsFragment,
            is NotificationSettingsFragment,
            is PrivacySafetySettingsFragment,
            is AppearanceSettingsFragment,
            is HelpAboutSettingsFragment,
            is EditProfileFragment,
            is ProfessionalVerificationFragment,
            is FollowListFragment,
            is SavedItemsFragment,
            is MyEventsFragment,
            is ConversationsFragment,
            is NotificationsFragment,
            is PostCreationFragment,
            is CollaborationCreationFragment,
            is EventsFragment,
            is JobsFragment,
            is SearchFragment,
            is MessageUserPickerFragment,
            is OnboardingFragment,
            is ProfileMediaFragment,
            is MediaViewerFragment,
            is ChatFragment -> {
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
        renderNavItem(
            label = binding.mainLabelHome,
            icon = binding.mainIconHome,
            isSelected = tab == AppTab.HOME
        )
        renderNavItem(
            label = binding.mainLabelDiscover,
            icon = binding.mainIconDiscover,
            isSelected = tab == AppTab.DISCOVER
        )
        renderNavItem(
            label = binding.mainLabelProfile,
            icon = binding.mainIconProfile,
            isSelected = tab == AppTab.PROFILE
        )
        binding.mainNavHome.alpha = if (tab == AppTab.HOME) 1f else 0.72f
        binding.mainNavDiscover.alpha = if (tab == AppTab.DISCOVER) 1f else 0.72f
        binding.mainNavProfile.alpha = if (tab == AppTab.PROFILE) 1f else 0.72f
        selectedTabView(tab).animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(90)
            .withEndAction {
                selectedTabView(tab).animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()
    }

    private fun renderNavItem(label: android.widget.TextView, icon: android.widget.ImageView, isSelected: Boolean) {
        val color = getColor(if (isSelected) R.color.neon_purple else R.color.text_secondary)
        label.setTextColor(color)
        icon.setColorFilter(color)
        label.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
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

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
