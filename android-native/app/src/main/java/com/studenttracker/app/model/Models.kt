package com.studenttracker.app.model

enum class AttendanceStatus(val dbValue: String, val arabicLabel: String) {
    PRESENT("present", "حاضر"),
    ABSENT("absent", "غائب");

    companion object {
        fun fromDb(v: String?): AttendanceStatus =
            if (v == "absent") ABSENT else PRESENT
    }
}

data class ClassEntity(
    val id: Long,
    val className: String,
    val createdAt: String
)

data class SectionEntity(
    val id: Long,
    val classId: Long,
    val sectionName: String,
    val createdAt: String
)

data class StudentEntity(
    val id: Long,
    val studentName: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    val isActive: Boolean,
    val createdDate: String
)

data class HolidayEntity(
    val id: Long,
    val holidayDate: String, // YYYY-MM-DD
    val holidayName: String,
    val holidayType: String,
    val notes: String
)

data class StudentDailyEntry(
    val studentId: Long,
    val attendance: AttendanceStatus = AttendanceStatus.PRESENT,
    val participation: Double? = null, // 1.0, 0.5, 0.0, or null
    val notebook: Double? = null,
    val homework: Double? = null,
    val behavior: Double? = null,
    val dailyTestScore: Double? = null // 0.0 to 5.0
)

data class SavedSessionSummary(
    val recordDate: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    val studentCount: Int,
    val presentCount: Int,
    val absentCount: Int,
    val evaluationsCount: Int,
    val testsCount: Int,
    val updatedAt: String
)

data class RatedDateItem(
    val date: String,
    val value: Double
)

data class StudentCalculatedGrade(
    val studentId: Long,
    val studentName: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    val presentDays: Int,
    val absentDays: Int,
    val totalWorkingDays: Int,
    val attendancePercentage: Double,
    val attendanceScore: Double, // out of 19
    val participationRatings: List<RatedDateItem>,
    val participationScore: Double, // out of 19
    val notebookRatings: List<RatedDateItem>,
    val notebookScore: Double, // out of 19
    val homeworkRatings: List<RatedDateItem>,
    val homeworkScore: Double, // out of 19
    val behaviorRatings: List<RatedDateItem>,
    val behaviorScore: Double, // out of 19
    val dailyTests: List<RatedDateItem>,
    val quizScore: Double, // out of 5
    val totalScore: Double // out of 100
)

data class ExcelRowError(
    val rowNumber: Int,
    val studentName: String,
    val className: String,
    val sectionName: String,
    val reason: String
)

data class ExcelImportResult(
    val importedCount: Int,
    val duplicateCount: Int,
    val emptyRowCount: Int,
    val errorCount: Int,
    val rowErrors: List<ExcelRowError>
)

data class BackupFileEntry(
    val fileName: String,
    val filePath: String,
    val createdAt: String,
    val sizeBytes: Long,
    val isAutoPreRestore: Boolean,
    val isAutoDaily: Boolean = false,
    val studentsCount: Int = 0,
    val classesCount: Int = 0,
    val sectionsCount: Int = 0,
    val recordsCount: Int = 0
)

data class StudentAbsenceSummary(
    val studentId: Long,
    val studentName: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    val totalWorkingDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val attendancePercentage: Double,
    val lastAbsentDate: String?,
    val absentDatesNewestFirst: List<String>
)

data class DailyScheduleEntity(
    val id: Long,
    val dayOfWeek: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    val lessonNumber: Int,
    val startTime: String,
    val endTime: String,
    val createdDate: String
)


