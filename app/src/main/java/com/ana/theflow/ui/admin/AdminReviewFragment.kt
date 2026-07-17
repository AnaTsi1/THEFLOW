package com.ana.theflow.ui.admin

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ana.theflow.R
import com.ana.theflow.data.model.professional.ProfessionalApplication
import com.ana.theflow.data.model.studio.StudioClaim
import com.ana.theflow.data.repository.AdminRepository
import com.ana.theflow.databinding.FragmentAdminReviewBinding
import com.ana.theflow.utilities.Constants

class AdminReviewFragment : Fragment() {

    private var _binding: FragmentAdminReviewBinding? = null
    private val binding get() = _binding!!
    private val adminRepository = AdminRepository()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.adminBTNBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.adminBTNRefresh.setOnClickListener {
            loadPendingReviews()
        }
        loadPendingReviews()
    }

    private fun loadPendingReviews() {
        setLoading(true)
        binding.adminLAYStudioClaims.removeAllViews()
        binding.adminLAYProfessionalApplications.removeAllViews()

        adminRepository.loadPendingReviews(
            onSuccess = { data ->
                setLoading(false)
                renderStudioClaims(data.studioClaims)
                renderProfessionalApplications(data.professionalApplications)

                val total = data.studioClaims.size + data.professionalApplications.size
                val statusMessage = if (total == 0) {
                    "No pending requests right now."
                } else {
                    "$total pending request${if (total == 1) "" else "s"} waiting for review."
                }
                binding.adminLBLMessage.text = listOf(
                    statusMessage,
                    data.warnings.joinToString("\n")
                ).filter { it.isNotBlank() }.joinToString("\n\n")
                binding.adminLBLMessage.visibility = View.VISIBLE
            },
            onFailure = { error ->
                setLoading(false)
                binding.adminLBLMessage.text = error
                binding.adminLBLMessage.visibility = View.VISIBLE
            }
        )
    }

    private fun renderStudioClaims(claims: List<StudioClaim>) {
        if (claims.isEmpty()) {
            binding.adminLAYStudioClaims.addView(emptyText("No pending studio claims."))
            return
        }

        claims.forEach { claim ->
            binding.adminLAYStudioClaims.addView(
                reviewCard(
                    title = claim.studioName.ifBlank { "Studio claim" },
                    body = listOf(
                        "Requester: ${claim.requesterName.ifBlank { claim.requesterEmail }}",
                        "Email: ${claim.requesterEmail}",
                        "Why: ${claim.justification.ifBlank { "Not provided" }}",
                        "Verification: ${claim.verificationDetails.ifBlank { "Not provided" }}"
                    ).joinToString("\n"),
                    onApprove = {
                        setLoading(true)
                        adminRepository.approveStudioClaim(
                            claim = claim,
                            onSuccess = {
                                Toast.makeText(requireContext(), "Studio claim approved", Toast.LENGTH_SHORT).show()
                                loadPendingReviews()
                            },
                            onFailure = ::showActionError
                        )
                    },
                    onReject = {
                        setLoading(true)
                        adminRepository.rejectStudioClaim(
                            claim = claim,
                            onSuccess = {
                                Toast.makeText(requireContext(), "Studio claim rejected", Toast.LENGTH_SHORT).show()
                                loadPendingReviews()
                            },
                            onFailure = ::showActionError
                        )
                    }
                )
            )
        }
    }

    private fun renderProfessionalApplications(applications: List<ProfessionalApplication>) {
        if (applications.isEmpty()) {
            binding.adminLAYProfessionalApplications.addView(emptyText("No pending professional applications."))
            return
        }

        applications.forEach { application ->
            binding.adminLAYProfessionalApplications.addView(
                reviewCard(
                    title = application.requestedDisplayName.ifBlank { applicationTypeLabel(application.applicationType) },
                    body = listOf(
                        "Type: ${applicationTypeLabel(application.applicationType)}",
                        "Applicant UID: ${application.applicantUid}",
                        "Documents: ${if (application.documents.isEmpty()) "None" else application.documents.joinToString(", ")}"
                    ).joinToString("\n"),
                    onApprove = {
                        setLoading(true)
                        adminRepository.approveProfessionalApplication(
                            application = application,
                            onSuccess = {
                                Toast.makeText(requireContext(), "Application approved", Toast.LENGTH_SHORT).show()
                                loadPendingReviews()
                            },
                            onFailure = ::showActionError
                        )
                    },
                    onReject = {
                        setLoading(true)
                        adminRepository.rejectProfessionalApplication(
                            application = application,
                            onSuccess = {
                                Toast.makeText(requireContext(), "Application rejected", Toast.LENGTH_SHORT).show()
                                loadPendingReviews()
                            },
                            onFailure = ::showActionError
                        )
                    }
                )
            )
        }
    }

    private fun reviewCard(
        title: String,
        body: String,
        onApprove: () -> Unit,
        onReject: () -> Unit
    ): View {
        val context = requireContext()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        card.addView(TextView(context).apply {
            text = title
            setTextColor(requireContext().getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(TextView(context).apply {
            text = body
            setTextColor(requireContext().getColor(R.color.text_secondary))
            textSize = 14f
            setLineSpacing(3f, 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        })

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        actions.addView(Button(context).apply {
            text = "Approve"
            setTextColor(context.getColor(R.color.text_primary))
            setTypeface(null, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_button_primary)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            setOnClickListener { onApprove() }
        })

        actions.addView(Button(context).apply {
            text = "Reject"
            setTextColor(context.getColor(R.color.text_primary))
            setBackgroundResource(R.drawable.bg_button_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = dp(10)
            }
            setOnClickListener { onReject() }
        })

        card.addView(actions)
        return card
    }

    private fun emptyText(message: String): TextView {
        return TextView(requireContext()).apply {
            text = message
            setTextColor(requireContext().getColor(R.color.text_muted))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun applicationTypeLabel(type: String): String {
        return when {
            type.equals(Constants.ProfessionalApplicationType.VERIFIED_TEACHER.firestoreValue, ignoreCase = true) -> "Verified Teacher"
            type.equals(Constants.ProfessionalApplicationType.CHOREOGRAPHER.firestoreValue, ignoreCase = true) -> "Choreographer"
            else -> "Studio / Dance School"
        }
    }

    private fun showActionError(error: String) {
        setLoading(false)
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.adminProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
