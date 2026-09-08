package com.academicflow.service.ingestion

import java.security.MessageDigest
import java.util.Locale

object DocumentValidator {
    const val MAX_BYTES = 20L * 1024 * 1024

    private val allowedExt = setOf(
        "pdf", "docx", "doc", "xlsx", "xls", "csv", "tsv", "txt", "md",
        "png", "jpg", "jpeg", "webp"
    )

    fun sanitizeFileName(original: String?): String {
        val base = (original ?: "upload").substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("""[^\w.\- ()\[\]]+"""), "_").trim().take(200)
        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
            throw DocumentIngestionException(DocumentErrorCode.PATH_TRAVERSAL, "Unsafe filename rejected")
        }
        if (base.contains("..")) {
            throw DocumentIngestionException(DocumentErrorCode.PATH_TRAVERSAL, "Path traversal in filename rejected")
        }
        return cleaned.ifBlank { "upload.bin" }
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun detectKind(fileName: String, declaredContentType: String?, bytes: ByteArray): Pair<DocumentKind, String> {
        val ext = extensionOf(fileName)
        val magic = sniff(bytes)
        val declared = (declaredContentType ?: "").lowercase(Locale.ROOT)

        val kind = when {
            magic == DocumentKind.PDF || ext == "pdf" || declared.contains("pdf") -> DocumentKind.PDF
            magic == DocumentKind.DOCX || ext == "docx" || declared.contains("wordprocessingml") -> DocumentKind.DOCX
            magic == DocumentKind.XLSX || ext == "xlsx" || declared.contains("spreadsheetml") -> DocumentKind.XLSX
            magic == DocumentKind.DOC || ext == "doc" || declared == "application/msword" -> DocumentKind.DOC
            magic == DocumentKind.XLS || ext == "xls" || declared == "application/vnd.ms-excel" -> DocumentKind.XLS
            magic == DocumentKind.IMAGE || ext in setOf("png", "jpg", "jpeg", "webp") || declared.startsWith("image/") -> DocumentKind.IMAGE
            ext in setOf("csv", "tsv") || declared.contains("csv") || declared.contains("tab-separated") -> DocumentKind.CSV
            ext in setOf("txt", "md") || declared.startsWith("text/") -> DocumentKind.TXT
            else -> DocumentKind.UNKNOWN
        }

        if (kind == DocumentKind.UNKNOWN || (ext.isNotBlank() && ext !in allowedExt && kind != DocumentKind.IMAGE)) {
            throw DocumentIngestionException(
                DocumentErrorCode.UNSUPPORTED_FILE_TYPE,
                "Unsupported document type. Use PDF, DOCX, XLSX/XLS, CSV, TXT, or PNG/JPG."
            )
        }

        // Extension vs magic mismatch for high-risk cases
        if (ext == "pdf" && magic != null && magic != DocumentKind.PDF) {
            throw DocumentIngestionException(
                DocumentErrorCode.CONTENT_MISMATCH,
                "File extension .pdf does not match detected content type"
            )
        }
        if (ext in setOf("png", "jpg", "jpeg") && magic != null && magic != DocumentKind.IMAGE && magic != DocumentKind.PDF) {
            throw DocumentIngestionException(
                DocumentErrorCode.CONTENT_MISMATCH,
                "Image extension does not match detected content"
            )
        }

        val detectedMime = when (kind) {
            DocumentKind.PDF -> "application/pdf"
            DocumentKind.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            DocumentKind.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            DocumentKind.DOC -> "application/msword"
            DocumentKind.XLS -> "application/vnd.ms-excel"
            DocumentKind.CSV -> "text/csv"
            DocumentKind.TXT -> "text/plain"
            DocumentKind.IMAGE -> when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            DocumentKind.UNKNOWN -> declaredContentType ?: "application/octet-stream"
        }
        return kind to detectedMime
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Magic-byte sniff; null if inconclusive. */
    private fun sniff(bytes: ByteArray): DocumentKind? {
        if (bytes.size < 4) return null
        // PDF
        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
            return DocumentKind.PDF
        }
        // PNG
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return DocumentKind.IMAGE
        }
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return DocumentKind.IMAGE
        // ZIP-based (docx/xlsx) — PK
        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val asString = String(bytes.copyOfRange(0, minOf(bytes.size, 2000)), Charsets.ISO_8859_1)
            return when {
                asString.contains("word/") -> DocumentKind.DOCX
                asString.contains("xl/") -> DocumentKind.XLSX
                else -> null
            }
        }
        // OLE compound (doc/xls)
        if (bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte()) {
            return null // ambiguous DOC/XLS — rely on extension
        }
        return null
    }
}
