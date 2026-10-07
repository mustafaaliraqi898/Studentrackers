package com.studenttracker.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
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
                    val colRef = excelColumnName(cIdx)
                    val cellRef = "$colRef$rowNum"
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

    private fun excelColumnName(colIndexZeroBased: Int): String {
        var n = colIndexZeroBased
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
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
                    GradingService.formatRoundedFinalGrade(g.totalScore)
                )
            )
        }
        return buildXlsxBytes("نتائج اليومي", rows)
    }

    /**
     * Exports comprehensive student results including dynamic StudyPeriods columns:
     * اسم الطالب | الصف | الشعبة | الفترة الأولى اليومي | الفترة الأولى التحريري | معدل الفترة الأولى | ... | معدل النصف الأول | درجة نصف السنة | معدل النصف الثاني | السعي السنوي
     * Plus base daily breakdown columns if no StudyPeriods exist or alongside them.
     */
    fun generateComprehensiveResultsXlsx(
        reports: List<StudentComprehensiveGradeReport>,
        periods: List<StudyPeriodEntity>
    ): ByteArray {
        val rows = mutableListOf<List<String>>()
        val header = mutableListOf(
            "اسم الطالب",
            "الصف",
            "الشعبة"
        )
        if (periods.isNotEmpty()) {
            for (p in periods) {
                if (p.stageType == AcademicStage.MID_YEAR) {
                    header.add("نصف السنة")
                } else {
                    header.add("${p.periodName} اليومي")
                    header.add("${p.periodName} التحريري")
                    header.add("معدل ${p.periodName}")
                }
            }
            header.add("معدل النصف الأول")
            header.add("درجة نصف السنة")
            header.add("معدل النصف الثاني")
            header.add("السعي السنوي")
        } else {
            header.addAll(
                listOf(
                    "الحضور (19)",
                    "المشاركة (19)",
                    "الدفتر (19)",
                    "الواجب (19)",
                    "السلوك (19)",
                    "الاختبار (5)",
                    "الدرجة اليومية (100)",
                    "السعي السنوي"
                )
            )
        }
        rows.add(header)

        for (rep in reports) {
            val row = mutableListOf(
                rep.studentName,
                rep.className,
                rep.sectionName
            )
            if (periods.isNotEmpty()) {
                val breakdownByPeriodId = rep.periodBreakdowns.associateBy { it.period.id }
                for (p in periods) {
                    val bd = breakdownByPeriodId[p.id]
                    if (p.stageType == AcademicStage.MID_YEAR) {
                        val midYearStr = if (bd?.writtenExamGrade != null) {
                            "${GradingService.formatRoundedFinalGrade(bd.writtenExamGrade)}/100"
                        } else {
                            "غير مدخل"
                        }
                        row.add(midYearStr)
                    } else {
                        val dailyStr = if (bd != null) GradingService.formatScore(bd.dailyGrade) else "--"
                        val writtenStr = if (bd?.writtenExamGrade != null) GradingService.formatScore(bd.writtenExamGrade) else "--"
                        val avgStr = if (bd?.periodAverage != null) GradingService.formatRoundedFinalGrade(bd.periodAverage) else "--"
                        row.add(dailyStr)
                        row.add(writtenStr)
                        row.add(avgStr)
                    }
                }
                row.add(rep.firstHalfAverage?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل")
                row.add(rep.midYearGrade?.let { "${GradingService.formatRoundedFinalGrade(it)}/100" } ?: "غير مدخل")
                row.add(rep.secondHalfAverage?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل")
                row.add(rep.annualEffort?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل")
            } else {
                val g = rep.baseDailyGrade
                row.addAll(
                    listOf(
                        GradingService.formatScore(g.attendanceScore),
                        GradingService.formatScore(g.participationScore),
                        GradingService.formatScore(g.notebookScore),
                        GradingService.formatScore(g.homeworkScore),
                        GradingService.formatScore(g.behaviorScore),
                        GradingService.formatScore(g.quizScore),
                        GradingService.formatRoundedFinalGrade(g.totalScore),
                        rep.annualEffort?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل"
                    )
                )
            }
            rows.add(row)
        }

        return buildXlsxBytes("التقارير والنتائج", rows)
    }

    fun generateAbsenceXlsx(summaries: List<StudentAbsenceSummary>): ByteArray {
        val rows = mutableListOf<List<String>>()
        rows.add(
            listOf(
                "اسم الطالب",
                "الصف",
                "الشعبة",
                "عدد أيام الغياب"
            )
        )
        summaries.forEach { item ->
            rows.add(
                listOf(
                    item.studentName,
                    item.className,
                    item.sectionName,
                    item.absentDays.toString()
                )
            )
        }
        return buildXlsxBytes("عدد أيام الغياب", rows)
    }

    fun buildAbsenceExportFileName(
        className: String? = null,
        sectionName: String? = null,
        dateIso: String = java.time.LocalDate.now().toString()
    ): String {
        val safeClass = className?.trim()?.replace(Regex("\\s+"), "_")?.takeIf { it.isNotEmpty() }
        val safeSec = sectionName?.trim()?.replace(Regex("\\s+"), "_")?.takeIf { it.isNotEmpty() }
        return when {
            safeClass != null && safeSec != null -> "سجل_غياب_${safeClass}_${safeSec}_${dateIso}.xlsx"
            safeClass != null -> "سجل_غياب_${safeClass}_${dateIso}.xlsx"
            else -> "سجل_أيام_الغياب_${dateIso}.xlsx"
        }
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
            val rawName = TrackerRepository.normalizeStudentName(cells.getOrNull(0) ?: "")
            val rawClass = TrackerRepository.normalizeStudentName(cells.getOrNull(1) ?: "")
            val rawSection = TrackerRepository.normalizeStudentName(cells.getOrNull(2) ?: "")

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
    // 3. LOCAL SQLITE BACKUP & RESTORE (SAF User-Selected Folder + 7-Backup Retention + Safety)
    // =========================================================================
    private const val PREFS_NAME = "student_tracker_backup_prefs"
    private const val KEY_LAST_AUTO_BACKUP_DATE = "last_auto_backup_date"
    private const val KEY_LAST_ERROR_LOG = "last_backup_error_log"
    private const val KEY_BACKUP_FOLDER_URI = "user_selected_backup_folder_uri"
    private const val KEY_BACKUP_STATS_CACHE = "backup_stats_cache_v1"
    private const val MAX_LOCAL_BACKUPS = 7

    private fun getBackupsDirectory(context: Context): File {
        val dir = File(context.filesDir, "sqlite_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Saves the user-chosen SAF directory Uri and persists read/write permissions across reboots.
     */
    fun saveBackupFolderUri(context: Context, treeUri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BACKUP_FOLDER_URI, treeUri.toString()).commit()
    }

    /**
     * Returns the saved SAF Tree Uri if configured, or null if not yet selected.
     */
    fun getSavedBackupFolderUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_BACKUP_FOLDER_URI, null) ?: return null
        return runCatching { Uri.parse(uriStr) }.getOrNull()
    }

    /**
     * Verifies whether the user has selected a backup folder AND that the folder still exists
     * and has valid read/write permission.
     */
    fun isBackupFolderValidAndAccessible(context: Context): Boolean {
        val treeUri = getSavedBackupFolderUri(context) ?: return false
        return runCatching {
            val persisted = context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == treeUri && perm.isReadPermission && perm.isWritePermission
            }
            val docDir = DocumentFile.fromTreeUri(context, treeUri)
            persisted && docDir != null && docDir.exists() && docDir.isDirectory && docDir.canWrite()
        }.getOrDefault(false)
    }

    /**
     * Formats the SAF Tree Uri into a clear, human-readable folder path for display in the UI
     * (e.g. "Download/سجل متابعة الطلاب/النسخ الاحتياطية").
     */
    fun getBackupFolderDisplayPath(context: Context): String? {
        val treeUri = getSavedBackupFolderUri(context) ?: return null
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val decoded = Uri.decode(docId)
            when {
                decoded.startsWith("primary:") -> decoded.removePrefix("primary:").ifEmpty { "الذاكرة الداخلية" }
                decoded.startsWith("home:") -> "المستندات/" + decoded.removePrefix("home:")
                decoded.startsWith("downloads") -> "التنزيلات (Download)"
                decoded.contains(":") -> decoded.substringAfter(":")
                else -> DocumentFile.fromTreeUri(context, treeUri)?.name ?: decoded
            }
        }.getOrElse {
            DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: treeUri.toString()
        }
    }

    private fun cacheBackupStats(
        context: Context,
        fileName: String,
        students: Int,
        classes: Int,
        sections: Int,
        records: Int
    ) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("${KEY_BACKUP_STATS_CACHE}_$fileName", "$students,$classes,$sections,$records")
                .apply()
        }
    }

    private fun getCachedBackupStats(context: Context, fileName: String): Map<String, Int>? {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString("${KEY_BACKUP_STATS_CACHE}_$fileName", null) ?: return null
            val parts = raw.split(",")
            if (parts.size == 4) {
                mapOf(
                    "students" to (parts[0].toIntOrNull() ?: 0),
                    "classes" to (parts[1].toIntOrNull() ?: 0),
                    "sections" to (parts[2].toIntOrNull() ?: 0),
                    "records" to (parts[3].toIntOrNull() ?: 0)
                )
            } else null
        }.getOrNull()
    }

    private fun inspectSafDocumentCounts(
        context: Context,
        dbHelper: AppDatabaseHelper,
        docFile: DocumentFile
    ): Map<String, Int> {
        val name = docFile.name ?: return emptyMap()
        getCachedBackupStats(context, name)?.let { return it }

        val tempInspect = File(context.cacheDir, "inspect_saf_temp.db")
        return try {
            if (tempInspect.exists()) tempInspect.delete()
            context.contentResolver.openInputStream(docFile.uri)?.use { inp ->
                FileOutputStream(tempInspect).use { out ->
                    inp.copyTo(out)
                }
            }
            if (tempInspect.exists() && tempInspect.length() > 100) {
                val counts = dbHelper.inspectDatabaseFileCounts(tempInspect)
                cacheBackupStats(
                    context,
                    name,
                    counts["students"] ?: 0,
                    counts["classes"] ?: 0,
                    counts["sections"] ?: 0,
                    counts["records"] ?: 0
                )
                counts
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        } finally {
            if (tempInspect.exists()) tempInspect.delete()
        }
    }

    fun recordBackupError(context: Context, operation: String, e: Throwable) {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val entry = "[$stamp] ($operation): ${e.message ?: "خطأ غير معروف"}"
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_ERROR_LOG, entry).apply()
        }
    }

    fun getLastBackupError(context: Context): String? {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_ERROR_LOG, null)
        }.getOrNull()
    }

    fun clearLastBackupError(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LAST_ERROR_LOG).apply()
        }
    }

    fun listBackups(context: Context): List<BackupFileEntry> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val dbHelper = AppDatabaseHelper.getInstance(context)

        // If the user has selected a valid SAF folder, list backups directly from that SAF folder
        if (isBackupFolderValidAndAccessible(context)) {
            val treeUri = getSavedBackupFolderUri(context)!!
            val docDir = DocumentFile.fromTreeUri(context, treeUri)
            if (docDir != null && docDir.exists()) {
                val docFiles = docDir.listFiles().filter { f ->
                    val name = f.name ?: ""
                    f.isFile && (name.endsWith(".db") || name.endsWith(".sqlite")) && f.length() > 100
                }
                return docFiles.sortedByDescending { it.lastModified() }.map { f ->
                    val fName = f.name ?: "StudentTracker_Backup.db"
                    val lastMod = f.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                    val modifiedTime = java.time.Instant.ofEpochMilli(lastMod)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(formatter)
                    val counts = inspectSafDocumentCounts(context, dbHelper, f)
                    BackupFileEntry(
                        fileName = fName,
                        filePath = f.uri.toString(),
                        createdAt = modifiedTime,
                        sizeBytes = f.length(),
                        isAutoPreRestore = fName.contains("_Safety") || fName.startsWith("auto_safety_"),
                        isAutoDaily = fName.contains("_AutoDaily") || fName.contains("_AutoExit"),
                        studentsCount = counts["students"] ?: 0,
                        classesCount = counts["classes"] ?: 0,
                        sectionsCount = counts["sections"] ?: 0,
                        recordsCount = counts["records"] ?: 0
                    )
                }
            }
        }

        // Fallback if no SAF folder is selected yet: list internal backups if any exist
        val dir = getBackupsDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") } ?: emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            val modifiedTime = java.time.Instant.ofEpochMilli(f.lastModified())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(formatter)
            val counts = runCatching { dbHelper.inspectDatabaseFileCounts(f) }.getOrDefault(emptyMap())
            BackupFileEntry(
                fileName = f.name,
                filePath = f.absolutePath,
                createdAt = modifiedTime,
                sizeBytes = f.length(),
                isAutoPreRestore = f.name.contains("_Safety") || f.name.startsWith("auto_safety_"),
                isAutoDaily = f.name.contains("_AutoDaily") || f.name.contains("_AutoExit"),
                studentsCount = counts["students"] ?: 0,
                classesCount = counts["classes"] ?: 0,
                sectionsCount = counts["sections"] ?: 0,
                recordsCount = counts["records"] ?: 0
            )
        }
    }

    fun pruneOldBackups(context: Context, maxCount: Int = MAX_LOCAL_BACKUPS) {
        runCatching {
            if (isBackupFolderValidAndAccessible(context)) {
                val treeUri = getSavedBackupFolderUri(context)!!
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                if (docDir != null && docDir.exists()) {
                    val safBackups = docDir.listFiles().filter { f ->
                        val name = f.name ?: ""
                        f.isFile && name.startsWith("StudentTracker_Backup_") && name.endsWith(".db")
                    }.sortedByDescending { it.lastModified() }

                    if (safBackups.size > maxCount) {
                        safBackups.drop(maxCount).forEach { oldDoc ->
                            oldDoc.delete()
                        }
                    }
                }
            }

            // Also keep internal fallback directory pruned
            val dir = getBackupsDirectory(context)
            val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") }
                ?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > maxCount) {
                files.drop(maxCount).forEach { oldFile ->
                    oldFile.delete()
                }
            }
        }.onFailure { e ->
            recordBackupError(context, "pruneOldBackups", e)
        }
    }

    fun createLocalBackup(
        context: Context,
        dbHelper: AppDatabaseHelper,
        isAutoSafety: Boolean = false,
        isAutoDaily: Boolean = false
    ): BackupFileEntry {
        try {
            // For manual or auto-exit backups, require that the user has selected a valid SAF folder
            val hasValidSafFolder = isBackupFolderValidAndAccessible(context)
            if (!hasValidSafFolder && !isAutoSafety) {
                throw IllegalStateException("يرجى تحديد مجلد حفظ النسخ الاحتياطية أولاً")
            }

            // 1. Read expected live counts from the active SQLite connection before creating backup
            val db = dbHelper.writableDatabase
            val expectedStudents = db.rawQuery("SELECT COUNT(*) FROM Students", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val expectedClasses = db.rawQuery("SELECT COUNT(*) FROM Classes", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

            // 2. Flush WAL and memory
            dbHelper.flushAndCheckpoint()

            val now = LocalDateTime.now()
            val stamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val tag = when {
                isAutoSafety -> "_Safety"
                isAutoDaily -> "_AutoDaily"
                else -> ""
            }
            val fileName = "StudentTracker_Backup_${stamp}${tag}.db"
            val stagingFile = File(getBackupsDirectory(context), fileName)
            if (stagingFile.exists()) {
                stagingFile.delete()
            }

            // 3. Create a self-contained, WAL-free snapshot using VACUUM INTO (or fallback to closed-connection copy)
            val vacuumSucceeded = runCatching {
                val escapedPath = stagingFile.absolutePath.replace("'", "''")
                db.execSQL("VACUUM INTO '$escapedPath';")
                stagingFile.exists() && stagingFile.length() > 100
            }.getOrDefault(false)

            if (!vacuumSucceeded) {
                // Fallback: close SQLiteOpenHelper completely so Android merges -wal into .db, then copy
                dbHelper.close()
                val srcFile = dbHelper.getDatabaseFile()
                require(srcFile.exists() && srcFile.length() > 0) {
                    "ملف قاعدة البيانات غير موجود أو فارغ"
                }
                srcFile.copyTo(stagingFile, overwrite = true)
                // Re-open connection
                dbHelper.writableDatabase
            }

            // 4. Verify the created snapshot file actually contains the current students & classes
            require(stagingFile.exists() && stagingFile.length() > 100) {
                "فشل إنشاء ملف النسخة الاحتياطية على الجهاز"
            }
            val backupCounts = dbHelper.inspectDatabaseFileCounts(stagingFile)
            val backedUpStudents = backupCounts["students"] ?: -1
            val backedUpClasses = backupCounts["classes"] ?: -1
            val backedUpSections = backupCounts["sections"] ?: 0
            val backedUpRecords = backupCounts["records"] ?: 0

            require(backedUpStudents == expectedStudents && backedUpClasses == expectedClasses) {
                "فشل التحقق من النسخة الاحتياطية: المتوقع ($expectedStudents طالب) والمحفوظ ($backedUpStudents طالب)"
            }

            cacheBackupStats(
                context,
                fileName,
                backedUpStudents,
                backedUpClasses,
                backedUpSections,
                backedUpRecords
            )

            // 5. Write the verified SQLite snapshot directly into the user's selected SAF folder
            var finalFilePath = stagingFile.absolutePath
            var finalSizeBytes = stagingFile.length()

            if (hasValidSafFolder) {
                val treeUri = getSavedBackupFolderUri(context)!!
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw IllegalStateException("تعذر الوصول إلى مجلد النسخ الاحتياطي المختار. يرجى إعادة تحديده.")

                // Remove any existing file with the same name
                docDir.findFile(fileName)?.delete()

                val createdDoc = docDir.createFile("application/octet-stream", fileName)
                    ?: throw IllegalStateException("تعذر إنشاء ملف النسخة الاحتياطية داخل المجلد المختار. يرجى التأكد من الصلاحية.")

                context.contentResolver.openOutputStream(createdDoc.uri)?.use { out ->
                    stagingFile.inputStream().use { inp ->
                        inp.copyTo(out)
                    }
                } ?: throw IllegalStateException("تعذر الكتابة داخل المجلد المختار")

                finalFilePath = createdDoc.uri.toString()
                finalSizeBytes = createdDoc.length().takeIf { it > 0L } ?: stagingFile.length()

                // Remove internal staging copy so backups live in the user-chosen folder
                stagingFile.delete()
            }

            // 6. Enforce keeping only the latest 7 local backups
            pruneOldBackups(context, MAX_LOCAL_BACKUPS)

            // Mark today's date as having a successful backup
            val todayStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_AUTO_BACKUP_DATE, todayStr)
                .apply()

            return BackupFileEntry(
                fileName = fileName,
                filePath = finalFilePath,
                createdAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                sizeBytes = finalSizeBytes,
                isAutoPreRestore = isAutoSafety,
                isAutoDaily = isAutoDaily,
                studentsCount = backedUpStudents,
                classesCount = backedUpClasses,
                sectionsCount = backedUpSections,
                recordsCount = backedUpRecords
            )
        } catch (e: Exception) {
            recordBackupError(context, "createLocalBackup", e)
            throw e
        }
    }

    /**
     * Creates an automatic local SQLite backup when the user actually exits or closes the app
     * (including swiping the app away from Recents or tapping "Close All").
     * Uses both an in-memory and SharedPreferences timestamp cooldown (10 seconds) so that if
     * multiple exit signals (Back button, onStop(isFinishing), onTaskRemoved, and expedited WorkManager)
     * fire during the same app termination sequence, only a single exit backup is created.
     */
    @Volatile
    private var lastExitBackupEpochMs: Long = 0L
    private const val KEY_LAST_EXIT_BACKUP_MS = "last_exit_backup_epoch_ms"

    @Synchronized
    fun createAutoBackupOnAppExit(
        context: Context,
        dbHelper: AppDatabaseHelper
    ): BackupFileEntry? {
        // Only run automatic exit backup if the user has chosen a valid SAF backup folder
        if (!isBackupFolderValidAndAccessible(context)) {
            return null
        }

        val nowMs = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persistedLastMs = prefs.getLong(KEY_LAST_EXIT_BACKUP_MS, 0L)
        val effectiveLastMs = maxOf(lastExitBackupEpochMs, persistedLastMs)

        if (nowMs - effectiveLastMs < 10_000L) {
            return null
        }

        // Reserve timestamp immediately before I/O so concurrent exit hooks don't duplicate
        lastExitBackupEpochMs = nowMs
        prefs.edit().putLong(KEY_LAST_EXIT_BACKUP_MS, nowMs).commit()

        return try {
            val created = createLocalBackup(
                context = context,
                dbHelper = dbHelper,
                isAutoSafety = false,
                isAutoDaily = true
            )
            val completedMs = System.currentTimeMillis()
            lastExitBackupEpochMs = completedMs
            prefs.edit().putLong(KEY_LAST_EXIT_BACKUP_MS, completedMs).commit()
            created
        } catch (e: Exception) {
            // Reset lock on failure so retry can proceed
            lastExitBackupEpochMs = 0L
            prefs.edit().putLong(KEY_LAST_EXIT_BACKUP_MS, 0L).commit()
            recordBackupError(context, "createAutoBackupOnAppExit", e)
            null
        }
    }

    /**
     * Legacy helper kept for compatibility (not called on app startup).
     */
    fun ensureDailyAutoBackup(
        context: Context,
        dbHelper: AppDatabaseHelper
    ): BackupFileEntry? {
        if (!isBackupFolderValidAndAccessible(context)) {
            return null
        }
        return try {
            val todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastAutoDate = prefs.getString(KEY_LAST_AUTO_BACKUP_DATE, null)

            val existingToday = listBackups(context).any {
                it.createdAt.startsWith(todayStr) || it.fileName.contains(todayStr)
            }

            if (lastAutoDate == todayStr && existingToday) {
                return null
            }
            if (existingToday) {
                prefs.edit().putString(KEY_LAST_AUTO_BACKUP_DATE, todayStr).apply()
                return null
            }

            createLocalBackup(context, dbHelper, isAutoSafety = false, isAutoDaily = true)
        } catch (e: Exception) {
            recordBackupError(context, "ensureDailyAutoBackup", e)
            null
        }
    }

    fun restoreBackup(
        context: Context,
        dbHelper: AppDatabaseHelper,
        backupEntry: BackupFileEntry
    ): BackupFileEntry {
        val tempRestoreFile = File(context.cacheDir, "restore_entry_temp.db")
        try {
            if (tempRestoreFile.exists()) tempRestoreFile.delete()

            val backupSourceFile: File = if (backupEntry.filePath.startsWith("content://")) {
                val docUri = Uri.parse(backupEntry.filePath)
                context.contentResolver.openInputStream(docUri)?.use { inp ->
                    FileOutputStream(tempRestoreFile).use { out ->
                        inp.copyTo(out)
                        out.fd.sync()
                    }
                } ?: throw IllegalArgumentException("تعذر قراءة ملف النسخة الاحتياطية من المجلد المختار")
                tempRestoreFile
            } else {
                File(backupEntry.filePath)
            }

            require(backupSourceFile.exists() && backupSourceFile.length() > 100) {
                "ملف النسخة الاحتياطية غير موجود أو تالف"
            }

            // Inspect backup file before restoring to know exact expected counts
            val expectedBackupCounts = dbHelper.inspectDatabaseFileCounts(backupSourceFile)
            val expectedStudents = expectedBackupCounts["students"] ?: 0
            val expectedClasses = expectedBackupCounts["classes"] ?: 0

            // 1. Mandatory automatic safety backup of current database first (aborts if this throws)
            val safetyBackup = createLocalBackup(context, dbHelper, isAutoSafety = true, isAutoDaily = false)

            // 2. Close active SQLite connection AND delete stale -wal, -shm, and -journal files
            // CRITICAL: If -wal/-shm are not deleted, SQLite will replay old WAL frames (such as deleted students) on reopen!
            val destFile = dbHelper.getDatabaseFile()
            val walFile = dbHelper.getWalFile()
            val shmFile = dbHelper.getShmFile()
            val journalFile = dbHelper.getJournalFile()

            dbHelper.close()

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            if (journalFile.exists()) journalFile.delete()

            // 3. Copy the backup file into the primary SQLite database location
            FileOutputStream(destFile).use { out ->
                backupSourceFile.inputStream().use { inp ->
                    inp.copyTo(out)
                }
                out.fd.sync()
            }

            // Ensure no -wal or -shm exist before reopening
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 4. Re-open the database and verify the restored data matches the backup file
            val reopenedDb = dbHelper.writableDatabase
            val restoredStudents = reopenedDb.rawQuery("SELECT COUNT(*) FROM Students", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val restoredClasses = reopenedDb.rawQuery("SELECT COUNT(*) FROM Classes", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

            require(restoredStudents == expectedStudents && restoredClasses == expectedClasses) {
                "فشل التحقق بعد الاستعادة: النسخة تحتوي ($expectedStudents طالب) ولكن القاعدة المستعادة تحتوي ($restoredStudents طالب)"
            }

            return safetyBackup
        } catch (e: Exception) {
            recordBackupError(context, "restoreBackup", e)
            throw e
        } finally {
            if (tempRestoreFile.exists()) tempRestoreFile.delete()
        }
    }

    fun deleteBackup(context: Context, entry: BackupFileEntry) {
        if (entry.filePath.startsWith("content://")) {
            runCatching {
                val uri = Uri.parse(entry.filePath)
                DocumentFile.fromSingleUri(context, uri)?.delete()
            }
        } else {
            File(entry.filePath).delete()
        }
    }

    fun exportDatabaseToUri(context: Context, dbHelper: AppDatabaseHelper, uri: Uri) {
        val tempExport = File(context.cacheDir, "export_snapshot_temp.db")
        if (tempExport.exists()) tempExport.delete()
        val db = dbHelper.writableDatabase
        dbHelper.flushAndCheckpoint()
        val escapedPath = tempExport.absolutePath.replace("'", "''")
        val vacuumOk = runCatching {
            db.execSQL("VACUUM INTO '$escapedPath';")
            tempExport.exists() && tempExport.length() > 100
        }.getOrDefault(false)

        val srcToCopy = if (vacuumOk) tempExport else dbHelper.getDatabaseFile()
        context.contentResolver.openOutputStream(uri)?.use { out ->
            srcToCopy.inputStream().use { inp ->
                inp.copyTo(out)
            }
        }
        if (tempExport.exists()) tempExport.delete()
    }

    fun restoreDatabaseFromUri(context: Context, dbHelper: AppDatabaseHelper, uri: Uri) {
        // 1. Read uploaded file into a temporary file and validate SQLite header + tables
        val tempFile = File(context.cacheDir, "restore_temp.db")
        if (tempFile.exists()) tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { inp ->
            FileOutputStream(tempFile).use { out ->
                inp.copyTo(out)
                out.fd.sync()
            }
        } ?: throw IllegalArgumentException("تعذر قراءة الملف المختار")

        require(tempFile.length() > 100) { "الملف المختار فارغ أو غير صالح" }
        val header = ByteArray(16)
        tempFile.inputStream().use { it.read(header) }
        val headerStr = String(header, Charsets.US_ASCII)
        require(headerStr.startsWith("SQLite format 3")) { "الملف المختار ليس قاعدة بيانات SQLite صالحة" }

        val expectedCounts = dbHelper.inspectDatabaseFileCounts(tempFile)
        val expectedStudents = expectedCounts["students"] ?: 0

        // 2. Create automatic safety backup of current database first
        createLocalBackup(context, dbHelper, isAutoSafety = true)

        // 3. Close connection and delete stale -wal / -shm / -journal files
        val destFile = dbHelper.getDatabaseFile()
        val walFile = dbHelper.getWalFile()
        val shmFile = dbHelper.getShmFile()
        val journalFile = dbHelper.getJournalFile()

        dbHelper.close()
        if (walFile.exists()) walFile.delete()
        if (shmFile.exists()) shmFile.delete()
        if (journalFile.exists()) journalFile.delete()

        FileOutputStream(destFile).use { out ->
            tempFile.inputStream().use { inp ->
                inp.copyTo(out)
            }
            out.fd.sync()
        }
        tempFile.delete()

        // 4. Reopen and verify
        val reopenedDb = dbHelper.writableDatabase
        val actualStudents = reopenedDb.rawQuery("SELECT COUNT(*) FROM Students", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        require(actualStudents == expectedStudents) {
            "فشل التحقق بعد استعادة الملف الخارجي: المتوقع ($expectedStudents طالب) والموجود ($actualStudents طالب)"
        }
    }
}
