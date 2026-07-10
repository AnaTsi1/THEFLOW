package com.ana.theflow.ui.media

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ana.theflow.databinding.FragmentMediaViewerBinding
import com.bumptech.glide.Glide

class MediaViewerFragment : Fragment() {

    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding!!

    // Creates and returns the fragment view.
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Connects the screen UI after the view is ready.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val url = requireArguments().getString(ARG_URL).orEmpty()
        val mediaType = requireArguments().getString(ARG_MEDIA_TYPE).orEmpty()
        binding.mediaViewerBTNBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        if (mediaType == MEDIA_TYPE_VIDEO) {
            showVideo(url)
        } else {
            showPhoto(url)
        }
    }

    // Shows a photo in the viewer.
    private fun showPhoto(url: String) {
        binding.mediaViewerIMGPhoto.visibility = View.VISIBLE
        binding.mediaViewerVIDEO.visibility = View.GONE
        Glide.with(this)
            .load(url)
            .fitCenter()
            .into(binding.mediaViewerIMGPhoto)
    }

    // Shows and starts a video in the viewer.
    private fun showVideo(url: String) {
        binding.mediaViewerIMGPhoto.visibility = View.GONE
        binding.mediaViewerVIDEO.visibility = View.VISIBLE
        binding.mediaViewerVIDEO.setVideoURI(Uri.parse(url))
        binding.mediaViewerVIDEO.setOnPreparedListener { player ->
            player.isLooping = true
            binding.mediaViewerVIDEO.start()
        }
    }

    // Clears the fragment binding when the view is destroyed.
    override fun onDestroyView() {
        binding.mediaViewerVIDEO.stopPlayback()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "ARG_URL"
        private const val ARG_MEDIA_TYPE = "ARG_MEDIA_TYPE"
        private const val MEDIA_TYPE_VIDEO = "video"

        // Creates a viewer for one media url.
        fun newInstance(url: String, mediaType: String): MediaViewerFragment {
            return MediaViewerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_MEDIA_TYPE, mediaType)
                }
            }
        }
    }
}
