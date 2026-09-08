package com.academicflow.service.ingestion

import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Document ingestion pipeline:
 * validate → detect → extract (with OCR fallback) → normalize → confidence.
 * Upload success is separate from extraction success.
 */
@Service
class DocumentIngestionService {

    private val pdfText = PdfTextExtractor()
    private val pdfOcr = PdfOcrExtractor()
    private val docx = DocxExtractor()
    private val sheet = SpreadsheetExtractor()
    private val csv = CsvExtractor()
    private val text = TextExtractor()
    private val image = ImageOcrExtractor()

    fun ingest(originalFileName: String?, declaredContentType: String?, bytes: ByteArray): IngestionResult {
        if (bytes.isEmpty()) {
            throw DocumentIngestionException(DocumentErrorCode.MISSING_FILE, "Empty outline file")
        }
        if (bytes.size.toLong() > DocumentValidator.MAX_BYTES) {
            throw DocumentIngestionException(
                DocumentErrorCode.FILE_TOO_LARGE,
                "Outline exceeds ${DocumentValidator.MAX_BYTES / (1024 * 1024)} MB limit"
            )
        }

        val safeName = DocumentValidator.sanitizeFileName(originalFileName)
        val (kind, detectedMime) = DocumentValidator.detectKind(safeName, declaredContentType, bytes)
        val checksum = DocumentValidator.sha256(bytes)
        val ext = DocumentValidator.extensionOf(safeName)

        val raw = try {
            extractWithFallback(kind, bytes, safeName)
        } catch (e: DocumentIngestionException) {
            return IngestionResult(
                kind = kind,
                detectedContentType = detectedMime,
                extension = ext,
                fileSizeBytes = bytes.size.toLong(),
                checksumSha256 = checksum,
                safeFileName = safeName,
                processingStatus = ProcessingStatus.FAILED,
                extractionMethod = null,
                processingMessage = e.message,
                errorCode = e.code,
                confidence = BigDecimal.ZERO,
                needsReview = true,
                text = "",
                topics = emptyList(),
                outcomes = emptyList(),
                prerequisites = emptyList(),
                fields = emptyList(),
                unmapped = emptyMap(),
                warnings = listOf(e.message ?: e.code.name),
                rawJson = """{"error":"${e.code}"}"""
            )
        }

        if (raw.passwordProtected) {
            return IngestionResult(
                kind = kind,
                detectedContentType = detectedMime,
                extension = ext,
                fileSizeBytes = bytes.size.toLong(),
                checksumSha256 = checksum,
                safeFileName = safeName,
                processingStatus = ProcessingStatus.REVIEW_REQUIRED,
                extractionMethod = raw.method,
                processingMessage = raw.warnings.firstOrNull(),
                errorCode = DocumentErrorCode.PDF_PASSWORD_PROTECTED,
                confidence = BigDecimal.ZERO,
                needsReview = true,
                text = "",
                topics = emptyList(),
                outcomes = emptyList(),
                prerequisites = emptyList(),
                fields = emptyList(),
                unmapped = emptyMap(),
                warnings = raw.warnings,
                rawJson = """{"passwordProtected":true}"""
            )
        }

        val (topics, outcomes, prereqs) = OutlineSectionParser.parse(raw.text)
        val fields = OutlineSectionParser.extractIdentityFields(raw.text) + raw.fields
        val confidence = when {
            raw.text.isBlank() -> BigDecimal("0.05")
            raw.method.contains("OCR") -> OutlineSectionParser.confidence(raw.text, topics, outcomes, prereqs)
                .min(BigDecimal("0.55"))
            else -> OutlineSectionParser.confidence(raw.text, topics, outcomes, prereqs)
        }

        val status = when {
            raw.ocrRequired && raw.text.isBlank() -> ProcessingStatus.OCR_REQUIRED
            raw.ocrRequired -> ProcessingStatus.LOW_CONFIDENCE
            confidence < BigDecimal("0.60") -> ProcessingStatus.LOW_CONFIDENCE
            confidence < BigDecimal("0.75") -> ProcessingStatus.REVIEW_REQUIRED
            else -> ProcessingStatus.EXTRACTED
        }

        val message = when (status) {
            ProcessingStatus.OCR_REQUIRED ->
                "Document stored. Scanned/image content detected — OCR processing required."
            ProcessingStatus.LOW_CONFIDENCE ->
                "Document stored. Extraction confidence is low — chairperson review required."
            ProcessingStatus.REVIEW_REQUIRED ->
                "Document stored. Some extracted information should be reviewed."
            ProcessingStatus.EXTRACTED ->
                "Document analyzed successfully."
            else -> raw.warnings.firstOrNull()
        }

        val warnings = raw.warnings.toMutableList()
        if (confidence < BigDecimal("0.60")) {
            warnings += "Low-confidence extraction — chairperson should review before matching."
        }
        if (topics.isEmpty() && raw.text.isNotBlank()) warnings += "No topics detected automatically."

        val rawJson = buildString {
            append("{")
            append("\"method\":\"").append(raw.method).append("\",")
            append("\"topics\":").append(topics.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" }).append(",")
            append("\"outcomes\":").append(outcomes.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" }).append(",")
            append("\"prerequisites\":").append(prereqs.joinToString(",", "[", "]") { "\"${it.replace("\"", "'")}\"" }).append(",")
            append("\"unmappedKeys\":").append(raw.unmapped.keys.joinToString(",", "[", "]") { "\"$it\"" })
            append("}")
        }

        return IngestionResult(
            kind = kind,
            detectedContentType = detectedMime,
            extension = ext,
            fileSizeBytes = bytes.size.toLong(),
            checksumSha256 = checksum,
            safeFileName = safeName,
            processingStatus = status,
            extractionMethod = raw.method,
            processingMessage = message,
            errorCode = when (status) {
                ProcessingStatus.OCR_REQUIRED -> DocumentErrorCode.OCR_REQUIRED
                ProcessingStatus.LOW_CONFIDENCE -> DocumentErrorCode.LOW_EXTRACTION_CONFIDENCE
                else -> null
            },
            confidence = confidence,
            needsReview = status != ProcessingStatus.EXTRACTED && status != ProcessingStatus.COMPLETED,
            text = raw.text.take(50000),
            topics = topics,
            outcomes = outcomes,
            prerequisites = prereqs,
            fields = fields,
            unmapped = raw.unmapped,
            warnings = warnings.distinct(),
            rawJson = rawJson
        )
    }

    private fun extractWithFallback(kind: DocumentKind, bytes: ByteArray, fileName: String): RawExtraction {
        return when (kind) {
            DocumentKind.PDF -> {
                val primary = pdfText.extract(bytes, fileName)
                if (primary.passwordProtected) return primary
                if (primary.ocrRequired) {
                    val ocr = pdfOcr.extract(bytes, fileName)
                    if (ocr.text.isNotBlank()) {
                        ocr.copy(warnings = (primary.warnings + ocr.warnings).distinct())
                    } else {
                        primary.copy(
                            warnings = (primary.warnings + ocr.warnings).distinct(),
                            ocrRequired = true
                        )
                    }
                } else primary
            }
            DocumentKind.DOCX -> docx.extract(bytes, fileName)
            DocumentKind.XLSX, DocumentKind.XLS -> sheet.extract(bytes, fileName)
            DocumentKind.CSV -> csv.extract(bytes, fileName)
            DocumentKind.TXT -> text.extract(bytes, fileName)
            DocumentKind.IMAGE -> image.extract(bytes, fileName)
            DocumentKind.DOC -> throw DocumentIngestionException(
                DocumentErrorCode.UNSUPPORTED_FILE_TYPE,
                "Legacy .doc is not fully supported. Please upload DOCX or PDF."
            )
            DocumentKind.UNKNOWN -> throw DocumentIngestionException(
                DocumentErrorCode.UNSUPPORTED_FILE_TYPE,
                "Unsupported document type"
            )
        }
    }
}
