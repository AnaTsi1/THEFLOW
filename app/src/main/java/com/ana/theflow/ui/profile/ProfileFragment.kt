package com.ana.theflow.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.MainActivity
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentProfileBinding
import com.ana.theflow.ui.common.PostCardRenderer

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private var currentUser: User? = null
    private var isEditing = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.profileBTNEdit.setOnClickListener {
            if (isEditing) saveProfile() else setEditMode(true)
        }
        binding.profileBTNSettings.setOnClickListener {
            (requireActivity() as MainActivity).openSettings()
        }
        binding.profileBTNCreatePost.setOnClickListener {
            createPost()
        }
        loadProfile()
    }

    private fun loadProfile() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                currentUser = user
                renderProfile(user)
                populateEditFields(user)
                loadOwnPosts(user.uid)
                activityTrackingRepository.trackViewProfile(
                    targetUserId = user.uid,
                    targetName = "${user.firstName} ${user.lastName}".trim(),
                    danceStyles = user.danceStyles,
                    location = user.location
                )
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun renderProfile(user: User) {
        val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        binding.profileLBLName.text = fullName
        binding.profileLBLHeadline.text = user.headline
        binding.profileLBLHeadline.visibility = if (user.headline.isBlank()) View.GONE else View.VISIBLE
        binding.profileLBLLocation.text = buildString {
            if (user.location.isNotBlank()) append(user.location)
            if (user.age > 0) {
                if (isNotBlank()) append(" / ")
                append("${user.age}")
            }
            if (isBlank()) append(user.email)
        }
        binding.profileLBLAbout.text = user.bio
        binding.profileLBLAbout.visibility = if (user.bio.isBlank()) View.GONE else View.VISIBLE

        val isIncomplete = user.headline.isBlank() ||
            user.bio.isBlank() ||
            user.danceStyles.isEmpty() ||
            user.danceLevel.isBlank()
        binding.profileLBLCompleteCta.visibility = if (isIncomplete) View.VISIBLE else View.GONE

        binding.profileLBLDetails.text = listOfNotNull(
            line("Badges", user.professionalBadges.joinToString(", ")),
            line("Styles", user.danceStyles.joinToString(", ")),
            line("Level", user.danceLevel),
            line("Years", user.yearsOfExperience),
            line("Studios", user.studiosTrainedAt.joinToString(", ")),
            line("Teachers", user.teachersLearnedFrom.joinToString(", ")),
            line("Performances", user.performancesCompetitions.joinToString(", ")),
            line("Availability", user.availability),
            line("Instagram", user.instagramUrl),
            line("TikTok", user.tiktokUrl),
            line("YouTube", user.youtubeUrl)
        ).joinToString("\n").ifBlank { "Add professional details to strengthen your dancer profile." }
    }

    private fun line(label: String, value: String): String? {
        return value.ifBlank { null }?.let { "$label: $it" }
    }

    private fun populateEditFields(user: User) {
        binding.profileEDTFirstName.setText(user.firstName)
        binding.profileEDTLastName.setText(user.lastName)
        binding.profileEDTBirthDate.setText(user.birthDate)
        binding.profileEDTAge.setText(if (user.age > 0) user.age.toString() else "")
        binding.profileEDTHeadline.setText(user.headline)
        binding.profileEDTAbout.setText(user.bio)
        binding.profileEDTStyles.setText(user.danceStyles.joinToString(", "))
        binding.profileEDTLevel.setText(user.danceLevel)
        binding.profileEDTYears.setText(user.yearsOfExperience)
        binding.profileEDTLocation.setText(user.location)
        binding.profileEDTStudios.setText(user.studiosTrainedAt.joinToString(", "))
        binding.profileEDTTeachers.setText(user.teachersLearnedFrom.joinToString(", "))
        binding.profileEDTPerformances.setText(user.performancesCompetitions.joinToString(", "))
        binding.profileEDTAvailability.setText(user.availability)
        binding.profileEDTInstagram.setText(user.instagramUrl)
        binding.profileEDTTiktok.setText(user.tiktokUrl)
        binding.profileEDTYoutube.setText(user.youtubeUrl)
    }

    private fun setEditMode(enabled: Boolean) {
        isEditing = enabled
        binding.profileLAYEdit.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.profileBTNEdit.text = if (enabled) "Save Profile" else "Edit Profile"
    }

    private fun saveProfile() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        binding.profileBTNEdit.isEnabled = false
        userRepository.updateUserProfile(
            uid = uid,
            firstName = binding.profileEDTFirstName.text.toString().trim(),
            lastName = binding.profileEDTLastName.text.toString().trim(),
            birthDate = binding.profileEDTBirthDate.text.toString().trim(),
            age = binding.profileEDTAge.text.toString().toIntOrNull() ?: 0,
            headline = binding.profileEDTHeadline.text.toString().trim(),
            bio = binding.profileEDTAbout.text.toString().trim(),
            location = binding.profileEDTLocation.text.toString().trim(),
            danceStyles = commaList(binding.profileEDTStyles.text.toString()),
            danceLevel = binding.profileEDTLevel.text.toString().trim(),
            yearsOfExperience = binding.profileEDTYears.text.toString().trim(),
            studiosTrainedAt = commaList(binding.profileEDTStudios.text.toString()),
            teachersLearnedFrom = commaList(binding.profileEDTTeachers.text.toString()),
            performancesCompetitions = commaList(binding.profileEDTPerformances.text.toString()),
            availability = binding.profileEDTAvailability.text.toString().trim(),
            instagramUrl = binding.profileEDTInstagram.text.toString().trim(),
            tiktokUrl = binding.profileEDTTiktok.text.toString().trim(),
            youtubeUrl = binding.profileEDTYoutube.text.toString().trim(),
            onSuccess = {
                binding.profileBTNEdit.isEnabled = true
                setEditMode(false)
                loadProfile()
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                binding.profileBTNEdit.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun createPost() {
        val user = currentUser ?: return
        val text = binding.profileEDTNewPost.text.toString().trim()
        binding.profileBTNCreatePost.isEnabled = false
        postRepository.createTextPost(
            author = user,
            text = text,
            onSuccess = {
                binding.profileBTNCreatePost.isEnabled = true
                binding.profileEDTNewPost.text?.clear()
                activityTrackingRepository.trackCreatePost(
                    postId = it,
                    authorType = user.role,
                    text = text
                )
                loadOwnPosts(user.uid)
                Toast.makeText(requireContext(), "Post created", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                binding.profileBTNCreatePost.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun loadOwnPosts(uid: String) {
        binding.profileLAYPosts.removeAllViews()
        postRepository.loadPostsByAuthor(
            authorId = uid,
            onSuccess = { posts ->
                binding.profileLBLPostsEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                posts.forEach { post ->
                    PostCardRenderer.addPostCard(
                        parent = binding.profileLAYPosts,
                        post = post,
                        onOpen = {
                            activityTrackingRepository.trackViewPost(
                                postId = it.postId,
                                authorName = it.authorName,
                                authorType = it.authorType
                            )
                        }
                    )
                }
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun commaList(value: String): List<String> {
        return value.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
