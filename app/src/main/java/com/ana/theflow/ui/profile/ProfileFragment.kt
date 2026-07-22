package com.ana.theflow.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.post.Post
import com.ana.theflow.data.model.post.PostMediaItem
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.databinding.FragmentProfileBinding
import com.ana.theflow.ui.common.PostCardRenderer
import com.ana.theflow.utilities.CityOptions

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private val storageRepository = StorageRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()
    private var currentUser: User? = null
    private var pendingProfilePhotoUri: Uri? = null
    private var pendingCoverImageUri: Uri? = null
    private var pendingPostMediaUri: Uri? = null
    private var pendingPostMediaType: String = MEDIA_TYPE_NONE
    private var selectedComposerMode = ComposerMode.REGULAR

    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingProfilePhotoUri = uri
        binding.profileBTNProfilePhoto.text = if (uri == null) {
            "Choose Profile Photo"
        } else {
            "Profile Photo Selected"
        }
    }

    private val coverImagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingCoverImageUri = uri
        binding.profileBTNCoverImage.text = if (uri == null) {
            "Choose Cover Image"
        } else {
            "Cover Image Selected"
        }
    }

    private val postMediaPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val mediaType = resolveMediaType(uri)
        if (uri != null && mediaType == MEDIA_TYPE_MEDIA) {
            pendingPostMediaUri = null
            pendingPostMediaType = MEDIA_TYPE_NONE
            binding.profileLBLSelectedMedia.visibility = View.GONE
            Toast.makeText(requireContext(), "Choose a photo or video", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        pendingPostMediaUri = uri
        pendingPostMediaType = mediaType
        binding.profileLBLSelectedMedia.visibility = if (uri == null) View.GONE else View.VISIBLE
        binding.profileLBLSelectedMedia.text = when (pendingPostMediaType) {
            MEDIA_TYPE_VIDEO -> "Video selected"
            MEDIA_TYPE_PHOTO -> "Photo selected"
            else -> "Media selected"
        }
    }

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.profileBTNEdit.setOnClickListener {
            setEditMode(true)
        }
        binding.profileBTNSettings.setOnClickListener {
            (requireActivity() as MainActivity).openSettings()
        }
        binding.profileBTNSavedItems.setOnClickListener {
            (requireActivity() as MainActivity).openSavedItems()
        }
        binding.profileBTNCreatePost.setOnClickListener {
            createPost()
        }
        binding.profileBTNPostMedia.setOnClickListener {
            postMediaPicker.launch("*/*")
        }
        binding.profileBTNPostActivity.setOnClickListener {
            setComposerMode(ComposerMode.DANCE_ACTIVITY)
        }
        binding.profileBTNPostCollaboration.setOnClickListener {
            setComposerMode(ComposerMode.COLLABORATION)
        }
        binding.profileBTNProfilePhoto.setOnClickListener {
            profilePhotoPicker.launch("image/*")
        }
        binding.profileBTNCoverImage.setOnClickListener {
            coverImagePicker.launch("image/*")
        }
        binding.profileBTNSaveEdit.setOnClickListener {
            saveProfile()
        }
        binding.profileBTNCancelEdit.setOnClickListener {
            setEditMode(false)
        }
        configurePostComposer()
        loadProfile()
    }

    // Loads the current user profile.
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
                    targetName = "${user.firstName} ${user.lastName}".trim()
                )
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Shows user profile data on screen.
    private fun renderProfile(user: User) {
        val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
        binding.profileLBLName.text = fullName
        binding.profileLBLHeadline.text = user.headline
        binding.profileLBLHeadline.visibility = if (user.headline.isBlank()) View.GONE else View.VISIBLE
        binding.profileLBLAbout.text = user.bio
        binding.profileLBLAbout.visibility = if (user.bio.isBlank()) View.GONE else View.VISIBLE

        binding.profileLBLDetails.text = listOfNotNull(
            line("Badges", user.professionalBadges.joinToString(", ")),
            line("Background", user.professionalBackground),
            line("Years", user.yearsOfExperience),
            line("Studios", user.studiosTrainedAt.joinToString(", ")),
            line("Teachers", user.teachersLearnedFrom.joinToString(", ")),
            line("Performances", user.performancesCompetitions.joinToString(", ")),
            line("Availability", user.availability),
            line("Instagram", user.instagramUrl),
            line("TikTok", user.tiktokUrl),
            line("YouTube", user.youtubeUrl)
        ).joinToString("\n")
        binding.profileLAYSections.visibility = if (
            binding.profileLBLDetails.text.isBlank() && user.skills.isEmpty()
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.profileLBLDetails.visibility = if (binding.profileLBLDetails.text.isBlank()) View.GONE else View.VISIBLE
        binding.profileLBLSkills.text = user.skills.joinToString(separator = " / ")
        binding.profileLBLSkills.visibility = if (user.skills.isEmpty()) View.GONE else View.VISIBLE

        renderProfileImages(user)
    }

    // Formats one profile detail line.
    private fun line(label: String, value: String): String? {
        return value.ifBlank { null }?.let { "$label: $it" }
    }

    // Loads profile and cover images into the header.
    private fun renderProfileImages(user: User) {
        binding.profileIMGAvatar.setImageResource(android.R.color.transparent)
        binding.profileIMGCover.setImageResource(android.R.color.transparent)
        if (user.profileImageUrl.isNotBlank()) {
            Glide.with(this)
                .load(user.profileImageUrl)
                .centerCrop()
                .into(binding.profileIMGAvatar)
        }
        if (user.coverImageUrl.isNotBlank()) {
            Glide.with(this)
                .load(user.coverImageUrl)
                .centerCrop()
                .into(binding.profileIMGCover)
        }
    }

    // Fills the inline edit fields from the current profile.
    private fun populateEditFields(user: User) {
        binding.profileEDTBio.setText(user.bio)
        binding.profileEDTBackground.setText(user.professionalBackground)
        binding.profileEDTSkills.setText(user.skills.joinToString(", "))
    }

    // Shows or hides the inline profile editor.
    private fun setEditMode(enabled: Boolean) {
        val user = currentUser ?: return
        binding.profileLAYEdit.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.profileBTNEdit.visibility = if (enabled) View.GONE else View.VISIBLE
        if (enabled) {
            populateEditFields(user)
        } else {
            pendingProfilePhotoUri = null
            pendingCoverImageUri = null
            binding.profileBTNProfilePhoto.text = "Choose Profile Photo"
            binding.profileBTNCoverImage.text = "Choose Cover Image"
        }
    }

    // Saves the edited public profile fields.
    private fun saveProfile() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        val user = currentUser ?: return

        setEditLoading(true)
        userRepository.updateUserProfile(
            uid = uid,
            firstName = user.firstName,
            lastName = user.lastName,
            birthDate = user.birthDate,
            age = user.age,
            headline = user.headline,
            bio = binding.profileEDTBio.text.toString().trim(),
            professionalBackground = binding.profileEDTBackground.text.toString().trim(),
            skills = commaList(binding.profileEDTSkills.text.toString()),
            yearsOfExperience = user.yearsOfExperience,
            studiosTrainedAt = user.studiosTrainedAt,
            teachersLearnedFrom = user.teachersLearnedFrom,
            performancesCompetitions = user.performancesCompetitions,
            availability = user.availability,
            instagramUrl = user.instagramUrl,
            tiktokUrl = user.tiktokUrl,
            youtubeUrl = user.youtubeUrl,
            onSuccess = {
                uploadSelectedImages(uid)
            },
            onFailure = { error ->
                setEditLoading(false)
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Uploads selected profile images before reloading the profile.
    private fun uploadSelectedImages(uid: String) {
        val profileUri = pendingProfilePhotoUri
        val coverUri = pendingCoverImageUri

        if (profileUri != null) {
            storageRepository.uploadProfileImage(
                uid = uid,
                imageUri = profileUri,
                onSuccess = {
                    pendingProfilePhotoUri = null
                    uploadCoverImageIfNeeded(uid, coverUri)
                },
                onFailure = { error -> finishProfileSaveWithError(error) }
            )
            return
        }

        uploadCoverImageIfNeeded(uid, coverUri)
    }

    // Uploads a cover image when one was selected.
    private fun uploadCoverImageIfNeeded(uid: String, coverUri: Uri?) {
        if (coverUri == null) {
            finishProfileSave()
            return
        }

        storageRepository.uploadCoverImage(
            uid = uid,
            imageUri = coverUri,
            onSuccess = {
                pendingCoverImageUri = null
                finishProfileSave()
            },
            onFailure = { error -> finishProfileSaveWithError(error) }
        )
    }

    // Finishes a successful profile save and reloads Firestore data.
    private fun finishProfileSave() {
        setEditLoading(false)
        setEditMode(false)
        loadProfile()
        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
    }

    // Re-enables the inline editor after a save error.
    private fun finishProfileSaveWithError(error: String) {
        setEditLoading(false)
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    // Enables or disables edit controls while saving.
    private fun setEditLoading(isLoading: Boolean) {
        binding.profileBTNEdit.isEnabled = !isLoading
        binding.profileBTNSaveEdit.isEnabled = !isLoading
        binding.profileBTNCancelEdit.isEnabled = !isLoading
        binding.profileBTNProfilePhoto.isEnabled = !isLoading
        binding.profileBTNCoverImage.isEnabled = !isLoading
    }

    // Creates a post from the profile screen.
    private fun createPost() {
        val user = currentUser ?: return
        val text = binding.profileEDTPostText.text.toString().trim()
        binding.profileBTNCreatePost.isEnabled = false
        postRepository.createPost(
            author = user,
            text = text,
            mediaType = pendingPostMediaType,
            postType = selectedComposerMode.postType,
            activityType = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityType.text.toString().trim()
            } else {
                ""
            },
            activityLocation = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                CityOptions.normalizeOptionalCity(binding.profileEDTActivityLocation.text.toString()).orEmpty()
            } else {
                ""
            },
            activityDate = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityDate.text.toString().trim()
            } else {
                ""
            },
            activityTime = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityTime.text.toString().trim()
            } else {
                ""
            },
            activityPrice = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityPrice.text.toString().trim()
            } else {
                ""
            },
            activityLevel = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityLevel.text.toString().trim()
            } else {
                ""
            },
            activityDescription = if (selectedComposerMode == ComposerMode.DANCE_ACTIVITY) {
                binding.profileEDTActivityDescription.text.toString().trim()
            } else {
                ""
            },
            collaborationLookingFor = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                binding.profileEDTCollaborationLookingFor.text.toString().trim()
            } else {
                ""
            },
            collaborationStyle = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                binding.profileEDTCollaborationStyle.text.toString().trim()
            } else {
                ""
            },
            collaborationLocation = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                CityOptions.normalizeOptionalCity(binding.profileEDTCollaborationLocation.text.toString()).orEmpty()
            } else {
                ""
            },
            collaborationDate = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                binding.profileEDTCollaborationDate.text.toString().trim()
            } else {
                ""
            },
            collaborationPaid = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                binding.profileEDTCollaborationPaid.text.toString().trim()
            } else {
                ""
            },
            collaborationDescription = if (selectedComposerMode == ComposerMode.COLLABORATION) {
                binding.profileEDTCollaborationDescription.text.toString().trim()
            } else {
                ""
            },
            onSuccess = { postId ->
                uploadPostMediaIfNeeded(
                    postId = postId,
                    user = user,
                    text = text
                )
            },
            onFailure = { error ->
                binding.profileBTNCreatePost.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Uploads selected post media after the post document exists.
    private fun uploadPostMediaIfNeeded(postId: String, user: User, text: String) {
        val mediaUri = pendingPostMediaUri
        if (mediaUri == null) {
            finishPostCreate(postId, user, text)
            return
        }

        storageRepository.uploadPostMedia(
            postId = postId,
            mediaUri = mediaUri,
            fileName = postMediaFileName(mediaUri),
            mediaType = pendingPostMediaType,
            onSuccess = {
                finishPostCreate(postId, user, text)
            },
            onFailure = { error ->
                binding.profileBTNCreatePost.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Resets the composer and reloads posts after a successful create.
    private fun finishPostCreate(postId: String, user: User, text: String) {
        binding.profileBTNCreatePost.isEnabled = true
        resetPostComposer()
        activityTrackingRepository.trackCreatePost(
            postId = postId,
            authorType = user.role,
            text = text
        )
        loadOwnPosts(user.uid)
        Toast.makeText(requireContext(), "Post created", Toast.LENGTH_SHORT).show()
    }

    // Prepares dropdowns used by the dynamic post composer.
    private fun configurePostComposer() {
        configureDropdown(
            view = binding.profileEDTActivityType,
            options = listOf("Class", "Workshop", "Course", "Dance Spot", "Studio", "Audition", "Event")
        )
        configureDropdown(
            view = binding.profileEDTCollaborationPaid,
            options = listOf("Paid", "Unpaid", "Open to discuss")
        )
        CityOptions.configureCitySelector(requireContext(), binding.profileEDTActivityLocation)
        CityOptions.configureCitySelector(requireContext(), binding.profileEDTCollaborationLocation)
        setComposerMode(ComposerMode.REGULAR)
    }

    // Switches which extra composer fields are visible.
    private fun setComposerMode(mode: ComposerMode) {
        selectedComposerMode = if (selectedComposerMode == mode && mode != ComposerMode.REGULAR) {
            ComposerMode.REGULAR
        } else {
            mode
        }
        binding.profileLAYActivityFields.visibility = if (
            selectedComposerMode == ComposerMode.DANCE_ACTIVITY
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.profileLAYCollaborationFields.visibility = if (
            selectedComposerMode == ComposerMode.COLLABORATION
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    // Clears all composer inputs after publishing.
    private fun resetPostComposer() {
        binding.profileEDTPostText.text?.clear()
        binding.profileEDTActivityType.text?.clear()
        binding.profileEDTActivityLocation.text?.clear()
        binding.profileEDTActivityDate.text?.clear()
        binding.profileEDTActivityTime.text?.clear()
        binding.profileEDTActivityPrice.text?.clear()
        binding.profileEDTActivityLevel.text?.clear()
        binding.profileEDTActivityDescription.text?.clear()
        binding.profileEDTCollaborationLookingFor.text?.clear()
        binding.profileEDTCollaborationStyle.text?.clear()
        binding.profileEDTCollaborationLocation.text?.clear()
        binding.profileEDTCollaborationDate.text?.clear()
        binding.profileEDTCollaborationPaid.text?.clear()
        binding.profileEDTCollaborationDescription.text?.clear()
        pendingPostMediaUri = null
        pendingPostMediaType = MEDIA_TYPE_NONE
        binding.profileLBLSelectedMedia.visibility = View.GONE
        setComposerMode(ComposerMode.REGULAR)
    }

    // Connects a simple dropdown list to an AutoCompleteTextView.
    private fun configureDropdown(view: AutoCompleteTextView, options: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, options)
        view.setAdapter(adapter)
        view.threshold = 0
        view.setOnClickListener {
            view.showDropDown()
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) view.showDropDown()
        }
    }

    // Detects whether the selected post media is a photo or video.
    private fun resolveMediaType(uri: Uri?): String {
        if (uri == null) return MEDIA_TYPE_NONE
        val mimeType = requireContext().contentResolver.getType(uri).orEmpty()
        return when {
            mimeType.startsWith("video/") -> MEDIA_TYPE_VIDEO
            mimeType.startsWith("image/") -> MEDIA_TYPE_PHOTO
            else -> MEDIA_TYPE_MEDIA
        }
    }

    // Builds a storage-safe file name for post media.
    private fun postMediaFileName(uri: Uri): String {
        val extension = when (resolveMediaType(uri)) {
            MEDIA_TYPE_VIDEO -> "mp4"
            MEDIA_TYPE_PHOTO -> "jpg"
            else -> "upload"
        }
        return "post_${System.currentTimeMillis()}.$extension"
    }

    // Loads posts written by the current user.
    private fun loadOwnPosts(uid: String) {
        binding.profileLAYPosts.removeAllViews()
        postRepository.loadPostsByAuthor(
            authorId = uid,
            onSuccess = { posts ->
                binding.profileLBLPostsEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                renderProfileMedia(posts)
                posts.forEach { post ->
                    renderOwnPostCard(post)
                }
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Renders one owned post with edit, like, comment, and media actions.
    private fun renderOwnPostCard(post: Post) {
        postRepository.loadComments(
            postId = post.postId,
            onSuccess = { comments ->
                if (_binding == null) return@loadComments
                postRepository.isPostLikedByCurrentUser(
                    postId = post.postId,
                    onSuccess = { isLiked ->
                        if (_binding == null) return@isPostLikedByCurrentUser
                        PostCardRenderer.addPostCard(
                            parent = binding.profileLAYPosts,
                            post = post,
                            comments = comments,
                            isLiked = isLiked,
                            canEdit = true,
                            onOpen = { opened ->
                                activityTrackingRepository.trackViewPost(
                                    postId = opened.postId,
                                    authorName = opened.authorName,
                                    authorType = opened.authorType
                                )
                            },
                            onLike = { targetPost -> toggleLike(targetPost) },
                            onComment = { targetPost, text -> addComment(targetPost, text) },
                            onEdit = { targetPost -> showEditPostDialog(targetPost) },
                            onDelete = { targetPost -> confirmDeletePost(targetPost) },
                            onMediaOpen = { url, mediaType ->
                                (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                            }
                        )
                    }
                )
            },
            onFailure = {
                if (_binding == null) return@loadComments
                PostCardRenderer.addPostCard(
                    parent = binding.profileLAYPosts,
                    post = post,
                    canEdit = true,
                    onLike = { targetPost -> toggleLike(targetPost) },
                    onComment = { targetPost, text -> addComment(targetPost, text) },
                    onEdit = { targetPost -> showEditPostDialog(targetPost) },
                    onDelete = { targetPost -> confirmDeletePost(targetPost) },
                    onMediaOpen = { url, mediaType ->
                        (requireActivity() as MainActivity).openMediaViewer(url, mediaType)
                    }
                )
            }
        )
    }

    // Toggles a like and refreshes profile posts.
    private fun toggleLike(post: Post) {
        postRepository.toggleLike(
            postId = post.postId,
            onSuccess = {
                currentUser?.uid?.let { loadOwnPosts(it) }
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Adds a comment and refreshes profile posts.
    private fun addComment(post: Post, text: String) {
        val user = currentUser ?: return
        postRepository.addComment(
            postId = post.postId,
            author = user,
            text = text,
            onSuccess = {
                loadOwnPosts(user.uid)
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Opens a focused editor for the post text.
    private fun showEditPostDialog(post: Post) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(post.text)
            minLines = 3
            hint = "Edit your post"
            setTextColor(requireContext().getColor(R.color.text_primary))
            setHintTextColor(requireContext().getColor(R.color.text_muted))
            setBackgroundResource(R.drawable.bg_input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Post")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                postRepository.updatePostText(
                    postId = post.postId,
                    text = input.text.toString(),
                    onSuccess = {
                        currentUser?.uid?.let { loadOwnPosts(it) }
                        Toast.makeText(requireContext(), "Post updated", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .show()
    }

    // Confirms and deletes one owned post.
    private fun confirmDeletePost(post: Post) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Delete this post?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                postRepository.deletePost(
                    postId = post.postId,
                    onSuccess = {
                        currentUser?.uid?.let { loadOwnPosts(it) }
                        Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .show()
    }

    // Shows the profile media strip and full media panel.
    private fun renderProfileMedia(posts: List<Post>) {
        val mediaItems = posts
            .flatMap { post -> profileMediaItems(post) }
            .filter { it.item.visibleInMedia && it.item.url.isNotBlank() }
            .sortedWith(
                compareByDescending<ProfileMediaItem> { it.item.pinned }
                    .thenByDescending { it.item.uploadedAt }
            )

        binding.profileLAYMediaSection.visibility = if (mediaItems.isEmpty()) View.GONE else View.VISIBLE
        binding.profileLAYMediaStrip.removeAllViews()
        binding.profileLAYMediaGrid.removeAllViews()
        if (mediaItems.isEmpty()) {
            binding.profileLAYMediaPanel.visibility = View.GONE
            return
        }

        binding.profileBTNOpenMedia.setOnClickListener {
            (requireActivity() as MainActivity).openProfileMedia()
        }
        binding.profileBTNCloseMedia.setOnClickListener {
            binding.profileLAYMediaPanel.visibility = View.GONE
        }

        mediaItems.take(MEDIA_STRIP_LIMIT).forEach { media ->
            binding.profileLAYMediaStrip.addView(createMediaTile(media, compact = true))
        }
        mediaItems.forEach { media ->
            binding.profileLAYMediaGrid.addView(createMediaTile(media, compact = false))
        }
    }

    // Returns editable media items for one post, including old mediaUrls-only posts.
    private fun profileMediaItems(post: Post): List<ProfileMediaItem> {
        if (post.mediaItems.isNotEmpty()) {
            return post.mediaItems.map { item ->
                ProfileMediaItem(
                    postId = post.postId,
                    item = item,
                    allItems = post.mediaItems
                )
            }
        }

        return post.mediaUrls.mapIndexed { index, url ->
            val item = PostMediaItem(
                id = "legacy_${index}_${url.hashCode()}",
                url = url,
                mediaType = post.mediaType.ifBlank { MEDIA_TYPE_PHOTO },
                visibleInMedia = true,
                pinned = false,
                uploadedAt = (post.createdAt?.seconds ?: 0L) * 1000L
            )
            ProfileMediaItem(
                postId = post.postId,
                item = item,
                allItems = post.mediaUrls.mapIndexed { legacyIndex, legacyUrl ->
                    PostMediaItem(
                        id = "legacy_${legacyIndex}_${legacyUrl.hashCode()}",
                        url = legacyUrl,
                        mediaType = post.mediaType.ifBlank { MEDIA_TYPE_PHOTO },
                        visibleInMedia = true,
                        pinned = false,
                        uploadedAt = (post.createdAt?.seconds ?: 0L) * 1000L
                    )
                }
            )
        }
    }

    // Creates one media tile with preview and menu actions.
    private fun createMediaTile(media: ProfileMediaItem, compact: Boolean): View {
        val size = if (compact) 96.dp() else 220.dp()
        val frame = FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_media_gradient)
            setOnClickListener {
                (requireActivity() as MainActivity).openMediaViewer(media.item.url, media.item.mediaType)
            }
            layoutParams = LinearLayout.LayoutParams(
                if (compact) size else LinearLayout.LayoutParams.MATCH_PARENT,
                size
            ).apply {
                rightMargin = if (compact) 10.dp() else 0
                bottomMargin = if (compact) 0 else 12.dp()
            }
        }

        if (media.item.mediaType == MEDIA_TYPE_VIDEO) {
            frame.addView(createVideoLabel(compact))
        } else {
            val imageView = ImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            frame.addView(imageView)
            Glide.with(this)
                .load(media.item.url)
                .centerCrop()
                .into(imageView)
        }

        if (media.item.pinned) {
            frame.addView(createPinnedLabel())
        }

        frame.addView(createMediaMenuButton(media))
        return frame
    }

    // Creates the visual placeholder for video media.
    private fun createVideoLabel(compact: Boolean): TextView {
        return TextView(requireContext()).apply {
            text = if (compact) "VIDEO" else "Video"
            gravity = android.view.Gravity.CENTER
            setTextColor(requireContext().getColor(R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = if (compact) 12f else 18f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Creates the small pinned badge on a media tile.
    private fun createPinnedLabel(): TextView {
        return TextView(requireContext()).apply {
            text = "PINNED"
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.START or android.view.Gravity.TOP
            ).apply {
                leftMargin = 8.dp()
                topMargin = 8.dp()
            }
        }
    }

    // Creates the three-dot media menu button.
    private fun createMediaMenuButton(media: ProfileMediaItem): Button {
        return Button(requireContext()).apply {
            text = "..."
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                38.dp(),
                34.dp(),
                android.view.Gravity.END or android.view.Gravity.TOP
            ).apply {
                rightMargin = 8.dp()
                topMargin = 8.dp()
            }
            setOnClickListener { view ->
                showMediaMenu(view, media)
            }
        }
    }

    // Opens the media actions menu for one item.
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

    // Persists one media metadata change and reloads the profile posts.
    private fun updateMediaItem(media: ProfileMediaItem, pinned: Boolean, visibleInMedia: Boolean) {
        val updatedItems = media.allItems.map { item ->
            if (item.id == media.item.id) {
                item.copy(pinned = pinned, visibleInMedia = visibleInMedia)
            } else {
                item
            }
        }

        postRepository.updatePostMediaItems(
            postId = media.postId,
            mediaItems = updatedItems,
            onSuccess = {
                currentUser?.uid?.let { loadOwnPosts(it) }
            },
            onFailure = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Splits comma-separated text into a list.
    private fun commaList(value: String): List<String> {
        return value.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class ComposerMode(val postType: String) {
        REGULAR("regular"),
        DANCE_ACTIVITY("dance_activity"),
        COLLABORATION("collaboration")
    }

    private data class ProfileMediaItem(
        val postId: String,
        val item: PostMediaItem,
        val allItems: List<PostMediaItem>
    )

    private companion object {
        const val MEDIA_TYPE_NONE = "none"
        const val MEDIA_TYPE_PHOTO = "photo"
        const val MEDIA_TYPE_VIDEO = "video"
        const val MEDIA_TYPE_MEDIA = "media"
        const val MEDIA_STRIP_LIMIT = 8
    }
}

// Converts dp units to pixels.
private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
