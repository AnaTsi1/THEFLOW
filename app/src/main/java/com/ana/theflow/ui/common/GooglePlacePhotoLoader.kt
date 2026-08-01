// Loads Google Place photos for Discover cards without persisting external images. Fetches the
// first available photo per place and caches it for the current process.
package com.ana.theflow.ui.common

import android.content.Context
import android.net.Uri
import android.text.Html
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.ana.theflow.BuildConfig
import com.bumptech.glide.Glide
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest

object GooglePlacePhotoLoader {
    private const val CARD_PHOTO_WIDTH = 640
    private const val CARD_PHOTO_HEIGHT = 360

    private val uriCache = mutableMapOf<String, Uri>()
    private val attributionCache = mutableMapOf<String, String>()
    private val pendingPlaceIds = mutableSetOf<String>()

    // Loads a card-sized photo for a Google Place. Failures leave the existing placeholder visible.
    // onPhotoLoaded fires only once a real photo is actually about to be shown, so callers can hide
    // their own type-label placeholder exactly then - not before, and not on failure.
    fun load(
        context: Context,
        placeId: String,
        imageView: ImageView,
        attributionView: TextView,
        onPhotoLoaded: () -> Unit = {}
    ) {
        if (placeId.isBlank() || BuildConfig.PLACES_API_KEY.isBlank() || !Places.isInitialized()) return

        imageView.tag = placeId
        uriCache[placeId]?.let { uri ->
            Glide.with(context).load(uri).centerCrop().into(imageView)
            renderAttribution(placeId, attributionView)
            onPhotoLoaded()
            return
        }
        if (pendingPlaceIds.contains(placeId)) return
        pendingPlaceIds.add(placeId)

        val client = Places.createClient(context.applicationContext)
        val placeRequest = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.PHOTO_METADATAS)
        ).build()

        client.fetchPlace(placeRequest)
            .addOnSuccessListener { placeResponse ->
                val metadata = placeResponse.place.photoMetadatas?.firstOrNull()
                if (metadata == null) {
                    pendingPlaceIds.remove(placeId)
                    return@addOnSuccessListener
                }

                val photoRequest = FetchResolvedPhotoUriRequest.builder(metadata)
                    .setMaxWidth(CARD_PHOTO_WIDTH)
                    .setMaxHeight(CARD_PHOTO_HEIGHT)
                    .build()
                client.fetchResolvedPhotoUri(photoRequest)
                    .addOnSuccessListener { photoResponse ->
                        pendingPlaceIds.remove(placeId)
                        val photoUri = photoResponse.uri ?: return@addOnSuccessListener
                        uriCache[placeId] = photoUri
                        attributionCache[placeId] = metadata.attributions.orEmpty()
                        if (imageView.tag == placeId) {
                            Glide.with(context).load(photoUri).centerCrop().into(imageView)
                            renderAttribution(placeId, attributionView)
                            onPhotoLoaded()
                        }
                    }
                    .addOnFailureListener {
                        pendingPlaceIds.remove(placeId)
                    }
            }
            .addOnFailureListener {
                pendingPlaceIds.remove(placeId)
            }
    }

    // Google's attribution string is HTML (e.g. an <a href> crediting the photo's contributor) -
    // it must be parsed, not assigned as a raw string, or the markup shows up as literal text.
    private fun renderAttribution(placeId: String, attributionView: TextView) {
        val attribution = attributionCache[placeId].orEmpty()
        attributionView.text = if (attribution.isBlank()) "" else Html.fromHtml(attribution, Html.FROM_HTML_MODE_LEGACY)
        attributionView.visibility = if (attribution.isBlank()) View.GONE else View.VISIBLE
    }
}
