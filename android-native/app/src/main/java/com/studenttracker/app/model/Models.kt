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
    val updatedAt: String,
    val academicYearId: Long? = null,
    val academicYearName: String? = null,
    val periodId: Long? = null,
    val periodName: String? = null,
    val stageType: AcademicStage? = null
)

data class PeriodWeekWindow(
    val weekNumber: Int,
    val labelAr: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String,   // YYYY-MM-DD
    val dates: List<String> // All YYYY-MM-DD dates in this week inside the period
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

enum class StudyPeriodStatus(val dbValue: String, val arabicLabel: String) {
    OPEN("OPEN", "مفتوحة"),
    CLOSED("CLOSED", "مغلقة");

    companion object {
        fun fromDb(v: String?): StudyPeriodStatus =
            if (v == "CLOSED") CLOSED else OPEN
    }
}

enum class AcademicStage(
    val dbValue: String,
    val arabicLabel: String,
    val stageOrder: Int,
    val isSinglePeriodOnly: Boolean
) {
    FIRST_HALF("FIRST_HALF", "النصف الأول", 1, false),
    MID_YEAR("MID_YEAR", "نصف السنة", 2, true),
    SECOND_HALF("SECOND_HALF", "النصف الثاني", 3, false);

    companion object {
        fun fromDb(v: String?): AcademicStage = when (v) {
            "MID_YEAR" -> MID_YEAR
            "SECOND_HALF" -> SECOND_HALF
            else -> FIRST_HALF
        }
    }
}

data class AcademicYearEntity(
    val id: Long,
    val yearName: String,
    val isActive: Boolean,
    val createdDate: String
)

data class AcademicStageEntity(
    val id: Long,
    val academicYearId: Long,
    val stageType: AcademicStage,
    val stageName: String,
    val stageOrder: Int,
    val createdDate: String
)

data class StudyPeriodEntity(
    val id: Long,
    val periodName: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String?,  // YYYY-MM-DD or null when OPEN
    val status: StudyPeriodStatus,
    val createdDate: String,
    val academicYearId: Long = 1L,
    val stageType: AcademicStage = AcademicStage.FIRST_HALF,
    val sequenceNumber: Int = 1
)

data class PeriodGradeEntity(
    val id: Long,
    val studentId: Long,
    val periodId: Long,
    val dailyGrade: Double?,
    val writtenExamGrade: Double?,
    val periodAverage: Double?,
    val updatedDate: String
)

data class StudentPeriodGradeBreakdown(
    val period: StudyPeriodEntity,
    val calculatedDailyGrade: StudentCalculatedGrade,
    val dailyGrade: Double,          // out of 100
    val writtenExamGrade: Double?,   // out of 100 or null if not entered yet
    val periodAverage: Double?,      // (dailyGrade + writtenExamGrade) / 2 or null if writtenExamGrade is null
    val updatedDate: String?
)

data class StudentComprehensiveGradeReport(
    val studentId: Long,
    val studentName: String,
    val classId: Long,
    val className: String,
    val sectionId: Long,
    val sectionName: String,
    // Overall / selected filter daily grade breakdown (out of 100)
    val baseDailyGrade: StudentCalculatedGrade,
    // Period-by-period breakdowns (e.g., الفترة الأولى, الفترة الثانية, ...)
    val periodBreakdowns: List<StudentPeriodGradeBreakdown>,
    // Final average across completed periods (المعدل النهائي = مجموع معدلات الفترات المكتملة ÷ عددها)
    val completedPeriodsCount: Int,
    val finalAverage: Double?, // null if 0 completed periods
    // Stage averages & Annual Effort (السعي السنوي = (معدل النصف الأول + درجة نصف السنة + معدل النصف الثاني) ÷ 3)
    val firstHalfAverage: Double? = null,
    val midYearGrade: Double? = null,
    val secondHalfAverage: Double? = null,
    val annualEffort: Double? = null
)



