package com.ana.theflow.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ana.theflow.databinding.ItemDiscoverMapBinding
import com.ana.theflow.databinding.ItemDiscoverSectionBinding

// A fixed set of rows - the 4 Discover sections (Recommended/Studios/Events/Teachers) followed by
// one map preview row - deliberately not a generic feed adapter, since Discover always has
// exactly this fixed layout. All data-loading/caching logic lives in DiscoverFragment; this
// adapter only paints each row's static chrome and hands the content container to the fragment's
// bind callback.
//
// The map row uses a plain android.view.View-based MapView (not a SupportMapFragment) so it can
// live inside a RecyclerView item safely. A Fragment can't be "recycled" into an arbitrary
// ViewHolder the way a View can - FragmentManager owns Fragments by a container id and transaction
// history, and RecyclerView reuses the same item view instance across binds in ways that don't
// line up with that model (stale-fragment/duplicate-id issues, "Cannot add fragment already
// added"-style errors). MapView has no such requirement since it's a plain ViewGroup - the only
// cost is that its lifecycle (onCreate/onResume/onPause/onDestroy) has to be forwarded by hand
// instead of coming for free from a Fragment host, which DiscoverFragment does directly since the
// map row's ViewHolder is created exactly once and kept alive for the fragment view's whole
// lifetime (see setItemViewCacheSize in DiscoverFragment.setUpSections).
class DiscoverSectionsAdapter(
    private val onBindSection: (DiscoverSectionType, ItemDiscoverSectionBinding) -> Unit,
    private val onCreateMapRow: (ItemDiscoverMapBinding) -> Unit,
    private val onBindMapRow: (ItemDiscoverMapBinding) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val sections = DiscoverSectionType.values().toList()

    class SectionViewHolder(val binding: ItemDiscoverSectionBinding) : RecyclerView.ViewHolder(binding.root)
    class MapViewHolder(val binding: ItemDiscoverMapBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (position < sections.size) VIEW_TYPE_SECTION else VIEW_TYPE_MAP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_MAP) {
            val binding = ItemDiscoverMapBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            onCreateMapRow(binding)
            return MapViewHolder(binding)
        }
        val binding = ItemDiscoverSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding)
    }

    override fun getItemCount(): Int = sections.size + 1

    override fun getItemId(position: Int): Long {
        return if (position < sections.size) sections[position].ordinal.toLong() else MAP_ITEM_ID
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MapViewHolder) {
            onBindMapRow(holder.binding)
            return
        }
        val section = sections[position]
        val sectionHolder = holder as SectionViewHolder
        val context = sectionHolder.itemView.context
        sectionHolder.binding.sectionLBLEmoji.text = section.emoji
        sectionHolder.binding.sectionLBLTitle.text = context.getString(section.titleRes)
        sectionHolder.binding.sectionLBLSubtitle.text = context.getString(section.subtitleRes)
        sectionHolder.binding.sectionVIEWAccent.setBackgroundColor(context.getColor(section.accentColorRes))
        sectionHolder.binding.sectionLAYContent.removeAllViews()
        onBindSection(section, sectionHolder.binding)
    }

    init {
        setHasStableIds(true)
    }

    private companion object {
        const val VIEW_TYPE_SECTION = 0
        const val VIEW_TYPE_MAP = 1
        const val MAP_ITEM_ID = -1L
    }
}
