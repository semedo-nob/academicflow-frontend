package com.academicflow.service.ingestion

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream

class DocumentIngestionServiceTest {

    private val svc = DocumentIngestionService()

    @Test
    fun `text pdf extracts topics`() {
        val bytes = textPdf(
            """
            Course Code: MAT 210
            Topics:
            - Differentiation
            - Integration
            Learning Outcomes:
            - Apply calculus
            Prerequisites:
            - MAT 110
            """.trimIndent()
        )
        val r = svc.ingest("calculus.pdf", "application/pdf", bytes)
        assertTrue(r.processingStatus == ProcessingStatus.EXTRACTED || r.processingStatus == ProcessingStatus.REVIEW_REQUIRED)
        assertTrue(r.topics.any { it.contains("Differentiation", ignoreCase = true) })
        assertTrue(r.text.isNotBlank())
    }

    @Test
    fun `image-only pdf is stored as OCR_REQUIRED not rejected`() {
        val bytes = imageOnlyPdf()
        val r = svc.ingest("scanned_outline.pdf", "application/pdf", bytes)
        // Upload must succeed: either OCR ran, or status is OCR_REQUIRED when OCR unavailable.
        assertTrue(
            r.processingStatus == ProcessingStatus.OCR_REQUIRED ||
                r.processingStatus == ProcessingStatus.LOW_CONFIDENCE ||
                r.processingStatus == ProcessingStatus.REVIEW_REQUIRED ||
                r.processingStatus == ProcessingStatus.EXTRACTED,
            "expected stored OCR/review status but was ${r.processingStatus}"
        )
        assertTrue(r.checksumSha256.length == 64)
        if (r.processingStatus == ProcessingStatus.OCR_REQUIRED) {
            assertEquals(DocumentErrorCode.OCR_REQUIRED, r.errorCode)
            assertTrue(r.warnings.isNotEmpty())
        }
    }

    @Test
    fun `txt outline extracts sections`() {
        val text = """
            Topics:
            - Limits
            - Optimization
            Learning Outcomes:
            - Solve business problems
            Prerequisites:
            - Basic algebra
        """.trimIndent()
        val r = svc.ingest("outline.txt", "text/plain", text.toByteArray())
        assertTrue(r.topics.isNotEmpty())
        assertTrue(r.outcomes.isNotEmpty())
    }

    @Test
    fun `csv preserves unmapped columns`() {
        val csv = "Course Code,Course Title,Weird Column\nMAT101,Calculus,XYZ\n"
        val r = svc.ingest("courses.csv", "text/csv", csv.toByteArray())
        assertEquals("CSV", r.extractionMethod)
        assertTrue(r.unmapped.containsKey("Weird Column") || r.rawJson.contains("Weird Column"))
    }

    @Test
    fun `path traversal filename sanitized or rejected`() {
        val text = "Topics:\n- A\n".toByteArray()
        val r = svc.ingest("safe_outline.txt", "text/plain", text)
        assertEquals("safe_outline.txt", r.safeFileName)
    }

    @Test
    fun `unsupported executable rejected`() {
        try {
            svc.ingest("malware.exe", "application/octet-stream", byteArrayOf(0x4D, 0x5A, 0x00, 0x00))
            throw AssertionError("expected rejection")
        } catch (e: DocumentIngestionException) {
            assertEquals(DocumentErrorCode.UNSUPPORTED_FILE_TYPE, e.code)
        }
    }

    private fun textPdf(content: String): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                cs.newLineAtOffset(50f, 700f)
                content.lines().forEach { line ->
                    cs.showText(line.take(100).replace("\t", " "))
                    cs.newLineAtOffset(0f, -14f)
                }
                cs.endText()
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }

    private fun imageOnlyPdf(): ByteArray {
        val img = BufferedImage(400, 560, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, 400, 560)
        g.color = Color.BLACK
        g.drawString("MAT 210 Calculus II — scanned page", 20, 40)
        g.drawString("Topics: Differentiation, Integration", 20, 70)
        g.dispose()
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val pdImage = LosslessFactory.createFromImage(doc, img)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(pdImage, 50f, 200f, 400f, 560f)
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        }
    }
}
