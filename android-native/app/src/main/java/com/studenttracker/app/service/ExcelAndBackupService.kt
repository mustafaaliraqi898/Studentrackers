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
    // 3. LOCAL SQLITE BACKUP & RESTORE (Auto Daily + 7-Backup Retention + Safety)
    // =========================================================================
    private const val PREFS_NAME = "student_tracker_backup_prefs"
    private const val KEY_LAST_AUTO_BACKUP_DATE = "last_auto_backup_date"
    private const val KEY_LAST_ERROR_LOG = "last_backup_error_log"
    private const val MAX_LOCAL_BACKUPS = 7

    private fun getBackupsDirectory(context: Context): File {
        val dir = File(context.filesDir, "sqlite_backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
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
        val dir = getBackupsDirectory(context)
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") } ?: emptyList()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val dbHelper = AppDatabaseHelper.getInstance(context)
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
                isAutoPreRestore = f.name.contains("_Safety_") || f.name.startsWith("auto_safety_"),
                isAutoDaily = f.name.contains("_AutoDaily_"),
                studentsCount = counts["students"] ?: 0,
                classesCount = counts["classes"] ?: 0,
                sectionsCount = counts["sections"] ?: 0,
                recordsCount = counts["records"] ?: 0
            )
        }
    }

    fun pruneOldBackups(context: Context, maxCount: Int = MAX_LOCAL_BACKUPS) {
        runCatching {
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
            val targetFile = File(getBackupsDirectory(context), fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            // 3. Create a self-contained, WAL-free snapshot using VACUUM INTO (or fallback to closed-connection copy)
            val vacuumSucceeded = runCatching {
                val escapedPath = targetFile.absolutePath.replace("'", "''")
                db.execSQL("VACUUM INTO '$escapedPath';")
                targetFile.exists() && targetFile.length() > 100
            }.getOrDefault(false)

            if (!vacuumSucceeded) {
                // Fallback: close SQLiteOpenHelper completely so Android merges -wal into .db, then copy
                dbHelper.close()
                val srcFile = dbHelper.getDatabaseFile()
                require(srcFile.exists() && srcFile.length() > 0) {
                    "ملف قاعدة البيانات غير موجود أو فارغ"
                }
                srcFile.copyTo(targetFile, overwrite = true)
                // Re-open connection
                dbHelper.writableDatabase
            }

            // 4. Verify the created backup file actually contains the current students & classes
            require(targetFile.exists() && targetFile.length() > 100) {
                "فشل إنشاء ملف النسخة الاحتياطية على الجهاز"
            }
            val backupCounts = dbHelper.inspectDatabaseFileCounts(targetFile)
            val backedUpStudents = backupCounts["students"] ?: -1
            val backedUpClasses = backupCounts["classes"] ?: -1
            require(backedUpStudents == expectedStudents && backedUpClasses == expectedClasses) {
                "فشل التحقق من النسخة الاحتياطية: المتوقع ($expectedStudents طالب) والمحفوظ ($backedUpStudents طالب)"
            }

            // 5. Enforce keeping only the latest 7 local backups
            pruneOldBackups(context, MAX_LOCAL_BACKUPS)

            // Mark today's date as having a successful backup
            val todayStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_AUTO_BACKUP_DATE, todayStr)
                .apply()

            return BackupFileEntry(
                fileName = targetFile.name,
                filePath = targetFile.absolutePath,
                createdAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                sizeBytes = targetFile.length(),
                isAutoPreRestore = isAutoSafety,
                isAutoDaily = isAutoDaily,
                studentsCount = backedUpStudents,
                classesCount = backedUpClasses,
                sectionsCount = backupCounts["sections"] ?: 0,
                recordsCount = backupCounts["records"] ?: 0
            )
        } catch (e: Exception) {
            recordBackupError(context, "createLocalBackup", e)
            throw e
        }
    }

    /**
     * Creates an automatic local SQLite backup when the user actually exits or closes the app.
     * Uses a short cooldown (5 seconds) so that if both Back-Exit and onStop(isFinishing)/onDestroy
     * fire during the same app termination sequence, only a single exit backup is created.
     */
    @Volatile
    private var lastExitBackupEpochMs: Long = 0L

    @Synchronized
    fun createAutoBackupOnAppExit(
        context: Context,
        dbHelper: AppDatabaseHelper
    ): BackupFileEntry? {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastExitBackupEpochMs < 5000L) {
            return null
        }
        return try {
            val created = createLocalBackup(
                context = context,
                dbHelper = dbHelper,
                isAutoSafety = false,
                isAutoDaily = true
            )
            lastExitBackupEpochMs = System.currentTimeMillis()
            created
        } catch (e: Exception) {
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
        try {
            val backupFile = File(backupEntry.filePath)
            require(backupFile.exists() && backupFile.length() > 100) {
                "ملف النسخة الاحتياطية غير موجود أو تالف"
            }

            // Inspect backup file before restoring to know exact expected counts
            val expectedBackupCounts = dbHelper.inspectDatabaseFileCounts(backupFile)
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
                backupFile.inputStream().use { inp ->
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
        }
    }

    fun deleteBackup(entry: BackupFileEntry) {
        File(entry.filePath).delete()
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
