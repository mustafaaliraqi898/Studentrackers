package com.studenttracker.app.service

import android.content.Context
import android.net.Uri
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

object ExcelAndBackupService {

    // =========================================================================
    // 1. NATIVE .XLSX WRITER (Zero external heavy dependencies, pure OpenXML ZIP)
    // =========================================================================
    fun buildXlsxBytes(sheetName: String, rows: List<List<String>>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 4. xl/workbook.xml
            val safeSheet = escapeXml(sheetName)
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="$safeSheet" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zos.closeEntry()

            // 5. xl/worksheets/sheet1.xml (with rightToLeft="1" for Arabic RTL)
            val sb = StringBuilder()
            sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sb.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>""")
            sb.append("<sheetData>")

            rows.forEachIndexed { rIdx, rowCells ->
                val rowNum = rIdx + 1
                sb.append("""<row r="$rowNum">""")
                rowCells.forEachIndexed { cIdx, cellVal ->
                    val colLetter = ('A'.code + cIdx).toChar()
                    val cellRef = "$colLetter$rowNum"
                    val escaped = escapeXml(cellVal)
                    sb.append("""<c r="$cellRef" t="inlineStr"><is><t>$escaped</t></is></c>""")
                }
                sb.append("</row>")
            }

            sb.append("</sheetData></worksheet>")
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun generateEmptyStudentTemplateXlsx(): ByteArray {
        val rows = listOf(
            listOf("اسم الطالب", "الصف", "الشعبة"),
            listOf("أحمد علي", "الرابع", "أ"),
            listOf("محمد حسن", "الرابع", "أ"),
            listOf("علي كريم", "الرابع", "ب"),
            listOf("", "", ""),
            listOf("", "", "")
        )
        return buildXlsxBytes("قالب الطلاب", rows)
    }

    fun generateResultsXlsx(grades: List<StudentCalculatedGrade>): ByteArray {
        val rows = mutableListOf<List<String>>()
        rows.add(
            listOf(
                "ت",
                "اسم الطالب",
                "الصف",
                "الشعبة",
                "الحضور",
                "المشاركة",
                "الدفتر",
                "الواجب",
                "السلوك",
                "الاختبار",
                "الدرجة النهائية"
            )
        )
        grades.forEachIndexed { idx, g ->
            rows.add(
                listOf(
                    (idx + 1).toString(),
                    g.studentName,
                    g.className,
                    g.sectionName,
                    GradingService.formatScore(g.attendanceScore),
                    GradingService.formatScore(g.participationScore),
                    GradingService.formatScore(g.notebookScore),
                    GradingService.formatScore(g.homeworkScore),
                    GradingService.formatScore(g.behaviorScore),
                    GradingService.formatScore(g.quizScore),
                    GradingService.formatScore(g.totalScore)
                )
            )
        }
        return buildXlsxBytes("نتائج اليومي", rows)
    }

    // =========================================================================
    // 2. NATIVE .XLSX READER & VALIDATOR (Supports sharedStrings + inlineStr)
    // =========================================================================
    fun parseXlsxRows(inputStream: InputStream): List<List<String>> {
        val entriesMap = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entriesMap[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        val sharedStrings = mutableListOf<String>()
        val sharedBytes = entriesMap["xl/sharedStrings.xml"]
        if (sharedBytes != null) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(sharedBytes.inputStream())
            val siNodes = doc.getElementsByTagName("si")
            for (i in 0 until siNodes.length) {
                val si = siNodes.item(i) as Element
                val tNodes = si.getElementsByTagName("t")
                val sb = StringBuilder()
                for (j in 0 until tNodes.length) {
                    sb.append(tNodes.item(j).textContent ?: "")
                }
                sharedStrings.add(sb.toString())
            }
        }

        val sheetBytes = entriesMap["xl/worksheets/sheet1.xml"]
            ?: entriesMap.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
            ?: return emptyList()

        val sheetDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(sheetBytes.inputStream())
        val rowNodes = sheetDoc.getElementsByTagName("row")
        val matrix = mutableListOf<List<String>>()

        for (r in 0 until rowNodes.length) {
            val rowEl = rowNodes.item(r) as Element
            val cNodes = rowEl.getElementsByTagName("c")
            val rowCells = MutableList(6) { "" }
            for (c in 0 until cNodes.length) {
                val cellEl = cNodes.item(c) as Element
                val ref = cellEl.getAttribute("r") // e.g. A1, B1, C1
                val colIdx = ref.firstOrNull { it.isLetter() }?.let { it.uppercaseChar().code - 'A'.code } ?: c
                val cellType = cellEl.getAttribute("t")

                val valueStr = when (cellType) {
                    "s" -> {
                        val vList = cellEl.getElementsByTagName("v")
                        val idx = if (vList.length > 0) vList.item(0).textContent.toIntOrNull() else null
                        if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                    }
                    "inlineStr" -> {
                        val tList = cellEl.getElementsByTagName("t")
                        if (tList.length > 0) tList.item(0).textContent ?: "" else ""
                    }
                    else -> {
                        val vList = cellEl.getElementsByTagName("v")
                        if (vList.length > 0) vList.item(0).textContent ?: "" else ""
                    }
                }
                if (colIdx in rowCells.indices) {
                    rowCells[colIdx] = valueStr
                }
            }
            matrix.add(rowCells)
        }
        return matrix
    }

    fun importStudentsFromXlsxUri(
        context: Context,
        uri: Uri,
        repository: TrackerRepository
    ): ExcelImportResult {
        val matrix = context.contentResolver.openInputStream(uri)?.use { parseXlsxRows(it) }
            ?: return ExcelImportResult(0, 0, 0, 0, emptyList())

        if (matrix.isEmpty()) {
            return ExcelImportResult(0, 0, 0, 0, emptyList())
        }

        var importedCount = 0
        var duplicateCount = 0
        var emptyRowCount = 0
        var errorCount = 0
        val rowErrors = mutableListOf<ExcelRowError>()

        var classes = repository.getClasses()
        var sections = repository.getSections()

        val firstRow = matrix[0].map { it.trim() }
        val startIdx = if (firstRow.any { it.contains("اسم") || it.contains("الصف") || it.contains("الشعبة") }) 1 else 0

        for (r in startIdx until matrix.size) {
            val rowNum = r + 1
            val cells = matrix[r]
            val rawName = cells.getOrNull(0)?.trim()?.replace(Regex("\\s+"), " ") ?: ""
            val rawClass = cells.getOrNull(1)?.trim()?.replace(Regex("\\s+"), " ") ?: ""
            val rawSection = cells.getOrNull(2)?.trim()?.replace(Regex("\\s+"), " ") ?: ""

            if (rawName.isEmpty() && rawClass.isEmpty() && rawSection.isEmpty()) {
                emptyRowCount++
                continue
            }

            if (rawName.isEmpty()) {
                errorCount++
                rowErrors.add(ExcelRowError(rowNum, "(فارغ)", rawClass, rawSection, "اسم الطالب فارغ"))
                continue
            }
            if (rawClass.isEmpty()) {
                errorCount++
                rowErrors.add(ExcelRowError(rowNum, rawName, "(فارغ)", rawSection, "الصف فارغ"))
                continue
            }
            if (rawSection.isEmpty()) {
                errorCount++
                rowErrors.add(ExcelRowError(rowNum, rawName, rawClass, "(فارغ)", "الشعبة فارغة"))
                continue
            }

            var classObj = classes.find { it.className == rawClass }
            if (classObj == null) {
                val newClsId = repository.addClass(rawClass)
                classes = repository.getClasses()
                classObj = classes.find { it.id == newClsId }
            }
            if (classObj == null) {
                errorCount++
                rowErrors.add(ExcelRowError(rowNum, rawName, rawClass, rawSection, "تعذر إنشاء الصف"))
                continue
            }

            var secObj = sections.find { it.classId == classObj.id && it.sectionName == rawSection }
            if (secObj == null) {
                val newSecId = repository.addSection(classObj.id, rawSection)
                sections = repository.getSections()
                secObj = sections.find { it.id == newSecId }
            }
            if (secObj == null) {
                errorCount++
                rowErrors.add(ExcelRowError(rowNum, rawName, rawClass, rawSection, "تعذر إنشاء الشعبة"))
                continue
            }

            val (_, isDup) = repository.addStudent(rawName, classObj.id, secObj.id)
            if (isDup) {
                duplicateCount++
            } else {
                importedCount++
            }
        }

        return ExcelImportResult(
            importedCount = importedCount,
            duplicateCount = duplicateCount,
            emptyRowCount = emptyRowCount,
            errorCount = errorCount,
            rowErrors = rowErrors
        )
    }

    // =========================================================================
    // 3. LOCAL SQLITE BACKUP & RESTORE
    // =========================================================================
    private fun getBackupsDirectory(context: Context): File {
        val dir = File(context.filesDir, "sqlite_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listBackups(context: Context): List<BackupFileEntry> {
        val dir = getBackupsDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") } ?: emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            BackupFileEntry(
                fileName = f.name,
                filePath = f.absolutePath,
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                sizeBytes = f.length(),
                isAutoPreRestore = f.name.startsWith("auto_safety_")
            )
        }
    }

    fun createLocalBackup(
        context: Context,
        dbHelper: AppDatabaseHelper,
        isAutoSafety: Boolean = false
    ): BackupFileEntry {
        // Ensure WAL/journal is flushed by closing or checkpointing
        dbHelper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL);", null).close()
        val srcFile = dbHelper.getDatabaseFile()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val prefix = if (isAutoSafety) "auto_safety_" else "backup_"
        val targetFile = File(getBackupsDirectory(context), "${prefix}${stamp}.db")
        srcFile.copyTo(targetFile, overwrite = true)

        return BackupFileEntry(
            fileName = targetFile.name,
            filePath = targetFile.absolutePath,
            createdAt = stamp,
            sizeBytes = targetFile.length(),
            isAutoPreRestore = isAutoSafety
        )
    }

    fun restoreBackup(
        context: Context,
        dbHelper: AppDatabaseHelper,
        backupEntry: BackupFileEntry
    ) {
        // 1. Create automatic safety backup of current database first
        createLocalBackup(context, dbHelper, isAutoSafety = true)

        // 2. Close active SQLite connection and copy backup file over primary DB file
        val backupFile = File(backupEntry.filePath)
        require(backupFile.exists()) { "ملف النسخة الاحتياطية غير موجود" }

        val destFile = dbHelper.getDatabaseFile()
        dbHelper.close()
        FileOutputStream(destFile).use { out ->
            backupFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
    }

    fun deleteBackup(entry: BackupFileEntry) {
        File(entry.filePath).delete()
    }

    fun exportDatabaseToUri(context: Context, dbHelper: AppDatabaseHelper, uri: Uri) {
        dbHelper.writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL);", null).close()
        val srcFile = dbHelper.getDatabaseFile()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            srcFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
    }

    fun restoreDatabaseFromUri(context: Context, dbHelper: AppDatabaseHelper, uri: Uri) {
        // 1. Create automatic safety backup of current database first
        createLocalBackup(context, dbHelper, isAutoSafety = true)

        // 2. Read uploaded file into a temporary file and validate SQLite header
        val tempFile = File(context.cacheDir, "restore_temp.db")
        context.contentResolver.openInputStream(uri)?.use { inp ->
            FileOutputStream(tempFile).use { out ->
                inp.copyTo(out)
            }
        } ?: throw IllegalArgumentException("تعذر قراءة الملف المختار")

        require(tempFile.length() > 100) { "الملف المختار فارغ أو غير صالح" }
        val header = ByteArray(16)
        tempFile.inputStream().use { it.read(header) }
        val headerStr = String(header, Charsets.US_ASCII)
        require(headerStr.startsWith("SQLite format 3")) { "الملف المختار ليس قاعدة بيانات SQLite صالحة" }

        val destFile = dbHelper.getDatabaseFile()
        dbHelper.close()
        FileOutputStream(destFile).use { out ->
            tempFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
        tempFile.delete()
    }
}
