package com.academicflow.service.email

data class EmailMessage(
    val to: String,
    val subject: String,
    val htmlBody: String,
    val textBody: String,
    val tags: Map<String, String> = emptyMap()
)

data class EmailSendResult(
    val provider: String,
    val success: Boolean,
    val providerMessageId: String? = null,
    val error: String? = null
)

enum class EmailDeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    BOUNCED,
    FAILED,
    NOT_SENT
}
