package com.studenttracker.app.service

import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.round

object GradingService {

    fun round2(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    fun formatScore(value: Double): String {
        val r = round2(value)
        return if (r % 1.0 == 0.0) {
            r.toInt().toString()
        } else {
            String.format("%.2f", r).trimEnd('0').trimEnd('.')
        }
    }

    fun getRatingLabel(value: Double?): String {
        return when (value) {
            1.0 -> "كامل"
            0.5 -> "نصف"
            0.0 -> "صفر"
            else -> "غير مسجل"
        }
    }

    fun formatAbsenceDaysLabel(days: Int): String {
        return if (days in 3..10) "$days أيام" else "$days يوم"
    }

    /**
     * Counts how many times each student received an explicit 0.0 ("صفر") in Behavior
     * from actual saved Evaluations records (excluding weekends and holidays).
     * Does NOT count 1.0 ("كامل"), 0.5 ("نصف"), or null (unrecorded) as zero.
     */
    fun getBehaviorZeroCounts(
        dbHelper: AppDatabaseHelper,
        repository: TrackerRepository
    ): Map<Long, Int> {
        val holidaysSet = repository.getHolidays().map { it.holidayDate }.toSet()
        fun isExcludedDate(dateStr: String): Boolean {
            if (holidaysSet.contains(dateStr)) return true
            return isWeekendDay(dateStr)
        }

        val counts = mutableMapOf<Long, Int>()
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT StudentID, RecordDate, Behavior FROM Evaluations WHERE Behavior IS NOT NULL",
            null
        ).use { c ->
            while (c.moveToNext()) {
                if (c.isNull(2)) continue
                val stId = c.getLong(0)
                val recordDate = c.getString(1) ?: continue
                val behaviorVal = c.getDouble(2)
                if (!isExcludedDate(recordDate) && behaviorVal == 0.0) {
                    counts[stId] = (counts[stId] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun formatDisplayDate(isoDate: String): String {
        val parts = isoDate.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate
    }

    fun getArabicDayName(isoDate: String): String {
        return try {
            val ld = LocalDate.parse(isoDate)
            when (ld.dayOfWeek) {
                DayOfWeek.SUNDAY -> "الأحد"
                DayOfWeek.MONDAY -> "الإثنين"
                DayOfWeek.TUESDAY -> "الثلاثاء"
                DayOfWeek.WEDNESDAY -> "الأربعاء"
                DayOfWeek.THURSDAY -> "الخميس"
                DayOfWeek.FRIDAY -> "الجمعة"
                DayOfWeek.SATURDAY -> "السبت"
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun isWeekendDay(isoDate: String): Boolean {
        return try {
            val ld = LocalDate.parse(isoDate)
            ld.dayOfWeek == DayOfWeek.FRIDAY || ld.dayOfWeek == DayOfWeek.SATURDAY
        } catch (_: Exception) {
            false
        }
    }

    data class DayHolidayStatus(
        val isNonWorkingDay: Boolean,
        val title: String,
        val subtitle: String
    )

    fun checkDateStatus(isoDate: String, holidays: List<HolidayEntity>): DayHolidayStatus {
        val customHoliday = holidays.find { it.holidayDate == isoDate }
        if (customHoliday != null) {
            return DayHolidayStatus(
                isNonWorkingDay = true,
                title = "هذا اليوم عطلة",
                subtitle = "${customHoliday.holidayName} (${customHoliday.holidayType})" +
                    if (customHoliday.notes.isNotBlank()) " — ${customHoliday.notes}" else ""
            )
        }
        if (isWeekendDay(isoDate)) {
            val dayName = getArabicDayName(isoDate)
            return DayHolidayStatus(
                isNonWorkingDay = true,
                title = "عطلة أسبوعية",
                subtitle = "يوم $dayName عطلة أسبوعية تلقائية — لا يتم تسجيل غياب ولا يدخل ضمن أيام الدوام"
            )
        }
        return DayHolidayStatus(
            isNonWorkingDay = false,
            title = "يوم دوام (${getArabicDayName(isoDate)})",
            subtitle = ""
        )
    }

    fun getArabicOrdinalWeekLabel(weekNumber: Int): String {
        return when (weekNumber) {
            1 -> "الأسبوع الأول"
            2 -> "الأسبوع الثاني"
            3 -> "الأسبوع الثالث"
            4 -> "الأسبوع الرابع"
            5 -> "الأسبوع الخامس"
            6 -> "الأسبوع السادس"
            7 -> "الأسبوع السابع"
            8 -> "الأسبوع الثامن"
            9 -> "الأسبوع التاسع"
            10 -> "الأسبوع العاشر"
            11 -> "الأسبوع الحادي عشر"
            12 -> "الأسبوع الثاني عشر"
            else -> "الأسبوع $weekNumber"
        }
    }

    /**
     * Divides a StudyPeriod [startDate, endDate] into sequential 7-day weeks
     * (الأسبوع الأول, الأسبوع الثاني, ...) and returns each week with its dates list.
     */
    fun buildWeeksForPeriod(period: StudyPeriodEntity?): List<PeriodWeekWindow> {
        if (period == null) return emptyList()
        return try {
            val startLd = LocalDate.parse(period.startDate.trim())
            val endLd = if (!period.endDate.isNullOrBlank()) {
                LocalDate.parse(period.endDate.trim())
            } else {
                startLd.plusDays(27)
            }
            val safeEnd = if (endLd.isBefore(startLd)) startLd else endLd
            val weeks = mutableListOf<PeriodWeekWindow>()
            var currentStart = startLd
            var weekNum = 1
            while (!currentStart.isAfter(safeEnd) && weekNum <= 60) {
                val candidateEnd = currentStart.plusDays(6)
                val actualEnd = if (candidateEnd.isAfter(safeEnd)) safeEnd else candidateEnd
                val datesInWeek = mutableListOf<String>()
                var d = currentStart
                while (!d.isAfter(actualEnd)) {
                    datesInWeek.add(d.toString())
                    d = d.plusDays(1)
                }
                weeks.add(
                    PeriodWeekWindow(
                        weekNumber = weekNum,
                        labelAr = getArabicOrdinalWeekLabel(weekNum),
                        startDate = currentStart.toString(),
                        endDate = actualEnd.toString(),
                        dates = datesInWeek
                    )
                )
                currentStart = actualEnd.plusDays(1)
                weekNum++
            }
            weeks
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun calculateGradesForClass(
        dbHelper: AppDatabaseHelper,
        repository: TrackerRepository,
        classId: Long? = null,
        sectionId: Long? = null,
        monthPrefix: String? = null, // e.g. "2026-10", "ALL", or "PERIOD:<id>"
        startDateInclusive: String? = null,
        endDateInclusive: String? = null,
        liveOverrideDate: String? = null,
        liveOverrideEntries: Map<Long, StudentDailyEntry>? = null
    ): List<StudentCalculatedGrade> {
        val students = repository.getStudents(classId, sectionId, includeInactive = false)
        val holidaysSet = repository.getHolidays().map { it.holidayDate }.toSet()

        // Resolve PERIOD:<id> if passed via monthPrefix
        var effectiveStart = startDateInclusive
        var effectiveEnd = endDateInclusive
        var effectiveMonth = monthPrefix
        if (!monthPrefix.isNullOrBlank() && monthPrefix.startsWith("PERIOD:")) {
            val pid = monthPrefix.removePrefix("PERIOD:").toLongOrNull()
            val period = pid?.let { id -> repository.getStudyPeriods().firstOrNull { it.id == id } }
            if (period != null) {
                effectiveStart = period.startDate
                effectiveEnd = period.endDate
                effectiveMonth = null
            }
        }

        fun isWithinDateFilter(dateStr: String): Boolean {
            if (!effectiveStart.isNullOrBlank() && dateStr < effectiveStart!!) return false
            if (!effectiveEnd.isNullOrBlank() && dateStr > effectiveEnd!!) return false
            if (!effectiveMonth.isNullOrBlank() && effectiveMonth != "ALL") {
                if (!dateStr.startsWith(effectiveMonth!!)) return false
            }
            return true
        }

        fun isExcludedDate(dateStr: String): Boolean {
            if (!isWithinDateFilter(dateStr)) return true
            if (holidaysSet.contains(dateStr)) return true
            return isWeekendDay(dateStr)
        }

        val db = dbHelper.readableDatabase
        val whereParts = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()
        if (!effectiveStart.isNullOrBlank()) {
            whereParts.add("RecordDate >= ?")
            whereArgs.add(effectiveStart!!)
        }
        if (!effectiveEnd.isNullOrBlank()) {
            whereParts.add("RecordDate <= ?")
            whereArgs.add(effectiveEnd!!)
        }
        if (!effectiveMonth.isNullOrBlank() && effectiveMonth != "ALL") {
            whereParts.add("RecordDate LIKE ?")
            whereArgs.add("$effectiveMonth%")
        }
        val dateWhere = if (whereParts.isNotEmpty()) {
            "WHERE " + whereParts.joinToString(" AND ")
        } else {
            ""
        }
        val queryArgs = whereArgs.toTypedArray()

        // Load Attendance
        val attByStudent = mutableMapOf<Long, MutableMap<String, AttendanceStatus>>()
        db.rawQuery("SELECT StudentID, RecordDate, Status FROM Attendance $dateWhere ORDER BY RecordDate ASC", queryArgs).use { c ->
            while (c.moveToNext()) {
                val stId = c.getLong(0)
                val d = c.getString(1)
                val st = AttendanceStatus.fromDb(c.getString(2))
                if (!isExcludedDate(d)) {
                    attByStudent.getOrPut(stId) { linkedMapOf() }[d] = st
                }
            }
        }

        // Load Evaluations
        data class EvalRow(val part: Double?, val note: Double?, val hw: Double?, val beh: Double?)
        val evalByStudent = mutableMapOf<Long, MutableMap<String, EvalRow>>()
        db.rawQuery(
            "SELECT StudentID, RecordDate, Participation, Notebook, Homework, Behavior FROM Evaluations $dateWhere ORDER BY RecordDate ASC",
            queryArgs
        ).use { c ->
            while (c.moveToNext()) {
                val stId = c.getLong(0)
                val d = c.getString(1)
                if (!isExcludedDate(d)) {
                    val p = if (c.isNull(2)) null else c.getDouble(2)
                    val n = if (c.isNull(3)) null else c.getDouble(3)
                    val h = if (c.isNull(4)) null else c.getDouble(4)
                    val b = if (c.isNull(5)) null else c.getDouble(5)
                    evalByStudent.getOrPut(stId) { linkedMapOf() }[d] = EvalRow(p, n, h, b)
                }
            }
        }

        // Load DailyTests
        val testByStudent = mutableMapOf<Long, MutableMap<String, Double>>()
        db.rawQuery("SELECT StudentID, RecordDate, Score FROM DailyTests $dateWhere ORDER BY RecordDate ASC", queryArgs).use { c ->
            while (c.moveToNext()) {
                val stId = c.getLong(0)
                val d = c.getString(1)
                val sc = c.getDouble(2)
                if (!isExcludedDate(d)) {
                    testByStudent.getOrPut(stId) { linkedMapOf() }[d] = sc
                }
            }
        }

        return students.map { st ->
            val attMap = attByStudent[st.id]?.toMutableMap() ?: linkedMapOf()
            val evalMap = evalByStudent[st.id]?.toMutableMap() ?: linkedMapOf()
            val testMap = testByStudent[st.id]?.toMutableMap() ?: linkedMapOf()

            if (liveOverrideDate != null && liveOverrideEntries != null && !isExcludedDate(liveOverrideDate)) {
                val draft = liveOverrideEntries[st.id]
                if (draft != null && isWithinDateFilter(liveOverrideDate)) {
                    attMap[liveOverrideDate] = draft.attendance
                    evalMap[liveOverrideDate] = EvalRow(
                        draft.participation,
                        draft.notebook,
                        draft.homework,
                        draft.behavior
                    )
                    if (draft.dailyTestScore != null) {
                        testMap[liveOverrideDate] = draft.dailyTestScore.coerceIn(0.0, 5.0)
                    } else {
                        testMap.remove(liveOverrideDate)
                    }
                }
            }

            // 1. Attendance out of 19
            var presentDays = 0
            var absentDays = 0
            for ((_, status) in attMap) {
                if (status == AttendanceStatus.PRESENT) presentDays++ else absentDays++
            }
            val totalWorkingDays = presentDays + absentDays
            val attPercent = if (totalWorkingDays > 0) (presentDays.toDouble() / totalWorkingDays.toDouble()) * 100.0 else 100.0
            val attScore = if (totalWorkingDays > 0) round2((presentDays.toDouble() / totalWorkingDays.toDouble()) * 19.0) else 19.0

            // 2. Evaluations out of 19 each
            val partList = mutableListOf<RatedDateItem>()
            val noteList = mutableListOf<RatedDateItem>()
            val hwList = mutableListOf<RatedDateItem>()
            val behList = mutableListOf<RatedDateItem>()

            for ((d, ev) in evalMap.toSortedMap()) {
                if (ev.part != null) partList.add(RatedDateItem(d, ev.part))
                if (ev.note != null) noteList.add(RatedDateItem(d, ev.note))
                if (ev.hw != null) hwList.add(RatedDateItem(d, ev.hw))
                if (ev.beh != null) behList.add(RatedDateItem(d, ev.beh))
            }

            fun calc19(items: List<RatedDateItem>): Double {
                if (items.isEmpty()) return 19.0 // Not penalized as zero if unrecorded
                val avg = items.sumOf { it.value } / items.size.toDouble()
                return round2(avg * 19.0)
            }

            val partScore = calc19(partList)
            val noteScore = calc19(noteList)
            val hwScore = calc19(hwList)
            val behScore = calc19(behList)

            // 3. Daily Tests out of 5
            val testList = testMap.toSortedMap().map { (d, s) -> RatedDateItem(d, s) }
            val quizScore = if (testList.isEmpty()) {
                5.0
            } else {
                round2(testList.sumOf { it.value } / testList.size.toDouble())
            }

            val total = round2(attScore + partScore + noteScore + hwScore + behScore + quizScore)

            StudentCalculatedGrade(
                studentId = st.id,
                studentName = st.studentName,
                classId = st.classId,
                className = st.className,
                sectionId = st.sectionId,
                sectionName = st.sectionName,
                presentDays = presentDays,
                absentDays = absentDays,
                totalWorkingDays = totalWorkingDays,
                attendancePercentage = round2(attPercent),
                attendanceScore = attScore,
                participationRatings = partList,
                participationScore = partScore,
                notebookRatings = noteList,
                notebookScore = noteScore,
                homeworkRatings = hwList,
                homeworkScore = hwScore,
                behaviorRatings = behList,
                behaviorScore = behScore,
                dailyTests = testList,
                quizScore = quizScore,
                totalScore = total
            )
        }
    }

    /**
     * Dynamically calculates absence and attendance summaries for each student from SQLite Attendance records.
     * - Excludes Fridays and Saturdays automatically.
     * - Excludes any date registered in Holidays.
     * - Does NOT count missing/unrecorded days as absences; only actual recorded 'absent' rows on working days count.
     * - Sorted by default from most absent (absentDays DESC) to least absent.
     */
    fun calculateAbsenceSummaries(
        dbHelper: AppDatabaseHelper,
        repository: TrackerRepository,
        classId: Long? = null,
        sectionId: Long? = null,
        monthPrefix: String? = null // e.g. "2026-10" or "ALL"
    ): List<StudentAbsenceSummary> {
        val students = repository.getStudents(classId, sectionId, includeInactive = false)
        val holidaysSet = repository.getHolidays().map { it.holidayDate }.toSet()

        fun isExcludedDate(dateStr: String): Boolean {
            if (holidaysSet.contains(dateStr)) return true
            return isWeekendDay(dateStr)
        }

        val db = dbHelper.readableDatabase
        val dateWhere = if (!monthPrefix.isNullOrBlank() && monthPrefix != "ALL") {
            "WHERE RecordDate LIKE '$monthPrefix%'"
        } else {
            ""
        }

        val attByStudent = mutableMapOf<Long, MutableMap<String, AttendanceStatus>>()
        db.rawQuery(
            "SELECT StudentID, RecordDate, Status FROM Attendance $dateWhere ORDER BY RecordDate ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val stId = c.getLong(0)
                val d = c.getString(1)
                val st = AttendanceStatus.fromDb(c.getString(2))
                if (!isExcludedDate(d)) {
                    attByStudent.getOrPut(stId) { linkedMapOf() }[d] = st
                }
            }
        }

        return students.map { st ->
            val attMap = attByStudent[st.id] ?: emptyMap()
            var presentDays = 0
            var absentDays = 0
            val absentDates = mutableListOf<String>()

            for ((d, status) in attMap) {
                if (status == AttendanceStatus.PRESENT) {
                    presentDays++
                } else {
                    absentDays++
                    absentDates.add(d)
                }
            }

            val totalWorkingDays = presentDays + absentDays
            val attPercent = if (totalWorkingDays > 0) {
                round2((presentDays.toDouble() / totalWorkingDays.toDouble()) * 100.0)
            } else {
                100.0
            }
            val sortedAbsentNewestFirst = absentDates.sortedDescending()

            StudentAbsenceSummary(
                studentId = st.id,
                studentName = st.studentName,
                classId = st.classId,
                className = st.className,
                sectionId = st.sectionId,
                sectionName = st.sectionName,
                totalWorkingDays = totalWorkingDays,
                presentDays = presentDays,
                absentDays = absentDays,
                attendancePercentage = attPercent,
                lastAbsentDate = sortedAbsentNewestFirst.firstOrNull(),
                absentDatesNewestFirst = sortedAbsentNewestFirst
            )
        }.sortedWith(
            compareByDescending<StudentAbsenceSummary> { it.absentDays }
                .thenBy { it.attendancePercentage }
                .thenBy { it.studentName }
        )
    }

    /**
     * Calculates comprehensive student grade reports combining:
     * 1. Base daily grade out of 100 (for the selected filter or overall)
     * 2. StudyPeriod-specific daily grades out of 100 (calculated strictly within each StudyPeriod's [StartDate, EndDate])
     * 3. WrittenExamGrade (out of 100) stored in PeriodGrades for each StudyPeriod
     * 4. PeriodAverage = (DailyGrade + WrittenExamGrade) / 2 when WrittenExamGrade is present (else null / "--")
     * 5. FinalAverage = sum(completed PeriodAverages) / count(completed PeriodAverages) across all periods that have a PeriodAverage.
     *
     * Also keeps PeriodGrades.DailyGrade and PeriodGrades.PeriodAverage synchronized in SQLite whenever a PeriodGrades row exists.
     */
    fun calculateComprehensiveStudentReports(
        dbHelper: AppDatabaseHelper,
        repository: TrackerRepository,
        classId: Long? = null,
        sectionId: Long? = null,
        baseDateFilter: String? = "ALL"
    ): List<StudentComprehensiveGradeReport> {
        val baseGrades = calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = classId,
            sectionId = sectionId,
            monthPrefix = baseDateFilter
        )
        val periods = repository.getStudyPeriods()
        val storedPeriodGrades = repository.getPeriodGrades()
        val storedMap = storedPeriodGrades.associateBy { it.studentId to it.periodId }

        // Pre-calculate daily grades per period for all requested students
        val periodDailyGradesByPeriodId: Map<Long, Map<Long, StudentCalculatedGrade>> = periods.associate { period ->
            val gradesForPeriod = calculateGradesForClass(
                dbHelper = dbHelper,
                repository = repository,
                classId = classId,
                sectionId = sectionId,
                startDateInclusive = period.startDate,
                endDateInclusive = period.endDate
            ).associateBy { it.studentId }
            period.id to gradesForPeriod
        }

        return baseGrades.map { baseGrade ->
            val stId = baseGrade.studentId
            val breakdowns = periods.map { period ->
                val calcDaily = periodDailyGradesByPeriodId[period.id]?.get(stId) ?: baseGrade
                val dailyScore = round2(calcDaily.totalScore)
                val stored = storedMap[stId to period.id]
                val written = stored?.writtenExamGrade?.let { round2(it) }
                val periodAvg = if (written != null) {
                    round2((dailyScore + written) / 2.0)
                } else {
                    null
                }

                // Keep SQLite PeriodGrades row in sync if the daily grade changed since last save
                if (stored != null && (stored.dailyGrade != dailyScore || stored.periodAverage != periodAvg)) {
                    runCatching {
                        repository.upsertPeriodGrade(
                            studentId = stId,
                            periodId = period.id,
                            dailyGrade = dailyScore,
                            writtenExamGrade = written
                        )
                    }
                }

                StudentPeriodGradeBreakdown(
                    period = period,
                    calculatedDailyGrade = calcDaily,
                    dailyGrade = dailyScore,
                    writtenExamGrade = written,
                    periodAverage = periodAvg,
                    updatedDate = stored?.updatedDate
                )
            }

            val completedAverages = breakdowns.mapNotNull { it.periodAverage }
            val finalAvg = if (completedAverages.isNotEmpty()) {
                round2(completedAverages.sum() / completedAverages.size.toDouble())
            } else {
                null
            }

            StudentComprehensiveGradeReport(
                studentId = stId,
                studentName = baseGrade.studentName,
                classId = baseGrade.classId,
                className = baseGrade.className,
                sectionId = baseGrade.sectionId,
                sectionName = baseGrade.sectionName,
                baseDailyGrade = baseGrade,
                periodBreakdowns = breakdowns,
                completedPeriodsCount = completedAverages.size,
                finalAverage = finalAvg
            )
        }
    }
}
