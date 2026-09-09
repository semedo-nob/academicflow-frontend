package com.academicflow.service.permission

/**
 * Allocation board workflow is separate from capability permissions.
 * Capability = may the user ever do X; workflow = may they do X on this record now.
 */
object AllocationWorkflow {
    const val DRAFT = "DRAFT"
    const val SUBMITTED = "SUBMITTED"
    const val UNDER_REVIEW = "UNDER_REVIEW"
    const val CHANGES_REQUESTED = "CHANGES_REQUESTED"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"

    fun canComment(status: String): Boolean =
        status.uppercase() in setOf(DRAFT, SUBMITTED, UNDER_REVIEW, CHANGES_REQUESTED)

    fun canReview(status: String): Boolean =
        status.uppercase() in setOf(SUBMITTED, UNDER_REVIEW, CHANGES_REQUESTED)

    fun canRequestChanges(status: String): Boolean =
        status.uppercase() in setOf(SUBMITTED, UNDER_REVIEW)

    fun canApprove(status: String): Boolean =
        status.uppercase() in setOf(SUBMITTED, UNDER_REVIEW, CHANGES_REQUESTED)

    fun canReject(status: String): Boolean = canApprove(status)

    fun canEdit(status: String): Boolean =
        status.uppercase() in setOf(DRAFT, CHANGES_REQUESTED)

    fun canSubmit(status: String): Boolean =
        status.uppercase() in setOf(DRAFT, CHANGES_REQUESTED)
}
