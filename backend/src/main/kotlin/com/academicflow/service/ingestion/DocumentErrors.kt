package com.academicflow.service.ingestion

/**
 * Structured document ingestion errors — never map everything to generic HTTP 400.
 */
enum class DocumentErrorCode {
    MISSING_FILE,
    FILE_TOO_LARGE,
    UNSUPPORTED_FILE_TYPE,
    INVALID_DOCUMENT,
    CONTENT_MISMATCH,
    PATH_TRAVERSAL,
    PDF_PASSWORD_PROTECTED,
    EXTRACTION_FAILED,
    OCR_REQUIRED,
    OCR_FAILED,
    LOW_EXTRACTION_CONFIDENCE,
    MISSING_COURSE_CONTEXT,
    UNAUTHORIZED
}

class DocumentIngestionException(
    val code: DocumentErrorCode,
    message: String,
    val httpStatusHint: Int = when (code) {
        DocumentErrorCode.UNAUTHORIZED -> 403
        DocumentErrorCode.MISSING_COURSE_CONTEXT -> 404
        DocumentErrorCode.OCR_REQUIRED,
        DocumentErrorCode.LOW_EXTRACTION_CONFIDENCE -> 200 // informational, not failure
        else -> 400
    }
) : RuntimeException(message)
