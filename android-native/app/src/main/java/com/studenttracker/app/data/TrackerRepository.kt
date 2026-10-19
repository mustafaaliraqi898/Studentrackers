package com.studenttracker.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.studenttracker.app.model.*
import java.time.Instant

class TrackerRepository(private val dbHelper: AppDatabaseHelper) {

    companion object {
        /**
         * Normalizes Arabic student names for storage and comparison:
         * trims leading/trailing whitespace, removes zero-width characters and tatweel (ـ),
         * and collapses any multiple spaces/tabs into a single space.
         */
        fun normalizeStudentName(name: String): String {
            return name
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .replace("ـ", "")
                .trim()
                .replace(Regex("\\s+"), " ")
        }

        /**
         * Produces a canonical comparison key for Arabic names so minor alef/ta-marbuta/diacritic
         * or spacing differences are accurately matched within the same class.
         */
        fun canonicalArabicComparisonKey(name: String): String {
            val base = normalizeStudentName(name)
            val withoutTashkeel = base.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            return withoutTashkeel
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ٱ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .lowercase()
        }
    }

    /**
     * Returns how many non-null participation ratings each student has received
     * during the selected study period, scoped to the selected class and section.
     * Students without a saved rating are omitted and treated as zero by callers.
     */
    fun getParticipationEvaluationCounts(
        classId: Long,
        sectionId: Long,
        startDate: String,
        endDate: String?
    ): Map<Long, Int> {
        val counts = mutableMapOf<Long, Int>()
        val args = mutableListOf(classId.toString(), sectionId.toString(), startDate)
        val endClause = if (endDate.isNullOrBlank()) "" else {
            args.add(endDate)
            " AND e.RecordDate <= ?"
        }
        val sql = """
            SELECT e.StudentID, COUNT(*)
            FROM Evaluations e
            JOIN Students st ON st.ID = e.StudentID
            WHERE st.ClassID = ?
              AND st.SectionID = ?
              AND st.IsActive = 1
              AND e.RecordDate >= ?
              $endClause
              AND e.Participation IS NOT NULL
            GROUP BY e.StudentID
        """.trimIndent()
        val cursor = dbHelper.readableDatabase.rawQuery(sql, args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                counts[it.getLong(0)] = it.getInt(1)
            }
        }
        return counts
    }

    // ---------------- CLASSES ----------------
    fun getClasses(): List<ClassEntity> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<ClassEntity>()
        val cursor = db.rawQuery("SELECT ID, ClassName, CreatedAt FROM Classes ORDER BY ID ASC", null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ClassEntity(
                        id = it.getLong(0),
                        className = it.getString(1),
                        createdAt = it.getString(2)
                    )
                )
            }
        }
        return list
    }

    fun addClass(className: String): Long {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "اسم الصف مطلوب" }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("ClassName", trimmed)
            put("CreatedAt", Instant.now().toString())
        }
        return db.insertOrThrow("Classes", null, cv)
    }

    fun updateClass(id: Long, className: String) {
        val trimmed = className.trim()
        require(trimmed.isNotEmpty()) { "اسم الصف مطلوب" }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("ClassName", trimmed) }
        db.update("Classes", cv, "ID = ?", arrayOf(id.toString()))
    }

    fun deleteClass(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("Classes", "ID = ?", arrayOf(id.toString()))
    }

    // ---------------- SECTIONS ----------------
    fun getSections(classId: Long? = null): List<SectionEntity> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<SectionEntity>()
        val cursor = if (classId != null) {
            db.rawQuery(
                "SELECT ID, ClassID, SectionName, CreatedAt FROM Sections WHERE ClassID = ? ORDER BY ID ASC",
                arrayOf(classId.toString())
            )
        } else {
            db.rawQuery("SELECT ID, ClassID, SectionName, CreatedAt FROM Sections ORDER BY ClassID ASC, ID ASC", null)
        }
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SectionEntity(
                        id = it.getLong(0),
                        classId = it.getLong(1),
                        sectionName = it.getString(2),
                        createdAt = it.getString(3)
                    )
                )
            }
        }
        return list
    }

    fun addSection(classId: Long, sectionName: String): Long {
        val trimmed = sectionName.trim()
        require(trimmed.isNotEmpty()) { "اسم الشعبة مطلوب" }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("ClassID", classId)
            put("SectionName", trimmed)
            put("CreatedAt", Instant.now().toString())
        }
        return db.insertOrThrow("Sections", null, cv)
    }

    fun updateSection(id: Long, sectionName: String) {
        val trimmed = sectionName.trim()
        require(trimmed.isNotEmpty()) { "اسم الشعبة مطلوب" }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("SectionName", trimmed) }
        db.update("Sections", cv, "ID = ?", arrayOf(id.toString()))
    }

    fun deleteSection(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("Sections", "ID = ?", arrayOf(id.toString()))
    }

    // ---------------- STUDENTS ----------------
    fun getStudents(
        classId: Long? = null,
        sectionId: Long? = null,
        includeInactive: Boolean = false
    ): List<StudentEntity> {
        val db = dbHelper.readableDatabase
        val args = mutableListOf<String>()
        val sb = StringBuilder(
            """
            SELECT st.ID, st.StudentName, st.ClassID, c.ClassName, st.SectionID, s.SectionName, st.IsActive, st.CreatedDate
            FROM Students st
            JOIN Classes c ON c.ID = st.ClassID
            JOIN Sections s ON s.ID = st.SectionID
            WHERE 1=1
            """.trimIndent()
        )
        if (!includeInactive) {
            sb.append(" AND st.IsActive = 1")
        }
        if (classId != null) {
            sb.append(" AND st.ClassID = ?")
            args.add(classId.toString())
        }
        if (sectionId != null) {
            sb.append(" AND st.SectionID = ?")
            args.add(sectionId.toString())
        }
        sb.append(" ORDER BY st.ID ASC")

        val list = mutableListOf<StudentEntity>()
        val cursor = db.rawQuery(sb.toString(), args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    StudentEntity(
                        id = it.getLong(0),
                        studentName = it.getString(1),
                        classId = it.getLong(2),
                        className = it.getString(3),
                        sectionId = it.getLong(4),
                        sectionName = it.getString(5),
                        isActive = it.getInt(6) == 1,
                        createdDate = it.getString(7)
                    )
                )
            }
        }
        return list
    }

    fun searchStudents(
        query: String,
        classId: Long? = null,
        sectionId: Long? = null
    ): List<StudentEntity> {
        val db = dbHelper.readableDatabase
        val args = mutableListOf<String>()
        val sb = StringBuilder(
            """
            SELECT st.ID, st.StudentName, st.ClassID, c.ClassName, st.SectionID, s.SectionName, st.IsActive, st.CreatedDate
            FROM Students st
            JOIN Classes c ON c.ID = st.ClassID
            JOIN Sections s ON s.ID = st.SectionID
            WHERE st.IsActive = 1
            """.trimIndent()
        )
        val cleaned = query.trim()
        if (cleaned.isNotEmpty()) {
            sb.append(" AND st.StudentName LIKE ?")
            args.add("%$cleaned%")
        }
        if (classId != null) {
            sb.append(" AND st.ClassID = ?")
            args.add(classId.toString())
        }
        if (sectionId != null) {
            sb.append(" AND st.SectionID = ?")
            args.add(sectionId.toString())
        }
        sb.append(" ORDER BY st.StudentName ASC LIMIT 150")

        val list = mutableListOf<StudentEntity>()
        val cursor = db.rawQuery(sb.toString(), args.toTypedArray())
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    StudentEntity(
                        id = it.getLong(0),
                        studentName = it.getString(1),
                        classId = it.getLong(2),
                        className = it.getString(3),
                        sectionId = it.getLong(4),
                        sectionName = it.getString(5),
                        isActive = it.getInt(6) == 1,
                        createdDate = it.getString(7)
                    )
                )
            }
        }
        return list
    }

    fun addStudent(studentName: String, classId: Long, sectionId: Long): Pair<Long, Boolean> {
        val trimmed = normalizeStudentName(studentName)
        require(trimmed.isNotEmpty()) { "اسم الطالب مطلوب" }
        val targetKey = canonicalArabicComparisonKey(trimmed)

        val db = dbHelper.writableDatabase
        // Check duplicate across the ENTIRE ClassID (regardless of SectionID)
        val check = db.rawQuery(
            "SELECT ID, StudentName, SectionID, IsActive FROM Students WHERE ClassID = ?",
            arrayOf(classId.toString())
        )
        check.use {
            while (it.moveToNext()) {
                val existingId = it.getLong(0)
                val existingName = it.getString(1) ?: ""
                val existingSecId = it.getLong(2)
                val isAct = it.getInt(3) == 1
                if (canonicalArabicComparisonKey(existingName) == targetKey) {
                    if (!isAct && existingSecId == sectionId) {
                        val cv = ContentValues().apply {
                            put("IsActive", 1)
                            put("StudentName", trimmed)
                        }
                        db.update("Students", cv, "ID = ?", arrayOf(existingId.toString()))
                        return Pair(existingId, false)
                    }
                    // Duplicate in the same class (either active in same/another section, or inactive in another section)
                    return Pair(existingId, true)
                }
            }
        }

        val cv = ContentValues().apply {
            put("StudentName", trimmed)
            put("ClassID", classId)
            put("SectionID", sectionId)
            put("IsActive", 1)
            put("CreatedDate", Instant.now().toString())
        }
        val id = db.insertOrThrow("Students", null, cv)
        return Pair(id, false)
    }

    /**
     * Updates a student's name after verifying that no other student in the same ClassID
     * (across all sections of that class) already has the same normalized name.
     * Returns true if updated, or false if rejected due to a duplicate in the same class.
     */
    fun updateStudentName(studentId: Long, newName: String): Boolean {
        val trimmed = normalizeStudentName(newName)
        require(trimmed.isNotEmpty()) { "اسم الطالب مطلوب" }
        val targetKey = canonicalArabicComparisonKey(trimmed)

        val db = dbHelper.writableDatabase
        var studentClassId: Long? = null
        db.rawQuery("SELECT ClassID FROM Students WHERE ID = ?", arrayOf(studentId.toString())).use { c ->
            if (c.moveToFirst()) {
                studentClassId = c.getLong(0)
            }
        }
        val clsId = studentClassId ?: return false

        // Check if another student (ID != studentId) in the same ClassID has this name
        db.rawQuery(
            "SELECT ID, StudentName FROM Students WHERE ClassID = ? AND ID != ?",
            arrayOf(clsId.toString(), studentId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val otherName = c.getString(1) ?: ""
                if (canonicalArabicComparisonKey(otherName) == targetKey) {
                    return false
                }
            }
        }

        val cv = ContentValues().apply { put("StudentName", trimmed) }
        db.update("Students", cv, "ID = ?", arrayOf(studentId.toString()))
        return true
    }

    fun toggleStudentActive(studentId: Long, isActive: Boolean) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply { put("IsActive", if (isActive) 1 else 0) }
        db.update("Students", cv, "ID = ?", arrayOf(studentId.toString()))
    }

    /**
     * Transfers a student to another section/class. If transferring to a different ClassID,
     * checks that no other student in targetClassId already has the same name.
     * Returns true if transferred, or false if duplicate name exists in targetClassId.
     */
    fun transferStudentSection(studentId: Long, targetClassId: Long, targetSectionId: Long): Boolean {
        val db = dbHelper.writableDatabase
        var currentName = ""
        db.rawQuery("SELECT StudentName FROM Students WHERE ID = ?", arrayOf(studentId.toString())).use { c ->
            if (c.moveToFirst()) {
                currentName = c.getString(0) ?: ""
            }
        }
        if (currentName.isEmpty()) return false
        val targetKey = canonicalArabicComparisonKey(currentName)

        db.rawQuery(
            "SELECT ID, StudentName FROM Students WHERE ClassID = ? AND ID != ?",
            arrayOf(targetClassId.toString(), studentId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val otherName = c.getString(1) ?: ""
                if (canonicalArabicComparisonKey(otherName) == targetKey) {
                    return false
                }
            }
        }

        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("ClassID", targetClassId)
                put("SectionID", targetSectionId)
            }
            db.update("Students", cv, "ID = ?", arrayOf(studentId.toString()))
            db.update("DailyRecords", cv, "StudentID = ?", arrayOf(studentId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    fun deleteStudent(studentId: Long) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val arg = arrayOf(studentId.toString())
            db.delete("Attendance", "StudentID = ?", arg)
            db.delete("Evaluations", "StudentID = ?", arg)
            db.delete("DailyTests", "StudentID = ?", arg)
            db.delete("DailyRecords", "StudentID = ?", arg)
            db.delete("Students", "ID = ?", arg)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------------- HOLIDAYS ----------------
    fun getHolidays(): List<HolidayEntity> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<HolidayEntity>()
        val cursor = db.rawQuery(
            "SELECT ID, HolidayDate, HolidayName, HolidayType, Notes FROM Holidays ORDER BY HolidayDate ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    HolidayEntity(
                        id = it.getLong(0),
                        holidayDate = it.getString(1),
                        holidayName = it.getString(2),
                        holidayType = it.getString(3),
                        notes = it.getString(4) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun addOrUpdateHoliday(
        id: Long? = null,
        holidayDate: String,
        holidayName: String,
        holidayType: String,
        notes: String
    ) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("HolidayDate", holidayDate.trim())
            put("HolidayName", holidayName.trim())
            put("HolidayType", holidayType.trim())
            put("Notes", notes.trim())
        }
        if (id != null) {
            db.update("Holidays", cv, "ID = ?", arrayOf(id.toString()))
        } else {
            db.insertWithOnConflict("Holidays", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteHoliday(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("Holidays", "ID = ?", arrayOf(id.toString()))
    }

    // ---------------- DAILY TRACKING SESSIONS ----------------
    fun hasSavedSession(classId: Long, sectionId: Long, recordDate: String): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM DailyRecords WHERE ClassID = ? AND SectionID = ? AND RecordDate = ?",
            arrayOf(classId.toString(), sectionId.toString(), recordDate)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0) > 0
            }
        }
        return false
    }

    fun getSessionEntries(
        classId: Long,
        sectionId: Long,
        recordDate: String
    ): Map<Long, StudentDailyEntry> {
        val db = dbHelper.readableDatabase
        val map = mutableMapOf<Long, StudentDailyEntry>()
        val cursor = db.rawQuery(
            """
            SELECT
                dr.StudentID,
                a.Status,
                e.Participation,
                e.Notebook,
                e.Homework,
                e.Behavior,
                dt.Score
            FROM DailyRecords dr
            LEFT JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
            LEFT JOIN Evaluations e ON e.StudentID = dr.StudentID AND e.RecordDate = dr.RecordDate
            LEFT JOIN DailyTests dt ON dt.StudentID = dr.StudentID AND dt.RecordDate = dr.RecordDate
            WHERE dr.ClassID = ? AND dr.SectionID = ? AND dr.RecordDate = ?
            """.trimIndent(),
            arrayOf(classId.toString(), sectionId.toString(), recordDate)
        )
        cursor.use {
            while (it.moveToNext()) {
                val stId = it.getLong(0)
                val status = AttendanceStatus.fromDb(if (it.isNull(1)) "present" else it.getString(1))
                val part = if (it.isNull(2)) null else it.getDouble(2)
                val note = if (it.isNull(3)) null else it.getDouble(3)
                val hw = if (it.isNull(4)) null else it.getDouble(4)
                val beh = if (it.isNull(5)) null else it.getDouble(5)
                val score = if (it.isNull(6)) null else it.getDouble(6)

                map[stId] = StudentDailyEntry(
                    studentId = stId,
                    attendance = status,
                    participation = part,
                    notebook = note,
                    homework = hw,
                    behavior = beh,
                    dailyTestScore = score
                )
            }
        }
        return map
    }

    fun getStudentAttendanceStatusForDate(
        studentId: Long,
        recordDate: String
    ): AttendanceStatus? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT a.Status
            FROM DailyRecords dr
            JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
            WHERE dr.StudentID = ? AND dr.RecordDate = ?
            """.trimIndent(),
            arrayOf(studentId.toString(), recordDate)
        )
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                return AttendanceStatus.fromDb(it.getString(0))
            }
        }
        return null
    }

    /**
     * Immediately marks a student as ABSENT for the given date in SQLite (0 attendance for this day),
     * clears any evaluations/quiz for that day so no conflicting present ratings remain,
     * and ensures all other active students in the same section have a saved DailyRecords row
     * if the session was not yet initialized.
     */
    /**
     * Immediately updates or creates a student's attendance status (PRESENT or ABSENT) for the given
     * date in SQLite without creating duplicate DailyRecords (uses ON CONFLICT(StudentID, RecordDate) DO UPDATE).
     * Only affects the specified recordDate; previous days' records remain untouched.
     */
    fun setStudentAttendanceImmediately(
        studentId: Long,
        classId: Long,
        sectionId: Long,
        recordDate: String,
        targetStatus: AttendanceStatus,
        currentDraftEntries: Map<Long, StudentDailyEntry>? = null,
        academicYearId: Long? = null,
        periodId: Long? = null
    ): Map<Long, StudentDailyEntry> {
        val existingSaved = getSessionEntries(classId, sectionId, recordDate)
        val sectionStudents = getStudents(classId, sectionId, includeInactive = false)
        val dbEntriesToSave = mutableMapOf<Long, StudentDailyEntry>()
        val updatedDraftEntries = mutableMapOf<Long, StudentDailyEntry>()

        for (st in sectionStudents) {
            val savedEntry = existingSaved[st.id]
                ?: StudentDailyEntry(studentId = st.id, attendance = AttendanceStatus.PRESENT)
            dbEntriesToSave[st.id] = savedEntry

            val draftEntry = currentDraftEntries?.get(st.id) ?: savedEntry
            updatedDraftEntries[st.id] = draftEntry
        }

        val currentSavedStudent = dbEntriesToSave[studentId] ?: StudentDailyEntry(studentId = studentId)
        val currentDraftStudent = updatedDraftEntries[studentId] ?: StudentDailyEntry(studentId = studentId)

        if (targetStatus == AttendanceStatus.ABSENT) {
            // Absent rule: Attendance becomes ABSENT (0/19), while other elements remain at their current/default values and can be edited manually if needed
            dbEntriesToSave[studentId] = currentSavedStudent.copy(attendance = AttendanceStatus.ABSENT)
            updatedDraftEntries[studentId] = currentDraftStudent.copy(attendance = AttendanceStatus.ABSENT)
        } else {
            dbEntriesToSave[studentId] = currentSavedStudent.copy(attendance = AttendanceStatus.PRESENT)
            updatedDraftEntries[studentId] = currentDraftStudent.copy(attendance = AttendanceStatus.PRESENT)
        }

        saveDailySession(
            classId = classId,
            sectionId = sectionId,
            recordDate = recordDate,
            entries = dbEntriesToSave,
            academicYearId = academicYearId,
            periodId = periodId
        )
        return updatedDraftEntries
    }

    fun markStudentAbsentImmediately(
        studentId: Long,
        classId: Long,
        sectionId: Long,
        recordDate: String,
        currentDraftEntries: Map<Long, StudentDailyEntry>? = null,
        academicYearId: Long? = null,
        periodId: Long? = null
    ): Map<Long, StudentDailyEntry> {
        return setStudentAttendanceImmediately(
            studentId = studentId,
            classId = classId,
            sectionId = sectionId,
            recordDate = recordDate,
            targetStatus = AttendanceStatus.ABSENT,
            currentDraftEntries = currentDraftEntries,
            academicYearId = academicYearId,
            periodId = periodId
        )
    }

    fun resolveMatchingPeriodForDate(
        recordDate: String,
        preferredAcademicYearId: Long? = null,
        preferredPeriodId: Long? = null
    ): Pair<Long?, Long?> {
        val allPeriods = getStudyPeriods()
        if (preferredPeriodId != null) {
            val p = allPeriods.firstOrNull { it.id == preferredPeriodId }
            if (p != null) {
                return p.academicYearId to p.id
            }
        }
        val matchingPeriod = allPeriods.firstOrNull { sp ->
            val inYear = preferredAcademicYearId == null || sp.academicYearId == preferredAcademicYearId
            val afterStart = recordDate >= sp.startDate
            val beforeEnd = sp.endDate.isNullOrBlank() || recordDate <= sp.endDate
            inYear && afterStart && beforeEnd
        } ?: allPeriods.firstOrNull { sp ->
            val afterStart = recordDate >= sp.startDate
            val beforeEnd = sp.endDate.isNullOrBlank() || recordDate <= sp.endDate
            afterStart && beforeEnd
        }
        if (matchingPeriod != null) {
            return matchingPeriod.academicYearId to matchingPeriod.id
        }
        val fallbackYearId = preferredAcademicYearId ?: runCatching { getActiveAcademicYear().id }.getOrNull()
        return fallbackYearId to null
    }

    fun saveDailySession(
        classId: Long,
        sectionId: Long,
        recordDate: String,
        entries: Map<Long, StudentDailyEntry>,
        academicYearId: Long? = null,
        periodId: Long? = null
    ) {
        val (resolvedYearId, resolvedPeriodId) = resolveMatchingPeriodForDate(
            recordDate = recordDate,
            preferredAcademicYearId = academicYearId,
            preferredPeriodId = periodId
        )
        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            for ((studentId, entry) in entries) {
                // 1. Upsert DailyRecords with AcademicYearID and PeriodID
                db.execSQL(
                    """
                    INSERT INTO DailyRecords (StudentID, ClassID, SectionID, RecordDate, AcademicYearID, PeriodID, IsSaved, CreatedDate, ModifiedDate, UpdatedAt)
                    VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
                    ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
                        ClassID = excluded.ClassID,
                        SectionID = excluded.SectionID,
                        AcademicYearID = COALESCE(excluded.AcademicYearID, DailyRecords.AcademicYearID),
                        PeriodID = COALESCE(excluded.PeriodID, DailyRecords.PeriodID),
                        IsSaved = 1,
                        ModifiedDate = excluded.ModifiedDate,
                        UpdatedAt = excluded.UpdatedAt;
                    """.trimIndent(),
                    arrayOf<Any?>(
                        studentId,
                        classId,
                        sectionId,
                        recordDate,
                        resolvedYearId,
                        resolvedPeriodId,
                        now,
                        now,
                        now
                    )
                )

                // 2. Upsert Attendance
                db.execSQL(
                    """
                    INSERT INTO Attendance (StudentID, RecordDate, Status)
                    VALUES (?, ?, ?)
                    ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
                        Status = excluded.Status;
                    """.trimIndent(),
                    arrayOf<Any>(studentId, recordDate, entry.attendance.dbValue)
                )

                // 3. Upsert Evaluations
                val evalArgs = arrayOf<Any?>(
                    studentId,
                    recordDate,
                    entry.participation,
                    entry.notebook,
                    entry.homework,
                    entry.behavior
                )
                db.execSQL(
                    """
                    INSERT INTO Evaluations (StudentID, RecordDate, Participation, Notebook, Homework, Behavior)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
                        Participation = excluded.Participation,
                        Notebook = excluded.Notebook,
                        Homework = excluded.Homework,
                        Behavior = excluded.Behavior;
                    """.trimIndent(),
                    evalArgs
                )

                // 4. Upsert or delete DailyTests using this class's configured maximum.
                val testScore = entry.dailyTestScore
                if (testScore != null) {
                    val dailyTestMax = db.rawQuery(
                        "SELECT DailyTestMax FROM EvaluationSettings WHERE ClassID = ?",
                        arrayOf(classId.toString())
                    ).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getDouble(0) else 20.0
                    }
                    val clamped = testScore.coerceIn(0.0, dailyTestMax)
                    db.execSQL(
                        """
                        INSERT INTO DailyTests (StudentID, RecordDate, Score, TestValue)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
                            Score = excluded.Score,
                            TestValue = excluded.TestValue;
                        """.trimIndent(),
                        arrayOf<Any>(studentId, recordDate, clamped, clamped)
                    )
                } else {
                    db.delete(
                        "DailyTests",
                        "StudentID = ? AND RecordDate = ?",
                        arrayOf(studentId.toString(), recordDate)
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Deletes a saved daily tracking session for a specific (classId, sectionId, recordDate)
     * and removes the corresponding Attendance, Evaluations, and DailyTests rows for those students on that date.
     */
    fun deleteSavedSession(classId: Long, sectionId: Long, recordDate: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val studentIds = mutableListOf<Long>()
            db.rawQuery(
                "SELECT StudentID FROM DailyRecords WHERE ClassID = ? AND SectionID = ? AND RecordDate = ?",
                arrayOf(classId.toString(), sectionId.toString(), recordDate)
            ).use { c ->
                while (c.moveToNext()) {
                    studentIds.add(c.getLong(0))
                }
            }
            for (stId in studentIds) {
                db.delete("Attendance", "StudentID = ? AND RecordDate = ?", arrayOf(stId.toString(), recordDate))
                db.delete("Evaluations", "StudentID = ? AND RecordDate = ?", arrayOf(stId.toString(), recordDate))
                db.delete("DailyTests", "StudentID = ? AND RecordDate = ?", arrayOf(stId.toString(), recordDate))
            }
            db.delete(
                "DailyRecords",
                "ClassID = ? AND SectionID = ? AND RecordDate = ?",
                arrayOf(classId.toString(), sectionId.toString(), recordDate)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getSavedSessionsSummary(
        classId: Long? = null,
        sectionId: Long? = null,
        academicYearId: Long? = null,
        periodId: Long? = null
    ): List<SavedSessionSummary> {
        val db = dbHelper.readableDatabase
        val args = mutableListOf<String>()
        val whereSb = StringBuilder("WHERE 1=1")
        if (classId != null) {
            whereSb.append(" AND dr.ClassID = ?")
            args.add(classId.toString())
        }
        if (sectionId != null) {
            whereSb.append(" AND dr.SectionID = ?")
            args.add(sectionId.toString())
        }

        val yearsMap = runCatching { getAcademicYears().associateBy { it.id } }.getOrDefault(emptyMap())
        val periodsList = runCatching { getStudyPeriods() }.getOrDefault(emptyList())
        val periodsMap = periodsList.associateBy { it.id }

        val list = mutableListOf<SavedSessionSummary>()
        runCatching {
            val cursor = db.rawQuery(
                """
                SELECT
                    dr.RecordDate,
                    dr.ClassID,
                    COALESCE(c.ClassName, 'صف غير معروف') as ClassName,
                    dr.SectionID,
                    COALESCE(s.SectionName, '-') as SectionName,
                    COUNT(DISTINCT dr.StudentID) as StudentCount,
                    SUM(CASE WHEN a.Status = 'present' THEN 1 ELSE 0 END) as PresentCount,
                    SUM(CASE WHEN a.Status = 'absent' THEN 1 ELSE 0 END) as AbsentCount,
                    SUM(
                        (CASE WHEN e.Participation IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN e.Notebook IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN e.Homework IS NOT NULL THEN 1 ELSE 0 END) +
                        (CASE WHEN e.Behavior IS NOT NULL THEN 1 ELSE 0 END)
                    ) as EvalCount,
                    COUNT(dt.Score) as TestsCount,
                    MAX(dr.UpdatedAt) as LastUpdated,
                    MAX(dr.AcademicYearID) as AcademicYearID,
                    MAX(dr.PeriodID) as PeriodID,
                    COALESCE((SELECT dp.LessonTopic FROM DailyPlans dp WHERE dp.PlanDate = dr.RecordDate AND dp.ClassID = dr.ClassID ORDER BY dp.ID DESC LIMIT 1), '') as LessonTopic
                FROM DailyRecords dr
                LEFT JOIN Classes c ON c.ID = dr.ClassID
                LEFT JOIN Sections s ON s.ID = dr.SectionID
                LEFT JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
                LEFT JOIN Evaluations e ON e.StudentID = dr.StudentID AND e.RecordDate = dr.RecordDate
                LEFT JOIN DailyTests dt ON dt.StudentID = dr.StudentID AND dt.RecordDate = dr.RecordDate
                $whereSb
                GROUP BY dr.RecordDate, dr.ClassID, dr.SectionID
                ORDER BY dr.RecordDate DESC, dr.ClassID ASC, dr.SectionID ASC
                """.trimIndent(),
                args.toTypedArray()
            )
            cursor.use {
                while (it.moveToNext()) {
                    val recDate = if (it.isNull(0)) "" else (it.getString(0) ?: "")
                    if (recDate.isBlank()) continue
                    val explicitYearId = if (it.isNull(11)) null else it.getLong(11).takeIf { v -> v > 0L }
                    val explicitPeriodId = if (it.isNull(12)) null else it.getLong(12).takeIf { v -> v > 0L }

                    val matchedPeriod = if (explicitPeriodId != null && periodsMap.containsKey(explicitPeriodId)) {
                        periodsMap[explicitPeriodId]
                    } else {
                        periodsList.firstOrNull { sp ->
                            recDate >= sp.startDate && (sp.endDate.isNullOrBlank() || recDate <= sp.endDate)
                        }
                    }
                    val effectiveYearId = matchedPeriod?.academicYearId
                        ?: explicitYearId
                        ?: yearsMap.values.firstOrNull { y -> y.isActive }?.id
                    val effectiveYearName = effectiveYearId?.let { yId -> yearsMap[yId]?.yearName }

                    list.add(
                        SavedSessionSummary(
                            recordDate = recDate,
                            classId = if (it.isNull(1)) 0L else it.getLong(1),
                            className = if (it.isNull(2)) "صف غير معروف" else (it.getString(2) ?: "صف غير معروف"),
                            sectionId = if (it.isNull(3)) 0L else it.getLong(3),
                            sectionName = if (it.isNull(4)) "-" else (it.getString(4) ?: "-"),
                            studentCount = if (it.isNull(5)) 0 else it.getInt(5),
                            presentCount = if (it.isNull(6)) 0 else it.getInt(6),
                            absentCount = if (it.isNull(7)) 0 else it.getInt(7),
                            evaluationsCount = if (it.isNull(8)) 0 else it.getInt(8),
                            testsCount = if (it.isNull(9)) 0 else it.getInt(9),
                            updatedAt = if (it.isNull(10)) "" else (it.getString(10) ?: ""),
                            academicYearId = effectiveYearId,
                            academicYearName = effectiveYearName,
                            periodId = matchedPeriod?.id,
                            periodName = matchedPeriod?.periodName,
                            stageType = matchedPeriod?.stageType,
                            lessonTopic = if (it.isNull(13)) "" else (it.getString(13) ?: "")
                        )
                    )
                }
            }
        }
        return list.filter { item ->
            val matchYear = academicYearId == null || item.academicYearId == academicYearId
            val matchPeriod = periodId == null || item.periodId == periodId
            matchYear && matchPeriod
        }
    }

    fun getDatabaseStats(): Map<String, Int> {
        val db = dbHelper.readableDatabase
        fun count(table: String): Int {
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use {
                return if (it.moveToFirst()) it.getInt(0) else 0
            }
        }
        return mapOf(
            "classes" to count("Classes"),
            "sections" to count("Sections"),
            "students" to count("Students"),
            "records" to count("DailyRecords"),
            "holidays" to count("Holidays")
        )
    }

    // ---------------- DAILY SCHEDULE ("الجدول اليومي") ----------------
    fun getDailySchedules(): List<DailyScheduleEntity> {
        val db = dbHelper.readableDatabase
        val dayOrderCase = """
            CASE ds.DayOfWeek
                WHEN 'الأحد' THEN 1
                WHEN 'الاثنين' THEN 2
                WHEN 'الثلاثاء' THEN 3
                WHEN 'الأربعاء' THEN 4
                WHEN 'الخميس' THEN 5
                WHEN 'الجمعة' THEN 6
                WHEN 'السبت' THEN 7
                ELSE 8
            END
        """.trimIndent()

        val cursor = db.rawQuery(
            """
            SELECT
                ds.ID,
                ds.DayOfWeek,
                ds.ClassID,
                c.ClassName,
                ds.SectionID,
                s.SectionName,
                ds.LessonNumber,
                ds.StartTime,
                ds.EndTime,
                ds.CreatedDate
            FROM DailySchedule ds
            JOIN Classes c ON c.ID = ds.ClassID
            JOIN Sections s ON s.ID = ds.SectionID
            ORDER BY $dayOrderCase ASC, ds.LessonNumber ASC, ds.StartTime ASC, ds.ID ASC
            """.trimIndent(),
            null
        )
        val list = mutableListOf<DailyScheduleEntity>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DailyScheduleEntity(
                        id = it.getLong(0),
                        dayOfWeek = it.getString(1),
                        classId = it.getLong(2),
                        className = it.getString(3),
                        sectionId = it.getLong(4),
                        sectionName = it.getString(5),
                        lessonNumber = it.getInt(6),
                        startTime = it.getString(7),
                        endTime = it.getString(8),
                        createdDate = it.getString(9) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun isDuplicateDailyScheduleSlot(
        dayOfWeek: String,
        classId: Long,
        sectionId: Long,
        lessonNumber: Int,
        excludeId: Long? = null
    ): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = if (excludeId != null) {
            db.rawQuery(
                """
                SELECT COUNT(*) FROM DailySchedule
                WHERE DayOfWeek = ? AND ClassID = ? AND SectionID = ? AND LessonNumber = ? AND ID != ?
                """.trimIndent(),
                arrayOf(dayOfWeek, classId.toString(), sectionId.toString(), lessonNumber.toString(), excludeId.toString())
            )
        } else {
            db.rawQuery(
                """
                SELECT COUNT(*) FROM DailySchedule
                WHERE DayOfWeek = ? AND ClassID = ? AND SectionID = ? AND LessonNumber = ?
                """.trimIndent(),
                arrayOf(dayOfWeek, classId.toString(), sectionId.toString(), lessonNumber.toString())
            )
        }
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0) > 0
            }
        }
        return false
    }

    fun addDailyScheduleEntry(
        dayOfWeek: String,
        classId: Long,
        sectionId: Long,
        lessonNumber: Int,
        startTime: String,
        endTime: String
    ): Pair<Long, Boolean> {
        if (isDuplicateDailyScheduleSlot(dayOfWeek, classId, sectionId, lessonNumber, excludeId = null)) {
            return -1L to true
        }
        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("DayOfWeek", dayOfWeek.trim())
            put("ClassID", classId)
            put("SectionID", sectionId)
            put("LessonNumber", lessonNumber)
            put("StartTime", startTime.trim())
            put("EndTime", endTime.trim())
            put("CreatedDate", now)
        }
        val insertedId = db.insertWithOnConflict("DailySchedule", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (insertedId == -1L) {
            return -1L to true
        }
        return insertedId to false
    }

    fun updateDailyScheduleEntry(
        id: Long,
        dayOfWeek: String,
        classId: Long,
        sectionId: Long,
        lessonNumber: Int,
        startTime: String,
        endTime: String
    ): Boolean {
        if (isDuplicateDailyScheduleSlot(dayOfWeek, classId, sectionId, lessonNumber, excludeId = id)) {
            return true
        }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("DayOfWeek", dayOfWeek.trim())
            put("ClassID", classId)
            put("SectionID", sectionId)
            put("LessonNumber", lessonNumber)
            put("StartTime", startTime.trim())
            put("EndTime", endTime.trim())
        }
        val rows = runCatching {
            db.updateWithOnConflict("DailySchedule", cv, "ID = ?", arrayOf(id.toString()), SQLiteDatabase.CONFLICT_ABORT)
        }.getOrElse { -1 }
        return rows == -1
    }

    fun deleteDailyScheduleEntry(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete("DailySchedule", "ID = ?", arrayOf(id.toString()))
    }

    // ---------------- ACADEMIC YEARS & STUDY PERIODS ("العام الدراسي والفترات الدراسية") ----------------
    private fun ensureDefaultAcademicYearAndStagesExist() {
        val db = dbHelper.writableDatabase
        val count = db.rawQuery("SELECT COUNT(*) FROM AcademicYears", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        val now = Instant.now().toString()
        if (count == 0) {
            db.execSQL(
                "INSERT OR IGNORE INTO AcademicYears (ID, YearName, IsActive, CreatedDate) VALUES (1, '2026-2027', 1, '$now');"
            )
            ensureThreeStagesForYear(db, 1L, now)
        }
    }

    private fun ensureThreeStagesForYear(db: SQLiteDatabase, yearId: Long, now: String = Instant.now().toString()) {
        db.execSQL(
            "INSERT OR IGNORE INTO AcademicStages (AcademicYearID, StageType, StageName, StageOrder, CreatedDate) VALUES (?, 'FIRST_HALF', 'النصف الأول', 1, ?);",
            arrayOf<Any>(yearId, now)
        )
        db.execSQL(
            "INSERT OR IGNORE INTO AcademicStages (AcademicYearID, StageType, StageName, StageOrder, CreatedDate) VALUES (?, 'MID_YEAR', 'نصف السنة', 2, ?);",
            arrayOf<Any>(yearId, now)
        )
        db.execSQL(
            "INSERT OR IGNORE INTO AcademicStages (AcademicYearID, StageType, StageName, StageOrder, CreatedDate) VALUES (?, 'SECOND_HALF', 'النصف الثاني', 3, ?);",
            arrayOf<Any>(yearId, now)
        )
    }

    fun getAcademicYears(): List<AcademicYearEntity> {
        ensureDefaultAcademicYearAndStagesExist()
        val db = dbHelper.readableDatabase
        val list = mutableListOf<AcademicYearEntity>()
        db.rawQuery(
            "SELECT ID, YearName, IsActive, CreatedDate FROM AcademicYears ORDER BY IsActive DESC, YearName DESC, ID DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val yId = c.getLong(0)
                list.add(
                    AcademicYearEntity(
                        id = yId,
                        yearName = c.getString(1) ?: "",
                        isActive = c.getInt(2) == 1,
                        createdDate = c.getString(3) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getActiveAcademicYear(): AcademicYearEntity {
        val years = getAcademicYears()
        return years.firstOrNull { it.isActive } ?: years.first()
    }

    fun setActiveAcademicYear(yearId: Long) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE AcademicYears SET IsActive = 0;")
            db.execSQL("UPDATE AcademicYears SET IsActive = 1 WHERE ID = ?;", arrayOf<Any>(yearId))
            ensureThreeStagesForYear(db, yearId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun addAcademicYear(yearName: String, makeActive: Boolean = true): Pair<Long, String?> {
        val cleanName = yearName.trim()
        if (cleanName.isBlank()) {
            return -1L to "يرجى إدخال اسم العام الدراسي (مثال: 2026-2027)"
        }
        val existing = getAcademicYears().any { it.yearName.equals(cleanName, ignoreCase = true) }
        if (existing) {
            return -1L to "العام الدراسي «$cleanName» موجود مسبقاً"
        }

        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        var newId = -1L
        db.beginTransaction()
        try {
            if (makeActive) {
                db.execSQL("UPDATE AcademicYears SET IsActive = 0;")
            }
            val cv = ContentValues().apply {
                put("YearName", cleanName)
                put("IsActive", if (makeActive) 1 else 0)
                put("CreatedDate", now)
            }
            newId = db.insertOrThrow("AcademicYears", null, cv)
            ensureThreeStagesForYear(db, newId, now)
            db.setTransactionSuccessful()
        } catch (_: Exception) {
            return -1L to "تعذر إضافة العام الدراسي؛ تأكد من عدم تكرار الاسم"
        } finally {
            db.endTransaction()
        }
        return newId to null
    }

    fun updateAcademicYear(yearId: Long, newYearName: String): String? {
        val cleanName = newYearName.trim()
        if (cleanName.isBlank()) {
            return "يرجى إدخال اسم العام الدراسي"
        }
        val duplicate = getAcademicYears().any { it.id != yearId && it.yearName.equals(cleanName, ignoreCase = true) }
        if (duplicate) {
            return "العام الدراسي «$cleanName» موجود مسبقاً"
        }
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("YearName", cleanName)
        }
        db.update("AcademicYears", cv, "ID = ?", arrayOf(yearId.toString()))
        ensureThreeStagesForYear(db, yearId)
        return null
    }

    fun deleteAcademicYear(yearId: Long): String? {
        val allYears = getAcademicYears()
        if (allYears.size <= 1) {
            return "لا يمكن حذف العام الدراسي الوحيد في النظام"
        }
        val periodsInYear = getStudyPeriods(academicYearId = yearId)
        if (periodsInYear.isNotEmpty()) {
            return "لا يمكن حذف هذا العام الدراسي لوجود (${periodsInYear.size}) فترة مرتبطة به. يرجى حذف الفترات المرتبطة أولاً للحفاظ على سلامة البيانات."
        }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("AcademicStages", "AcademicYearID = ?", arrayOf(yearId.toString()))
            db.delete("AcademicYears", "ID = ?", arrayOf(yearId.toString()))
            // Ensure at least one remaining year is marked active
            val activeCount = db.rawQuery("SELECT COUNT(*) FROM AcademicYears WHERE IsActive = 1", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            if (activeCount == 0) {
                db.execSQL(
                    "UPDATE AcademicYears SET IsActive = 1 WHERE ID = (SELECT ID FROM AcademicYears ORDER BY YearName DESC, ID DESC LIMIT 1);"
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    fun getStagesForAcademicYear(academicYearId: Long): List<AcademicStageEntity> {
        val db = dbHelper.writableDatabase
        ensureThreeStagesForYear(db, academicYearId)
        val list = mutableListOf<AcademicStageEntity>()
        db.rawQuery(
            """
            SELECT ID, AcademicYearID, StageType, StageName, StageOrder, CreatedDate
            FROM AcademicStages
            WHERE AcademicYearID = ?
            ORDER BY StageOrder ASC, ID ASC
            """.trimIndent(),
            arrayOf(academicYearId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val stageType = AcademicStage.fromDb(c.getString(2))
                list.add(
                    AcademicStageEntity(
                        id = c.getLong(0),
                        academicYearId = c.getLong(1),
                        stageType = stageType,
                        stageName = c.getString(3) ?: stageType.arabicLabel,
                        stageOrder = c.getInt(4),
                        createdDate = c.getString(5) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getStudyPeriods(
        academicYearId: Long? = null,
        stageType: AcademicStage? = null
    ): List<StudyPeriodEntity> {
        ensureDefaultAcademicYearAndStagesExist()
        val db = dbHelper.readableDatabase
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (academicYearId != null) {
            whereClauses.add("AcademicYearID = ?")
            args.add(academicYearId.toString())
        }
        if (stageType != null) {
            whereClauses.add("StageType = ?")
            args.add(stageType.dbValue)
        }
        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else {
            ""
        }
        val stageOrderCase = """
            CASE StageType
                WHEN 'FIRST_HALF' THEN 1
                WHEN 'MID_YEAR' THEN 2
                WHEN 'SECOND_HALF' THEN 3
                ELSE 4
            END
        """.trimIndent()

        val cursor = db.rawQuery(
            """
            SELECT ID, PeriodName, StartDate, EndDate, Status, CreatedDate, AcademicYearID, StageType, SequenceNumber
            FROM StudyPeriods
            $whereSql
            ORDER BY AcademicYearID ASC, $stageOrderCase ASC, SequenceNumber ASC, StartDate ASC, ID ASC
            """.trimIndent(),
            args.toTypedArray()
        )
        val list = mutableListOf<StudyPeriodEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val endDateRaw = if (it.isNull(3)) null else it.getString(3)?.takeIf { s -> s.isNotBlank() }
                val yId = if (it.isNull(6) || it.getLong(6) <= 0L) 1L else it.getLong(6)
                val sType = AcademicStage.fromDb(if (it.isNull(7)) "FIRST_HALF" else it.getString(7))
                val seq = if (it.isNull(8) || it.getInt(8) <= 0) 1 else it.getInt(8)
                list.add(
                    StudyPeriodEntity(
                        id = it.getLong(0),
                        periodName = it.getString(1) ?: "",
                        startDate = it.getString(2) ?: "",
                        endDate = endDateRaw,
                        status = StudyPeriodStatus.fromDb(it.getString(4)),
                        createdDate = it.getString(5) ?: "",
                        academicYearId = yId,
                        stageType = sType,
                        sequenceNumber = seq
                    )
                )
            }
        }
        return list
    }

    fun getOpenStudyPeriod(): StudyPeriodEntity? {
        return getStudyPeriods().firstOrNull { it.status == StudyPeriodStatus.OPEN }
    }

    fun getOrdinalPeriodName(sequence: Int): String {
        return when (sequence) {
            1 -> "الفترة الأولى"
            2 -> "الفترة الثانية"
            3 -> "الفترة الثالثة"
            4 -> "الفترة الرابعة"
            5 -> "الفترة الخامسة"
            6 -> "الفترة السادسة"
            7 -> "الفترة السابعة"
            8 -> "الفترة الثامنة"
            9 -> "الفترة التاسعة"
            10 -> "الفترة العاشرة"
            else -> "الفترة $sequence"
        }
    }

    fun getSuggestedNextPeriodName(
        academicYearId: Long = getActiveAcademicYear().id,
        stageType: AcademicStage = AcademicStage.FIRST_HALF
    ): String {
        if (stageType == AcademicStage.MID_YEAR) {
            return "الفترة"
        }
        val count = getStudyPeriods(academicYearId = academicYearId, stageType = stageType).size
        return getOrdinalPeriodName(count + 1)
    }

    /**
     * Re-indexes SequenceNumber (1, 2, 3, ...) for all periods inside a given (academicYearId, stageType)
     * ordered by StartDate ASC, ID ASC so period ordering is always consistent and gap-free.
     */
    private fun resequenceStagePeriods(db: SQLiteDatabase, academicYearId: Long, stageType: AcademicStage) {
        val ids = mutableListOf<Long>()
        db.rawQuery(
            """
            SELECT ID FROM StudyPeriods
            WHERE AcademicYearID = ? AND StageType = ?
            ORDER BY StartDate ASC, SequenceNumber ASC, ID ASC
            """.trimIndent(),
            arrayOf(academicYearId.toString(), stageType.dbValue)
        ).use { c ->
            while (c.moveToNext()) {
                ids.add(c.getLong(0))
            }
        }
        ids.forEachIndexed { idx, pid ->
            val cv = ContentValues().apply { put("SequenceNumber", idx + 1) }
            db.update("StudyPeriods", cv, "ID = ?", arrayOf(pid.toString()))
        }
    }

    /**
     * Validates a period's start and end dates and checks for date overlap ONLY within
     * the same (academicYearId, stageType) as required:
     * - FIRST_HALF periods are checked against other FIRST_HALF periods in the same AcademicYear.
     * - SECOND_HALF periods are checked against other SECOND_HALF periods in the same AcademicYear.
     * - MID_YEAR allows only a single period per AcademicYear.
     */
    fun validateStagePeriodDates(
        academicYearId: Long,
        stageType: AcademicStage,
        startDate: String,
        endDate: String,
        excludeId: Long? = null
    ): String? {
        val cleanStart = startDate.trim()
        val cleanEnd = endDate.trim()
        if (cleanStart.isBlank()) {
            return "يرجى تحديد تاريخ البداية"
        }
        if (cleanEnd.isBlank()) {
            return "يرجى تحديد تاريخ النهاية"
        }
        if (cleanStart > cleanEnd) {
            return "لا يسمح بحفظ فترة يكون تاريخ نهايتها قبل تاريخ بدايتها"
        }

        val sameStagePeriods = getStudyPeriods(
            academicYearId = academicYearId,
            stageType = stageType
        ).filter { it.id != excludeId }

        if (stageType == AcademicStage.MID_YEAR && sameStagePeriods.isNotEmpty()) {
            return "مرحلة نصف السنة تحتوي على فترة واحدة فقط ولا يسمح بإضافة فترة ثانية داخلها"
        }

        for (other in sameStagePeriods) {
            val otherStart = other.startDate
            val otherEnd = other.endDate ?: other.startDate
            // Overlap check: [cleanStart, cleanEnd] overlaps with [otherStart, otherEnd]
            val overlaps = cleanStart <= otherEnd && cleanEnd >= otherStart
            if (overlaps) {
                return "يوجد تداخل في التواريخ مع «${other.periodName}» (${formatIsoDateToDisplay(otherStart)} → ${formatIsoDateToDisplay(otherEnd)}) داخل ${stageType.arabicLabel}"
            }
        }
        return null
    }

    /**
     * Adds a new period inside a specific AcademicYear and AcademicStage.
     * - Unlimited periods allowed in FIRST_HALF and SECOND_HALF.
     * - Strictly 1 period allowed in MID_YEAR.
     * - Requires both startDate and endDate with startDate <= endDate and no overlap within the same stage.
     */
    fun addPeriodToStage(
        academicYearId: Long,
        stageType: AcademicStage,
        periodName: String,
        startDate: String,
        endDate: String
    ): Pair<Long, String?> {
        val existingInStage = getStudyPeriods(academicYearId = academicYearId, stageType = stageType)
        if (stageType == AcademicStage.MID_YEAR && existingInStage.isNotEmpty()) {
            return -1L to "مرحلة نصف السنة تحتوي على فترة واحدة فقط ولا يسمح بإضافة فترة ثانية داخلها"
        }

        val nextSeq = existingInStage.size + 1
        val cleanName = periodName.trim().ifBlank {
            if (stageType == AcademicStage.MID_YEAR) "الفترة" else getOrdinalPeriodName(nextSeq)
        }
        val cleanStart = startDate.trim()
        val cleanEnd = endDate.trim()

        val validationError = validateStagePeriodDates(
            academicYearId = academicYearId,
            stageType = stageType,
            startDate = cleanStart,
            endDate = cleanEnd,
            excludeId = null
        )
        if (validationError != null) {
            return -1L to validationError
        }

        val db = dbHelper.writableDatabase
        ensureThreeStagesForYear(db, academicYearId)
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("AcademicYearID", academicYearId)
            put("StageType", stageType.dbValue)
            put("SequenceNumber", nextSeq)
            put("PeriodName", cleanName)
            put("StartDate", cleanStart)
            put("EndDate", cleanEnd)
            put("Status", StudyPeriodStatus.CLOSED.dbValue)
            put("CreatedDate", now)
        }

        val insertedId = runCatching {
            db.insertOrThrow("StudyPeriods", null, cv)
        }.getOrElse { -1L }

        if (insertedId == -1L) {
            return -1L to if (stageType == AcademicStage.MID_YEAR) {
                "مرحلة نصف السنة تحتوي على فترة واحدة فقط ولا يسمح بإضافة فترة ثانية داخلها"
            } else {
                "تعذر حفظ الفترة الدراسية"
            }
        }

        resequenceStagePeriods(db, academicYearId, stageType)
        return insertedId to null
    }

    /**
     * Updates an existing period's name, startDate, and endDate while enforcing
     * startDate <= endDate and non-overlap within the same stage.
     */
    fun updateStagePeriod(
        id: Long,
        periodName: String,
        startDate: String,
        endDate: String
    ): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة الدراسية غير موجودة"

        val cleanName = periodName.trim()
        if (cleanName.isBlank()) {
            return "يرجى إدخال اسم الفترة"
        }
        val cleanStart = startDate.trim()
        val cleanEnd = endDate.trim()

        val validationError = validateStagePeriodDates(
            academicYearId = target.academicYearId,
            stageType = target.stageType,
            startDate = cleanStart,
            endDate = cleanEnd,
            excludeId = id
        )
        if (validationError != null) {
            return validationError
        }

        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("PeriodName", cleanName)
            put("StartDate", cleanStart)
            put("EndDate", cleanEnd)
            put("Status", StudyPeriodStatus.CLOSED.dbValue)
        }
        db.update("StudyPeriods", cv, "ID = ?", arrayOf(id.toString()))
        resequenceStagePeriods(db, target.academicYearId, target.stageType)
        return null
    }

    /**
     * Safely deletes a period if no linked PeriodGrades with written exam scores prevent deletion,
     * and never deletes any student, attendance, or daily evaluation records.
     * Returns an Arabic error message if blocked by linked data, or null on success.
     */
    fun deleteStagePeriodSafely(id: Long): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة غير موجودة"

        val db = dbHelper.writableDatabase
        val linkedGradesCount = db.rawQuery(
            "SELECT COUNT(*) FROM PeriodGrades WHERE PeriodID = ? AND WrittenExamGrade IS NOT NULL",
            arrayOf(id.toString())
        ).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        if (linkedGradesCount > 0) {
            return "لا يمكن حذف «${target.periodName}» لوجود درجات مرتبطة بها ($linkedGradesCount درجة مسجلة). حفاظاً على البيانات لا يتم الحذف العشوائي."
        }

        db.beginTransaction()
        try {
            db.delete("PeriodGrades", "PeriodID = ?", arrayOf(id.toString()))
            db.delete("StudyPeriods", "ID = ?", arrayOf(id.toString()))
            resequenceStagePeriods(db, target.academicYearId, target.stageType)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return null
    }

    // Legacy compatibility helpers kept intact so other screens compile and work without changes
    fun validateStudyPeriodDates(
        startDate: String,
        endDate: String?,
        excludeId: Long? = null
    ): String? {
        val activeYearId = getActiveAcademicYear().id
        val target = excludeId?.let { id -> getStudyPeriods().firstOrNull { it.id == id } }
        val yearId = target?.academicYearId ?: activeYearId
        val stage = target?.stageType ?: AcademicStage.FIRST_HALF
        return validateStagePeriodDates(
            academicYearId = yearId,
            stageType = stage,
            startDate = startDate,
            endDate = endDate ?: startDate,
            excludeId = excludeId
        )
    }

    private fun formatIsoDateToDisplay(isoDate: String): String {
        val parts = isoDate.trim().split("-")
        if (parts.size != 3) return isoDate
        return "${parts[2]}/${parts[1]}/${parts[0]}"
    }

    fun startNewStudyPeriod(
        periodName: String,
        startDate: String
    ): Pair<Long, String?> {
        val activeYear = getActiveAcademicYear()
        return addPeriodToStage(
            academicYearId = activeYear.id,
            stageType = AcademicStage.FIRST_HALF,
            periodName = periodName,
            startDate = startDate,
            endDate = startDate
        )
    }

    fun closeStudyPeriod(
        id: Long,
        endDate: String
    ): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة الدراسية غير موجودة"
        return updateStagePeriod(
            id = id,
            periodName = target.periodName,
            startDate = target.startDate,
            endDate = endDate
        )
    }

    fun updateStudyPeriod(
        id: Long,
        periodName: String,
        startDate: String,
        endDate: String?
    ): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة الدراسية غير موجودة"
        return updateStagePeriod(
            id = id,
            periodName = periodName,
            startDate = startDate,
            endDate = endDate ?: target.endDate ?: startDate
        )
    }

    fun deleteStudyPeriod(id: Long) {
        deleteStagePeriodSafely(id)
    }

    // ---------------- PERIOD GRADES ("درجات الفترات والامتحان التحريري والمعدل") ----------------
    fun getPeriodGrades(
        studentId: Long? = null,
        periodId: Long? = null
    ): List<PeriodGradeEntity> {
        val db = dbHelper.readableDatabase
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (studentId != null) {
            whereClauses.add("StudentID = ?")
            args.add(studentId.toString())
        }
        if (periodId != null) {
            whereClauses.add("PeriodID = ?")
            args.add(periodId.toString())
        }
        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else {
            ""
        }

        val cursor = db.rawQuery(
            """
            SELECT ID, StudentID, PeriodID, DailyGrade, WrittenExamGrade, PeriodAverage, UpdatedDate
            FROM PeriodGrades
            $whereSql
            ORDER BY PeriodID ASC, StudentID ASC
            """.trimIndent(),
            args.toTypedArray()
        )
        val list = mutableListOf<PeriodGradeEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val daily = if (it.isNull(3)) null else it.getDouble(3)
                val written = if (it.isNull(4)) null else it.getDouble(4)
                val avg = if (it.isNull(5)) null else it.getDouble(5)
                list.add(
                    PeriodGradeEntity(
                        id = it.getLong(0),
                        studentId = it.getLong(1),
                        periodId = it.getLong(2),
                        dailyGrade = daily,
                        writtenExamGrade = written,
                        periodAverage = avg,
                        updatedDate = it.getString(6) ?: ""
                    )
                )
            }
        }
        return list
    }

    /**
     * Saves or updates a student's WrittenExamGrade (0..100 or null to clear) and synchronized
     * DailyGrade + PeriodAverage inside PeriodGrades (UNIQUE(StudentID, PeriodID)).
     * - For FIRST_HALF and SECOND_HALF periods: PeriodAverage = (DailyGrade + WrittenExamGrade) / 2.
     * - For MID_YEAR ("نصف السنة"): Mid-Year is a single direct exam out of 100 with no internal daily average,
     *   so PeriodAverage = WrittenExamGrade directly (and null when not entered).
     * Returns an Arabic validation error if writtenExamGrade is outside [0, 100], or null on success.
     */
    fun upsertPeriodGrade(
        studentId: Long,
        periodId: Long,
        dailyGrade: Double,
        writtenExamGrade: Double?
    ): String? {
        if (writtenExamGrade != null) {
            if (writtenExamGrade.isNaN() || writtenExamGrade < 0.0 || writtenExamGrade > 100.0) {
                return "يجب أن تكون الدرجة بين 0 و 100"
            }
        }
        val targetPeriod = getStudyPeriods().firstOrNull { it.id == periodId }
        val isMidYear = targetPeriod?.stageType == AcademicStage.MID_YEAR

        val roundedDaily: Double? = if (isMidYear) {
            null
        } else {
            kotlin.math.round(dailyGrade.coerceIn(0.0, 100.0) * 100.0) / 100.0
        }
        val roundedWritten = writtenExamGrade?.let { kotlin.math.round(it.coerceIn(0.0, 100.0) * 100.0) / 100.0 }
        val periodAvg = if (roundedWritten != null) {
            if (isMidYear) {
                roundedWritten
            } else {
                kotlin.math.round((((roundedDaily ?: 0.0) + roundedWritten) / 2.0) * 100.0) / 100.0
            }
        } else {
            null
        }

        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        db.execSQL(
            """
            INSERT INTO PeriodGrades (StudentID, PeriodID, DailyGrade, WrittenExamGrade, PeriodAverage, UpdatedDate)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(StudentID, PeriodID) DO UPDATE SET
                DailyGrade = excluded.DailyGrade,
                WrittenExamGrade = excluded.WrittenExamGrade,
                PeriodAverage = excluded.PeriodAverage,
                UpdatedDate = excluded.UpdatedDate;
            """.trimIndent(),
            arrayOf<Any?>(
                studentId,
                periodId,
                roundedDaily,
                roundedWritten,
                periodAvg,
                now
            )
        )
        return null
    }

    // ---------------- DAILY PLANS ("الخطة اليومية") ----------------
    fun getDailyPlanImagesDir(context: android.content.Context): java.io.File {
        val dir = java.io.File(context.filesDir, "daily_plan_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDailyPlans(
        filterDate: String? = null,
        filterClassId: Long? = null,
        filterSectionId: Long? = null,
        searchTopic: String? = null
    ): List<DailyPlanEntity> {
        val db = dbHelper.readableDatabase
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (!filterDate.isNullOrBlank()) {
            whereClauses.add("dp.PlanDate = ?")
            args.add(filterDate.trim())
        }
        if (filterClassId != null && filterClassId > 0L) {
            whereClauses.add("dp.ClassID = ?")
            args.add(filterClassId.toString())
        }
        if (filterSectionId != null && filterSectionId > 0L) {
            whereClauses.add("EXISTS (SELECT 1 FROM DailyPlanSections dps WHERE dps.PlanID = dp.ID AND dps.SectionID = ?)")
            args.add(filterSectionId.toString())
        }
        if (!searchTopic.isNullOrBlank()) {
            whereClauses.add("dp.LessonTopic LIKE ?")
            args.add("%${searchTopic.trim()}%")
        }

        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else {
            ""
        }

        val plansRaw = mutableListOf<Triple<Long, Array<String>, Long>>() // id, [planDate, className, lessonTopic, createdDate, updatedDate], classId
        db.rawQuery(
            """
            SELECT dp.ID, dp.PlanDate, dp.ClassID, COALESCE(c.ClassName, ''), dp.LessonTopic, dp.CreatedDate, dp.UpdatedDate
            FROM DailyPlans dp
            LEFT JOIN Classes c ON c.ID = dp.ClassID
            $whereSql
            ORDER BY dp.PlanDate DESC, dp.ID DESC
            """.trimIndent(),
            args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val planDate = c.getString(1) ?: ""
                val clsId = c.getLong(2)
                val clsName = c.getString(3) ?: ""
                val topic = c.getString(4) ?: ""
                val created = c.getString(5) ?: ""
                val updated = c.getString(6) ?: ""
                plansRaw.add(Triple(id, arrayOf(planDate, clsName, topic, created, updated), clsId))
            }
        }

        if (plansRaw.isEmpty()) return emptyList()

        // Load sections per plan
        val sectionsByPlan = mutableMapOf<Long, MutableList<Pair<Long, String>>>()
        db.rawQuery(
            """
            SELECT dps.PlanID, dps.SectionID, COALESCE(s.SectionName, '')
            FROM DailyPlanSections dps
            LEFT JOIN Sections s ON s.ID = dps.SectionID
            ORDER BY dps.SectionID ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val pId = c.getLong(0)
                val sId = c.getLong(1)
                val sName = c.getString(2) ?: ""
                sectionsByPlan.getOrPut(pId) { mutableListOf() }.add(sId to sName)
            }
        }

        // Load images per plan
        val imagesByPlan = mutableMapOf<Long, MutableList<DailyPlanImageEntity>>()
        db.rawQuery(
            """
            SELECT ID, PlanID, FileName, ImageBase64, SortOrder, CreatedDate
            FROM DailyPlanImages
            ORDER BY PlanID ASC, SortOrder ASC, ID ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val imgId = c.getLong(0)
                val pId = c.getLong(1)
                val fName = c.getString(2) ?: ""
                val b64 = c.getString(3) ?: ""
                val order = c.getInt(4)
                val created = c.getString(5) ?: ""
                imagesByPlan.getOrPut(pId) { mutableListOf() }.add(
                    DailyPlanImageEntity(
                        id = imgId,
                        planId = pId,
                        fileName = fName,
                        imageBase64 = b64,
                        sortOrder = order,
                        createdDate = created
                    )
                )
            }
        }

        return plansRaw.map { (planId, fields, clsId) ->
            val secPairs = sectionsByPlan[planId] ?: emptyList()
            DailyPlanEntity(
                id = planId,
                planDate = fields[0],
                classId = clsId,
                className = fields[1],
                sectionIds = secPairs.map { it.first },
                sectionNames = secPairs.map { it.second },
                lessonTopic = fields[2],
                images = imagesByPlan[planId] ?: emptyList(),
                createdDate = fields[3],
                updatedDate = fields[4]
            )
        }
    }

    /**
     * Gets all DailyPlans matching a specific date, classId, and sectionId (for StudentProfileScreen integration).
     */
    fun getDailyPlansForClassSectionAndDate(
        classId: Long,
        sectionId: Long,
        recordDate: String
    ): List<DailyPlanEntity> {
        return getDailyPlans(
            filterDate = recordDate,
            filterClassId = classId,
            filterSectionId = sectionId
        )
    }

    /**
     * Gets all DailyPlans matching a classId and sectionId across all dates (keyed by PlanDate),
     * used for displaying daily lesson plans inside StudentProfileScreen.
     */
    fun getDailyPlansForClassAndSection(
        classId: Long,
        sectionId: Long
    ): List<DailyPlanEntity> {
        return getDailyPlans(
            filterClassId = classId,
            filterSectionId = sectionId
        )
    }

    /**
     * Saves a new DailyPlan or updates an existing one (when existingPlanId != null).
     * Supports selecting one or multiple sections from the same class, and attaching unlimited images.
     * Images are stored both in internal app storage (`filesDir/daily_plan_images/<fileName>`) AND as Base64
     * inside `DailyPlanImages` so SQLite backups are 100% self-contained.
     */
    fun saveDailyPlan(
        context: android.content.Context,
        existingPlanId: Long? = null,
        planDate: String,
        classId: Long,
        sectionIds: List<Long>,
        lessonTopic: String,
        images: List<Pair<String, String>> // Pair(fileName, imageBase64)
    ): Pair<Long, String?> {
        val cleanDate = planDate.trim()
        if (cleanDate.isEmpty()) {
            return -1L to "يرجى اختيار التاريخ للخطة اليومية"
        }
        if (classId <= 0L) {
            return -1L to "يرجى اختيار الصف الدراسي"
        }
        val distinctSecIds = sectionIds.distinct().filter { it > 0L }
        if (distinctSecIds.isEmpty()) {
            return -1L to "يرجى اختيار شعبة واحدة على الأقل من الصف المحدد"
        }
        val cleanTopic = lessonTopic.trim()
        if (cleanTopic.isEmpty()) {
            return -1L to "يرجى كتابة عنوان الدرس أو الموضوع"
        }

        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        val imagesDir = getDailyPlanImagesDir(context)
        var savedPlanId = existingPlanId ?: -1L

        db.beginTransaction()
        try {
            if (existingPlanId != null && existingPlanId > 0L) {
                // Find old image files that were removed so we can clean them from disk
                val oldFileNames = mutableListOf<String>()
                db.rawQuery(
                    "SELECT FileName FROM DailyPlanImages WHERE PlanID = ?",
                    arrayOf(existingPlanId.toString())
                ).use { c ->
                    while (c.moveToNext()) {
                        oldFileNames.add(c.getString(0) ?: "")
                    }
                }
                val keptNames = images.map { it.first }.toSet()
                oldFileNames.filter { it.isNotEmpty() && it !in keptNames }.forEach { removedName ->
                    runCatching { java.io.File(imagesDir, removedName).delete() }
                }

                val cv = ContentValues().apply {
                    put("PlanDate", cleanDate)
                    put("ClassID", classId)
                    put("LessonTopic", cleanTopic)
                    put("UpdatedDate", now)
                }
                db.update("DailyPlans", cv, "ID = ?", arrayOf(existingPlanId.toString()))
                db.delete("DailyPlanSections", "PlanID = ?", arrayOf(existingPlanId.toString()))
                db.delete("DailyPlanImages", "PlanID = ?", arrayOf(existingPlanId.toString()))
                savedPlanId = existingPlanId
            } else {
                val cv = ContentValues().apply {
                    put("PlanDate", cleanDate)
                    put("ClassID", classId)
                    put("LessonTopic", cleanTopic)
                    put("CreatedDate", now)
                    put("UpdatedDate", now)
                }
                savedPlanId = db.insertOrThrow("DailyPlans", null, cv)
            }

            distinctSecIds.forEach { secId ->
                val secCv = ContentValues().apply {
                    put("PlanID", savedPlanId)
                    put("SectionID", secId)
                }
                db.insertWithOnConflict("DailyPlanSections", null, secCv, SQLiteDatabase.CONFLICT_IGNORE)
            }

            images.forEachIndexed { idx, (rawFileName, base64Data) ->
                val fName = rawFileName.ifBlank { "plan_${savedPlanId}_img_${idx + 1}_${System.currentTimeMillis()}.jpg" }
                // Write physical file to internal storage if not already written
                if (base64Data.isNotBlank()) {
                    runCatching {
                        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                        val outFile = java.io.File(imagesDir, fName)
                        if (!outFile.exists() || outFile.length() == 0L) {
                            java.io.FileOutputStream(outFile).use { it.write(bytes) }
                        }
                    }
                }
                val imgCv = ContentValues().apply {
                    put("PlanID", savedPlanId)
                    put("FileName", fName)
                    put("ImageBase64", base64Data)
                    put("SortOrder", idx)
                    put("CreatedDate", now)
                }
                db.insert("DailyPlanImages", null, imgCv)
            }

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            return -1L to (e.message ?: "تعذر حفظ الخطة اليومية")
        } finally {
            db.endTransaction()
        }

        return savedPlanId to null
    }

    /**
     * Deletes a DailyPlan and cleans up its physical image files from internal storage.
     */
    fun deleteDailyPlan(context: android.content.Context, planId: Long) {
        val db = dbHelper.writableDatabase
        val imagesDir = getDailyPlanImagesDir(context)
        val fileNames = mutableListOf<String>()
        runCatching {
            db.rawQuery(
                "SELECT FileName FROM DailyPlanImages WHERE PlanID = ?",
                arrayOf(planId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    fileNames.add(c.getString(0) ?: "")
                }
            }
        }
        db.beginTransaction()
        try {
            db.delete("DailyPlanImages", "PlanID = ?", arrayOf(planId.toString()))
            db.delete("DailyPlanSections", "PlanID = ?", arrayOf(planId.toString()))
            db.delete("DailyPlans", "ID = ?", arrayOf(planId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        fileNames.filter { it.isNotBlank() }.forEach { fName ->
            runCatching { java.io.File(imagesDir, fName).delete() }
        }
    }

    /**
     * Syncs physical image files in `filesDir/daily_plan_images` from the SQLite `DailyPlanImages` table
     * after restoring a backup (and removes orphan files not present in the restored DB).
     */
    fun syncDailyPlanImageFilesFromDb(context: android.content.Context) {
        runCatching {
            val db = dbHelper.readableDatabase
            val imagesDir = getDailyPlanImagesDir(context)
            val validNames = mutableSetOf<String>()
            db.rawQuery("SELECT FileName, ImageBase64 FROM DailyPlanImages", null).use { c ->
                while (c.moveToNext()) {
                    val fName = c.getString(0) ?: continue
                    val b64 = c.getString(1) ?: ""
                    if (fName.isNotBlank()) {
                        validNames.add(fName)
                        val targetFile = java.io.File(imagesDir, fName)
                        if ((!targetFile.exists() || targetFile.length() == 0L) && b64.isNotBlank()) {
                            runCatching {
                                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                java.io.FileOutputStream(targetFile).use { it.write(bytes) }
                            }
                        }
                    }
                }
            }
            imagesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name !in validNames) {
                    runCatching { file.delete() }
                }
            }
        }
    }
}

