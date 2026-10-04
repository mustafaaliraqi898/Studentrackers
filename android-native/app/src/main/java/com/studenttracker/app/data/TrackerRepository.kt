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
}
