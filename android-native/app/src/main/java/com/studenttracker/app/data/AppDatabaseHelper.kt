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
        const val DATABASE_VERSION = 3

        @Volatile
        private var instance: AppDatabaseHelper? = null

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
                UNIQUE(StudentName, ClassID, SectionID)
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
    }

    fun seedInitialDemoData(db: SQLiteDatabase) {
        val now = Instant.now().toString()
        db.beginTransaction()
        try {
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
