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
        }
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
        currentDraftEntries: Map<Long, StudentDailyEntry>? = null
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
            val absentEntry = StudentDailyEntry(
                studentId = studentId,
                attendance = AttendanceStatus.ABSENT,
                participation = null,
                notebook = null,
                homework = null,
                behavior = null,
                dailyTestScore = null
            )
            dbEntriesToSave[studentId] = absentEntry
            updatedDraftEntries[studentId] = absentEntry
        } else {
            dbEntriesToSave[studentId] = currentSavedStudent.copy(attendance = AttendanceStatus.PRESENT)
            updatedDraftEntries[studentId] = currentDraftStudent.copy(attendance = AttendanceStatus.PRESENT)
        }

        saveDailySession(
            classId = classId,
            sectionId = sectionId,
            recordDate = recordDate,
            entries = dbEntriesToSave
        )
        return updatedDraftEntries
    }

    fun markStudentAbsentImmediately(
        studentId: Long,
        classId: Long,
        sectionId: Long,
        recordDate: String,
        currentDraftEntries: Map<Long, StudentDailyEntry>? = null
    ): Map<Long, StudentDailyEntry> {
        return setStudentAttendanceImmediately(
            studentId = studentId,
            classId = classId,
            sectionId = sectionId,
            recordDate = recordDate,
            targetStatus = AttendanceStatus.ABSENT,
            currentDraftEntries = currentDraftEntries
        )
    }

    fun saveDailySession(
        classId: Long,
        sectionId: Long,
        recordDate: String,
        entries: Map<Long, StudentDailyEntry>
    ) {
        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            for ((studentId, entry) in entries) {
                // 1. Upsert DailyRecords
                db.execSQL(
                    """
                    INSERT INTO DailyRecords (StudentID, ClassID, SectionID, RecordDate, IsSaved, CreatedDate, ModifiedDate, UpdatedAt)
                    VALUES (?, ?, ?, ?, 1, ?, ?, ?)
                    ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
                        ClassID = excluded.ClassID,
                        SectionID = excluded.SectionID,
                        IsSaved = 1,
                        ModifiedDate = excluded.ModifiedDate,
                        UpdatedAt = excluded.UpdatedAt;
                    """.trimIndent(),
                    arrayOf<Any>(studentId, classId, sectionId, recordDate, now, now, now)
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

                // 4. Upsert or delete DailyTests
                val testScore = entry.dailyTestScore
                if (testScore != null) {
                    val clamped = testScore.coerceIn(0.0, 5.0)
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

    fun getSavedSessionsSummary(
        classId: Long? = null,
        sectionId: Long? = null
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

        val cursor = db.rawQuery(
            """
            SELECT
                dr.RecordDate,
                dr.ClassID,
                c.ClassName,
                dr.SectionID,
                s.SectionName,
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
                MAX(dr.UpdatedAt) as LastUpdated
            FROM DailyRecords dr
            JOIN Classes c ON c.ID = dr.ClassID
            JOIN Sections s ON s.ID = dr.SectionID
            LEFT JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
            LEFT JOIN Evaluations e ON e.StudentID = dr.StudentID AND e.RecordDate = dr.RecordDate
            LEFT JOIN DailyTests dt ON dt.StudentID = dr.StudentID AND dt.RecordDate = dr.RecordDate
            $whereSb
            GROUP BY dr.RecordDate, dr.ClassID, dr.SectionID
            ORDER BY dr.RecordDate DESC, dr.ClassID ASC, dr.SectionID ASC
            """.trimIndent(),
            args.toTypedArray()
        )
        val list = mutableListOf<SavedSessionSummary>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SavedSessionSummary(
                        recordDate = it.getString(0),
                        classId = it.getLong(1),
                        className = it.getString(2),
                        sectionId = it.getLong(3),
                        sectionName = it.getString(4),
                        studentCount = it.getInt(5),
                        presentCount = it.getInt(6),
                        absentCount = it.getInt(7),
                        evaluationsCount = it.getInt(8),
                        testsCount = it.getInt(9),
                        updatedAt = it.getString(10) ?: ""
                    )
                )
            }
        }
        return list
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

    // ---------------- STUDY PERIODS ("الفترات الدراسية") ----------------
    fun getStudyPeriods(): List<StudyPeriodEntity> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT ID, PeriodName, StartDate, EndDate, Status, CreatedDate
            FROM StudyPeriods
            ORDER BY StartDate ASC, ID ASC
            """.trimIndent(),
            null
        )
        val list = mutableListOf<StudyPeriodEntity>()
        cursor.use {
            while (it.moveToNext()) {
                val endDateRaw = if (it.isNull(3)) null else it.getString(3)?.takeIf { s -> s.isNotBlank() }
                list.add(
                    StudyPeriodEntity(
                        id = it.getLong(0),
                        periodName = it.getString(1) ?: "",
                        startDate = it.getString(2) ?: "",
                        endDate = endDateRaw,
                        status = StudyPeriodStatus.fromDb(it.getString(4)),
                        createdDate = it.getString(5) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getOpenStudyPeriod(): StudyPeriodEntity? {
        return getStudyPeriods().firstOrNull { it.status == StudyPeriodStatus.OPEN }
    }

    fun getSuggestedNextPeriodName(): String {
        val count = getStudyPeriods().size
        return when (count + 1) {
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
            else -> "الفترة ${count + 1}"
        }
    }

    /**
     * Checks whether a date range [startDate, endDate] overlaps with any other existing period
     * (excluding [excludeId] if provided), or starts on/before the end of a previous closed period.
     * Returns an Arabic validation error message if invalid, or null if valid.
     */
    fun validateStudyPeriodDates(
        startDate: String,
        endDate: String?,
        excludeId: Long? = null
    ): String? {
        if (startDate.isBlank()) {
            return "يرجى تحديد تاريخ بداية الفترة"
        }
        if (endDate != null) {
            if (endDate.isBlank()) {
                return "يرجى تحديد تاريخ نهاية الفترة"
            }
            if (endDate < startDate) {
                return "لا يسمح بأن يكون تاريخ النهاية قبل تاريخ البداية"
            }
        }

        val others = getStudyPeriods().filter { it.id != excludeId }
        for (other in others) {
            val otherStart = other.startDate
            val otherEnd = other.endDate
            if (otherEnd != null) {
                // Other period is CLOSED with [otherStart, otherEnd]
                if (endDate == null) {
                    // Creating or editing an OPEN period starting at startDate:
                    // Must be strictly after all previous closed periods' end dates
                    if (startDate <= otherEnd) {
                        return "تاريخ البداية غير منطقي بالنسبة للفترات السابقة؛ يجب أن يكون بعد تاريخ نهاية «${other.periodName}» (${formatIsoDateToDisplay(otherEnd)})"
                    }
                } else {
                    // Both periods have start and end dates: check overlap
                    val overlaps = startDate <= otherEnd && endDate >= otherStart
                    if (overlaps) {
                        return "التواريخ المحددة تتداخل مع «${other.periodName}» (${formatIsoDateToDisplay(otherStart)} → ${formatIsoDateToDisplay(otherEnd)})"
                    }
                }
            } else {
                // Other period is OPEN starting at otherStart
                if (endDate != null && endDate >= otherStart) {
                    return "تاريخ النهاية يتداخل مع بداية الفترة المفتوحة «${other.periodName}» (${formatIsoDateToDisplay(otherStart)})"
                }
            }
        }
        return null
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
        val openPeriod = getOpenStudyPeriod()
        if (openPeriod != null) {
            return -1L to "توجد فترة دراسية مفتوحة حالياً. يجب إنهاؤها أولاً قبل بدء فترة جديدة."
        }
        val cleanName = periodName.trim().ifBlank { getSuggestedNextPeriodName() }
        val cleanStart = startDate.trim()
        val dateErr = validateStudyPeriodDates(startDate = cleanStart, endDate = null, excludeId = null)
        if (dateErr != null) {
            return -1L to dateErr
        }

        val db = dbHelper.writableDatabase
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("PeriodName", cleanName)
            put("StartDate", cleanStart)
            putNull("EndDate")
            put("Status", StudyPeriodStatus.OPEN.dbValue)
            put("CreatedDate", now)
        }
        val insertedId = runCatching {
            db.insertOrThrow("StudyPeriods", null, cv)
        }.getOrElse { -1L }

        if (insertedId == -1L) {
            return -1L to "توجد فترة دراسية مفتوحة حالياً. يجب إنهاؤها أولاً قبل بدء فترة جديدة."
        }
        return insertedId to null
    }

    fun closeStudyPeriod(
        id: Long,
        endDate: String
    ): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة الدراسية غير موجودة"

        val cleanEnd = endDate.trim()
        if (cleanEnd.isBlank()) {
            return "يرجى تحديد تاريخ نهاية الفترة"
        }
        if (cleanEnd < target.startDate) {
            return "لا يسمح بأن يكون تاريخ النهاية قبل تاريخ البداية (${formatIsoDateToDisplay(target.startDate)})"
        }
        val dateErr = validateStudyPeriodDates(
            startDate = target.startDate,
            endDate = cleanEnd,
            excludeId = id
        )
        if (dateErr != null) return dateErr

        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("EndDate", cleanEnd)
            put("Status", StudyPeriodStatus.CLOSED.dbValue)
        }
        db.update("StudyPeriods", cv, "ID = ?", arrayOf(id.toString()))
        return null
    }

    fun updateStudyPeriod(
        id: Long,
        periodName: String,
        startDate: String,
        endDate: String?
    ): String? {
        val target = getStudyPeriods().firstOrNull { it.id == id }
            ?: return "الفترة الدراسية غير موجودة"

        val cleanName = periodName.trim()
        if (cleanName.isBlank()) {
            return "يرجى إدخال اسم الفترة الدراسية"
        }
        val cleanStart = startDate.trim()
        val effectiveStart = if (target.status == StudyPeriodStatus.OPEN) cleanStart else target.startDate
        val effectiveEnd = if (target.status == StudyPeriodStatus.CLOSED) endDate?.trim() else null

        if (target.status == StudyPeriodStatus.CLOSED && effectiveEnd.isNullOrBlank()) {
            return "يرجى تحديد تاريخ النهاية للفترة المغلقة"
        }

        val dateErr = validateStudyPeriodDates(
            startDate = effectiveStart,
            endDate = effectiveEnd,
            excludeId = id
        )
        if (dateErr != null) return dateErr

        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("PeriodName", cleanName)
            if (target.status == StudyPeriodStatus.OPEN) {
                put("StartDate", effectiveStart)
            }
            if (target.status == StudyPeriodStatus.CLOSED && effectiveEnd != null) {
                put("EndDate", effectiveEnd)
            }
        }
        db.update("StudyPeriods", cv, "ID = ?", arrayOf(id.toString()))
        return null
    }

    fun deleteStudyPeriod(id: Long) {
        // Deletes only the StudyPeriod time definition; never touches students, attendance, or evaluations.
        val db = dbHelper.writableDatabase
        db.delete("StudyPeriods", "ID = ?", arrayOf(id.toString()))
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
                return "يجب أن تكون درجة الامتحان التحريري بين 0 و 100"
            }
        }
        val roundedDaily = kotlin.math.round(dailyGrade.coerceIn(0.0, 100.0) * 100.0) / 100.0
        val roundedWritten = writtenExamGrade?.let { kotlin.math.round(it.coerceIn(0.0, 100.0) * 100.0) / 100.0 }
        val periodAvg = if (roundedWritten != null) {
            kotlin.math.round(((roundedDaily + roundedWritten) / 2.0) * 100.0) / 100.0
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
}
