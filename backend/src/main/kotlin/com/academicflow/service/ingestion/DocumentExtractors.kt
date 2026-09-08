package com.academicflow.service.ingestion

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

interface DocumentExtractor {
    fun supports(kind: DocumentKind): Boolean
    fun extract(bytes: ByteArray, fileName: String): RawExtraction
}

class PdfTextExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.PDF

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        try {
            Loader.loadPDF(bytes).use { doc ->
                if (doc.isEncrypted) {
                    // PDFBox may still open some encrypted docs with empty password
                }
                val text = PDFTextStripper().getText(doc).trim()
                val meaningful = text.replace(Regex("\\s+"), " ").length
                if (meaningful < 40) {
                    return RawExtraction(
                        text = text,
                        method = "PDF_TEXT",
                        warnings = listOf("Little or no extractable text — document may be scanned/image-based"),
                        ocrRequired = true
                    )
                }
                return RawExtraction(text = text, method = "PDF_TEXT")
            }
        } catch (e: InvalidPasswordException) {
            return RawExtraction(
                text = "",
                method = "PDF_TEXT",
                passwordProtected = true,
                warnings = listOf("This PDF is password protected. Please upload an unlocked copy.")
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                return RawExtraction(
                    text = "",
                    method = "PDF_TEXT",
                    passwordProtected = true,
                    warnings = listOf("This PDF is password protected. Please upload an unlocked copy.")
                )
            }
            throw DocumentIngestionException(
                DocumentErrorCode.INVALID_DOCUMENT,
                "Could not open PDF: ${e.message ?: "invalid or corrupted file"}"
            )
        }
    }
}

/**
 * Optional OCR via system `tesseract` CLI. If unavailable, returns OCR_REQUIRED without failing upload.
 */
class PdfOcrExtractor(
    private val maxPages: Int = 12,
    private val dpi: Int = 200
) : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.PDF

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        if (!TesseractCli.available()) {
            return RawExtraction(
                text = "",
                method = "PDF_OCR",
                ocrRequired = true,
                warnings = listOf("Scanned PDF detected. OCR is required but Tesseract is not installed on this server.")
            )
        }
        try {
            Loader.loadPDF(bytes).use { doc ->
                val renderer = PDFRenderer(doc)
                val pages = minOf(doc.numberOfPages, maxPages)
                val sb = StringBuilder()
                for (i in 0 until pages) {
                    val image = renderer.renderImageWithDPI(i, dpi.toFloat(), ImageType.RGB)
                    val pageText = TesseractCli.ocrImage(image)
                    if (pageText.isNotBlank()) {
                        sb.appendLine("--- page ${i + 1} ---")
                        sb.appendLine(pageText)
                    }
                }
                val text = sb.toString().trim()
                if (text.length < 40) {
                    return RawExtraction(
                        text = text,
                        method = "PDF_OCR",
                        ocrRequired = true,
                        warnings = listOf("OCR produced little text — chairperson review required.")
                    )
                }
                return RawExtraction(
                    text = text,
                    method = "PDF_OCR",
                    warnings = listOf("Text obtained via OCR — verify accuracy before relying on extracted requirements.")
                )
            }
        } catch (e: Exception) {
            return RawExtraction(
                text = "",
                method = "PDF_OCR",
                ocrRequired = true,
                warnings = listOf("OCR failed: ${e.message ?: "unknown error"}")
            )
        }
    }
}

class DocxExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.DOCX

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        try {
            XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
                val paras = doc.paragraphs.mapNotNull { it.text?.trim() }.filter { it.isNotEmpty() }
                val tables = doc.tables.flatMap { table ->
                    table.rows.flatMap { row ->
                        row.tableCells.mapNotNull { it.text?.trim() }.filter { it.isNotEmpty() }
                    }
                }
                val text = (paras + tables).joinToString("\n")
                return RawExtraction(text = text, method = "DOCX")
            }
        } catch (e: Exception) {
            throw DocumentIngestionException(DocumentErrorCode.INVALID_DOCUMENT, "Could not read DOCX: ${e.message}")
        }
    }
}

class SpreadsheetExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.XLSX || kind == DocumentKind.XLS

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        try {
            WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
                val fmt = DataFormatter()
                val lines = mutableListOf<String>()
                val unmapped = linkedMapOf<String, String>()
                for (s in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(s) ?: continue
                    lines += "=== Sheet: ${sheet.sheetName} ==="
                    val header = sheet.getRow(sheet.firstRowNum)?.let { row ->
                        (0 until row.lastCellNum).map { fmt.formatCellValue(row.getCell(it)).trim() }
                    } ?: emptyList()
                    for (r in sheet) {
                        val vals = (0 until maxOf(r.lastCellNum.toInt(), header.size)).map {
                            fmt.formatCellValue(r.getCell(it)).trim()
                        }
                        if (vals.all { it.isBlank() }) continue
                        lines += vals.filter { it.isNotBlank() }.joinToString(" | ")
                        if (header.isNotEmpty() && r.rowNum > sheet.firstRowNum) {
                            header.zip(vals).forEach { (h, v) ->
                                if (h.isNotBlank() && v.isNotBlank()) {
                                    val key = "${sheet.sheetName}.$h"
                                    if (!unmapped.containsKey(key)) unmapped[key] = v
                                    else unmapped[key] = unmapped[key] + "; " + v
                                }
                            }
                        }
                    }
                }
                return RawExtraction(
                    text = lines.joinToString("\n"),
                    method = if (wb is HSSFWorkbook) "XLS" else "XLSX",
                    unmapped = unmapped
                )
            }
        } catch (e: Exception) {
            throw DocumentIngestionException(DocumentErrorCode.INVALID_DOCUMENT, "Could not read spreadsheet: ${e.message}")
        }
    }
}

class CsvExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.CSV

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return RawExtraction(text = "", method = "CSV", warnings = listOf("CSV file is empty"))
        }
        val delim = if (lines.first().contains('\t')) '\t' else ','
        val headers = splitCsv(lines.first(), delim)
        val unmapped = linkedMapOf<String, String>()
        lines.drop(1).take(50).forEach { line ->
            val cols = splitCsv(line, delim)
            headers.zip(cols).forEach { (h, v) ->
                if (h.isNotBlank() && v.isNotBlank()) {
                    unmapped[h] = if (unmapped.containsKey(h)) unmapped[h] + "; " + v else v
                }
            }
        }
        return RawExtraction(text = text, method = "CSV", unmapped = unmapped)
    }

    private fun splitCsv(line: String, delim: Char): List<String> =
        line.split(delim).map { it.trim().trim('"') }
}

class TextExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.TXT
    override fun extract(bytes: ByteArray, fileName: String) =
        RawExtraction(text = String(bytes, Charsets.UTF_8), method = "TXT")
}

class ImageOcrExtractor : DocumentExtractor {
    override fun supports(kind: DocumentKind) = kind == DocumentKind.IMAGE

    override fun extract(bytes: ByteArray, fileName: String): RawExtraction {
        if (!TesseractCli.available()) {
            return RawExtraction(
                text = "",
                method = "IMAGE_OCR",
                ocrRequired = true,
                warnings = listOf("Image uploaded. OCR is required but Tesseract is not installed on this server.")
            )
        }
        return try {
            val image = ImageIO.read(ByteArrayInputStream(bytes))
                ?: return RawExtraction(
                    text = "",
                    method = "IMAGE_OCR",
                    ocrRequired = true,
                    warnings = listOf("Could not decode image for OCR")
                )
            val text = TesseractCli.ocrImage(image)
            RawExtraction(
                text = text,
                method = "IMAGE_OCR",
                ocrRequired = text.length < 40,
                warnings = if (text.length < 40) listOf("OCR confidence appears low — review required") else
                    listOf("Text obtained via OCR — verify accuracy.")
            )
        } catch (e: Exception) {
            RawExtraction(
                text = "",
                method = "IMAGE_OCR",
                ocrRequired = true,
                warnings = listOf("Image OCR failed: ${e.message}")
            )
        }
    }
}

object TesseractCli {
    @Volatile private var mode: Mode? = null

    private enum class Mode { LOCAL, DOCKER, NONE }

    private val dockerImage: String
        get() = System.getenv("ACADEMICFLOW_TESSERACT_IMAGE")?.takeIf { it.isNotBlank() } ?: "franky1/tesseract"

    fun available(): Boolean = resolveMode() != Mode.NONE

    fun ocrImage(image: BufferedImage): String {
        val dir = Files.createTempDirectory("af-ocr")
        try {
            val imgFile = dir.resolve("page.png").toFile()
            ImageIO.write(image, "png", imgFile)
            val outBase = dir.resolve("out").toAbsolutePath().toString()
            when (resolveMode()) {
                Mode.LOCAL -> runLocal(imgFile.absolutePath, outBase)
                Mode.DOCKER -> runDocker(dir.toAbsolutePath().toString(), imgFile.name, "out")
                Mode.NONE -> throw IllegalStateException("Tesseract is not available")
                null -> throw IllegalStateException("Tesseract is not available")
            }
            val outFile = dir.resolve("out.txt").toFile()
            return if (outFile.exists()) outFile.readText() else ""
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private fun resolveMode(): Mode {
        mode?.let { return it }
        val resolved = when {
            canRun(listOf("tesseract", "--version")) -> Mode.LOCAL
            canRun(listOf("docker", "image", "inspect", dockerImage)) -> Mode.DOCKER
            else -> Mode.NONE
        }
        mode = resolved
        return resolved
    }

    /** Test helper / admin: clear cached detection after installing tesseract. */
    fun resetAvailabilityCache() {
        mode = null
    }

    private fun canRun(cmd: List<String>): Boolean = try {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        p.waitFor(8, TimeUnit.SECONDS) && p.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private fun runLocal(imagePath: String, outBase: String) {
        val p = ProcessBuilder("tesseract", imagePath, outBase, "-l", "eng")
            .redirectErrorStream(true)
            .start()
        val finished = p.waitFor(180, TimeUnit.SECONDS)
        if (!finished || p.exitValue() != 0) {
            val err = p.inputStream.bufferedReader().readText()
            throw IllegalStateException(err.ifBlank { "tesseract failed" })
        }
    }

    private fun runDocker(hostDir: String, imageName: String, outBaseName: String) {
        // Mount host temp dir so container can read PNG and write out.txt
        val p = ProcessBuilder(
            "docker", "run", "--rm",
            "-v", "$hostDir:$hostDir",
            "-w", hostDir,
            dockerImage,
            "tesseract", "$hostDir/$imageName", "$hostDir/$outBaseName", "-l", "eng"
        ).redirectErrorStream(true).start()
        // Multi-page scanned allocation sheets can take several minutes per page under Docker OCR.
        val finished = p.waitFor(300, TimeUnit.SECONDS)
        if (!finished || p.exitValue() != 0) {
            val err = p.inputStream.bufferedReader().readText()
            throw IllegalStateException(err.ifBlank { "docker tesseract failed" })
        }
    }
}
