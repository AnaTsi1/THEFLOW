package com.ana.theflow.ui.discover

import com.ana.theflow.R

// The 4 fixed Discover sections, in display order. Recommended is always eager-loaded on
// screen open; the other 3 are lazy-loaded once their row scrolls near the viewport (see
// DiscoverFragment's RecyclerView lookahead setup).
enum class DiscoverSectionType(
    val emoji: String,
    val accentColorRes: Int,
    val titleRes: Int,
    val subtitleRes: Int
) {
    RECOMMENDED("🌟", R.color.discover_purple, R.string.discover_section_recommended_title, R.string.discover_section_recommended_subtitle),
    STUDIOS("📍", R.color.discover_accent_blue, R.string.discover_section_studios_title, R.string.discover_section_studios_subtitle),
    EVENTS("🎉", R.color.discover_accent_coral, R.string.discover_section_events_title, R.string.discover_section_events_subtitle),
    TEACHERS("💃", R.color.discover_accent_pink, R.string.discover_section_teachers_title, R.string.discover_section_teachers_subtitle)
}
