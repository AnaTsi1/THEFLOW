// Admin-only diagnostic screen: search any user, see their real learned recommendation profile
// in readable form, and a live preview of what Home "For You" and Discover "Recommended" would
// actually rank for them right now - built on the real, unmodified ranking strategies (not a
// reimplementation), so what's shown here is a genuine preview, not an approximation.
package com.ana.theflow.ui.admin

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.discovery.DiscoveryItem
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.recommendation.RecommendationNormalizer
import com.ana.theflow.data.recommendation.RecommendationProfile
import com.ana.theflow.data.recommendation.RecommendationScoreComponent
import com.ana.theflow.data.repository.AdminRecommendationRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.settings.SettingsUi
import com.ana.theflow.ui.settings.dp
import com.ana.theflow.utilities.AccountPermissions
import com.bumptech.glide.Glide

class AdminRecommendationInsightsFragment : Fragment() {

    private val repository = AdminRecommendationRepository()
    private val userRepository = UserRepository()

    private lateinit var content: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var resultsList: LinearLayout
    private lateinit var detailPanel: LinearLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var profileTabButton: TextView
    private lateinit var homeTabButton: TextView
    private lateinit var discoverTabButton: TextView
    private lateinit var profileTabContent: LinearLayout
    private lateinit var homeTabContent: LinearLayout
    private lateinit var discoverTabContent: LinearLayout

    private var selectedTab = InsightsTab.PROFILE
    private var currentSnapshot: AdminRecommendationRepository.Snapshot? = null
    private var homeFilters = PreviewFilters()
    private var discoverFilters = PreviewFilters()

    // Which ranked rows currently have their score breakdown expanded (tap to toggle) - keyed by
    // post/item id, not list position, so expand state survives a filter change re-rendering the
    // same tab and doesn't accidentally follow a different item into the same row position.
    private val expandedRowKeys = mutableSetOf<String>()

    private enum class InsightsTab { PROFILE, HOME, DISCOVER }
    private data class PreviewFilters(val contentType: String = ALL, val city: String = ALL, val reason: String = ALL) {
        companion object { const val ALL = "All" }
    }

    // Sets up the screen scaffold and renders the initial (empty, pre-search) state.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = SettingsUi.screen(this, "Recommendation Insights")
        val scroll = SettingsUi.contentScroll(requireContext())
        content = SettingsUi.contentColumn(requireContext())
        scroll.addView(content)
        root.addView(scroll)
        render()
        return root
    }

    // Builds the search box, search button, results list, and (initially hidden) detail panel
    // with its three tabs.
    private fun render() {
        content.removeAllViews()
        content.addView(SettingsUi.message(requireContext(), "Search a user to see their real learned recommendation profile and a live preview of what Home and Discover would show them right now."))

        searchField = EditText(requireContext()).apply {
            hint = "Search by name"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 52.dp()
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            setOnEditorActionListener { _, _, _ -> search(); true }
        }
        content.addView(searchField)

        content.addView(Button(requireContext()).apply {
            text = "Search"
            isAllCaps = false
            setTextColor(context.getColor(R.color.flow_surface))
            setBackgroundResource(R.drawable.bg_flow_button_primary)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                bottomMargin = 12.dp()
            }
            setOnClickListener { search() }
        })

        resultsList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        content.addView(resultsList)

        detailPanel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }
        content.addView(detailPanel)

        // Tabs instead of one long stacked scroll - an admin jumps straight to what they want to
        // check (profile summary vs. Home preview vs. Discover preview) instead of scrolling
        // through everything every time.
        tabBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 12.dp()
            }
        }
        profileTabButton = tabButton("Profile") { selectTab(InsightsTab.PROFILE) }
        homeTabButton = tabButton("Home Preview") { selectTab(InsightsTab.HOME) }
        discoverTabButton = tabButton("Discover Preview") { selectTab(InsightsTab.DISCOVER) }
        listOf(profileTabButton, homeTabButton, discoverTabButton).forEach { tabBar.addView(it) }
        detailPanel.addView(tabBar)

        profileTabContent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        homeTabContent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        discoverTabContent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        detailPanel.addView(profileTabContent)
        detailPanel.addView(homeTabContent)
        detailPanel.addView(discoverTabContent)
    }

    // Builds one clickable tab label.
    private fun tabButton(label: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(8.dp(), 10.dp(), 8.dp(), 10.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6.dp()
            }
            setOnClickListener { onClick() }
        }
    }

    // Switches the visible tab and restyles the tab buttons to show which one is active.
    private fun selectTab(tab: InsightsTab) {
        selectedTab = tab
        fun style(button: TextView, active: Boolean) {
            button.setBackgroundResource(if (active) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            button.setTextColor(requireContext().getColor(if (active) R.color.flow_surface else R.color.flow_brand))
        }
        style(profileTabButton, tab == InsightsTab.PROFILE)
        style(homeTabButton, tab == InsightsTab.HOME)
        style(discoverTabButton, tab == InsightsTab.DISCOVER)
        profileTabContent.visibility = if (tab == InsightsTab.PROFILE) View.VISIBLE else View.GONE
        homeTabContent.visibility = if (tab == InsightsTab.HOME) View.VISIBLE else View.GONE
        discoverTabContent.visibility = if (tab == InsightsTab.DISCOVER) View.VISIBLE else View.GONE
    }

    // Runs the name search and lists matching users to pick from.
    private fun search() {
        val query = searchField.text.toString().trim()
        resultsList.removeAllViews()
        if (query.isBlank()) return
        userRepository.searchUsers(
            query = query,
            dancersOnly = false,
            onSuccess = { users ->
                if (!isAdded) return@searchUsers
                resultsList.removeAllViews()
                if (users.isEmpty()) {
                    resultsList.addView(SettingsUi.message(requireContext(), "No users found."))
                    return@searchUsers
                }
                users.take(15).forEach { user -> resultsList.addView(userRow(user)) }
            },
            onFailure = { error ->
                if (!isAdded) return@searchUsers
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Builds one selectable row in the search results list.
    private fun userRow(user: User): View {
        val context = requireContext()
        val name = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            addView(avatar(user.profileImageUrl, 40.dp()).apply { (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 12.dp() })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = name
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = user.email.ifBlank { "No email on file" }
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                })
            })
            setOnClickListener { selectUser(user) }
        }
    }

    // Picks a user from the search results, resets any leftover filters/state from a previous
    // selection, and loads their full recommendation snapshot.
    private fun selectUser(user: User) {
        currentSnapshot = null
        homeFilters = PreviewFilters()
        discoverFilters = PreviewFilters()
        profileTabContent.removeAllViews()
        homeTabContent.removeAllViews()
        discoverTabContent.removeAllViews()
        detailPanel.visibility = View.VISIBLE
        selectTab(InsightsTab.PROFILE)
        profileTabContent.addView(ProgressBar(requireContext()).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.flow_brand))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 20.dp()
            }
        })
        repository.loadSnapshot(
            targetUid = user.uid,
            onSuccess = { snapshot ->
                if (!isAdded) return@loadSnapshot
                currentSnapshot = snapshot
                renderSnapshot(snapshot)
            },
            onFailure = { error ->
                if (!isAdded) return@loadSnapshot
                profileTabContent.removeAllViews()
                profileTabContent.addView(SettingsUi.message(requireContext(), "Could not load this user's recommendation data: $error"))
            }
        )
    }

    // Renders every part of the detail panel once a user's snapshot has loaded.
    private fun renderSnapshot(snapshot: AdminRecommendationRepository.Snapshot) {
        profileTabContent.removeAllViews()
        profileTabContent.addView(SettingsUi.message(
            requireContext(),
            "This shows what THE FLOW's recommendation engine has learned about this user - both " +
                "what they explicitly told us and what their real activity shows - and how it's currently " +
                "shaping their Home and Discover feeds."
        ))
        profileTabContent.addView(signalMixCard(snapshot))
        profileTabContent.addView(profileCard(snapshot.user, snapshot.profile))
        renderHomeTab(snapshot)
        renderDiscoverTab(snapshot)
    }

    // ---- "What's actually driving this?" - the headline visualization ----
    // Ties the Profile tab's raw learned-score data directly to what the live previews are
    // actually showing: for every item in the CURRENT top-ranked Home + Discover lists, which
    // single signal won (reasonCategory - the same grouping the preview filters use), as a share
    // of the combined preview set. This is deliberately the first thing shown - it answers "what's
    // driving this person's recommendations, at a glance" without an admin needing to cross-
    // reference the profile bars against the preview list's "why" labels themselves.
    private fun signalMixCard(snapshot: AdminRecommendationRepository.Snapshot): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16.dp()
            }
        }
        card.addView(TextView(context).apply {
            text = "What's driving this person's recommendations"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(context).apply {
            text = "Across their current top Home + Discover results, share of items where each signal is the single strongest reason."
            setTextColor(context.getColor(R.color.flow_text_muted))
            textSize = 11f
            setPadding(0, 2.dp(), 0, 12.dp())
        })

        val categories = snapshot.homeForYou.map { reasonCategory(it.explanation.components) } +
            snapshot.discoverRecommended.map { reasonCategory(it.explanation.components) }
        if (categories.isEmpty()) {
            card.addView(TextView(context).apply {
                text = "No ranked items available yet to summarize."
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 12f
            })
            return card
        }
        val counts = categories.groupingBy { it }.eachCount()
        val total = categories.size
        val ordered = counts.entries.sortedByDescending { it.value }

        card.addView(stackedProportionBar(ordered.map { it.key to it.value.toDouble() }, total.toDouble()))
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10.dp(), 0, 0)
            ordered.forEachIndexed { index, (label, count) ->
                addView(legendRow(label, count, total, signalColorFor(index)))
            }
        })
        return card
    }

    // A single horizontal bar split into colored segments proportional to each category's share -
    // reads faster than a pie chart for this many categories and stays in the same flat, rectangular
    // visual language as every other chart on this screen.
    private fun stackedProportionBar(entries: List<Pair<String, Double>>, total: Double): View {
        val context = requireContext()
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipToOutline = true
            setBackgroundResource(R.drawable.bg_flow_input)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 22.dp())
        }
        entries.forEachIndexed { index, (_, value) ->
            val weight = (value / total).toFloat().coerceAtLeast(0.01f)
            bar.addView(View(context).apply {
                setBackgroundColor(signalColorFor(index))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
            })
        }
        return bar
    }

    // One line of the legend under the signal-mix bar: a color swatch, the category name, and its
    // share as both a percentage and a raw count.
    private fun legendRow(label: String, count: Int, total: Int, color: Int): View {
        val context = requireContext()
        val percent = if (total > 0) (count * 100.0 / total) else 0.0
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3.dp(), 0, 3.dp())
            addView(View(context).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(10.dp(), 10.dp()).apply { rightMargin = 8.dp() }
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "%.0f%% (%d)".format(percent, count)
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    // Fixed, stable color-per-signal-category so the same signal always reads the same color
    // across a session (and across different users) rather than shuffling with whatever happens
    // to be the top category for a given person.
    private fun signalColorFor(index: Int): Int {
        val palette = listOf(R.color.flow_brand, R.color.discover_accent_blue, R.color.discover_accent_coral, R.color.discover_accent_pink, R.color.flow_text_muted)
        return requireContext().getColor(palette[index % palette.size])
    }

    // ---- Home "For You" tab: filter row + filtered ranked list ----
    private fun renderHomeTab(snapshot: AdminRecommendationRepository.Snapshot) {
        homeTabContent.removeAllViews()
        homeTabContent.addView(sectionHeader(
            "Home “For You” - live preview",
            "This shows the real ranked order this user's Home feed would show right now. " +
                "Higher scores rank first - this feed is driven mainly by their dance style preferences.",
            R.color.flow_brand
        ))
        val items = snapshot.homeForYou
        if (items.isEmpty()) {
            homeTabContent.addView(SettingsUi.message(requireContext(), "No public posts available to rank."))
            return
        }
        val contentTypes = items.map { homeContentType(it.post) }.distinct().sorted()
        val cities = items.map { homeCity(it.post) }.distinct().sorted()
        val reasons = items.map { reasonCategory(it.explanation.components) }.distinct().sorted()
        homeTabContent.addView(filterRow(contentTypes, cities, reasons, homeFilters) { updated ->
            homeFilters = updated
            renderHomeTab(snapshot)
        })

        val filtered = items.filter { ranked ->
            (homeFilters.contentType == PreviewFilters.ALL || homeContentType(ranked.post) == homeFilters.contentType) &&
                (homeFilters.city == PreviewFilters.ALL || homeCity(ranked.post) == homeFilters.city) &&
                (homeFilters.reason == PreviewFilters.ALL || reasonCategory(ranked.explanation.components) == homeFilters.reason)
        }
        if (filtered.isEmpty()) {
            homeTabContent.addView(SettingsUi.message(requireContext(), "No items match the current filters."))
        } else {
            filtered.forEachIndexed { index, ranked ->
                homeTabContent.addView(rankedPostRow(index + 1, ranked) { renderHomeTab(snapshot) })
            }
        }
    }

    // ---- Discover "Recommended" tab: filter row + filtered ranked list ----
    private fun renderDiscoverTab(snapshot: AdminRecommendationRepository.Snapshot) {
        discoverTabContent.removeAllViews()
        discoverTabContent.addView(sectionHeader(
            "Discover “Recommended” - live preview",
            "This shows what this user's Discover page would recommend right now. " +
                "Higher scores rank first - this feed prioritizes what's nearby and matches their skill level.",
            R.color.discover_accent_blue
        ))
        val items = snapshot.discoverRecommended
        if (items.isEmpty()) {
            discoverTabContent.addView(SettingsUi.message(requireContext(), "No studios/events available to rank."))
            return
        }
        val contentTypes = items.map { discoverContentType(it.item) }.distinct().sorted()
        val cities = items.map { discoverCity(it.item) }.distinct().sorted()
        val reasons = items.map { reasonCategory(it.explanation.components) }.distinct().sorted()
        discoverTabContent.addView(filterRow(contentTypes, cities, reasons, discoverFilters) { updated ->
            discoverFilters = updated
            renderDiscoverTab(snapshot)
        })

        val filtered = items.filter { ranked ->
            (discoverFilters.contentType == PreviewFilters.ALL || discoverContentType(ranked.item) == discoverFilters.contentType) &&
                (discoverFilters.city == PreviewFilters.ALL || discoverCity(ranked.item) == discoverFilters.city) &&
                (discoverFilters.reason == PreviewFilters.ALL || reasonCategory(ranked.explanation.components) == discoverFilters.reason)
        }
        if (filtered.isEmpty()) {
            discoverTabContent.addView(SettingsUi.message(requireContext(), "No items match the current filters."))
        } else {
            filtered.forEachIndexed { index, ranked ->
                discoverTabContent.addView(rankedItemRow(index + 1, ranked) { renderDiscoverTab(snapshot) })
            }
        }
    }

    private fun homeContentType(post: Post): String = post.postType.ifBlank { if (post.activityType.isNotBlank()) "event" else "post" }
    private fun homeCity(post: Post): String = post.activityLocation.ifBlank { post.collaborationLocation }.ifBlank { "Unspecified" }
    private fun discoverContentType(item: DiscoveryItem): String = item.displayType.ifBlank { item.type }.ifBlank { "item" }
    private fun discoverCity(item: DiscoveryItem): String = item.location.ifBlank { "Unspecified" }

    // Groups the raw score-component labels into a small set of admin-facing categories, so
    // filtering by "why" isolates whether a specific signal (style, location, level...) is
    // actually driving results, without needing to know every internal component name.
    private fun reasonCategory(components: List<RecommendationScoreComponent>): String {
        val top = components.filter { it.score > 0 }.maxByOrNull { it.score } ?: return "No strong signal"
        return when (top.label) {
            "Explicit style preference", "Learned style" -> "Style match"
            "Proximity", "Learned location", "Resolved location", "Preferred area" -> "Location match"
            "Level match" -> "Level match"
            "Freshness" -> "Freshness"
            "Popularity" -> "Popularity"
            "Studio behavior", "Teacher behavior", "Creator behavior" -> "Studio/Teacher/Creator affinity"
            else -> "Other"
        }
    }

    // Three plain Spinners (content type / city / why-reason) - no Material dependency in this
    // project, and a dialog would hide the current selection at a glance. Options are derived
    // from whatever's actually in the current result set, not hardcoded, so they stay relevant.
    private fun filterRow(
        contentTypes: List<String>,
        cities: List<String>,
        reasons: List<String>,
        current: PreviewFilters,
        onChange: (PreviewFilters) -> Unit
    ): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10.dp())
        }
        row.addView(labeledSpinner(context, "Type", contentTypes, current.contentType) { onChange(current.copy(contentType = it)) })
        row.addView(labeledSpinner(context, "City", cities, current.city) { onChange(current.copy(city = it)) })
        row.addView(labeledSpinner(context, "Why", reasons, current.reason) { onChange(current.copy(reason = it)) })
        return row
    }

    // One filter dropdown with a small caption above it, defaulting to "All" plus whatever
    // options were passed in.
    private fun labeledSpinner(context: android.content.Context, label: String, options: List<String>, selected: String, onSelect: (String) -> Unit): View {
        val values = listOf(PreviewFilters.ALL) + options
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 6.dp() }
            addView(TextView(context).apply {
                text = label
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 10f
            })
            addView(Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(values.indexOf(selected).coerceAtLeast(0), false)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val value = values.getOrNull(position) ?: PreviewFilters.ALL
                        if (value != selected) onSelect(value)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
            })
        }
    }

    // ---- Profile card: readable learned-signal breakdown, not raw JSON ----
    private fun profileCard(user: User, profile: RecommendationProfile): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16.dp()
            }
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(avatar(user.profileImageUrl, 48.dp()).apply { (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 12.dp() })
        header.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
            })
            val badges = AccountPermissions.badges(user)
            addView(TextView(context).apply {
                text = badges.ifEmpty { listOf("Personal account") }.joinToString(" · ")
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 2.dp(), 0, 0)
            })
        })
        card.addView(header)

        card.addView(divider())
        card.addView(statRow(
            "Level" to (user.danceLevel.ifBlank { "Not set" }),
            "City" to (profile.preferredRecommendationArea.ifBlank { profile.profileLocation.ifBlank { "Not set" } })
        ))
        card.addView(statRow(
            "Studios engaged" to profile.studioScores.size.toString(),
            "Teachers engaged" to profile.teacherScores.size.toString()
        ))
        card.addView(statRow(
            "Saved items" to profile.savedItemIds.size.toString(),
            "Items already seen" to profile.seenItemIds.size.toString()
        ))

        // Explicit (declared at onboarding/profile) and learned (accumulated from real behavior -
        // likes/comments/saves/views/follows/shares) are two genuinely different signals the
        // ranking engine reads from two different fields (RecommendationContext.danceStyles vs.
        // RecommendationProfile.styleScores). Both get shown here, clearly labeled as what they
        // actually are, so a ranked item's "Explicit style preference" reason never looks
        // contradictory against what this card is showing for that same signal.
        card.addView(explicitPreferencesRow(profile))
        card.addView(levelChartSection(user, profile))
        card.addView(barChartSection("Learned style affinity - share of style weight", profile.styleScores, R.color.flow_brand))
        card.addView(barChartSection("Learned location affinity - share of location weight", profile.locationScores, R.color.discover_accent_blue))
        card.addView(barChartSection("Favorite studios - share of studio weight", profile.studioScores, R.color.discover_accent_coral))
        card.addView(barChartSection("Favorite teachers - share of teacher weight", profile.teacherScores, R.color.discover_accent_pink))

        return card
    }

    // Level isn't a distribution the way styles/locations are - there are only 3 real values, and
    // one is always explicitly declared. Shown as a 3-way segmented indicator (the declared level
    // highlighted) with each segment's learned score as a thin sub-bar, rather than forcing it into
    // the same 5-row bar-chart shape as genuinely multi-valued signals.
    private fun levelChartSection(user: User, profile: RecommendationProfile): View {
        val context = requireContext()
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14.dp(), 0, 0)
        }
        section.addView(TextView(context).apply {
            text = "Level"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp())
        })
        val declared = RecommendationNormalizer.id(user.danceLevel.ifBlank { profile.danceLevel })
        val levels = listOf("beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced")
        val maxLearned = levels.maxOfOrNull { profile.levelScores[it.first] ?: 0.0 }?.takeIf { it > 0 } ?: 1.0

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        levels.forEach { (id, label) ->
            val isDeclared = id == declared
            val learned = profile.levelScores[id] ?: 0.0
            row.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = if (id != "advanced") 8.dp() else 0 }
                setBackgroundResource(if (isDeclared) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_input)
                setPadding(6.dp(), 8.dp(), 6.dp(), 8.dp())
                addView(TextView(context).apply {
                    text = label
                    setTextColor(context.getColor(if (isDeclared) R.color.flow_surface else R.color.flow_text_secondary))
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = if (learned > 0) "learned %.1f".format(learned) else "declared"
                    setTextColor(context.getColor(if (isDeclared) R.color.flow_surface else R.color.flow_text_muted))
                    textSize = 10f
                    alpha = 0.85f
                })
                val track = FrameLayout(context).apply {
                    setBackgroundColor(Color.argb(40, 0, 0, 0))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 4.dp()).apply { topMargin = 4.dp() }
                }
                track.addView(View(context).apply {
                    setBackgroundColor(context.getColor(if (isDeclared) R.color.flow_surface else R.color.flow_brand))
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleX = (learned / maxLearned).toFloat().coerceIn(0.03f, 1f)
                    pivotX = 0f
                })
                addView(track)
            })
        }
        section.addView(row)
        return section
    }

    // Shows the styles/level/city the user actually typed in during onboarding or their profile,
    // as opposed to what the engine has learned from their behavior.
    private fun explicitPreferencesRow(profile: RecommendationProfile): View {
        val context = requireContext()
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14.dp(), 0, 0)
        }
        section.addView(TextView(context).apply {
            text = "Explicitly declared (onboarding / profile)"
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 6.dp())
        })
        section.addView(TextView(context).apply {
            text = "Styles: " + profile.danceStyles.ifEmpty { listOf("none declared") }.joinToString(", ")
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 2.dp(), 0, 0)
        })
        section.addView(TextView(context).apply {
            text = "Level: " + profile.danceLevel.ifBlank { "none declared" }
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 2.dp(), 0, 0)
        })
        section.addView(TextView(context).apply {
            text = "City: " + profile.preferredRecommendationArea.ifBlank { profile.profileLocation.ifBlank { "none declared" } }
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 2.dp(), 0, 0)
        })
        return section
    }

    private fun statRow(vararg pairs: Pair<String, String>): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(), 0, 0)
            pairs.forEach { (label, value) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = label
                        setTextColor(context.getColor(R.color.flow_text_muted))
                        textSize = 11f
                    })
                    addView(TextView(context).apply {
                        text = value
                        setTextColor(context.getColor(R.color.flow_ink))
                        textSize = 14f
                        setTypeface(typeface, Typeface.BOLD)
                    })
                })
            }
        }
    }

    // Simple width-proportional bar chart - no chart library needed, consistent with this
    // codebase's programmatic-view style throughout.
    private fun barChartSection(title: String, scores: Map<String, Double>, colorRes: Int): View {
        val context = requireContext()
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14.dp(), 0, 0)
        }
        section.addView(TextView(context).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 8.dp())
        })
        // Some historical data (written before this pipeline consistently normalized keys)
        // has case-variant duplicates for the same underlying key (e.g. both "Hip_Hop" and
        // "hip_hop"). The ranking engine only ever looks up the normalized form, so merge by
        // normalized key here too rather than showing a confusing double entry for one real signal.
        val merged = mutableMapOf<String, Double>()
        val displayLabel = mutableMapOf<String, String>()
        scores.forEach { (key, value) ->
            val normalized = RecommendationNormalizer.id(key)
            merged[normalized] = (merged[normalized] ?: 0.0) + value
            if (!displayLabel.containsKey(normalized) || key == key.replaceFirstChar { it.uppercase() }) {
                displayLabel[normalized] = key.replace("_", " ")
            }
        }
        // "Out of their total scoring weight" - the percentage is each key's share of the sum of
        // ALL positive entries in this category (not just the top 5 shown), so it's an honest
        // reading of dominance rather than a number relative to an arbitrary visible subset.
        val totalWeight = merged.values.filter { it > 0 }.sum()
        val top = merged.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .take(5)
            .map { (displayLabel[it.key] ?: it.key.replace("_", " ")) to it.value }
        if (top.isEmpty()) {
            section.addView(TextView(context).apply {
                text = "No learned signal yet - this user hasn't interacted enough for the engine to have a strong opinion here."
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 12f
            })
            return section
        }
        val maxScore = top.first().second
        top.forEach { (label, score) ->
            section.addView(barRow(label, score, maxScore, totalWeight, colorRes))
        }
        return section
    }

    // One row of a bar chart: the label, a proportional filled bar, its share of the total weight,
    // and the raw score in parentheses.
    private fun barRow(label: String, score: Double, maxScore: Double, totalWeight: Double, colorRes: Int): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 3.dp(), 0, 3.dp())
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(78.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            maxLines = 1
        })
        val track = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.bg_flow_input)
            layoutParams = LinearLayout.LayoutParams(0, 16.dp(), 1f).apply {
                marginStart = 6.dp()
                marginEnd = 8.dp()
            }
        }
        val fraction = if (maxScore > 0) (score / maxScore).coerceIn(0.02, 1.0) else 0.02
        track.addView(View(context).apply {
            setBackgroundColor(context.getColor(colorRes))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                width = (BAR_TRACK_WIDTH_DP.dp() * fraction).toInt().coerceAtLeast(4.dp())
            }
        })
        row.addView(track)
        val percent = if (totalWeight > 0) score * 100.0 / totalWeight else 0.0
        row.addView(TextView(context).apply {
            text = "%.0f%%".format(percent)
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(32.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        row.addView(TextView(context).apply {
            text = "(%.1f)".format(score)
            setTextColor(context.getColor(R.color.flow_text_muted))
            textSize = 10f
        })
        return row
    }

    // ---- Ranked preview rows (Home posts / Discover items) ----
    private fun sectionHeader(title: String, subtitle: String, accentColorRes: Int): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 18.dp(), 0, 8.dp())
            addView(View(context).apply {
                setBackgroundColor(context.getColor(accentColorRes))
                layoutParams = LinearLayout.LayoutParams(28.dp(), 3.dp()).apply { bottomMargin = 6.dp() }
            })
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 11f
                setPadding(0, 1.dp(), 0, 0)
            })
        }
    }

    // Adapts a ranked Home post into the shared rankedRow layout.
    private fun rankedPostRow(rank: Int, ranked: AdminRecommendationRepository.RankedPost, onToggled: () -> Unit): View {
        val post = ranked.post
        val author = post.authorName.ifBlank { "Unknown" }
        val summary = post.text.ifBlank { post.activityType.ifBlank { post.collaborationStyle.ifBlank { "(no text)" } } }
        return rankedRow(post.postId, rank, summary.take(70), "by $author", ranked.explanation.finalScore, ranked.explanation.components, R.color.flow_brand, onToggled)
    }

    // Adapts a ranked Discover item into the shared rankedRow layout.
    private fun rankedItemRow(rank: Int, ranked: AdminRecommendationRepository.RankedItem, onToggled: () -> Unit): View {
        val item = ranked.item
        val subtitle = listOfNotNull(item.displayType.ifBlank { item.type }, item.location.ifBlank { null }).joinToString(" · ")
        return rankedRow(item.id, rank, item.title.ifBlank { item.studio }, subtitle, ranked.explanation.finalScore, ranked.explanation.components, R.color.discover_accent_blue, onToggled)
    }

    // Tapping the row expands/collapses a plain-language breakdown of the top signals behind the
    // score, so an admin can see WHY a number is what it is without reading source code. The rank
    // and score badges are each labeled (not bare numbers) so the row is unambiguous to someone
    // seeing this screen for the first time.
    private fun rankedRow(
        key: String,
        rank: Int,
        title: String,
        subtitle: String,
        score: Double,
        components: List<RecommendationScoreComponent>,
        accentColorRes: Int,
        onToggled: () -> Unit
    ): View {
        val context = requireContext()
        val isExpanded = expandedRowKeys.contains(key)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            setOnClickListener {
                if (isExpanded) expandedRowKeys.remove(key) else expandedRowKeys.add(key)
                onToggled()
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(labeledBadge(caption = "RANK", value = "#$rank", captionColorRes = R.color.flow_text_muted, badgeBgRes = R.drawable.bg_badge, badgeTextColorRes = null).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 10.dp()
        })
        topRow.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 11f
            })
            addView(TextView(context).apply {
                text = topSignalLabel(components)
                setTextColor(context.getColor(accentColorRes))
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 3.dp(), 0, 0)
            })
        })
        topRow.addView(labeledBadge(caption = "SCORE", value = "%.1f".format(score), captionColorRes = R.color.flow_text_muted, badgeBgRes = R.drawable.bg_flow_button_primary, badgeTextColorRes = R.color.flow_surface))
        row.addView(topRow)

        row.addView(TextView(context).apply {
            text = if (isExpanded) "▴ Hide score breakdown" else "▾ Tap to see why this score"
            setTextColor(context.getColor(accentColorRes))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 8.dp(), 0, 0)
        })
        if (isExpanded) {
            row.addView(scoreBreakdown(components, score, accentColorRes))
        }
        return row
    }

    // A small vertical "caption above pill" stack, reused for both the rank and score badges so
    // neither ever reads as a bare, unexplained number.
    private fun labeledBadge(caption: String, value: String, captionColorRes: Int, badgeBgRes: Int, badgeTextColorRes: Int?): LinearLayout {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = caption
                setTextColor(context.getColor(captionColorRes))
                textSize = 8f
                letterSpacing = 0.06f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 2.dp())
            })
            addView(TextView(context).apply {
                text = value
                setTextColor(if (badgeTextColorRes != null) context.getColor(badgeTextColorRes) else Color.WHITE)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setBackgroundResource(badgeBgRes)
                minWidth = 30.dp()
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
            })
        }
    }

    // Top 2-3 contributing signals in plain language, e.g. "Style match: +24.0", ending with the
    // running total - answers "why is the score this number" without needing to read source code.
    private fun scoreBreakdown(components: List<RecommendationScoreComponent>, total: Double, accentColorRes: Int): View {
        val context = requireContext()
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_input)
            setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }
        val top = components.filter { it.score != 0.0 }.sortedByDescending { it.score }.take(3)
        if (top.isEmpty()) {
            section.addView(TextView(context).apply {
                text = "No individual signal stands out - this score is close to baseline."
                setTextColor(context.getColor(R.color.flow_text_muted))
                textSize = 11f
            })
            return section
        }
        top.forEach { component ->
            section.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 2.dp(), 0, 2.dp())
                addView(TextView(context).apply {
                    text = plainSignalName(component.label)
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(TextView(context).apply {
                    text = component.score.signedScore()
                    setTextColor(context.getColor(if (component.score >= 0) accentColorRes else R.color.flow_error))
                    textSize = 12f
                    setTypeface(typeface, Typeface.BOLD)
                })
            })
        }
        section.addView(divider().apply { (layoutParams as? LinearLayout.LayoutParams)?.let { it.topMargin = 6.dp(); it.bottomMargin = 6.dp() } })
        section.addView(TextView(context).apply {
            text = "= %.1f total match score".format(total)
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        })
        return section
    }

    // Component labels are already fairly readable, but a couple read better rephrased for an
    // admin-facing summary rather than as an internal score-component name.
    private fun plainSignalName(label: String): String = when (label) {
        "Explicit style preference" -> "Style match (explicitly declared)"
        "Learned style" -> "Style match (learned from behavior)"
        "Proximity" -> "Distance / proximity"
        "Preferred area", "Resolved location" -> "Location match"
        else -> label
    }

    private fun Double.signedScore(): String = if (this >= 0) "+%.1f".format(this) else "%.1f".format(this)

    // Picks the single strongest positive component driving this item's rank and translates it
    // into plain language - reuses the same score breakdown already computed for ranking, rather
    // than depending on DiscoveryRepository.explanationFor's own signed-in-user-scoped cache
    // (this screen inspects an arbitrary other user, not whoever's actually signed in).
    private fun topSignalLabel(components: List<RecommendationScoreComponent>): String {
        val top = components.filter { it.score > 0 }.maxByOrNull { it.score } ?: return "No strong signal - low score"
        return "Why: " + when (top.label) {
            "Explicit style preference" -> "matches a style they explicitly dance"
            "Learned style" -> "matches a style they've engaged with"
            "Proximity" -> "it's near them"
            "Learned location" -> "they've shown interest in this area"
            "Resolved location", "Preferred area" -> "it's in their area"
            "Level match" -> "matches their level"
            "Freshness" -> "recently added"
            "Popularity" -> "popular right now"
            "Studio behavior" -> "they've engaged with this studio before"
            "Teacher behavior" -> "they've engaged with this teacher before"
            "Creator behavior" -> "they've engaged with this creator before"
            "Content type" -> "matches content they usually engage with"
            "Media type" -> "matches media they usually engage with"
            else -> top.label.lowercase()
        }
    }

    private fun divider(): View {
        return View(requireContext()).apply {
            setBackgroundColor(requireContext().getColor(R.color.flow_border))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp()).apply {
                topMargin = 10.dp()
                bottomMargin = 4.dp()
            }
        }
    }

    // A circular profile image, or a plain placeholder icon if the user has no photo.
    private fun avatar(url: String, size: Int): View {
        val imageView = ImageView(requireContext())
        imageView.layoutParams = LinearLayout.LayoutParams(size, size)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.clipToOutline = true
        imageView.setBackgroundResource(R.drawable.bg_flow_icon_button)
        if (url.isNotBlank()) {
            Glide.with(imageView).load(url).circleCrop().into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_profile_24)
            imageView.setColorFilter(requireContext().getColor(R.color.flow_text_muted))
        }
        return imageView
    }

    private companion object {
        const val BAR_TRACK_WIDTH_DP = 160
    }
}
