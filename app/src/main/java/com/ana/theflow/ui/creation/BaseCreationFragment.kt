package com.ana.theflow.ui.creation

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.bumptech.glide.Glide

abstract class BaseCreationFragment : Fragment() {

    protected val postRepository = PostRepository()
    protected val authRepository = AuthRepository()
    protected val userRepository = UserRepository()
    protected val storageRepository = StorageRepository()
    protected val activityTrackingRepository = ActivityTrackingRepository()

    private var currentUser: User? = null
    private var pendingMediaUri: Uri? = null
    private var pendingMediaType: String = MEDIA_TYPE_NONE
    private lateinit var publishButton: Button
    private lateinit var authorName: TextView
    private lateinit var authorAvatar: ImageView
    private lateinit var mediaPreview: FrameLayout
    private lateinit var mediaPreviewImage: ImageView
    private lateinit var mediaPreviewLabel: TextView

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingMediaUri = uri
        pendingMediaType = resolveMediaType(uri)
        renderMediaPreview()
    }

    abstract val titleText: String
    abstract val publishButtonText: String
    abstract val publishSuccessMessage: String
    abstract fun buildFields(parent: LinearLayout)
    abstract fun buildPayload(): CreatePayload?

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ScrollView(requireContext()).apply {
            setBackgroundColor(requireContext().getColor(R.color.flow_background))
            clipToPadding = false
            setPadding(18.dp(), 8.dp(), 18.dp(), 88.dp())
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(header())
                addView(composerCard())
            })
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCurrentUser()
    }

    protected fun openMediaPicker() {
        mediaPicker.launch("*/*")
    }

    protected fun field(hint: String, minHeightDp: Int = 52, multiLine: Boolean = false): EditText {
        return EditText(requireContext()).apply {
            this.hint = hint
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = minHeightDp.dp()
            gravity = if (multiLine) Gravity.TOP else Gravity.CENTER_VERTICAL
            inputType = if (multiLine) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_CLASS_TEXT
            setPadding(14.dp(), if (multiLine) 12.dp() else 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10.dp()
            }
        }
    }

    protected fun optionButton(textValue: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = textValue
            isAllCaps = false
            minWidth = 0
            minHeight = 0
            setTextColor(context.getColor(R.color.flow_brand))
            textSize = 12f
            setBackgroundResource(R.drawable.bg_flow_button_secondary)
            setPadding(12.dp(), 0, 12.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                40.dp()
            ).apply {
                rightMargin = 8.dp()
                bottomMargin = 8.dp()
            }
            setOnClickListener { onClick() }
        }
    }

    protected fun sectionTitle(textValue: String): TextView {
        return TextView(requireContext()).apply {
            text = textValue
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16.dp(), 0, 2.dp())
        }
    }

    protected fun joinedDetails(vararg pairs: Pair<String, String>): String {
        return pairs
            .mapNotNull { (label, value) -> value.trim().ifBlank { null }?.let { "$label: $it" } }
            .joinToString("\n")
    }

    protected fun mediaType(): String = pendingMediaType

    private fun header(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                52.dp()
            )

            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_arrow_back_24)
                setColorFilter(context.getColor(R.color.flow_ink))
                setBackgroundResource(R.drawable.bg_flow_icon_button)
                contentDescription = "Back"
                setOnClickListener { parentFragmentManager.popBackStack() }
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
            })

            addView(TextView(context).apply {
                text = titleText
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 12.dp()
                }
            })

            publishButton = Button(context).apply {
                text = publishButtonText
                isAllCaps = false
                minWidth = 0
                setTextColor(context.getColor(R.color.flow_surface))
                setBackgroundResource(R.drawable.bg_flow_button_primary)
                setPadding(18.dp(), 0, 18.dp(), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 42.dp())
                setOnClickListener { publish() }
            }
            addView(publishButton)
        }
    }

    private fun composerCard(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_flow_card)
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
            addView(authorRow())
            buildFields(this)
            addView(mediaPreviewView())
        }
    }

    private fun authorRow(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply {
                authorAvatar = this
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(48.dp(), 48.dp()).apply {
                    rightMargin = 10.dp()
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    authorName = this
                    text = "Dancer"
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = "Create for THE FLOW"
                    setTextColor(context.getColor(R.color.flow_text_muted))
                    textSize = 12f
                    setPadding(0, 2.dp(), 0, 0)
                })
            })
        }
    }

    private fun mediaPreviewView(): View {
        mediaPreview = FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_flow_media)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                210.dp()
            ).apply {
                topMargin = 12.dp()
            }
        }
        mediaPreviewImage = ImageView(requireContext()).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        mediaPreviewLabel = TextView(requireContext()).apply {
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        mediaPreview.addView(mediaPreviewImage)
        mediaPreview.addView(mediaPreviewLabel)
        return mediaPreview
    }

    private fun renderMediaPreview() {
        val uri = pendingMediaUri
        mediaPreview.visibility = if (uri == null) View.GONE else View.VISIBLE
        if (uri == null) return
        if (pendingMediaType == MEDIA_TYPE_VIDEO) {
            mediaPreviewImage.setImageResource(android.R.color.transparent)
            mediaPreviewLabel.text = "Video selected"
        } else {
            mediaPreviewImage.setImageURI(uri)
            mediaPreviewLabel.text = ""
        }
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        userRepository.getUserByUid(
            uid = uid,
            onSuccess = { user ->
                currentUser = user
                val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
                authorName.text = fullName
                if (user.profileImageUrl.isNotBlank()) {
                    Glide.with(this).load(user.profileImageUrl).circleCrop().into(authorAvatar)
                }
            },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load your profile."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun publish() {
        val user = currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "User is not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val payload = buildPayload() ?: return
        publishButton.isEnabled = false
        postRepository.createPost(
            author = user,
            text = payload.text,
            mediaType = mediaType(),
            postType = payload.postType,
            visibility = payload.visibility,
            activityType = payload.activityType,
            activityLocation = payload.activityLocation,
            activityDate = payload.activityDate,
            activityTime = payload.activityTime,
            activityPrice = payload.activityPrice,
            activityLevel = payload.activityLevel,
            activityDescription = payload.activityDescription,
            activityCapacity = payload.activityCapacity,
            collaborationLookingFor = payload.collaborationLookingFor,
            collaborationStyle = payload.collaborationStyle,
            collaborationLocation = payload.collaborationLocation,
            collaborationDate = payload.collaborationDate,
            collaborationPaid = payload.collaborationPaid,
            collaborationDescription = payload.collaborationDescription,
            onSuccess = { postId -> uploadMediaIfNeeded(postId, user, payload.text) },
            onFailure = { error ->
                publishButton.isEnabled = true
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not publish this."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun uploadMediaIfNeeded(postId: String, user: User, text: String) {
        val uri = pendingMediaUri
        if (uri == null) {
            finishCreate(postId, user, text)
            return
        }
        storageRepository.uploadPostMedia(
            postId = postId,
            mediaUri = uri,
            fileName = "creation_${System.currentTimeMillis()}",
            mediaType = pendingMediaType,
            onSuccess = { finishCreate(postId, user, text) },
            onFailure = { error ->
                publishButton.isEnabled = true
                Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not upload this media."), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun finishCreate(postId: String, user: User, text: String) {
        activityTrackingRepository.trackCreatePost(postId, user.role, text)
        Toast.makeText(requireContext(), publishSuccessMessage, Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    private fun resolveMediaType(uri: Uri?): String {
        if (uri == null) return MEDIA_TYPE_NONE
        val type = requireContext().contentResolver.getType(uri).orEmpty()
        return if (type.startsWith("video/")) MEDIA_TYPE_VIDEO else MEDIA_TYPE_PHOTO
    }

    data class CreatePayload(
        val text: String,
        val postType: String = POST_TYPE_REGULAR,
        val visibility: String = "public",
        val activityType: String = "",
        val activityLocation: String = "",
        val activityDate: String = "",
        val activityTime: String = "",
        val activityPrice: String = "",
        val activityLevel: String = "",
        val activityDescription: String = "",
        val activityCapacity: Long = 0,
        val collaborationLookingFor: String = "",
        val collaborationStyle: String = "",
        val collaborationLocation: String = "",
        val collaborationDate: String = "",
        val collaborationPaid: String = "",
        val collaborationDescription: String = ""
    )

    companion object {
        const val POST_TYPE_REGULAR = "regular"
        const val POST_TYPE_DANCE_ACTIVITY = "dance_activity"
        const val POST_TYPE_COLLABORATION = "collaboration"
        const val MEDIA_TYPE_NONE = "none"
        const val MEDIA_TYPE_PHOTO = "photo"
        const val MEDIA_TYPE_VIDEO = "video"
    }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
