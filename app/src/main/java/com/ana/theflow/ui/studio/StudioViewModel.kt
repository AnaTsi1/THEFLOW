package com.ana.theflow.ui.studio

import androidx.lifecycle.ViewModel
import com.ana.theflow.data.model.studio.Studio
import com.ana.theflow.data.repository.StudioRepository

class StudioViewModel : ViewModel() {

    private val studioRepository = StudioRepository()

    // Searches verified studios by location and style.
    fun searchStudios(
        location: String,
        danceStyle: String,
        onSuccess: (List<Studio>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        studioRepository.searchStudios(
            location = location,
            danceStyle = danceStyle,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    // Creates a pending studio page.
    fun createStudioPage(
        studio: Studio,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        studioRepository.createStudioPage(
            studio = studio,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }
}
