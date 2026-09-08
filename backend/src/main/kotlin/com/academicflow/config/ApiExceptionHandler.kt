package com.academicflow.config

import com.academicflow.service.ingestion.DocumentErrorCode
import com.academicflow.service.ingestion.DocumentIngestionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class ApiExceptionHandler {

    data class ApiError(
        val code: String,
        val message: String,
        val status: Int
    )

    @ExceptionHandler(DocumentIngestionException::class)
    fun handleDoc(ex: DocumentIngestionException): ResponseEntity<ApiError> {
        val status = when (ex.code) {
            DocumentErrorCode.UNAUTHORIZED -> HttpStatus.FORBIDDEN
            DocumentErrorCode.MISSING_COURSE_CONTEXT -> HttpStatus.NOT_FOUND
            DocumentErrorCode.FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(
            ApiError(ex.code.name, ex.message ?: ex.code.name, status.value())
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(ex: ResponseStatusException): ResponseEntity<ApiError> {
        val status = HttpStatus.valueOf(ex.statusCode.value())
        val msg = ex.reason ?: status.reasonPhrase
        return ResponseEntity.status(status).body(
            ApiError(status.name, msg, status.value())
        )
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError(
                DocumentErrorCode.MISSING_FILE.name,
                "Missing upload part '${ex.requestPartName}'. Send multipart field 'file'.",
                400
            )
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(@Suppress("UNUSED_PARAMETER") ex: MaxUploadSizeExceededException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ApiError(DocumentErrorCode.FILE_TOO_LARGE.name, "Uploaded file exceeds server size limit", 413)
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegal(ex: IllegalArgumentException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError("INVALID_REQUEST", ex.message ?: "Invalid request", 400)
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError("INVALID_STATE", ex.message ?: "Invalid state", 400)
        )
}
