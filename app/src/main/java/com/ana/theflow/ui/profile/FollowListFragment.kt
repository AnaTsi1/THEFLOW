// Follow relationship list screen for followers and following profiles.
package com.ana.theflow.ui.profile

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentFollowListBinding
import com.bumptech.glide.Glide

// Displays one relationship collection and opens profile screens from each row.
class FollowListFragment : Fragment() {

    private var _binding: FragmentFollowListBinding? = null
    private val binding get() = _binding!!
    private val userRepository = UserRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFollowListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.followBTNBack.setOnClickListener { parentFragmentManager.popBackStack() }
        renderTitle()
        loadUsers()
    }

    // Loads followers or following users according to the fragment arguments.
    private fun loadUsers() {
        setLoading(true)
        binding.followLAYUsers.removeAllViews()
        val uid = requireArguments().getString(ARG_USER_ID).orEmpty()
        val mode = mode()
        val onSuccess: (List<User>) -> Unit = onSuccess@ { users ->
            if (_binding == null) return@onSuccess
            setLoading(false)
            renderUsers(users)
        }
        val onFailure: (String) -> Unit = onFailure@ { error ->
            if (_binding == null) return@onFailure
            setLoading(false)
            binding.followLBLMessage.text = error
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }

        when (mode) {
            Mode.FOLLOWERS -> userRepository.loadFollowers(uid, onSuccess, onFailure)
            Mode.FOLLOWING -> userRepository.loadFollowing(uid, onSuccess, onFailure)
        }
    }

    // Shows the title and helper text for the selected relationship list.
    private fun renderTitle() {
        val titleRes = if (mode() == Mode.FOLLOWERS) R.string.profile_followers else R.string.profile_following_count
        binding.followLBLTitle.text = getString(titleRes)
        binding.followLBLMessage.text = getString(R.string.profile_follow_list_hint)
    }

    // Renders one row per user or a clear empty state.
    private fun renderUsers(users: List<User>) {
        binding.followLBLMessage.text = if (users.isEmpty()) {
            getString(R.string.profile_follow_list_empty)
        } else {
            resources.getQuantityString(R.plurals.profile_follow_list_count, users.size, users.size)
        }
        users.forEach { user ->
            binding.followLAYUsers.addView(userRow(user))
        }
    }

    // Creates a compact profile row.
    private fun userRow(user: User): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            setOnClickListener {
                (requireActivity() as MainActivity).openUserProfile(user.uid)
            }

            addView(ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = getString(R.string.post_author_photo)
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    marginEnd = dp(12)
                }
                if (user.profileImageUrl.isNotBlank()) {
                    Glide.with(this).load(user.profileImageUrl).circleCrop().into(this)
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(TextView(context).apply {
                    text = user.fullName()
                    setTextColor(context.getColor(R.color.text_primary))
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                })

                addView(TextView(context).apply {
                    text = listOf(user.role, user.location).filter { it.isNotBlank() }.joinToString(" / ")
                    setTextColor(context.getColor(R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, dp(4), 0, 0)
                })
            })
        }
    }

    private fun mode(): Mode {
        return if (requireArguments().getString(ARG_MODE) == MODE_FOLLOWING) Mode.FOLLOWING else Mode.FOLLOWERS
    }

    private fun setLoading(isLoading: Boolean) {
        binding.followProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun User.fullName(): String {
        return "${firstName} ${lastName}".trim().ifBlank { getString(R.string.post_fallback_author) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class Mode {
        FOLLOWERS,
        FOLLOWING
    }

    companion object {
        private const val ARG_USER_ID = "ARG_USER_ID"
        private const val ARG_MODE = "ARG_MODE"
        private const val MODE_FOLLOWERS = "followers"
        private const val MODE_FOLLOWING = "following"

        fun followers(uid: String): FollowListFragment {
            return newInstance(uid, MODE_FOLLOWERS)
        }

        fun following(uid: String): FollowListFragment {
            return newInstance(uid, MODE_FOLLOWING)
        }

        private fun newInstance(uid: String, mode: String): FollowListFragment {
            return FollowListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, uid)
                    putString(ARG_MODE, mode)
                }
            }
        }
    }
}
