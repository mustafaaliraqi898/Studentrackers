package com.studenttracker.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.time.Instant

class AppDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "student_tracker_native.db"
        const val DATABASE_VERSION = 8

        @Volatile
        private var instance: AppDatabaseHelper? = null

        @Volatile
        var lastMigrationDuplicateWarning: String? = null

        fun getInstance(context: Context): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createAllTables(db)
        seedInitialDemoData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        createAllTables(db)
        if (oldVersion < 3) {
            runCatching {
                db.execSQL("ALTER TABLE DailyRecords ADD COLUMN IsSaved INTEGER NOT NULL DEFAULT 1;")
            }
            runCatching {
                db.execSQL("ALTER TABLE DailyRecords ADD COLUMN CreatedDate TEXT NOT NULL DEFAULT '';")
            }
            runCatching {
                db.execSQL("ALTER TABLE DailyRecords ADD COLUMN ModifiedDate TEXT NOT NULL DEFAULT '';")
            }
            runCatching {
                db.execSQL("ALTER TABLE DailyTests ADD COLUMN TestValue REAL NOT NULL DEFAULT 0;")
            }
            runCatching {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);")
            }
        }
        if (oldVersion < 4) {
            migrateStudentsUniqueConstraintToClassLevel(db)
        }
        if (oldVersion < 5) {
            createDailyScheduleTableAndIndexes(db)
        }
        if (oldVersion < 6) {
            createStudyPeriodsTableAndIndexes(db)
        }
        if (oldVersion < 7) {
            createPeriodGradesTableAndIndexes(db)
        }
        if (oldVersion < 8) {
            migrateStudyPeriodsToAcademicYearHierarchy(db)
        }
    }

    private fun createAcademicYearsAndStagesTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS AcademicYears (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                YearName TEXT NOT NULL UNIQUE,
                IsActive INTEGER NOT NULL DEFAULT 1,
                CreatedDate TEXT NOT NULL
            );
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_academicyears_name ON AcademicYears(YearName);")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS AcademicStages (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                AcademicYearID INTEGER NOT NULL,
                StageType TEXT NOT NULL CHECK(StageType IN ('FIRST_HALF', 'MID_YEAR', 'SECOND_HALF')),
                StageName TEXT NOT NULL,
                StageOrder INTEGER NOT NULL,
                CreatedDate TEXT NOT NULL,
                FOREIGN KEY (AcademicYearID) REFERENCES AcademicYears(ID) ON DELETE CASCADE,
                UNIQUE(AcademicYearID, StageType)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_academicstages_unique_year_type ON AcademicStages(AcademicYearID, StageType);")
    }

    private fun ensureThreeStagesForYear(db: SQLiteDatabase, yearId: Long, now: String) {
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

    private fun migrateStudyPeriodsToAcademicYearHierarchy(db: SQLiteDatabase) {
        createAcademicYearsAndStagesTables(db)
        val now = Instant.now().toString()

        // Ensure at least one default AcademicYear ('2026-2027') exists so any existing StudyPeriods are safely attached
        db.execSQL(
            "INSERT OR IGNORE INTO AcademicYears (ID, YearName, IsActive, CreatedDate) VALUES (1, '2026-2027', 1, '$now');"
        )
        var defaultYearId = 1L
        db.rawQuery("SELECT ID FROM AcademicYears ORDER BY IsActive DESC, ID ASC LIMIT 1", null).use { c ->
            if (c.moveToFirst()) {
                defaultYearId = c.getLong(0)
            }
        }
        ensureThreeStagesForYear(db, defaultYearId, now)

        // Safely add new hierarchy columns to StudyPeriods if not already present
        runCatching {
            db.execSQL("ALTER TABLE StudyPeriods ADD COLUMN AcademicYearID INTEGER NOT NULL DEFAULT $defaultYearId;")
        }
        runCatching {
            db.execSQL("ALTER TABLE StudyPeriods ADD COLUMN StageType TEXT NOT NULL DEFAULT 'FIRST_HALF';")
        }
        runCatching {
            db.execSQL("ALTER TABLE StudyPeriods ADD COLUMN SequenceNumber INTEGER NOT NULL DEFAULT 1;")
        }

        // Drop old single-open index if present so multiple stages/years can manage periods freely
        runCatching {
            db.execSQL("DROP INDEX IF EXISTS idx_studyperiods_single_open;")
        }

        // Normalize SequenceNumber for any pre-existing rows per (AcademicYearID, StageType)
        val existingIdsByGroup = mutableMapOf<Pair<Long, String>, MutableList<Long>>()
        runCatching {
            db.rawQuery(
                "SELECT ID, AcademicYearID, StageType FROM StudyPeriods ORDER BY StartDate ASC, ID ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val yId = if (c.isNull(1) || c.getLong(1) <= 0L) defaultYearId else c.getLong(1)
                    val sType = if (c.isNull(2) || c.getString(2).isNullOrBlank()) "FIRST_HALF" else c.getString(2)
                    existingIdsByGroup.getOrPut(yId to sType) { mutableListOf() }.add(id)
                }
            }
            for ((group, ids) in existingIdsByGroup) {
                ids.forEachIndexed { index, periodId ->
                    val cv = ContentValues().apply {
                        put("AcademicYearID", group.first)
                        put("StageType", group.second)
                        put("SequenceNumber", index + 1)
                    }
                    db.update("StudyPeriods", cv, "ID = ?", arrayOf(periodId.toString()))
                }
            }
        }

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studyperiods_year_stage_seq ON StudyPeriods(AcademicYearID, StageType, SequenceNumber);")
        // Enforce at database level that MID_YEAR can have at most 1 period per AcademicYear
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_studyperiods_midyear_single ON StudyPeriods(AcademicYearID, StageType) WHERE StageType = 'MID_YEAR';"
        )
    }

    private fun createPeriodGradesTableAndIndexes(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS PeriodGrades (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID INTEGER NOT NULL,
                PeriodID INTEGER NOT NULL,
                DailyGrade REAL NULL CHECK((DailyGrade >= 0 AND DailyGrade <= 100) OR DailyGrade IS NULL),
                WrittenExamGrade REAL NULL CHECK((WrittenExamGrade >= 0 AND WrittenExamGrade <= 100) OR WrittenExamGrade IS NULL),
                PeriodAverage REAL NULL CHECK((PeriodAverage >= 0 AND PeriodAverage <= 100) OR PeriodAverage IS NULL),
                UpdatedDate TEXT NOT NULL,
                FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
                FOREIGN KEY (PeriodID) REFERENCES StudyPeriods(ID) ON DELETE CASCADE,
                UNIQUE(StudentID, PeriodID)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_periodgrades_student ON PeriodGrades(StudentID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_periodgrades_period ON PeriodGrades(PeriodID);")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_periodgrades_unique_student_period ON PeriodGrades(StudentID, PeriodID);")
    }

    private fun createStudyPeriodsTableAndIndexes(db: SQLiteDatabase) {
        createAcademicYearsAndStagesTables(db)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS StudyPeriods (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                AcademicYearID INTEGER NOT NULL DEFAULT 1,
                StageType TEXT NOT NULL DEFAULT 'FIRST_HALF' CHECK(StageType IN ('FIRST_HALF', 'MID_YEAR', 'SECOND_HALF')),
                SequenceNumber INTEGER NOT NULL DEFAULT 1,
                PeriodName TEXT NOT NULL,
                StartDate TEXT NOT NULL,
                EndDate TEXT NULL,
                Status TEXT NOT NULL CHECK(Status IN ('OPEN', 'CLOSED')),
                CreatedDate TEXT NOT NULL,
                FOREIGN KEY (AcademicYearID) REFERENCES AcademicYears(ID) ON DELETE RESTRICT
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studyperiods_status ON StudyPeriods(Status);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studyperiods_dates ON StudyPeriods(StartDate, EndDate);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studyperiods_year_stage_seq ON StudyPeriods(AcademicYearID, StageType, SequenceNumber);")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_studyperiods_midyear_single ON StudyPeriods(AcademicYearID, StageType) WHERE StageType = 'MID_YEAR';"
        )
    }

    private fun createDailyScheduleTableAndIndexes(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS DailySchedule (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                DayOfWeek TEXT NOT NULL,
                ClassID INTEGER NOT NULL,
                SectionID INTEGER NOT NULL,
                LessonNumber INTEGER NOT NULL CHECK(LessonNumber >= 1),
                StartTime TEXT NOT NULL,
                EndTime TEXT NOT NULL,
                CreatedDate TEXT NOT NULL,
                FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
                FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
                UNIQUE(DayOfWeek, ClassID, SectionID, LessonNumber)
            );
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_day_lesson ON DailySchedule(DayOfWeek, LessonNumber, StartTime);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_class_sec ON DailySchedule(ClassID, SectionID);")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_unique_slot ON DailySchedule(DayOfWeek, ClassID, SectionID, LessonNumber);")
    }

    /**
     * Migrates Students table from UNIQUE(StudentName, ClassID, SectionID) to UNIQUE(StudentName, ClassID)
     * without deleting any existing students. If existing data already has duplicate names across sections
     * in the same class, preserves all rows safely and sets a diagnostic notice for the user.
     */
    private fun migrateStudentsUniqueConstraintToClassLevel(db: SQLiteDatabase) {
        runCatching {
            // Normalize existing student names (trim + collapse multiple spaces) safely where no collision occurs
            val cursor = db.rawQuery("SELECT ID, StudentName FROM Students", null)
            val updates = mutableListOf<Pair<Long, String>>()
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val raw = it.getString(1) ?: ""
                    val norm = raw.trim().replace(Regex("\\s+"), " ")
                    if (norm != raw && norm.isNotEmpty()) {
                        updates.add(id to norm)
                    }
                }
            }
            updates.forEach { (id, norm) ->
                runCatching {
                    val cv = ContentValues().apply { put("StudentName", norm) }
                    db.update("Students", cv, "ID = ?", arrayOf(id.toString()))
                }
            }

            // Check if there are existing duplicate student names in the same ClassID across different sections
            val dupCursor = db.rawQuery(
                """
                SELECT StudentName, ClassID, COUNT(*) as cnt
                FROM Students
                GROUP BY StudentName, ClassID
                HAVING cnt > 1
                """.trimIndent(),
                null
            )
            val dupNames = mutableListOf<String>()
            dupCursor.use {
                while (it.moveToNext()) {
                    dupNames.add(it.getString(0))
                }
            }

            if (dupNames.isNotEmpty()) {
                // Keep all existing students untouched as requested; notify user that existing duplicates were preserved
                lastMigrationDuplicateWarning =
                    "تنبيه Migration: وُجدت أسماء مكررة سابقة في نفس الصف (${dupNames.joinToString("، ")})؛ تم الحفاظ على جميع الطلاب الحاليين بأمان وتطبيق منع التكرار على الإضافات والتعديلات."
                return
            }

            // Rebuild Students table with UNIQUE(StudentName, ClassID) while preserving all IDs and foreign keys
            db.execSQL("PRAGMA foreign_keys = OFF;")
            db.beginTransaction()
            try {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS Students_v4 (
                        ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        StudentName TEXT NOT NULL,
                        ClassID INTEGER NOT NULL,
                        SectionID INTEGER NOT NULL,
                        IsActive INTEGER NOT NULL DEFAULT 1,
                        CreatedDate TEXT NOT NULL,
                        FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
                        FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
                        UNIQUE(StudentName, ClassID)
                    );
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO Students_v4 (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate)
                    SELECT ID, StudentName, ClassID, SectionID, IsActive, CreatedDate FROM Students;
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE Students;")
                db.execSQL("ALTER TABLE Students_v4 RENAME TO Students;")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_class_sec ON Students(ClassID, SectionID);")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_students_unique_name_class ON Students(StudentName, ClassID);")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.execSQL("PRAGMA foreign_keys = ON;")
            }
        }
    }

    private fun createAllTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Classes (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ClassName TEXT NOT NULL UNIQUE,
                CreatedAt TEXT NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Sections (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                ClassID INTEGER NOT NULL,
                SectionName TEXT NOT NULL,
                CreatedAt TEXT NOT NULL,
                FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
                UNIQUE(ClassID, SectionName)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Students (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentName TEXT NOT NULL,
                ClassID INTEGER NOT NULL,
                SectionID INTEGER NOT NULL,
                IsActive INTEGER NOT NULL DEFAULT 1,
                CreatedDate TEXT NOT NULL,
                FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
                FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
                UNIQUE(StudentName, ClassID)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS DailyRecords (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID INTEGER NOT NULL,
                ClassID INTEGER NOT NULL,
                SectionID INTEGER NOT NULL,
                RecordDate TEXT NOT NULL,
                IsSaved INTEGER NOT NULL DEFAULT 1,
                CreatedDate TEXT NOT NULL DEFAULT '',
                ModifiedDate TEXT NOT NULL DEFAULT '',
                UpdatedAt TEXT NOT NULL,
                FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
                FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
                FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
                UNIQUE(StudentID, ClassID, SectionID, RecordDate),
                UNIQUE(StudentID, RecordDate)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Attendance (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID INTEGER NOT NULL,
                RecordDate TEXT NOT NULL,
                Status TEXT NOT NULL CHECK(Status IN ('present', 'absent')),
                FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
                UNIQUE(StudentID, RecordDate)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Evaluations (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID INTEGER NOT NULL,
                RecordDate TEXT NOT NULL,
                Participation REAL NULL CHECK(Participation IN (1, 0.5, 0) OR Participation IS NULL),
                Notebook REAL NULL CHECK(Notebook IN (1, 0.5, 0) OR Notebook IS NULL),
                Homework REAL NULL CHECK(Homework IN (1, 0.5, 0) OR Homework IS NULL),
                Behavior REAL NULL CHECK(Behavior IN (1, 0.5, 0) OR Behavior IS NULL),
                FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
                UNIQUE(StudentID, RecordDate)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS DailyTests (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                StudentID INTEGER NOT NULL,
                RecordDate TEXT NOT NULL,
                Score REAL NOT NULL CHECK(Score >= 0 AND Score <= 5),
                TestValue REAL NOT NULL DEFAULT 0 CHECK(TestValue >= 0 AND TestValue <= 5),
                FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
                UNIQUE(StudentID, RecordDate)
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Holidays (
                ID INTEGER PRIMARY KEY AUTOINCREMENT,
                HolidayDate TEXT NOT NULL UNIQUE,
                HolidayName TEXT NOT NULL,
                HolidayType TEXT NOT NULL,
                Notes TEXT DEFAULT ''
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sections_class ON Sections(ClassID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_class_sec ON Students(ClassID, SectionID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dailyrecords_date ON DailyRecords(RecordDate, ClassID, SectionID);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_student_date ON Attendance(StudentID, RecordDate);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_evaluations_student_date ON Evaluations(StudentID, RecordDate);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dailytests_student_date ON DailyTests(StudentID, RecordDate);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_holidays_date ON Holidays(HolidayDate);")
        createDailyScheduleTableAndIndexes(db)
        createStudyPeriodsTableAndIndexes(db)
        createPeriodGradesTableAndIndexes(db)
    }

    fun seedInitialDemoData(db: SQLiteDatabase) {
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO AcademicYears (ID, YearName, IsActive, CreatedDate) VALUES (1, '2026-2027', 1, '$now');")
            ensureThreeStagesForYear(db, 1L, now)

            db.execSQL("INSERT OR IGNORE INTO Classes (ID, ClassName, CreatedAt) VALUES (1, 'الثالث المتوسط', '$now');")
            db.execSQL("INSERT OR IGNORE INTO Classes (ID, ClassName, CreatedAt) VALUES (2, 'الرابع العلمي', '$now');")

            db.execSQL("INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (1, 1, 'أ', '$now');")
            db.execSQL("INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (2, 1, 'ب', '$now');")
            db.execSQL("INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (3, 2, 'أ', '$now');")

            val studentsA = listOf(
                "أحمد علي",
                "محمد حسن",
                "علي كريم",
                "مصطفى حسين",
                "حسن قاسم",
                "حسين محمد",
                "عباس علي",
                "عمر خالد"
            )
            studentsA.forEachIndexed { idx, name ->
                val id = idx + 1
                db.execSQL("INSERT OR IGNORE INTO Students (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate) VALUES ($id, '$name', 1, 1, 1, '$now');")
            }

            val studentsB = listOf("يوسف عبد الله", "زيد محمود", "إبراهيم طارق")
            studentsB.forEachIndexed { idx, name ->
                val id = idx + 9
                db.execSQL("INSERT OR IGNORE INTO Students (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate) VALUES ($id, '$name', 1, 2, 1, '$now');")
            }

            db.execSQL("INSERT OR IGNORE INTO Holidays (HolidayDate, HolidayName, HolidayType, Notes) VALUES ('2026-10-08', 'عطلة رسمية محلية', 'عطلة رسمية', 'لا تحتسب ضمن أيام الدوام');")
            db.execSQL("INSERT OR IGNORE INTO Holidays (HolidayDate, HolidayName, HolidayType, Notes) VALUES ('2026-10-10', 'مناسبة وطنية / عطلة رسمية', 'مناسبة', 'مثال عطلة 10/10');")

            // Seed sample records for 2026-10-01 and 2026-10-04
            val dates = listOf("2026-10-01", "2026-10-04", "2026-10-05")
            for (d in dates) {
                for (stId in 1..8) {
                    val att = if (d == "2026-10-01" && stId == 4) "absent" else "present"
                    val part = if (att == "present") 1.0 else null
                    val note = if (att == "present") 1.0 else null
                    val hw = if (att == "present") (if (stId % 2 == 0) 1.0 else 0.5) else null
                    val beh = if (att == "present") 1.0 else null

                    db.execSQL(
                        "INSERT OR IGNORE INTO DailyRecords (StudentID, ClassID, SectionID, RecordDate, IsSaved, CreatedDate, ModifiedDate, UpdatedAt) VALUES ($stId, 1, 1, '$d', 1, '$now', '$now', '$now');"
                    )
                    db.execSQL(
                        "INSERT OR IGNORE INTO Attendance (StudentID, RecordDate, Status) VALUES ($stId, '$d', '$att');"
                    )
                    val cv = ContentValues().apply {
                        put("StudentID", stId)
                        put("RecordDate", d)
                        if (part != null) put("Participation", part) else putNull("Participation")
                        if (note != null) put("Notebook", note) else putNull("Notebook")
                        if (hw != null) put("Homework", hw) else putNull("Homework")
                        if (beh != null) put("Behavior", beh) else putNull("Behavior")
                    }
                    db.insertWithOnConflict("Evaluations", null, cv, SQLiteDatabase.CONFLICT_IGNORE)

                    if (d == "2026-10-01" && att == "present") {
                        val score = if (stId % 2 == 0) 4.5 else 4.0
                        db.execSQL(
                            "INSERT OR IGNORE INTO DailyTests (StudentID, RecordDate, Score, TestValue) VALUES ($stId, '$d', $score, $score);"
                        )
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getDatabaseFile(): File {
        return context.getDatabasePath(DATABASE_NAME)
    }

    fun getWalFile(): File {
        return File(getDatabaseFile().absolutePath + "-wal")
    }

    fun getShmFile(): File {
        return File(getDatabaseFile().absolutePath + "-shm")
    }

    fun getJournalFile(): File {
        return File(getDatabaseFile().absolutePath + "-journal")
    }

    /**
     * Ensures all pending SQLite writes and WAL frames are flushed into the primary database file.
     */
    fun flushAndCheckpoint() {
        val db = writableDatabase
        runCatching {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { cursor ->
                while (cursor.moveToNext()) {
                    // consume checkpoint result
                }
            }
        }
        runCatching {
            db.rawQuery("PRAGMA shrink_memory;", null).use { }
        }
    }

    /**
     * Opens a standalone read-only connection to any SQLite file (e.g., backup file or restored file)
     * and returns the exact counts of Students, Classes, Sections, and DailyRecords inside it.
     */
    fun inspectDatabaseFileCounts(dbFile: File): Map<String, Int> {
        require(dbFile.exists() && dbFile.length() > 100) {
            "ملف قاعدة البيانات غير موجود أو فارغ: ${dbFile.name}"
        }
        val counts = mutableMapOf(
            "students" to 0,
            "classes" to 0,
            "sections" to 0,
            "records" to 0,
            "holidays" to 0
        )
        val checkDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            fun countTable(table: String): Int {
                return runCatching {
                    checkDb.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                        if (c.moveToFirst()) c.getInt(0) else 0
                    }
                }.getOrDefault(0)
            }
            counts["students"] = countTable("Students")
            counts["classes"] = countTable("Classes")
            counts["sections"] = countTable("Sections")
            counts["records"] = countTable("DailyRecords")
            counts["holidays"] = countTable("Holidays")
        } finally {
            checkDb.close()
        }
        return counts
    }

    fun resetDatabase(includeDemo: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM Attendance;")
            db.execSQL("DELETE FROM Evaluations;")
            db.execSQL("DELETE FROM DailyTests;")
            db.execSQL("DELETE FROM DailyRecords;")
            db.execSQL("DELETE FROM Students;")
            db.execSQL("DELETE FROM Sections;")
            db.execSQL("DELETE FROM Classes;")
            db.execSQL("DELETE FROM Holidays;")
            runCatching { db.execSQL("DELETE FROM DailySchedule;") }
            runCatching { db.execSQL("DELETE FROM PeriodGrades;") }
            runCatching { db.execSQL("DELETE FROM StudyPeriods;") }
            if (includeDemo) {
                seedInitialDemoData(db)
            } else {
                val now = Instant.now().toString()
                db.execSQL("INSERT INTO Classes (ID, ClassName, CreatedAt) VALUES (1, 'الثالث المتوسط', '$now');")
                db.execSQL("INSERT INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (1, 1, 'أ', '$now');")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
