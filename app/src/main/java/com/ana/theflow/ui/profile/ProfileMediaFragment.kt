package com.ana.theflow.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostMediaItem
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.databinding.FragmentProfileMediaBinding
import com.bumptech.glide.Glide

class ProfileMediaFragment : Fragment() {

    private var _binding: FragmentProfileMediaBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val postRepository = PostRepository()

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.profileMediaBTNBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        loadMedia()
    }

    // Loads profile media from the current user's posts.
    private fun loadMedia() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        postRepository.loadPostsByAuthor(
            authorId = uid,
            onSuccess = { posts ->
                renderGrid(mediaItems(posts))
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Collects visible media items from posts.
    private fun mediaItems(posts: List<Post>): List<ProfileMediaItem> {
        return posts
            .flatMap { post -> itemsForPost(post) }
            .filter { it.item.visibleInMedia && it.item.url.isNotBlank() }
            .sortedWith(
                compareByDescending<ProfileMediaItem> { it.item.pinned }
                    .thenByDescending { it.item.uploadedAt }
            )
    }

    // Returns media items for one post, including legacy mediaUrls-only posts.
    private fun itemsForPost(post: Post): List<ProfileMediaItem> {
        if (post.mediaItems.isNotEmpty()) {
            return post.mediaItems.map { item ->
                ProfileMediaItem(postId = post.postId, item = item, allItems = post.mediaItems)
            }
        }

        val legacyItems = post.mediaUrls.mapIndexed { index, url ->
            PostMediaItem(
                id = "legacy_${index}_${url.hashCode()}",
                url = url,
                mediaType = post.mediaType.ifBlank { MEDIA_TYPE_PHOTO },
                visibleInMedia = true,
                pinned = false,
                uploadedAt = (post.createdAt?.seconds ?: 0L) * 1000L
            )
        }
        return legacyItems.map { item ->
            ProfileMediaItem(postId = post.postId, item = item, allItems = legacyItems)
        }
    }

    // Draws media in rows of exactly three square slots.
    private fun renderGrid(items: List<ProfileMediaItem>) {
        binding.profileMediaLAYGrid.removeAllViews()
        binding.profileMediaLBLEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.chunked(3).forEach { rowItems ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dp()
                }
            }

            rowItems.forEach { item ->
                row.addView(createGridSlot(item))
            }
            repeat(3 - rowItems.size) {
                row.addView(createEmptyGridSpace())
            }
            binding.profileMediaLAYGrid.addView(row)
        }
    }

    // Creates one square slot in the three-column media grid.
    private fun createGridSlot(media: ProfileMediaItem): View {
        val frame = FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_media_gradient)
            layoutParams = LinearLayout.LayoutParams(0, 112.dp(), 1f).apply {
                leftMargin = 4.dp()
                rightMargin = 4.dp()
            }
        }

        if (media.item.mediaType == MEDIA_TYPE_VIDEO) {
            frame.addView(videoLabel())
        } else {
            val image = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            frame.addView(image)
            Glide.with(this).load(media.item.url).centerCrop().into(image)
        }

        frame.setOnClickListener {
            (requireActivity() as MainActivity).openMediaViewer(media.item.url, media.item.mediaType)
        }
        if (media.item.pinned) {
            frame.addView(pinBadge())
        }
        frame.addView(menuButton(media))
        return frame
    }

    // Creates an invisible grid space to preserve three-column proportions.
    private fun createEmptyGridSpace(): View {
        return Space(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, 112.dp(), 1f).apply {
                leftMargin = 4.dp()
                rightMargin = 4.dp()
            }
        }
    }

    // Creates the video placeholder label.
    private fun videoLabel(): TextView {
        return TextView(requireContext()).apply {
            text = "VIDEO"
            gravity = android.view.Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Creates the pinned badge.
    private fun pinBadge(): TextView {
        return TextView(requireContext()).apply {
            text = "PIN"
            textSize = 10f
            setTextColor(requireContext().getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_primary)
            setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.START or android.view.Gravity.TOP
            ).apply {
                leftMargin = 6.dp()
                topMargin = 6.dp()
            }
        }
    }

    // Creates the three-dot menu button for one media item.
    private fun menuButton(media: ProfileMediaItem): Button {
        return Button(requireContext()).apply {
            text = "..."
            textSize = 13f
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            setTextColor(requireContext().getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = FrameLayout.LayoutParams(
                34.dp(),
                30.dp(),
                android.view.Gravity.END or android.view.Gravity.TOP
            ).apply {
                rightMargin = 6.dp()
                topMargin = 6.dp()
            }
            setOnClickListener {
                showMediaMenu(this, media)
            }
        }
    }

    // Opens actions for one media item.
    private fun showMediaMenu(anchor: View, media: ProfileMediaItem) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(if (media.item.pinned) "Unpin from top" else "Pin to top")
            menu.add("Remove from Media")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Pin to top", "Unpin from top" -> {
                        updateMediaItem(media, pinned = !media.item.pinned, visibleInMedia = media.item.visibleInMedia)
                        true
                    }
                    "Remove from Media" -> {
                        updateMediaItem(media, pinned = false, visibleInMedia = false)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    // Saves one media metadata change.
    private fun updateMediaItem(media: ProfileMediaItem, pinned: Boolean, visibleInMedia: Boolean) {
        val updatedItems = media.allItems.map { item ->
            if (item.id == media.item.id) item.copy(pinned = pinned, visibleInMedia = visibleInMedia) else item
        }
        postRepository.updatePostMediaItems(
            postId = media.postId,
            mediaItems = updatedItems,
            onSuccess = { loadMedia() },
            onFailure = { error -> Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show() }
        )
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ProfileMediaItem(
        val postId: String,
        val item: PostMediaItem,
        val allItems: List<PostMediaItem>
    )

    private companion object {
        const val MEDIA_TYPE_PHOTO = "photo"
        const val MEDIA_TYPE_VIDEO = "video"
    }
}

// Converts dp units to pixels.
private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
