package com.ana.theflow.ui.creation

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.content.DialogInterface
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ana.theflow.MainActivity
import com.ana.theflow.R
import com.ana.theflow.data.model.user.User
import com.ana.theflow.data.repository.ActivityTrackingRepository
import com.ana.theflow.data.repository.AuthRepository
import com.ana.theflow.data.repository.PostRepository
import com.ana.theflow.data.repository.StorageRepository
import com.ana.theflow.data.repository.UserRepository
import com.ana.theflow.ui.common.UiText
import com.ana.theflow.utilities.CityOptions
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EventCreationFragment : DialogFragment() {

    private val postRepository = PostRepository()
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val storageRepository = StorageRepository()
    private val activityTrackingRepository = ActivityTrackingRepository()

    private var currentUser: User? = null
    private var pendingCoverUri: Uri? = null
    private var isCreating = false
    private var hasEndDateTime = false
    private var scheduleType = ScheduleType.ONE_TIME
    private var showScheduleDetails = false
    private var showLocationDetails = false
    private var showDanceDetails = false
    private var showRegistrationDetails = false
    private var showAdditionalDetails = false

    private lateinit var root: LinearLayout
    private lateinit var coverPreview: ImageView
    private lateinit var coverFallback: TextView
    private lateinit var removeCoverButton: Button
    private lateinit var organizerAvatar: ImageView
    private lateinit var organizerName: TextView
    private lateinit var organizerRole: TextView
    private lateinit var nameField: EditText
    private lateinit var startDateField: EditText
    private lateinit var startTimeField: EditText
    private lateinit var endDateField: EditText
    private lateinit var endTimeField: EditText
    private lateinit var endDateTimeContainer: LinearLayout
    private lateinit var timezoneLabel: TextView
    private lateinit var scheduleOptions: LinearLayout
    private lateinit var scheduleDetailsContainer: LinearLayout
    private lateinit var recurrenceContainer: LinearLayout
    private lateinit var locationDetailsContainer: LinearLayout
    private lateinit var danceDetailsContainer: LinearLayout
    private lateinit var registrationDetailsContainer: LinearLayout
    private lateinit var venueField: EditText
    private lateinit var addressField: EditText
    private lateinit var cityField: EditText
    private lateinit var styleField: EditText
    private lateinit var levelField: EditText
    private lateinit var eventTypeField: EditText
    private lateinit var capacityField: EditText
    private lateinit var descriptionField: EditText
    private lateinit var additionalContainer: LinearLayout
    private lateinit var registrationMethodField: EditText
    private lateinit var externalRegistrationLinkField: EditText
    private lateinit var contactField: EditText
    private lateinit var createButton: Button
    private lateinit var errorText: TextView

    private val coverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingCoverUri = uri
        renderCover()
        updateCreateState()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hideKeyboard()
                    requestClose()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            hasEndDateTime = it.getBoolean(KEY_HAS_END)
            scheduleType = ScheduleType.valueOf(it.getString(KEY_SCHEDULE, ScheduleType.ONE_TIME.name))
            showScheduleDetails = it.getBoolean(KEY_SHOW_SCHEDULE)
            showLocationDetails = it.getBoolean(KEY_SHOW_LOCATION)
            showDanceDetails = it.getBoolean(KEY_SHOW_DANCE)
            showRegistrationDetails = it.getBoolean(KEY_SHOW_REGISTRATION)
            showAdditionalDetails = it.getBoolean(KEY_SHOW_ADDITIONAL)
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                hideKeyboard()
                requestClose()
            }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_event_create_modal)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(topBar())
        root.addView(ScrollView(requireContext()).apply {
            isFillViewport = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18.dp(), 12.dp(), 18.dp(), 18.dp())
                addView(coverArea())
                addView(organizerRow())
                buildFields(this)
            })
        })
        root.addView(bottomBar())
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        restoreText(savedInstanceState)
        renderSchedule()
        renderProgressiveSections()
        renderCover()
        loadCurrentUser()
        updateCreateState()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.42f)
            val metrics = resources.displayMetrics
            val isTablet = metrics.widthPixels / metrics.density >= 600
            val width = if (isTablet) 640.dp() else (metrics.widthPixels - 24.dp()).coerceAtLeast(280.dp())
            val height = (metrics.heightPixels * if (isTablet) 0.88f else 0.94f).toInt()
            setLayout(width, height)
            setGravity(Gravity.CENTER)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_END, hasEndDateTime)
        outState.putString(KEY_SCHEDULE, scheduleType.name)
        outState.putBoolean(KEY_SHOW_SCHEDULE, showScheduleDetails)
        outState.putBoolean(KEY_SHOW_LOCATION, showLocationDetails)
        outState.putBoolean(KEY_SHOW_DANCE, showDanceDetails)
        outState.putBoolean(KEY_SHOW_REGISTRATION, showRegistrationDetails)
        outState.putBoolean(KEY_SHOW_ADDITIONAL, showAdditionalDetails)
        outState.putString(KEY_NAME, nameField.text.toString())
        outState.putString(KEY_START_DATE, startDateField.text.toString())
        outState.putString(KEY_START_TIME, startTimeField.text.toString())
        outState.putString(KEY_END_DATE, endDateField.text.toString())
        outState.putString(KEY_END_TIME, endTimeField.text.toString())
        outState.putString(KEY_VENUE, venueField.text.toString())
        outState.putString(KEY_ADDRESS, addressField.text.toString())
        outState.putString(KEY_CITY, cityField.text.toString())
        outState.putString(KEY_STYLE, styleField.text.toString())
        outState.putString(KEY_LEVEL, levelField.text.toString())
        outState.putString(KEY_TYPE, eventTypeField.text.toString())
        outState.putString(KEY_CAPACITY, capacityField.text.toString())
        outState.putString(KEY_DESCRIPTION, descriptionField.text.toString())
        outState.putString(KEY_CONTACT, contactField.text.toString())
        outState.putString(KEY_REGISTRATION_METHOD, registrationMethodField.text.toString())
        outState.putString(KEY_EXTERNAL_REGISTRATION_LINK, externalRegistrationLinkField.text.toString())
    }

    private fun topBar(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp(), 10.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 60.dp())
            addView(ImageButton(context).apply {
                setImageResource(R.drawable.ic_close_24)
                setColorFilter(context.getColor(R.color.flow_ink))
                setBackgroundResource(R.drawable.bg_flow_icon_button)
                contentDescription = context.getString(R.string.action_cancel)
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp())
                setOnClickListener { requestClose() }
            })
            addView(TextView(context).apply {
                text = "Create Event"
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 21f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 12.dp() }
            })
        }
    }

    private fun coverArea(): View {
        return FrameLayout(requireContext()).apply {
            setBackgroundResource(R.drawable.bg_flow_media)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 168.dp()).apply { bottomMargin = 12.dp() }
            coverPreview = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            coverFallback = TextView(context).apply {
                text = "Event cover"
                gravity = Gravity.CENTER
                setTextColor(context.getColor(R.color.flow_brand))
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            addView(coverPreview)
            addView(coverFallback)
            addView(Button(context).apply {
                text = "Add photo"
                isAllCaps = false
                setTextColor(context.getColor(R.color.flow_brand))
                setBackgroundResource(R.drawable.bg_flow_button_secondary)
                layoutParams = FrameLayout.LayoutParams(118.dp(), 40.dp(), Gravity.BOTTOM or Gravity.START).apply {
                    leftMargin = 12.dp()
                    bottomMargin = 12.dp()
                }
                setOnClickListener { coverPicker.launch("image/*") }
            })
            removeCoverButton = Button(context).apply {
                text = "Remove"
                isAllCaps = false
                setTextColor(context.getColor(R.color.flow_error))
                setBackgroundResource(R.drawable.bg_flow_button_secondary)
                layoutParams = FrameLayout.LayoutParams(104.dp(), 40.dp(), Gravity.BOTTOM or Gravity.END).apply {
                    rightMargin = 12.dp()
                    bottomMargin = 12.dp()
                }
                setOnClickListener {
                    pendingCoverUri = null
                    renderCover()
                    updateCreateState()
                }
            }
            addView(removeCoverButton)
        }
    }

    private fun organizerRow(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            setBackgroundResource(R.drawable.bg_flow_card)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12.dp() }
            addView(ImageView(context).apply {
                organizerAvatar = this
                setBackgroundResource(R.drawable.bg_avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(44.dp(), 44.dp()).apply { rightMargin = 10.dp() }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    organizerName = this
                    text = "Dancer"
                    setTextColor(context.getColor(R.color.flow_ink))
                    textSize = 15f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    organizerRole = this
                    text = "Created by this account"
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(0, 2.dp(), 0, 0)
                })
            })
        }
    }

    private fun buildFields(parent: LinearLayout) {
        nameField = field("Event name")
        parent.addView(nameField)
        parent.addView(section("Schedule type"))
        scheduleOptions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        parent.addView(scheduleOptions)
        parent.addView(section("Date and time"))
        parent.addView(horizontalRow(
            pickerField("Start date") { showDatePicker(startDateField) }.also { startDateField = it },
            pickerField("Start time") { showTimePicker(startTimeField) }.also { startTimeField = it }
        ))
        parent.addView(expandableRow("Schedule", ::scheduleSummary) {
            showScheduleDetails = !showScheduleDetails
            renderProgressiveSections()
        })
        scheduleDetailsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            timezoneLabel = TextView(context).apply {
                text = "Time zone: ${TimeZone.getDefault().id}"
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                setPadding(4.dp(), 4.dp(), 0, 0)
            }
            addView(timezoneLabel)
            addView(optionButton("Add end date and time") {
                hasEndDateTime = !hasEndDateTime
                renderSchedule()
            })
            endDateTimeContainer = horizontalRow(
                pickerField("End date") { showDatePicker(endDateField) }.also { endDateField = it },
                pickerField("End time") { showTimePicker(endTimeField) }.also { endTimeField = it }
            )
            addView(endDateTimeContainer)
            recurrenceContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = "Recurring details are stored as readable event notes until first-class recurrence fields are added."
                    setTextColor(context.getColor(R.color.flow_text_secondary))
                    textSize = 12f
                    setPadding(4.dp(), 6.dp(), 4.dp(), 2.dp())
                })
            }
            addView(recurrenceContainer)
        }
        parent.addView(scheduleDetailsContainer)

        parent.addView(section("Location"))
        venueField = field("Venue or studio")
        addressField = field("Address")
        cityField = AutoCompleteTextView(requireContext()).apply {
            hint = "City"
            setTextColor(context.getColor(R.color.flow_ink))
            setHintTextColor(context.getColor(R.color.flow_text_muted))
            setBackgroundResource(R.drawable.bg_flow_input)
            minHeight = 52.dp()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 0, 14.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        }
        CityOptions.configureCitySelector(requireContext(), cityField as AutoCompleteTextView)
        parent.addView(cityField)
        parent.addView(expandableRow("Location details", ::locationSummary) {
            showLocationDetails = !showLocationDetails
            renderProgressiveSections()
        })
        locationDetailsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(venueField)
            addView(addressField)
        }
        parent.addView(locationDetailsContainer)

        parent.addView(expandableRow("Dance details", ::danceSummary) {
            showDanceDetails = !showDanceDetails
            renderProgressiveSections()
        })
        danceDetailsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            eventTypeField = selectionField("Event type", eventTypes)
            styleField = selectionField("Dance style", danceStyles)
            levelField = selectionField("Level", danceLevels)
            addView(eventTypeField)
            addView(styleField)
            addView(levelField)
        }
        parent.addView(danceDetailsContainer)

        parent.addView(expandableRow("Registration", ::registrationSummary) {
            showRegistrationDetails = !showRegistrationDetails
            renderProgressiveSections()
        })
        registrationDetailsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            registrationMethodField = selectionField("Registration method", registrationMethods)
            externalRegistrationLinkField = field("External registration link").apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            capacityField = field("Capacity").apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(5))
            }
            addView(registrationMethodField)
            addView(externalRegistrationLinkField)
            addView(capacityField)
        }
        parent.addView(registrationDetailsContainer)

        parent.addView(compactInfoRow("Who can see this event?", "Public", "Public event visibility is the supported Firestore state for event discovery."))
        parent.addView(expandableRow("Additional details", ::additionalSummary) {
            showAdditionalDetails = !showAdditionalDetails
            renderProgressiveSections()
        })
        additionalContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            contactField = field("Contact information")
            descriptionField = field("Description", minHeightDp = 104, multiLine = true)
            addView(contactField)
            addView(descriptionField)
        }
        parent.addView(additionalContainer)
        listOf(nameField, startDateField, startTimeField, endDateField, endTimeField, venueField, addressField, cityField, styleField, levelField, eventTypeField, capacityField, registrationMethodField, externalRegistrationLinkField, descriptionField, contactField)
            .forEach { it.addTextChangedListener(validationWatcher) }
    }

    private fun bottomBar(): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 10.dp(), 18.dp(), 16.dp())
            setBackgroundColor(context.getColor(R.color.flow_surface))
            errorText = TextView(context).apply {
                setTextColor(context.getColor(R.color.flow_error))
                textSize = 12f
                visibility = View.GONE
                setPadding(0, 0, 0, 8.dp())
            }
            createButton = Button(context).apply {
                text = "Create Event"
                isAllCaps = false
                setTextColor(context.getColor(R.color.white))
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_24, 0, 0, 0)
                compoundDrawableTintList = ColorStateList.valueOf(context.getColor(R.color.white))
                compoundDrawablePadding = 8.dp()
                setBackgroundResource(R.drawable.bg_flow_button_primary)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 50.dp())
                setOnClickListener { createEvent() }
            }
            addView(errorText)
            addView(createButton)
        }
    }

    private fun loadCurrentUser() {
        val uid = authRepository.getCurrentUserUid() ?: return
        userRepository.getUserByUid(
            uid,
            onSuccess = { user ->
                if (!isAdded) return@getUserByUid
                currentUser = user
                organizerName.text = "${user.firstName} ${user.lastName}".trim().ifBlank { "Dancer" }
                organizerRole.text = "${user.role.cleanDisplayValue().ifBlank { "Dancer" }} - created by this account"
                if (user.profileImageUrl.isNotBlank()) Glide.with(this).load(user.profileImageUrl).circleCrop().into(organizerAvatar)
                updateCreateState()
            },
            onFailure = { error -> Toast.makeText(requireContext(), UiText.friendlyError(error, "We could not load your profile."), Toast.LENGTH_SHORT).show() }
        )
    }

    private fun createEvent() {
        val user = currentUser ?: return showError("Your profile is still loading.")
        val validation = validationMessage()
        if (validation != null) return showError(validation)
        if (isCreating) return
        isCreating = true
        createButton.isEnabled = false
        createButton.text = "Creating..."
        val location = listOf(venueField.textString(), addressField.textString(), cityField.textString()).filter { it.isNotBlank() }.joinToString(", ")
        val description = buildDescription()
        postRepository.createPost(
            author = user,
            text = nameField.textString(),
            mediaType = if (pendingCoverUri == null) BaseCreationFragment.MEDIA_TYPE_NONE else BaseCreationFragment.MEDIA_TYPE_PHOTO,
            postType = BaseCreationFragment.POST_TYPE_DANCE_ACTIVITY,
            visibility = "public",
            activityType = nameField.textString(),
            activityLocation = location,
            activityDate = startDateField.textString(),
            activityTime = startTimeField.textString(),
            activityPrice = "",
            activityLevel = levelField.textString(),
            activityDescription = description,
            activityCapacity = capacityField.textString().toLongOrNull() ?: 0L,
            onSuccess = { postId -> uploadCoverIfNeeded(postId, user) },
            onFailure = { failCreate(it, "We could not create this event.") }
        )
    }

    private fun uploadCoverIfNeeded(postId: String, user: User) {
        val uri = pendingCoverUri
        if (uri == null) {
            finishCreate(postId, user)
            return
        }
        storageRepository.uploadPostMedia(
            postId = postId,
            mediaUri = uri,
            fileName = "event_cover_${System.currentTimeMillis()}",
            mediaType = BaseCreationFragment.MEDIA_TYPE_PHOTO,
            onLoading = { loading ->
                if (isAdded) createButton.text = if (loading) "Uploading..." else "Create Event"
            },
            onSuccess = { finishCreate(postId, user) },
            onFailure = { failCreate(it, "The event was created, but the cover upload failed.") }
        )
    }

    private fun finishCreate(postId: String, user: User) {
        activityTrackingRepository.trackCreatePost(postId, user.role, nameField.textString())
        Toast.makeText(requireContext(), "Event created", Toast.LENGTH_SHORT).show()
        dismissAllowingStateLoss()
        (activity as? MainActivity)?.openPost(postId)
    }

    private fun failCreate(raw: String, fallback: String) {
        isCreating = false
        createButton.text = "Create Event"
        updateCreateState()
        showError(UiText.friendlyError(raw, fallback))
    }

    private fun buildDescription(): String {
        return listOf(
            descriptionField.textString(),
            "Schedule: ${scheduleType.label}",
            if (hasEndDateTime) "Ends: ${endDateField.textString()} ${endTimeField.textString()}".trim() else "",
            eventTypeField.textString().ifBlank { null }?.let { "Event type: $it" },
            styleField.textString().ifBlank { null }?.let { "Dance style: $it" },
            registrationMethodField.textString().ifBlank { null }?.let { "Registration: $it" },
            externalRegistrationLinkField.textString().ifBlank { null }?.let { "Registration link: $it" },
            contactField.textString().ifBlank { null }?.let { "Contact: $it" }
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun validationMessage(): String? {
        if (currentUser == null) return "Your profile is still loading."
        if (nameField.textString().isBlank()) return "Add an event name."
        if (startDateField.textString().isBlank()) return "Add a start date."
        if (startTimeField.textString().isBlank()) return "Add a start time."
        if (CityOptions.normalizeOptionalCity(cityField.textString()) == null) return "Choose a city from the list."
        if (isPastStart()) return "Choose a future start date."
        if (hasEndDateTime && !isEndAfterStart()) return "End date and time must be after the start."
        return null
    }

    private fun updateCreateState() {
        if (!::createButton.isInitialized) return
        val validation = validationMessage()
        val valid = validation == null && !isCreating
        createButton.isEnabled = valid
        createButton.alpha = if (valid) 1f else 0.48f
        if (!isCreating && validation != null) {
            errorText.text = validation
            errorText.visibility = View.VISIBLE
        } else if (!isCreating) {
            errorText.visibility = View.GONE
        }
    }

    private fun renderSchedule() {
        if (!::scheduleOptions.isInitialized) return
        endDateTimeContainer.visibility = if (hasEndDateTime || scheduleType == ScheduleType.DATE_RANGE) View.VISIBLE else View.GONE
        recurrenceContainer.visibility = if (scheduleType == ScheduleType.RECURRING) View.VISIBLE else View.GONE
        scheduleOptions.removeAllViews()
        ScheduleType.entries.forEach { type ->
            scheduleOptions.addView(optionButton(type.label) {
                scheduleType = type
                if (type == ScheduleType.DATE_RANGE) {
                    hasEndDateTime = true
                    showScheduleDetails = true
                }
                if (type == ScheduleType.RECURRING) showScheduleDetails = true
                renderSchedule()
                updateCreateState()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 42.dp()).apply {
                    topMargin = 8.dp()
                }
                setTextColor(context.getColor(if (scheduleType == type) R.color.white else R.color.flow_brand))
                setBackgroundResource(if (scheduleType == type) R.drawable.bg_flow_button_primary else R.drawable.bg_flow_button_secondary)
            })
        }
        renderProgressiveSections()
    }

    private fun renderProgressiveSections() {
        if (!::scheduleDetailsContainer.isInitialized) return
        scheduleDetailsContainer.visibility = if (showScheduleDetails) View.VISIBLE else View.GONE
        locationDetailsContainer.visibility = if (showLocationDetails) View.VISIBLE else View.GONE
        danceDetailsContainer.visibility = if (showDanceDetails) View.VISIBLE else View.GONE
        registrationDetailsContainer.visibility = if (showRegistrationDetails) View.VISIBLE else View.GONE
        additionalContainer.visibility = if (showAdditionalDetails) View.VISIBLE else View.GONE
    }

    private fun renderCover() {
        val uri = pendingCoverUri
        coverFallback.visibility = if (uri == null) View.VISIBLE else View.GONE
        removeCoverButton.visibility = if (uri == null) View.GONE else View.VISIBLE
        if (uri == null) {
            coverPreview.setImageDrawable(null)
        } else {
            coverPreview.setImageURI(uri)
        }
    }

    private fun requestClose() {
        if (!hasUnsavedChanges()) {
            dismissAllowingStateLoss()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Discard event?")
            .setMessage("Your event details have not been saved.")
            .setNegativeButton("Keep editing", null)
            .setPositiveButton("Discard") { _, _ -> dismissAllowingStateLoss() }
            .show()
    }

    private fun hasUnsavedChanges(): Boolean {
        if (pendingCoverUri != null) return true
        return listOf(nameField, startDateField, startTimeField, endDateField, endTimeField, venueField, addressField, cityField, styleField, levelField, eventTypeField, capacityField, registrationMethodField, externalRegistrationLinkField, descriptionField, contactField)
            .any { it.textString().isNotBlank() }
    }

    private fun restoreText(state: Bundle?) {
        if (state == null) return
        nameField.setText(state.getString(KEY_NAME, ""))
        startDateField.setText(state.getString(KEY_START_DATE, ""))
        startTimeField.setText(state.getString(KEY_START_TIME, ""))
        endDateField.setText(state.getString(KEY_END_DATE, ""))
        endTimeField.setText(state.getString(KEY_END_TIME, ""))
        venueField.setText(state.getString(KEY_VENUE, ""))
        addressField.setText(state.getString(KEY_ADDRESS, ""))
        cityField.setText(state.getString(KEY_CITY, ""))
        styleField.setText(state.getString(KEY_STYLE, ""))
        levelField.setText(state.getString(KEY_LEVEL, ""))
        eventTypeField.setText(state.getString(KEY_TYPE, ""))
        capacityField.setText(state.getString(KEY_CAPACITY, ""))
        descriptionField.setText(state.getString(KEY_DESCRIPTION, ""))
        contactField.setText(state.getString(KEY_CONTACT, ""))
        registrationMethodField.setText(state.getString(KEY_REGISTRATION_METHOD, ""))
        externalRegistrationLinkField.setText(state.getString(KEY_EXTERNAL_REGISTRATION_LINK, ""))
    }

    private fun isPastStart(): Boolean {
        val date = parseDateTime(startDateField.textString(), startTimeField.textString()) ?: return false
        return date.time < System.currentTimeMillis() - 60_000L
    }

    private fun isEndAfterStart(): Boolean {
        val start = parseDateTime(startDateField.textString(), startTimeField.textString()) ?: return false
        val end = parseDateTime(endDateField.textString(), endTimeField.textString()) ?: return false
        return end.after(start)
    }

    private fun parseDateTime(date: String, time: String) =
        runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse("$date $time") }.getOrNull()

    private fun showDatePicker(target: EditText) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            target.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(target: EditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            target.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, minute))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun hideKeyboard() {
        val view = dialog?.currentFocus ?: root
        (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showError(text: String) {
        errorText.text = text
        errorText.visibility = View.VISIBLE
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    private fun field(hint: String, minHeightDp: Int = 52, multiLine: Boolean = false) = EditText(requireContext()).apply {
        this.hint = hint
        setTextColor(context.getColor(R.color.flow_ink))
        setHintTextColor(context.getColor(R.color.flow_text_muted))
        setBackgroundResource(R.drawable.bg_flow_input)
        minHeight = minHeightDp.dp()
        gravity = if (multiLine) Gravity.TOP else Gravity.CENTER_VERTICAL
        inputType = if (multiLine) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
        setPadding(14.dp(), if (multiLine) 12.dp() else 0, 14.dp(), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
    }

    private fun pickerField(hint: String, onPick: () -> Unit) = field(hint).apply {
        isFocusable = false
        isClickable = true
        setOnClickListener { onPick() }
    }

    private fun selectionField(hint: String, options: List<String>) = pickerField(hint) {
        showChoicePicker(hint, options) { selected ->
            when (hint) {
                "Event type" -> eventTypeField.setText(selected)
                "Dance style" -> styleField.setText(selected)
                "Level" -> levelField.setText(selected)
                "Registration method" -> registrationMethodField.setText(selected)
            }
        }
    }

    private fun showChoicePicker(title: String, options: List<String>, onSelected: (String) -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which -> onSelected(options[which]) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun horizontalRow(left: View, right: View) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 6.dp() })
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 6.dp() })
    }

    private fun optionButton(textValue: String, onClick: () -> Unit) = Button(requireContext()).apply {
        text = textValue
        isAllCaps = false
        minWidth = 0
        setTextColor(context.getColor(R.color.flow_brand))
        setBackgroundResource(R.drawable.bg_flow_button_secondary)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 40.dp()).apply {
            rightMargin = 8.dp()
            topMargin = 8.dp()
        }
        setOnClickListener { onClick() }
    }

    private fun expandableRow(title: String, summary: () -> String, onClick: () -> Unit) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundResource(R.drawable.bg_flow_card)
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = title
                setTextColor(context.getColor(R.color.flow_ink))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = summary()
                setTextColor(context.getColor(R.color.flow_text_secondary))
                textSize = 12f
                setPadding(0, 3.dp(), 0, 0)
            })
        })
        addView(TextView(context).apply {
            text = ">"
            setTextColor(context.getColor(R.color.flow_brand))
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        setOnClickListener { onClick() }
    }

    private fun compactInfoRow(title: String, value: String, description: String) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.bg_flow_card)
        setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 10.dp() }
        addView(TextView(context).apply {
            text = title
            setTextColor(context.getColor(R.color.flow_ink))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(context).apply {
            text = value
            setTextColor(context.getColor(R.color.flow_brand))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 4.dp(), 0, 0)
        })
        addView(TextView(context).apply {
            text = description
            setTextColor(context.getColor(R.color.flow_text_secondary))
            textSize = 12f
            setPadding(0, 3.dp(), 0, 0)
        })
    }

    private fun section(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(context.getColor(R.color.flow_ink))
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, 16.dp(), 0, 2.dp())
    }

    private fun scheduleSummary(): String {
        return if (hasEndDateTime) "${scheduleType.label} / end time set" else scheduleType.label
    }

    private fun locationSummary(): String {
        return listOf(venueField.textString(), addressField.textString()).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Venue and address are optional" }
    }

    private fun danceSummary(): String {
        if (!::eventTypeField.isInitialized || !::styleField.isInitialized || !::levelField.isInitialized) {
            return "Optional dance metadata"
        }
        return listOf(eventTypeField.textString(), styleField.textString(), levelField.textString()).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "Optional dance metadata" }
    }

    private fun registrationSummary(): String {
        if (!::registrationMethodField.isInitialized || !::capacityField.isInitialized) {
            return "Optional registration settings"
        }
        return listOf(registrationMethodField.textString(), capacityField.textString().ifBlank { null }?.let { "$it spots" }).filterNotNull().joinToString(" / ").ifBlank { "Optional registration settings" }
    }

    private fun additionalSummary(): String {
        if (!::contactField.isInitialized || !::descriptionField.isInitialized) {
            return "Description and contact are optional"
        }
        return listOf(contactField.textString(), descriptionField.textString().ifBlank { null }?.let { "Description added" }).filterNotNull().joinToString(" / ").ifBlank { "Description and contact are optional" }
    }

    private val validationWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            errorText.visibility = View.GONE
            updateCreateState()
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private enum class ScheduleType(val label: String) {
        ONE_TIME("One-time event"),
        RECURRING("Recurring event"),
        DATE_RANGE("Date-range event")
    }

    companion object {
        const val TAG = "EventCreationFragment"
        private const val KEY_HAS_END = "has_end"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_SHOW_SCHEDULE = "show_schedule"
        private const val KEY_SHOW_LOCATION = "show_location"
        private const val KEY_SHOW_DANCE = "show_dance"
        private const val KEY_SHOW_REGISTRATION = "show_registration"
        private const val KEY_SHOW_ADDITIONAL = "show_additional"
        private const val KEY_NAME = "name"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_END_DATE = "end_date"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_VENUE = "venue"
        private const val KEY_ADDRESS = "address"
        private const val KEY_CITY = "city"
        private const val KEY_STYLE = "style"
        private const val KEY_LEVEL = "level"
        private const val KEY_TYPE = "type"
        private const val KEY_CAPACITY = "capacity"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_CONTACT = "contact"
        private const val KEY_REGISTRATION_METHOD = "registration_method"
        private const val KEY_EXTERNAL_REGISTRATION_LINK = "external_registration_link"
        private val eventTypes = listOf("Class", "Workshop", "Social", "Audition", "Performance", "Battle", "Intensive")
        private val danceStyles = listOf("Hip Hop", "Heels", "Salsa", "Contemporary", "Afro", "Ballet", "Jazz", "Bachata")
        private val danceLevels = listOf("Beginner", "Intermediate", "Advanced", "Professional", "Open Level", "All levels")
        private val registrationMethods = listOf("No registration", "Internal registration", "External link", "Contact organizer")
    }
}

private fun EditText.textString(): String = text?.toString()?.trim().orEmpty()

private fun String.cleanDisplayValue(): String {
    return trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase(Locale.getDefault()).replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
            }
        }
}

private fun Int.dp(): Int {
    return (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
