package com.academicflow.service.ingestion

import java.math.BigDecimal

enum class DocumentKind {
    PDF, DOCX, DOC, XLSX, XLS, CSV, TXT, IMAGE, UNKNOWN
}

enum class ProcessingStatus {
    UPLOADED,
    VALIDATING,
    EXTRACTING,
    OCR_REQUIRED,
    OCR_PROCESSING,
    EXTRACTED,
    LOW_CONFIDENCE,
    REVIEW_REQUIRED,
    FAILED,
    COMPLETED
}

data class ExtractedField(
    val key: String,
    val value: String,
    val source: String? = null,
    val confidence: String = "MEDIUM" // HIGH | MEDIUM | LOW | LOW_CONFIDENCE
)

data class RawExtraction(
    val text: String,
    val method: String,
    val fields: List<ExtractedField> = emptyList(),
    val topics: List<String> = emptyList(),
    val outcomes: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val unmapped: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val ocrRequired: Boolean = false,
    val passwordProtected: Boolean = false
)

data class IngestionResult(
    val kind: DocumentKind,
    val detectedContentType: String,
    val extension: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val safeFileName: String,
    val processingStatus: ProcessingStatus,
    val extractionMethod: String?,
    val processingMessage: String?,
    val errorCode: DocumentErrorCode?,
    val confidence: BigDecimal,
    val needsReview: Boolean,
    val text: String,
    val topics: List<String>,
    val outcomes: List<String>,
    val prerequisites: List<String>,
    val fields: List<ExtractedField>,
    val unmapped: Map<String, String>,
    val warnings: List<String>,
    val rawJson: String
)
