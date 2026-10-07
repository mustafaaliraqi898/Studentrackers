package com.studenttracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import com.studenttracker.app.service.ExcelAndBackupService
import com.studenttracker.app.service.GradingService

@Composable
fun DailyTrackingScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    selectedDate: String,
    holidays: List<HolidayEntity>,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    isDirty: Boolean,
    onDirtyChange: (Boolean) -> Unit,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onSelectDate: (String) -> Unit,
    onOpenStudentProfile: (Long) -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit,
    preserveInitialDateFromSavedSession: Boolean = false,
    onConsumeSavedSessionJump: () -> Unit = {}
) {
    val context = LocalContext.current

    // Read real device LocalDate (never UTC conversion, never hardcoded)
    fun currentDeviceIsoDate(): String {
        return java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
    }

    // Track if the user explicitly opened a saved session so we don't overwrite that specific saved date on first mount
    var keepSavedSessionDateOnce by remember { mutableStateOf(preserveInitialDateFromSavedSession) }

    // Sub-mode inside Daily section: 0 = "إضافة متابعة", 1 = "السجلات اليومية المحفوظة"
    var activeSubTab by remember { mutableIntStateOf(0) }

    // 1. Academic Years & Periods hierarchy
    val academicYears = remember(refreshTick) { repository.getAcademicYears() }
    var selectedYearId by remember(academicYears) {
        val todayIso = currentDeviceIsoDate()
        val allPeriods = repository.getStudyPeriods()
        val periodContainingToday = allPeriods.firstOrNull { sp ->
            todayIso >= sp.startDate && (sp.endDate.isNullOrBlank() || todayIso <= sp.endDate)
        }
        val defaultYr = if (preserveInitialDateFromSavedSession) {
            val periodForSaved = allPeriods.firstOrNull { sp ->
                selectedDate >= sp.startDate && (sp.endDate.isNullOrBlank() || selectedDate <= sp.endDate)
            }
            academicYears.firstOrNull { it.id == periodForSaved?.academicYearId }
                ?: academicYears.firstOrNull { it.isActive }
                ?: academicYears.firstOrNull()
        } else {
            academicYears.firstOrNull { it.id == periodContainingToday?.academicYearId }
                ?: academicYears.firstOrNull { it.isActive }
                ?: academicYears.firstOrNull()
        }
        mutableLongStateOf(defaultYr?.id ?: 1L)
    }
    LaunchedEffect(academicYears) {
        if (academicYears.isNotEmpty() && academicYears.none { it.id == selectedYearId }) {
            val active = academicYears.firstOrNull { it.isActive } ?: academicYears.first()
            selectedYearId = active.id
        }
    }
    val selectedYear = remember(academicYears, selectedYearId) {
        academicYears.firstOrNull { it.id == selectedYearId }
    }

    val periodsInYear = remember(refreshTick, selectedYearId) {
        repository.getStudyPeriods(academicYearId = selectedYearId)
    }

    // Match period containing device's real today date (or saved session date if explicitly opened from saved records), else first period
    var selectedPeriodId by remember(selectedYearId, periodsInYear) {
        val refDate = if (keepSavedSessionDateOnce) selectedDate else currentDeviceIsoDate()
        val matching = periodsInYear.firstOrNull { sp ->
            refDate >= sp.startDate && (sp.endDate.isNullOrBlank() || refDate <= sp.endDate)
        } ?: periodsInYear.firstOrNull()
        mutableStateOf<Long?>(matching?.id)
    }
    LaunchedEffect(periodsInYear, selectedYearId) {
        if (periodsInYear.isEmpty()) {
            selectedPeriodId = null
        } else if (selectedPeriodId == null || periodsInYear.none { it.id == selectedPeriodId }) {
            val refDate = if (keepSavedSessionDateOnce) selectedDate else currentDeviceIsoDate()
            val matching = periodsInYear.firstOrNull { sp ->
                refDate >= sp.startDate && (sp.endDate.isNullOrBlank() || refDate <= sp.endDate)
            } ?: periodsInYear.first()
            selectedPeriodId = matching.id
        }
    }

    val selectedPeriod = remember(periodsInYear, selectedPeriodId) {
        periodsInYear.firstOrNull { it.id == selectedPeriodId }
    }

    // Weeks inside the selected Period
    val periodWeeks = remember(selectedPeriod) {
        GradingService.buildWeeksForPeriod(selectedPeriod)
    }

    /**
     * Resolves the default (weekNumber, isoDate) for a given StudyPeriod and its weeks
     * based on the device's real current LocalDate:
     * - If today is inside [period.startDate .. period.endDate]:
     *   selects the week containing today and selects today itself.
     * - If today is outside the period:
     *   picks the closest valid week (first week if today < startDate, last week if today > endDate)
     *   and selects the first valid study day (non-weekend, non-holiday) in that week.
     */
    fun resolveDefaultWeekAndDateForPeriod(
        period: StudyPeriodEntity?,
        weeks: List<PeriodWeekWindow>
    ): Pair<Int, String>? {
        if (period == null || weeks.isEmpty()) return null
        val todayIso = currentDeviceIsoDate()
        val pStart = period.startDate.trim()
        val pEnd = period.endDate?.trim()?.takeIf { it.isNotEmpty() } ?: weeks.last().endDate

        return if (todayIso >= pStart && todayIso <= pEnd) {
            val weekWithToday = weeks.firstOrNull { w ->
                todayIso in w.dates || (todayIso >= w.startDate && todayIso <= w.endDate)
            } ?: weeks.first()
            Pair(weekWithToday.weekNumber, todayIso)
        } else {
            val targetWeek = if (todayIso < pStart) weeks.first() else weeks.last()
            val firstStudyDay = targetWeek.dates.firstOrNull { d ->
                d >= pStart && d <= pEnd && !GradingService.checkDateStatus(d, holidays).isNonWorkingDay
            } ?: weeks.flatMap { it.dates }.firstOrNull { d ->
                d >= pStart && d <= pEnd && !GradingService.checkDateStatus(d, holidays).isNonWorkingDay
            } ?: targetWeek.dates.firstOrNull { d -> d >= pStart && d <= pEnd }
                ?: pStart
            val resolvedWeek = weeks.firstOrNull { w -> firstStudyDay in w.dates } ?: targetWeek
            Pair(resolvedWeek.weekNumber, firstStudyDay)
        }
    }

    var selectedWeekNumber by remember(selectedPeriod?.id, periodWeeks) {
        val resolved = if (keepSavedSessionDateOnce && selectedPeriod != null) {
            val w = periodWeeks.firstOrNull { selectedDate in it.dates }
            if (w != null) Pair(w.weekNumber, selectedDate) else resolveDefaultWeekAndDateForPeriod(selectedPeriod, periodWeeks)
        } else {
            resolveDefaultWeekAndDateForPeriod(selectedPeriod, periodWeeks)
        }
        mutableIntStateOf(resolved?.first ?: 1)
    }

    // Whenever Academic Year -> Period -> Class -> Section is opened or changed,
    // automatically set the week and date according to the real device LocalDate
    LaunchedEffect(selectedYearId, selectedPeriod?.id, selectedClassId, selectedSectionId) {
        if (selectedPeriod != null && periodWeeks.isNotEmpty()) {
            if (keepSavedSessionDateOnce) {
                val pStart = selectedPeriod.startDate.trim()
                val pEnd = selectedPeriod.endDate?.trim()?.takeIf { it.isNotEmpty() } ?: periodWeeks.last().endDate
                if (selectedDate >= pStart && selectedDate <= pEnd) {
                    val matchingW = periodWeeks.firstOrNull { w -> selectedDate in w.dates } ?: periodWeeks.first()
                    selectedWeekNumber = matchingW.weekNumber
                    keepSavedSessionDateOnce = false
                    onConsumeSavedSessionJump()
                    return@LaunchedEffect
                }
                keepSavedSessionDateOnce = false
                onConsumeSavedSessionJump()
            }
            val resolved = resolveDefaultWeekAndDateForPeriod(selectedPeriod, periodWeeks)
            if (resolved != null) {
                selectedWeekNumber = resolved.first
                if (selectedDate != resolved.second) {
                    onSelectDate(resolved.second)
                }
            }
        }
    }

    val selectedWeek = remember(periodWeeks, selectedWeekNumber) {
        periodWeeks.firstOrNull { it.weekNumber == selectedWeekNumber } ?: periodWeeks.firstOrNull()
    }

    val students = remember(refreshTick, selectedClassId, selectedSectionId, classes, sections) {
        repository.getStudents(selectedClassId, selectedSectionId, includeInactive = false)
    }
    var hasSaved by remember(selectedClassId, selectedSectionId, selectedDate, refreshTick) {
        mutableStateOf(repository.hasSavedSession(selectedClassId, selectedSectionId, selectedDate))
    }
    var isEditingSaved by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(!hasSaved)
    }

    var savedEntriesSnapshot by remember(selectedClassId, selectedSectionId, selectedDate, students, refreshTick) {
        mutableStateOf(repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate))
    }

    var entriesMap by remember(selectedClassId, selectedSectionId, selectedDate, students, refreshTick) {
        val saved = savedEntriesSnapshot
        val initial = mutableMapOf<Long, StudentDailyEntry>()
        students.forEach { st ->
            initial[st.id] = saved[st.id] ?: StudentDailyEntry(studentId = st.id)
        }
        mutableStateOf<Map<Long, StudentDailyEntry>>(initial)
    }

    // Top notification banner state (replaces previous message immediately & auto-hides, never covers bottom Save button)
    var topNotificationMessage by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf<String?>(null)
    }
    var topNotificationIsWarning by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(false)
    }
    var topNotificationSeq by remember { mutableIntStateOf(0) }

    fun showTopNotification(msg: String, isWarning: Boolean = false) {
        topNotificationMessage = msg
        topNotificationIsWarning = isWarning
        topNotificationSeq++
    }

    LaunchedEffect(topNotificationSeq) {
        if (topNotificationMessage != null) {
            kotlinx.coroutines.delay(2800L)
            topNotificationMessage = null
        }
    }

    // Track students whose attendance was explicitly touched by the user in this session
    var explicitlyTouchedPresentIds by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(setOf<Long>())
    }

    var pendingConfirmAbsentStudent by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf<StudentEntity?>(null)
    }

    var searchQuery by remember(selectedClassId, selectedSectionId) { mutableStateOf("") }

    val filteredStudents = remember(students, searchQuery) {
        val q = TrackerRepository.normalizeStudentName(searchQuery)
        if (q.isEmpty()) {
            students
        } else {
            val qKey = TrackerRepository.canonicalArabicComparisonKey(q)
            students.filter { st ->
                st.studentName.contains(q) ||
                    TrackerRepository.canonicalArabicComparisonKey(st.studentName).contains(qKey)
            }
        }
    }

    fun executeImmediateAttendanceStatus(student: StudentEntity, targetStatus: AttendanceStatus) {
        val updatedEntries = repository.setStudentAttendanceImmediately(
            studentId = student.id,
            classId = selectedClassId,
            sectionId = selectedSectionId,
            recordDate = selectedDate,
            targetStatus = targetStatus,
            currentDraftEntries = entriesMap,
            academicYearId = selectedYearId,
            periodId = selectedPeriod?.id
        )
        entriesMap = updatedEntries
        savedEntriesSnapshot = repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate)
        hasSaved = true
        if (targetStatus == AttendanceStatus.ABSENT) {
            explicitlyTouchedPresentIds = explicitlyTouchedPresentIds - student.id
            showTopNotification(
                msg = "تم تسجيل الغياب للطالب (${student.studentName}) وحفظه في قاعدة البيانات (الحضور = 0/19 مع بقاء العناصر الأخرى قابلة للتعديل)",
                isWarning = true
            )
        } else {
            explicitlyTouchedPresentIds = explicitlyTouchedPresentIds + student.id
            showTopNotification(
                msg = "تم تسجيل الحضور للطالب (${student.studentName}) وحفظه في قاعدة البيانات (19/19)",
                isWarning = false
            )
        }
        onDataChanged()
    }

    fun executeImmediateAbsent(student: StudentEntity) {
        executeImmediateAttendanceStatus(student, AttendanceStatus.ABSENT)
    }

    fun requestMarkAbsentFromSearch(student: StudentEntity) {
        val wasExplicitlyPresent = savedEntriesSnapshot[student.id]?.attendance == AttendanceStatus.PRESENT ||
            explicitlyTouchedPresentIds.contains(student.id)
        if (wasExplicitlyPresent) {
            pendingConfirmAbsentStudent = student
        } else {
            executeImmediateAbsent(student)
        }
    }

    val dayStatus = remember(selectedDate, holidays) {
        GradingService.checkDateStatus(selectedDate, holidays)
    }

    // Calculate grades scoped to the selected Period if one is selected, otherwise all
    val periodFilterPrefix = remember(selectedPeriod) {
        if (selectedPeriod != null) "PERIOD:${selectedPeriod.id}" else null
    }

    val liveGradesMap = remember(refreshTick, selectedClassId, selectedSectionId, selectedDate, entriesMap, holidays, periodFilterPrefix) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = selectedClassId,
            sectionId = selectedSectionId,
            monthPrefix = periodFilterPrefix,
            liveOverrideDate = if (dayStatus.isNonWorkingDay) null else selectedDate,
            liveOverrideEntries = if (dayStatus.isNonWorkingDay) null else entriesMap
        ).associateBy { it.studentId }
    }

    var showDateDialog by remember { mutableStateOf(false) }
    var tempDateInput by remember(selectedDate) { mutableStateOf(selectedDate) }

    fun openBoundedDatePicker() {
        val parts = selectedDate.split("-")
        val initY = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val initM = (parts.getOrNull(1)?.toIntOrNull() ?: 10) - 1
        val initD = parts.getOrNull(2)?.toIntOrNull() ?: 4

        val dialog = android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                val pickedIso = String.format(java.util.Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                if (selectedPeriod != null) {
                    val pStart = selectedPeriod.startDate
                    val pEnd = selectedPeriod.endDate ?: pStart
                    if (pickedIso < pStart || pickedIso > pEnd) {
                        showTopNotification(
                            "التاريخ المختار خارج نطاق «${selectedPeriod.periodName}» (${GradingService.formatDisplayDate(pStart)} → ${GradingService.formatDisplayDate(pEnd)})",
                            isWarning = true
                        )
                        return@DatePickerDialog
                    }
                }
                onSelectDate(pickedIso)
                val matchingW = periodWeeks.firstOrNull { w -> pickedIso in w.dates }
                if (matchingW != null) {
                    selectedWeekNumber = matchingW.weekNumber
                }
            },
            initY,
            initM,
            initD
        )
        if (selectedPeriod != null) {
            runCatching {
                val minEpoch = java.time.LocalDate.parse(selectedPeriod.startDate)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                dialog.datePicker.minDate = minEpoch
                val endStr = selectedPeriod.endDate
                if (!endStr.isNullOrBlank()) {
                    val maxEpoch = java.time.LocalDate.parse(endStr)
                        .atTime(23, 59, 59)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    dialog.datePicker.maxDate = maxEpoch
                }
            }
        }
        dialog.show()
    }

    val canEdit = !dayStatus.isNonWorkingDay && isEditingSaved && (periodsInYear.isNotEmpty())

    // Calculate live daily score (out of 100) for a single day's entry according to the 100-point daily formula:
    // Attendance (19) + Participation (19) + Notebook (19) + Homework (19) + Behavior (19) + DailyQuiz (5) = 100
    fun calculateSingleDayScoreOutOf100(entry: StudentDailyEntry): Double {
        val att = if (entry.attendance == AttendanceStatus.PRESENT) 19.0 else 0.0
        val part = (entry.participation ?: 1.0) * 19.0
        val note = (entry.notebook ?: 1.0) * 19.0
        val hw = (entry.homework ?: 1.0) * 19.0
        val beh = (entry.behavior ?: 1.0) * 19.0
        val quiz = (entry.dailyTestScore ?: 5.0).coerceIn(0.0, 5.0)
        return GradingService.round2(att + part + note + hw + beh + quiz)
    }

    val allSavedSessions = remember(refreshTick) {
        repository.getSavedSessionsSummary()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Sub-Mode Switcher: [ إضافة متابعة ] | [ السجلات اليومية المحفوظة ]
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (activeSubTab == 0) Slate900 else Slate100,
                    contentColor = if (activeSubTab == 0) Color.White else Slate800,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = 0 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة متابعة", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Surface(
                    color = if (activeSubTab == 1) Teal700 else Slate100,
                    contentColor = if (activeSubTab == 1) Color.White else Slate800,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = 1 }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "السجلات اليومية المحفوظة (${allSavedSessions.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        if (activeSubTab == 1) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SavedRecordsScreen(
                    classes = classes,
                    sections = sections,
                    savedSessions = allSavedSessions,
                    repository = repository,
                    refreshTick = refreshTick,
                    onOpenSavedSession = { clsId, secId, date ->
                        keepSavedSessionDateOnce = true
                        onSelectClass(clsId)
                        onSelectSection(secId)
                        onSelectDate(date)
                        val matchedPeriod = runCatching { repository.getStudyPeriods() }.getOrDefault(emptyList()).firstOrNull { sp ->
                            date >= sp.startDate && (sp.endDate.isNullOrBlank() || date <= sp.endDate)
                        }
                        if (matchedPeriod != null) {
                            selectedYearId = matchedPeriod.academicYearId
                            selectedPeriodId = matchedPeriod.id
                        }
                        activeSubTab = 0
                    },
                    onDataChanged = onDataChanged,
                    onShowMessage = onShowMessage
                )
            }
        } else {
            // Top Notification Banner (appears at the top below screen title, never covers bottom save button)
            if (topNotificationMessage != null) {
            Surface(
                color = if (topNotificationIsWarning) Red50 else Emerald50,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (topNotificationIsWarning) Red600 else Emerald600),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (topNotificationIsWarning) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (topNotificationIsWarning) Red700 else Emerald700,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = topNotificationMessage!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (topNotificationIsWarning) Red700 else Emerald700,
                            maxLines = 2
                        )
                    }
                    IconButton(
                        onClick = { topNotificationMessage = null },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق",
                            tint = Slate600,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // STEP 1 & 2: Academic Year -> Study Period Selection Card
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Academic Year selector
                        Text(
                            text = "1. العام الدراسي:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate700
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            academicYears.forEach { yr ->
                                val isSel = yr.id == selectedYearId
                                Surface(
                                    color = if (isSel) Slate900 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.clickable {
                                        selectedYearId = yr.id
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = yr.yearName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Period selector grouped by Stage inside the selected Academic Year
                        Text(
                            text = "2. الفترة الدراسية (${selectedYear?.yearName ?: ""}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate700
                        )

                        if (periodsInYear.isEmpty()) {
                            Surface(
                                color = Amber50,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Amber600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Amber700)
                                    Text(
                                        text = "يرجى إضافة فترة دراسية أولاً في قسم الفترات لهذا العام الدراسي (${selectedYear?.yearName ?: ""}) لبدء تسجيل المتابعة اليومية.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Amber700
                                    )
                                }
                            }
                        } else {
                            AcademicStage.values().forEach { stage ->
                                val stagePeriods = periodsInYear.filter { it.stageType == stage }
                                if (stagePeriods.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = stage.arabicLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Teal700
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            stagePeriods.forEach { sp ->
                                                val isSel = sp.id == selectedPeriod?.id
                                                val rangeText = "${GradingService.formatDisplayDate(sp.startDate)} → ${
                                                    sp.endDate?.let { GradingService.formatDisplayDate(it) } ?: "مفتوحة"
                                                }"
                                                Surface(
                                                    color = if (isSel) Teal700 else Slate100,
                                                    contentColor = if (isSel) Color.White else Slate800,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(1.dp, if (isSel) Teal700 else Slate200),
                                                    modifier = Modifier.clickable {
                                                        selectedPeriodId = sp.id
                                                        val spWeeks = GradingService.buildWeeksForPeriod(sp)
                                                        val resolved = resolveDefaultWeekAndDateForPeriod(sp, spWeeks)
                                                        if (resolved != null) {
                                                            selectedWeekNumber = resolved.first
                                                            onSelectDate(resolved.second)
                                                        }
                                                    }
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "${stage.arabicLabel} — ${sp.periodName}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = rangeText,
                                                            fontSize = 10.sp,
                                                            color = if (isSel) Teal100 else Slate600
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedPeriod != null) {
                            Surface(
                                color = Teal50,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Teal600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${selectedYear?.yearName ?: ""} • ${selectedPeriod.stageType.arabicLabel} • ${selectedPeriod.periodName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Teal700
                                        )
                                        Text(
                                            text = "نطاق الفترة: من ${GradingService.formatDisplayDate(selectedPeriod.startDate)} إلى ${
                                                selectedPeriod.endDate?.let { GradingService.formatDisplayDate(it) } ?: "مفتوحة"
                                            }",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3 & 4: Class & Section Selection
            item {
                ClassSectionFilterBar(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    onSelectClass = onSelectClass,
                    onSelectSection = onSelectSection
                )
            }

            // STEP 5 & 6: Week Selection inside Period + Date Selection inside Period
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (periodWeeks.isNotEmpty()) {
                            Text(
                                text = "5. أسبوع المتابعة داخل «${selectedPeriod?.periodName ?: ""}»:",
                                fontSize = 12.sp,
                                color = Slate700,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                periodWeeks.forEach { w ->
                                    val isSel = w.weekNumber == selectedWeek?.weekNumber
                                    Surface(
                                        color = if (isSel) Slate900 else Slate100,
                                        contentColor = if (isSel) Color.White else Slate800,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.clickable {
                                            selectedWeekNumber = w.weekNumber
                                            val pStart = selectedPeriod?.startDate?.trim() ?: w.startDate
                                            val pEnd = selectedPeriod?.endDate?.trim()?.takeIf { it.isNotEmpty() } ?: w.endDate
                                            if (selectedDate !in w.dates || selectedDate < pStart || selectedDate > pEnd) {
                                                val todayIso = currentDeviceIsoDate()
                                                val targetDay = if (todayIso in w.dates && todayIso >= pStart && todayIso <= pEnd) {
                                                    todayIso
                                                } else {
                                                    w.dates.firstOrNull { d ->
                                                        d >= pStart && d <= pEnd && !GradingService.checkDateStatus(d, holidays).isNonWorkingDay
                                                    } ?: w.dates.firstOrNull { d -> d >= pStart && d <= pEnd }
                                                }
                                                if (targetDay != null) {
                                                    onSelectDate(targetDay)
                                                }
                                            }
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = w.labelAr,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "${GradingService.formatDisplayDate(w.startDate)} → ${GradingService.formatDisplayDate(w.endDate)}",
                                                fontSize = 10.sp,
                                                color = if (isSel) Teal100 else Slate600
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "6. تاريخ المتابعة اليومية (${selectedWeek?.labelAr ?: "ضمن الفترة"})",
                                    fontSize = 12.sp,
                                    color = Slate600,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${GradingService.getArabicDayName(selectedDate)} — ${GradingService.formatDisplayDate(selectedDate)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { openBoundedDatePicker() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("التقويم", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Days of the selected Week inside the selected Period (including surrounding Sun..Sat days)
                        val weekDatesToShow = selectedWeek?.dates ?: emptyList()
                        if (weekDatesToShow.isNotEmpty()) {
                            val pStart = selectedPeriod?.startDate?.trim() ?: ""
                            val pEnd = selectedPeriod?.endDate?.trim()?.takeIf { it.isNotEmpty() } ?: "9999-12-31"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                weekDatesToShow.forEach { d ->
                                    val isOutOfPeriod = (pStart.isNotEmpty() && d < pStart) || d > pEnd
                                    val isSel = !isOutOfPeriod && d == selectedDate
                                    val status = GradingService.checkDateStatus(d, holidays)
                                    val hasSavedForDay = remember(refreshTick, selectedClassId, selectedSectionId, d) {
                                        if (isOutOfPeriod) false else repository.hasSavedSession(selectedClassId, selectedSectionId, d)
                                    }
                                    Surface(
                                        color = when {
                                            isOutOfPeriod -> Slate50
                                            isSel -> Teal700
                                            status.isNonWorkingDay -> Amber50
                                            hasSavedForDay -> Emerald50
                                            else -> Slate100
                                        },
                                        contentColor = when {
                                            isOutOfPeriod -> Slate500
                                            isSel -> Color.White
                                            status.isNonWorkingDay -> Amber700
                                            hasSavedForDay -> Emerald700
                                            else -> Slate800
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isOutOfPeriod -> Slate200
                                                isSel -> Teal700
                                                status.isNonWorkingDay -> Amber600
                                                hasSavedForDay -> Emerald600
                                                else -> Slate200
                                            }
                                        ),
                                        modifier = if (isOutOfPeriod) {
                                            Modifier.clickable {
                                                showTopNotification(
                                                    "هذا اليوم (${GradingService.formatDisplayDate(d)}) يقع خارج حدود الفترة المختارة ولا يمكن تحديده أو التسجيل فيه",
                                                    isWarning = true
                                                )
                                            }
                                        } else {
                                            Modifier.clickable { onSelectDate(d) }
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = GradingService.getArabicDayName(d),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = GradingService.formatDisplayDate(d),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (isOutOfPeriod) {
                                                Text(
                                                    text = "خارج الفترة",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate500
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (dayStatus.isNonWorkingDay) {
                item {
                    Surface(
                        color = Amber50,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Amber600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = Amber700)
                            Column {
                                Text(
                                    text = dayStatus.title,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Amber700
                                )
                                Text(
                                    text = dayStatus.subtitle,
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                            }
                        }
                    }
                }
            } else if (hasSaved && !isEditingSaved) {
                item {
                    Surface(
                        color = Emerald50,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Emerald600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "هذا السجل محفوظ مسبقاً (وضع العرض الآمن)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = Emerald700
                                )
                                Text(
                                    text = "الحضور والغياب يُحفظ مباشرة عند الضغط. لتعديل المشاركة أو الدفتر أو الواجب أو السلوك أو الاختبار اضغط على تعديل السجل",
                                    fontSize = 11.sp,
                                    color = Slate700
                                )
                            }
                            Button(
                                onClick = {
                                    isEditingSaved = true
                                    showTopNotification("تم تفعيل وضع تعديل التقييمات اليومية")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعديل السجل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // STEP 7: Student Search & Fast Exception Entry Banner
            if (!dayStatus.isNonWorkingDay && periodsInYear.isNotEmpty()) {
                item {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "7. قائمة طلاب الصف والشعبة (${students.size} طالب) — الافتراضي 100/100 (إدخال الاستثناءات فقط)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Teal700
                                )
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث عن طالب بالاسم داخل الشعبة لتسجيل الغياب أو التقييم...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = "بحث", tint = Teal700)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (searchQuery.trim().isNotEmpty()) {
                                Text(
                                    text = "نتائج البحث (${filteredStudents.size} طالب) — بدّل الحالة مباشرة مع الحفظ الفوري:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate700
                                )

                                if (filteredStudents.isEmpty()) {
                                    Text(
                                        text = "لا يوجد طالب مطابق للاسم \"$searchQuery\" في هذه الشعبة",
                                        fontSize = 12.sp,
                                        color = Red700,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredStudents.forEach { st ->
                                            val stEntry = entriesMap[st.id] ?: StudentDailyEntry(studentId = st.id)
                                            val isAlreadyAbsent = stEntry.attendance == AttendanceStatus.ABSENT
                                            val wasSavedInDb = savedEntriesSnapshot.containsKey(st.id)

                                            Surface(
                                                color = if (isAlreadyAbsent) Red50 else Slate50,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, if (isAlreadyAbsent) Red600 else Slate200),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "${st.studentName} — ${if (isAlreadyAbsent) "غائب" else "حاضر"}",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 14.sp,
                                                            color = Slate900,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = when {
                                                                isAlreadyAbsent -> "الحالة الحالية: غائب (درجة الحضور لليوم = 0 / 19)"
                                                                wasSavedInDb -> "الحالة الحالية: حاضر (درجة الحضور لليوم = 19 / 19)"
                                                                else -> "الحالة الحالية: حاضر افتراضياً (19 / 19)"
                                                            },
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAlreadyAbsent) Red700 else Emerald700
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    if (isAlreadyAbsent) {
                                                        Button(
                                                            onClick = {
                                                                executeImmediateAttendanceStatus(st, AttendanceStatus.PRESENT)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                        ) {
                                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("حاضر", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                                        }
                                                    } else {
                                                        Button(
                                                            onClick = {
                                                                requestMarkAbsentFromSearch(st)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Red600),
                                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                        ) {
                                                            Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("غائب", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (periodsInYear.isNotEmpty()) {
                items(filteredStudents, key = { it.id }) { st ->
                    val entry = entriesMap[st.id] ?: StudentDailyEntry(studentId = st.id)
                    val grade = liveGradesMap[st.id]
                    val isPresent = entry.attendance == AttendanceStatus.PRESENT
                    val todaySingleScore = calculateSingleDayScoreOutOf100(entry)

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isPresent) Slate200 else Red600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Student Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenStudentProfile(st.id) }
                                ) {
                                    Text(
                                        text = st.studentName,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Slate900
                                    )
                                    val attDayScore = if (isPresent) "19" else "0"
                                    val partDayScore = GradingService.formatScore((entry.participation ?: 1.0) * 19.0)
                                    val noteDayScore = GradingService.formatScore((entry.notebook ?: 1.0) * 19.0)
                                    val hwDayScore = GradingService.formatScore((entry.homework ?: 1.0) * 19.0)
                                    val behDayScore = GradingService.formatScore((entry.behavior ?: 1.0) * 19.0)
                                    val quizDayScore = GradingService.formatScore((entry.dailyTestScore ?: 5.0).coerceIn(0.0, 5.0))
                                    Text(
                                        text = "درجة اليوم: حضور $attDayScore | مشاركة $partDayScore | دفتر $noteDayScore | واجب $hwDayScore | سلوك $behDayScore | اختبار $quizDayScore",
                                        fontSize = 11.sp,
                                        color = Slate600
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = if (isPresent) Emerald50 else Red50,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, if (isPresent) Emerald600 else Red600)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${GradingService.formatScore(todaySingleScore)} / 100",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                                color = if (isPresent) Emerald700 else Red700
                                            )
                                            Text(
                                                text = "درجة هذا اليوم",
                                                fontSize = 9.sp,
                                                color = if (isPresent) Emerald700 else Red700
                                            )
                                        }
                                    }
                                    if (grade != null) {
                                        Surface(
                                            color = Teal50,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, Teal600),
                                            modifier = Modifier.clickable { onOpenStudentProfile(st.id) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "${GradingService.formatScore(grade.totalScore)} / 100",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 13.sp,
                                                    color = Teal700
                                                )
                                                Text(
                                                    text = "معدل الفترة",
                                                    fontSize = 9.sp,
                                                    color = Teal700
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Attendance Present / Absent Toggle (Always enabled on working days & saves immediately to SQLite)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (isPresent) Emerald600 else Emerald50,
                                    contentColor = if (isPresent) Color.White else Emerald700,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = !dayStatus.isNonWorkingDay) {
                                            executeImmediateAttendanceStatus(st, AttendanceStatus.PRESENT)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حاضر (19/19)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Surface(
                                    color = if (!isPresent) Red600 else Red50,
                                    contentColor = if (!isPresent) Color.White else Red700,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = !dayStatus.isNonWorkingDay) {
                                            executeImmediateAttendanceStatus(st, AttendanceStatus.ABSENT)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("غائب (0/19)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            if (!isPresent) {
                                Surface(
                                    color = Red50,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Red100),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "الطالب غائب اليوم (الحضور = 0/19) — تبقى باقي العناصر على قيمها الافتراضية ويمكنك تعديل أي عنصر يدوياً لهذا اليوم:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Red700,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Evaluations & Daily Quiz remain visible and editable even when the student is ABSENT
                            val evalEnabled = canEdit
                            ThreeStateRatingRow(
                                label = "المشاركة",
                                currentValue = entry.participation,
                                enabled = evalEnabled,
                                scorePreviewText = "(${GradingService.formatScore((entry.participation ?: 1.0) * 19.0)} / 19)",
                                onValueChange = { v ->
                                    entriesMap = entriesMap.toMutableMap().apply {
                                        put(st.id, entry.copy(participation = v))
                                    }
                                    onDirtyChange(true)
                                }
                            )
                            ThreeStateRatingRow(
                                label = "الدفتر",
                                currentValue = entry.notebook,
                                enabled = evalEnabled,
                                scorePreviewText = "(${GradingService.formatScore((entry.notebook ?: 1.0) * 19.0)} / 19)",
                                onValueChange = { v ->
                                    entriesMap = entriesMap.toMutableMap().apply {
                                        put(st.id, entry.copy(notebook = v))
                                    }
                                    onDirtyChange(true)
                                }
                            )
                            ThreeStateRatingRow(
                                label = "الواجب",
                                currentValue = entry.homework,
                                enabled = evalEnabled,
                                scorePreviewText = "(${GradingService.formatScore((entry.homework ?: 1.0) * 19.0)} / 19)",
                                onValueChange = { v ->
                                    entriesMap = entriesMap.toMutableMap().apply {
                                        put(st.id, entry.copy(homework = v))
                                    }
                                    onDirtyChange(true)
                                }
                            )
                            ThreeStateRatingRow(
                                label = "السلوك",
                                currentValue = entry.behavior,
                                enabled = evalEnabled,
                                scorePreviewText = "(${GradingService.formatScore((entry.behavior ?: 1.0) * 19.0)} / 19)",
                                onValueChange = { v ->
                                    entriesMap = entriesMap.toMutableMap().apply {
                                        put(st.id, entry.copy(behavior = v))
                                    }
                                    onDirtyChange(true)
                                }
                            )

                            // Daily Test Score (0 to 5)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate50, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "الاختبار اليومي (من 5)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate800
                                    )
                                    Text(
                                        text = if (entry.dailyTestScore != null) {
                                            "الدرجة المسجلة: ${GradingService.formatScore(entry.dailyTestScore)} / 5"
                                        } else {
                                            "الافتراضي: 5 / 5 (لم يسجل استثناء)"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entry.dailyTestScore != null) Teal700 else Slate500
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(5.0, 4.5, 4.0, 3.0, 2.0, 1.0, 0.0).forEach { sc ->
                                        val isSel = entry.dailyTestScore == sc
                                        Surface(
                                            color = if (isSel) Teal700 else Color.White,
                                            contentColor = if (isSel) Color.White else Slate800,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (isSel) Teal700 else Slate200),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = evalEnabled) {
                                                    val nextVal = if (isSel) null else sc
                                                    entriesMap = entriesMap.toMutableMap().apply {
                                                        put(st.id, entry.copy(dailyTestScore = nextVal))
                                                    }
                                                    onDirtyChange(true)
                                                }
                                        ) {
                                            Text(
                                                text = GradingService.formatScore(sc),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Save Bar
        if (!dayStatus.isNonWorkingDay && periodsInYear.isNotEmpty()) {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (selectedPeriod != null) {
                                val pStart = selectedPeriod.startDate
                                val pEnd = selectedPeriod.endDate ?: pStart
                                if (selectedDate < pStart || selectedDate > pEnd) {
                                    showTopNotification(
                                        "لا يمكن حفظ المتابعة خارج نطاق الفترة المختارة (${GradingService.formatDisplayDate(pStart)} → ${GradingService.formatDisplayDate(pEnd)})",
                                        isWarning = true
                                    )
                                    return@Button
                                }
                            }
                            repository.saveDailySession(
                                classId = selectedClassId,
                                sectionId = selectedSectionId,
                                recordDate = selectedDate,
                                entries = entriesMap,
                                academicYearId = selectedYearId,
                                periodId = selectedPeriod?.id
                            )
                            savedEntriesSnapshot = repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate)
                            hasSaved = true
                            isEditingSaved = false
                            onDirtyChange(false)
                            onDataChanged()
                            showTopNotification("تم حفظ السجل اليومي (${GradingService.formatDisplayDate(selectedDate)}) بنجاح")
                        },
                        enabled = isEditingSaved,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDirty) "حفظ التعديلات في السجل اليومي" else "حفظ السجل اليومي",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
        }
    }

    if (pendingConfirmAbsentStudent != null) {
        val targetStudent = pendingConfirmAbsentStudent!!
        AlertDialog(
            onDismissRequest = { pendingConfirmAbsentStudent = null },
            title = {
                Text(
                    text = "تأكيد تغيير الحالة إلى غائب",
                    fontWeight = FontWeight.ExtraBold,
                    color = Red700
                )
            },
            text = {
                Text(
                    text = "الطالب (${targetStudent.studentName}) مسجل حاضر مسبقاً لهذا اليوم (${GradingService.formatDisplayDate(selectedDate)}).\nهل تريد تغيير حالته من حاضر إلى غائب (درجة الحضور لليوم = 0)؟",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmAbsentStudent = null
                        executeImmediateAbsent(targetStudent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("تأكيد الغياب", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmAbsentStudent = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showDateDialog) {
        AlertDialog(
            onDismissRequest = { showDateDialog = false },
            title = { Text("اختيار تاريخ المتابعة (YYYY-MM-DD)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tempDateInput,
                    onValueChange = { tempDateInput = it },
                    singleLine = true,
                    placeholder = { Text("2026-10-04") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempDateInput.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        if (selectedPeriod != null) {
                            val pStart = selectedPeriod.startDate
                            val pEnd = selectedPeriod.endDate ?: pStart
                            if (tempDateInput < pStart || tempDateInput > pEnd) {
                                showTopNotification("التاريخ خارج نطاق الفترة المختارة", isWarning = true)
                                return@Button
                            }
                        }
                        showDateDialog = false
                        onSelectDate(tempDateInput)
                    } else {
                        showTopNotification("يرجى إدخال التاريخ بصيغة YYYY-MM-DD", isWarning = true)
                    }
                }) {
                    Text("انتقال للتاريخ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ============================================================================
// SAVED RECORDS SCREEN ("السجلات اليومية المحفوظة")
// ============================================================================
@Composable
fun SavedRecordsScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    savedSessions: List<SavedSessionSummary>,
    repository: TrackerRepository? = null,
    refreshTick: Int = 0,
    onOpenSavedSession: (Long, Long, String) -> Unit,
    onDataChanged: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val academicYears = remember(refreshTick) {
        runCatching { repository?.getAcademicYears() ?: emptyList() }.getOrDefault(emptyList())
    }
    var filterYearId by remember { mutableStateOf<Long?>(null) }
    val periodsForFilter = remember(refreshTick, filterYearId) {
        runCatching { repository?.getStudyPeriods(academicYearId = filterYearId) ?: emptyList() }.getOrDefault(emptyList())
    }
    var filterPeriodId by remember { mutableStateOf<Long?>(null) }
    var filterClassId by remember { mutableStateOf<Long?>(null) }
    var filterSectionId by remember { mutableStateOf<Long?>(null) }
    var pendingDeleteSession by remember { mutableStateOf<SavedSessionSummary?>(null) }

    val availableSections = remember(sections, filterClassId) {
        if (filterClassId == null) emptyList() else sections.filter { it.classId == filterClassId }
    }

    val filtered = remember(savedSessions, filterYearId, filterPeriodId, filterClassId, filterSectionId) {
        savedSessions.filter { s ->
            val matchYear = filterYearId == null || s.academicYearId == filterYearId
            val matchPeriod = filterPeriodId == null || s.periodId == filterPeriodId
            val matchClass = filterClassId == null || s.classId == filterClassId
            val matchSection = filterSectionId == null || s.sectionId == filterSectionId
            matchYear && matchPeriod && matchClass && matchSection
        }.distinctBy { "${it.recordDate}_${it.classId}_${it.sectionId}" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Filters Card: Academic Year -> Period -> Class -> Section
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تصفية السجلات اليومية المحفوظة (${filtered.size}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900
                    )

                    if (academicYears.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (filterYearId == null) Slate900 else Slate100,
                                contentColor = if (filterYearId == null) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    filterYearId = null
                                    filterPeriodId = null
                                }
                            ) {
                                Text(
                                    text = "كل الأعوام",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            academicYears.forEach { yr ->
                                val isSel = filterYearId == yr.id
                                Surface(
                                    color = if (isSel) Slate900 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        filterYearId = yr.id
                                        filterPeriodId = null
                                    }
                                ) {
                                    Text(
                                        text = yr.yearName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (periodsForFilter.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (filterPeriodId == null) Teal700 else Slate100,
                                contentColor = if (filterPeriodId == null) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { filterPeriodId = null }
                            ) {
                                Text(
                                    text = "كل الفترات",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            periodsForFilter.forEach { sp ->
                                val isSel = filterPeriodId == sp.id
                                Surface(
                                    color = if (isSel) Teal700 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { filterPeriodId = sp.id }
                                ) {
                                    Text(
                                        text = "${sp.stageType.arabicLabel} - ${sp.periodName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Class filter
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (filterClassId == null) Slate900 else Slate100,
                            contentColor = if (filterClassId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                filterClassId = null
                                filterSectionId = null
                            }
                        ) {
                            Text(
                                text = "جميع الصفوف",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        classes.forEach { c ->
                            val isSel = filterClassId == c.id
                            Surface(
                                color = if (isSel) Slate900 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    filterClassId = c.id
                                    filterSectionId = null
                                }
                            ) {
                                Text(
                                    text = c.className,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Section filter when a class is chosen
                    if (availableSections.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (filterSectionId == null) Teal700 else Slate100,
                                contentColor = if (filterSectionId == null) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { filterSectionId = null }
                            ) {
                                Text(
                                    text = "كل الشعب",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            availableSections.forEach { sec ->
                                val isSel = filterSectionId == sec.id
                                Surface(
                                    color = if (isSel) Teal700 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { filterSectionId = sec.id }
                                ) {
                                    Text(
                                        text = "شعبة ${sec.sectionName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد سجلات متابعة يومية محفوظة تطابق عوامل التصفية المختارة.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(filtered, key = { "${it.recordDate}_${it.classId}_${it.sectionId}" }) { s ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSavedSession(s.classId, s.sectionId, s.recordDate) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${GradingService.getArabicDayName(s.recordDate)} — ${GradingService.formatDisplayDate(s.recordDate)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "${s.className} • شعبة ${s.sectionName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                            val yearPeriodMeta = buildString {
                                if (!s.academicYearName.isNullOrBlank()) {
                                    append("العام الدراسي: ${s.academicYearName}")
                                }
                                if (s.stageType != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append(s.stageType.arabicLabel)
                                }
                                if (!s.periodName.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" — ")
                                    append(s.periodName)
                                }
                            }
                            if (yearPeriodMeta.isNotBlank()) {
                                Text(
                                    text = yearPeriodMeta,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Teal700
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onOpenSavedSession(s.classId, s.sectionId, s.recordDate) },
                                colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فتح / تعديل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (repository != null) {
                                OutlinedButton(
                                    onClick = { pendingDeleteSession = s },
                                    border = BorderStroke(1.dp, Red600),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف السجل", tint = Red600, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red700)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الطلاب: ${s.studentCount}", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Text("حضور: ${s.presentCount}", fontSize = 12.sp, color = Emerald700, fontWeight = FontWeight.Bold)
                        Text("غياب: ${s.absentCount}", fontSize = 12.sp, color = Red700, fontWeight = FontWeight.Bold)
                        Text("تقييمات: ${s.evaluationsCount}", fontSize = 12.sp, color = Teal700, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (pendingDeleteSession != null && repository != null) {
        val target = pendingDeleteSession!!
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = {
                Text("تأكيد حذف سجل المتابعة اليومي", fontWeight = FontWeight.ExtraBold, color = Red700)
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف سجل المتابعة اليومي لتاريخ ${GradingService.formatDisplayDate(target.recordDate)} (${target.className} — شعبة ${target.sectionName})؟\nسيتم حذف بيانات الحضور والتقييمات المسجلة لهذا اليوم فقط.",
                    fontSize = 13.sp,
                    color = Slate800
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteSavedSession(target.classId, target.sectionId, target.recordDate)
                        pendingDeleteSession = null
                        onDataChanged()
                        onShowMessage("تم حذف سجل المتابعة اليومي (${GradingService.formatDisplayDate(target.recordDate)}) بنجاح")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("تأكيد الحذف", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }
}

// ============================================================================
// STUDENT PROFILE SCREEN
// ============================================================================
@Composable
fun StudentProfileScreen(
    studentId: Long,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onBack: () -> Unit,
    onDataChanged: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    var localTick by remember { mutableIntStateOf(0) }
    val studyPeriods = remember(refreshTick, localTick) {
        repository.getStudyPeriods()
    }
    // null = "كامل السجلات" (All periods), non-null = specific StudyPeriod.id
    var selectedPeriodId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(studyPeriods) {
        if (selectedPeriodId != null && studyPeriods.none { it.id == selectedPeriodId }) {
            selectedPeriodId = null
        }
    }
    val selectedFilter = remember(selectedPeriodId) {
        if (selectedPeriodId != null) "PERIOD:$selectedPeriodId" else "ALL"
    }

    val allGrades = remember(refreshTick, localTick, studentId, selectedFilter) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            monthPrefix = selectedFilter
        )
    }
    val grade = allGrades.find { it.studentId == studentId }

    val comprehensiveReport = remember(refreshTick, localTick, studentId, selectedFilter) {
        GradingService.calculateComprehensiveStudentReports(
            dbHelper = dbHelper,
            repository = repository,
            baseDateFilter = selectedFilter
        ).find { it.studentId == studentId }
    }

    // Written exam grade dialog state
    var editingPeriodBreakdown by remember { mutableStateOf<StudentPeriodGradeBreakdown?>(null) }
    var writtenExamInput by remember { mutableStateOf("") }
    var writtenExamError by remember { mutableStateOf<String?>(null) }

    if (grade == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("لم يتم العثور على بيانات الطالب")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBack) { Text("رجوع") }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Student Identity + Final Average + Daily Grade Header
        item {
            val behaviorZeroCount = grade.behaviorRatings.count { it.value == 0.0 }
            val hasFrequentBehaviorZero = behaviorZeroCount >= 6
            val finalAvg = comprehensiveReport?.finalAverage
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = grade.studentName,
                                color = if (hasFrequentBehaviorZero) Red600 else Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${grade.className} — شعبة ${grade.sectionName}",
                                color = Teal100,
                                fontSize = 13.sp
                            )
                        }
                        val selectedPeriodForHeader = studyPeriods.firstOrNull { it.id == selectedPeriodId }
                        val isMidYearSelectedHeader = selectedPeriodForHeader?.stageType == AcademicStage.MID_YEAR
                        val selectedMidYearBreakdown = if (isMidYearSelectedHeader) {
                            comprehensiveReport?.periodBreakdowns?.firstOrNull { it.period.id == selectedPeriodId }
                        } else null
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Teal700, shape = RoundedCornerShape(12.dp)) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (isMidYearSelectedHeader) {
                                        val midScore = selectedMidYearBreakdown?.writtenExamGrade
                                        Text(
                                            text = if (midScore != null) "${GradingService.formatScore(midScore)}/100" else "غير مدخل",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text("درجة نصف السنة", color = Teal100, fontSize = 10.sp)
                                    } else {
                                        Text(
                                            text = "${GradingService.formatScore(grade.totalScore)} / 100",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text("الدرجة اليومية", color = Teal100, fontSize = 10.sp)
                                    }
                                }
                            }
                            Surface(color = Emerald700, shape = RoundedCornerShape(12.dp)) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (finalAvg != null) "${GradingService.formatScore(finalAvg)} / 100" else "--",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text("المعدل النهائي", color = Emerald50, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    // Filter chips for Student Profile: All ("كامل السجلات") + all StudyPeriods by Period ID
                    if (studyPeriods.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isAll = selectedPeriodId == null
                            Surface(
                                color = if (isAll) Emerald600 else Slate800,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedPeriodId = null }
                            ) {
                                Text(
                                    text = "كامل السجلات",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            studyPeriods.forEach { sp ->
                                val isSel = selectedPeriodId == sp.id
                                val chipLabel = if (sp.stageType == AcademicStage.MID_YEAR) {
                                    if (sp.periodName.isBlank() || sp.periodName == "الفترة" || sp.periodName == "نصف السنة") {
                                        "نصف السنة"
                                    } else {
                                        "${sp.stageType.arabicLabel} - ${sp.periodName}"
                                    }
                                } else {
                                    "${sp.stageType.arabicLabel} - ${sp.periodName}"
                                }
                                Surface(
                                    color = if (isSel) Emerald600 else Slate800,
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { selectedPeriodId = sp.id }
                                ) {
                                    Text(
                                        text = chipLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Study Periods Grades & Written Exam & Period Average Card ("درجات الفترات والامتحان التحريري والمعدل النهائي")
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "درجات الفترات الدراسية والامتحان التحريري",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "معدل الفترة = (الدرجة اليومية + درجة الامتحان التحريري) ÷ 2",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }
                    }

                    val allBreakdowns = comprehensiveReport?.periodBreakdowns ?: emptyList()
                    val breakdowns = if (selectedPeriodId != null) {
                        allBreakdowns.filter { it.period.id == selectedPeriodId }
                    } else {
                        allBreakdowns
                    }
                    if (breakdowns.isEmpty()) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لم يتم إنشاء فترات دراسية بعد. يمكنك إضافة فترة دراسية من قسم «الفترات الدراسية» لحساب درجة كل فترة وإدخال درجة الامتحان التحريري وحساب المعدل النهائي.",
                                fontSize = 12.sp,
                                color = Slate700,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        breakdowns.forEach { bd ->
                            val period = bd.period
                            val isMidYear = period.stageType == AcademicStage.MID_YEAR
                            val periodDisplayTitle = if (isMidYear) {
                                if (period.periodName.isBlank() || period.periodName == "الفترة" || period.periodName == "نصف السنة") {
                                    "نصف السنة"
                                } else {
                                    "${period.stageType.arabicLabel} — ${period.periodName}"
                                }
                            } else {
                                "${period.stageType.arabicLabel} — ${period.periodName}"
                            }
                            val dateRangeLabel = "${GradingService.formatDisplayDate(period.startDate)} → ${
                                if (period.endDate != null) GradingService.formatDisplayDate(period.endDate) else "مفتوحة"
                            }"
                            Surface(
                                color = Slate50,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = periodDisplayTitle,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    color = Slate900
                                                )
                                                Surface(
                                                    color = if (period.status == StudyPeriodStatus.OPEN) Emerald100 else Slate200,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = period.status.arabicLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (period.status == StudyPeriodStatus.OPEN) Emerald700 else Slate700,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = dateRangeLabel,
                                                fontSize = 11.sp,
                                                color = Slate600
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                editingPeriodBreakdown = bd
                                                writtenExamInput = bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
                                                writtenExamError = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isMidYear) {
                                                    if (bd.writtenExamGrade != null) "تعديل نصف السنة" else "إدخال نصف السنة"
                                                } else {
                                                    if (bd.writtenExamGrade != null) "تعديل التحريري" else "إدخال التحريري"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

                                    if (isMidYear) {
                                        // Single direct score box for نصف السنة: درجة نصف السنة / 100 ("78/100" or "غير مدخل")
                                        Surface(
                                            color = if (bd.writtenExamGrade != null) Emerald50 else Color.White,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, if (bd.writtenExamGrade != null) Emerald600 else Slate200),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    editingPeriodBreakdown = bd
                                                    writtenExamInput = bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
                                                    writtenExamError = null
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "درجة نصف السنة / 100",
                                                    fontSize = 12.sp,
                                                    color = Slate800,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = if (bd.writtenExamGrade != null) "${GradingService.formatScore(bd.writtenExamGrade)}/100" else "غير مدخل",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (bd.writtenExamGrade != null) Emerald700 else Slate500
                                                )
                                            }
                                        }
                                    } else {
                                        // 3 Metrics Row: اليومي | التحريري | معدل الفترة
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Slate200),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("الدرجة اليومية", fontSize = 11.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = "${GradingService.formatScore(bd.dailyGrade)} / 100",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Slate900
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Slate200),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        editingPeriodBreakdown = bd
                                                        writtenExamInput = bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
                                                        writtenExamError = null
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("الامتحان التحريري", fontSize = 11.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = if (bd.writtenExamGrade != null) "${GradingService.formatScore(bd.writtenExamGrade)} / 100" else "--",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (bd.writtenExamGrade != null) Teal700 else Slate500
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = if (bd.periodAverage != null) Emerald50 else Color.White,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (bd.periodAverage != null) Emerald600 else Slate200),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text("معدل الفترة", fontSize = 11.sp, color = Slate700, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = if (bd.periodAverage != null) "${GradingService.formatScore(bd.periodAverage)} / 100" else "--",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (bd.periodAverage != null) Emerald700 else Slate500
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Final Average Summary Bar (shown when viewing all periods)
                        if (selectedPeriodId == null) {
                            val finalAvg = comprehensiveReport?.finalAverage
                            val completedCount = comprehensiveReport?.completedPeriodsCount ?: 0
                            Surface(
                                color = Slate900,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "المعدل النهائي لجميع الفترات المكتملة",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (completedCount > 0) {
                                                "محسوب من $completedCount فترة مكتملة (مجموع معدلات الفترات ÷ $completedCount)"
                                            } else {
                                                "أدخل درجة الامتحان التحريري لفترة واحدة على الأقل لحساب المعدل النهائي"
                                            },
                                            color = Slate200,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = if (finalAvg != null) "${GradingService.formatScore(finalAvg)} / 100" else "--",
                                        color = Emerald50,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val selectedPeriodObj = studyPeriods.firstOrNull { it.id == selectedPeriodId }
        val isSelectedMidYear = selectedPeriodObj?.stageType == AcademicStage.MID_YEAR

        if (!isSelectedMidYear) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تفصيل عناصر الدرجة اليومية (من 100)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        ProfileScoreRow("1. الحضور (${grade.presentDays} حضور / ${grade.absentDays} غياب من أصل ${grade.totalWorkingDays} يوم دوام)", grade.attendanceScore, 19)
                        ProfileScoreRow("2. المشاركة (${grade.participationRatings.size} تقييم مسجل)", grade.participationScore, 19)
                        ProfileScoreRow("3. الدفتر (${grade.notebookRatings.size} تقييم مسجل)", grade.notebookScore, 19)
                        ProfileScoreRow("4. الواجب (${grade.homeworkRatings.size} تقييم مسجل)", grade.homeworkScore, 19)
                        ProfileScoreRow("5. السلوك (${grade.behaviorRatings.size} تقييم مسجل)", grade.behaviorScore, 19)
                        ProfileScoreRow("6. الاختبار اليومي (${grade.dailyTests.size} اختبار)", grade.quizScore, 5)
                    }
                }
            }

            item {
                ProfileHistoryCard("سجل تقييمات المشاركة", grade.participationRatings, isTest = false)
            }
            item {
                ProfileHistoryCard("سجل تقييمات الدفتر", grade.notebookRatings, isTest = false)
            }
            item {
                ProfileHistoryCard("سجل تقييمات الواجب", grade.homeworkRatings, isTest = false)
            }
            item {
                ProfileHistoryCard("سجل تقييمات السلوك", grade.behaviorRatings, isTest = false)
            }
            item {
                ProfileHistoryCard("سجل الاختبارات اليومية (من 5)", grade.dailyTests, isTest = true)
            }
        }
    }

    // Written Exam Entry/Edit Dialog
    if (editingPeriodBreakdown != null) {
        val bd = editingPeriodBreakdown!!
        val isMidYear = bd.period.stageType == AcademicStage.MID_YEAR
        val periodTitle = if (isMidYear) "نصف السنة" else bd.period.periodName
        AlertDialog(
            onDismissRequest = { editingPeriodBreakdown = null },
            title = {
                Text(
                    text = if (isMidYear) "درجة نصف السنة / 100" else "درجة الامتحان التحريري — ${bd.period.periodName}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الطالب: ${grade.studentName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                    if (!isMidYear) {
                        Text(
                            text = "الدرجة اليومية المحسوبة لهذه الفترة: ${GradingService.formatScore(bd.dailyGrade)} / 100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Teal700
                        )
                    }

                    if (writtenExamError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = writtenExamError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = writtenExamInput,
                        onValueChange = {
                            writtenExamInput = it
                            writtenExamError = null
                        },
                        label = {
                            Text(
                                if (isMidYear) "درجة نصف السنة / 100" else "درجة الامتحان التحريري (من 0 إلى 100)"
                            )
                        },
                        placeholder = { Text(if (isMidYear) "مثال: 75" else "مثال: 70") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedPreview = writtenExamInput.trim().toDoubleOrNull()
                    if (parsedPreview != null && parsedPreview in 0.0..100.0) {
                        Surface(
                            color = Emerald50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMidYear) {
                                Text(
                                    text = "درجة نصف السنة بعد الحفظ: ${GradingService.formatScore(parsedPreview)}/100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                val previewAvg = GradingService.round2((bd.dailyGrade + parsedPreview) / 2.0)
                                Text(
                                    text = "معدل ${bd.period.periodName} بعد الحفظ: (${GradingService.formatScore(bd.dailyGrade)} + ${GradingService.formatScore(parsedPreview)}) ÷ 2 = ${GradingService.formatScore(previewAvg)} / 100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = writtenExamInput.trim()
                        val numericVal: Double? = if (trimmed.isEmpty()) {
                            null
                        } else {
                            trimmed.toDoubleOrNull()
                        }
                        if (trimmed.isNotEmpty() && numericVal == null) {
                            writtenExamError = "يرجى إدخال رقم صحيح أو عشري صالح بين 0 و 100"
                            return@Button
                        }
                        if (numericVal != null && (numericVal < 0.0 || numericVal > 100.0)) {
                            writtenExamError = "يجب أن تكون الدرجة بين 0 و 100"
                            return@Button
                        }
                        val err = repository.upsertPeriodGrade(
                            studentId = studentId,
                            periodId = bd.period.id,
                            dailyGrade = bd.dailyGrade,
                            writtenExamGrade = numericVal
                        )
                        if (err != null) {
                            writtenExamError = err
                        } else {
                            editingPeriodBreakdown = null
                            localTick++
                            onDataChanged()
                            onShowMessage(
                                if (numericVal != null) {
                                    if (isMidYear) {
                                        "تم حفظ درجة نصف السنة (${GradingService.formatScore(numericVal)}/100)"
                                    } else {
                                        "تم حفظ درجة الامتحان التحريري وحساب معدل $periodTitle في قاعدة البيانات"
                                    }
                                } else {
                                    "تم مسح الدرجة لـ $periodTitle"
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("حفظ الدرجة", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bd.writtenExamGrade != null) {
                        TextButton(
                            onClick = {
                                repository.upsertPeriodGrade(
                                    studentId = studentId,
                                    periodId = bd.period.id,
                                    dailyGrade = bd.dailyGrade,
                                    writtenExamGrade = null
                                )
                                editingPeriodBreakdown = null
                                localTick++
                                onDataChanged()
                                onShowMessage("تم مسح الدرجة لـ $periodTitle")
                            }
                        ) {
                            Text("مسح الدرجة", color = Red600, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(onClick = { editingPeriodBreakdown = null }) {
                        Text("إلغاء", color = Slate700, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileScoreRow(title: String, score: Double, maxScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate50, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800, modifier = Modifier.weight(1f))
        Text(
            text = "${GradingService.formatScore(score)} / $maxScore",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Teal700
        )
    }
}

@Composable
private fun ProfileHistoryCard(title: String, items: List<RatedDateItem>, isTest: Boolean) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Slate900)
            if (items.isEmpty()) {
                Text("لا توجد تقييمات مسجلة بعد (تحتسب الدرجة كاملة بدون ظلم الطالب)", fontSize = 12.sp, color = Slate500)
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${GradingService.getArabicDayName(item.date)} ${GradingService.formatDisplayDate(item.date)}",
                            fontSize = 12.sp,
                            color = Slate700
                        )
                        Text(
                            text = if (isTest) "${GradingService.formatScore(item.value)} / 5" else GradingService.getRatingLabel(item.value),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Teal700
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// REPORTS & RESULTS SCREEN
// ============================================================================
@Composable
fun ReportsScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onOpenStudentProfile: (Long) -> Unit,
    onDataChanged: () -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var sortMode by remember { mutableStateOf("HIGHEST") }
    var localTick by remember { mutableIntStateOf(0) }

    // Filter state: Class ("ALL" or specific classId), Section ("ALL" or specific sectionId), StudyPeriod ("ALL" or specific periodId)
    var filterClassId by remember(selectedClassId) { mutableStateOf<Long?>(selectedClassId) }
    var filterSectionId by remember(selectedSectionId) { mutableStateOf<Long?>(selectedSectionId) }
    var selectedPeriodFilterId by remember { mutableStateOf<Long?>(null) } // null = جميع الفترات

    val studyPeriods = remember(refreshTick, localTick) {
        repository.getStudyPeriods()
    }

    val availableSections = remember(sections, filterClassId) {
        if (filterClassId == null) emptyList() else sections.filter { it.classId == filterClassId }
    }

    LaunchedEffect(filterClassId, availableSections) {
        if (filterClassId == null) {
            filterSectionId = null
        } else if (filterSectionId != null && availableSections.none { it.id == filterSectionId }) {
            filterSectionId = null
        }
    }

    val baseFilterStr = remember(selectedPeriodFilterId) {
        if (selectedPeriodFilterId != null) "PERIOD:$selectedPeriodFilterId" else "ALL"
    }

    val reports = remember(
        refreshTick,
        localTick,
        filterClassId,
        filterSectionId,
        baseFilterStr,
        sortMode,
        classes,
        sections,
        studyPeriods
    ) {
        val raw = GradingService.calculateComprehensiveStudentReports(
            dbHelper = dbHelper,
            repository = repository,
            classId = filterClassId,
            sectionId = if (filterClassId == null) null else filterSectionId,
            baseDateFilter = baseFilterStr
        )

        fun sortKeyScore(rep: StudentComprehensiveGradeReport): Double {
            if (selectedPeriodFilterId != null) {
                val pBd = rep.periodBreakdowns.find { it.period.id == selectedPeriodFilterId }
                if (pBd != null) {
                    return pBd.periodAverage ?: pBd.dailyGrade
                }
            }
            return rep.finalAverage ?: rep.baseDailyGrade.totalScore
        }

        when (sortMode) {
            "HIGHEST" -> raw.sortedWith(
                compareByDescending<StudentComprehensiveGradeReport> { sortKeyScore(it) }
                    .thenByDescending { it.baseDailyGrade.totalScore }
                    .thenBy { it.studentName }
            )
            "LOWEST" -> raw.sortedWith(
                compareBy<StudentComprehensiveGradeReport> { sortKeyScore(it) }
                    .thenBy { it.baseDailyGrade.totalScore }
                    .thenBy { it.studentName }
            )
            else -> raw.sortedBy { it.studentName }
        }
    }

    // Dialog state for entering/editing WrittenExamGrade directly from ReportsScreen
    var editingReportStudent by remember { mutableStateOf<StudentComprehensiveGradeReport?>(null) }
    var editingPeriodBreakdown by remember { mutableStateOf<StudentPeriodGradeBreakdown?>(null) }
    var writtenExamInput by remember { mutableStateOf("") }
    var writtenExamError by remember { mutableStateOf<String?>(null) }

    val exportResultsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val periodsToExport = if (selectedPeriodFilterId != null) {
                    studyPeriods.filter { it.id == selectedPeriodFilterId }
                } else {
                    studyPeriods
                }
                val bytes = ExcelAndBackupService.generateComprehensiveResultsXlsx(
                    reports = reports,
                    periods = periodsToExport
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onShowMessage("تم تصدير النتائج والمعدلات إلى ملف Excel بنجاح")
            }.onFailure {
                onShowMessage("تعذر تصدير ملف Excel")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Filters Card: Class + Section + StudyPeriod
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Class Filter
                    Text(
                        text = "الصف:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (filterClassId == null) Slate900 else Slate100,
                            contentColor = if (filterClassId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                filterClassId = null
                                filterSectionId = null
                            }
                        ) {
                            Text(
                                text = "الكل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        classes.forEach { cls ->
                            val isSel = filterClassId == cls.id
                            Surface(
                                color = if (isSel) Slate900 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    filterClassId = cls.id
                                    filterSectionId = null
                                    onSelectClass(cls.id)
                                }
                            ) {
                                Text(
                                    text = cls.className,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // Section Filter
                    Text(
                        text = "الشعبة:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (filterSectionId == null) Teal700 else Slate100,
                            contentColor = if (filterSectionId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { filterSectionId = null }
                        ) {
                            Text(
                                text = "الكل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        availableSections.forEach { sec ->
                            val isSel = filterSectionId == sec.id
                            Surface(
                                color = if (isSel) Teal700 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    filterSectionId = sec.id
                                    onSelectSection(sec.id)
                                }
                            ) {
                                Text(
                                    text = "شعبة ${sec.sectionName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // StudyPeriod Filter
                    Text(
                        text = "الفترة الدراسية:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (selectedPeriodFilterId == null) Emerald700 else Slate100,
                            contentColor = if (selectedPeriodFilterId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { selectedPeriodFilterId = null }
                        ) {
                            Text(
                                text = "جميع الفترات والمعدل النهائي",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        studyPeriods.forEach { sp ->
                            val isSel = selectedPeriodFilterId == sp.id
                            val filterLabel = if (sp.stageType == AcademicStage.MID_YEAR) {
                                "نصف السنة"
                            } else {
                                "${sp.periodName} (${sp.status.arabicLabel})"
                            }
                            Surface(
                                color = if (isSel) Emerald700 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedPeriodFilterId = sp.id }
                            ) {
                                Text(
                                    text = filterLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Sort & Export Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "HIGHEST" to "الأعلى درجة",
                        "LOWEST" to "الأقل درجة",
                        "ALPHA" to "أبجدي"
                    ).forEach { (key, label) ->
                        val isSel = sortMode == key
                        Surface(
                            color = if (isSel) Slate900 else Slate100,
                            contentColor = if (isSel) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { sortMode = key }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = { exportResultsLauncher.launch("نتائج_ومعدلات_الطلاب.xlsx") },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصدير Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Student Report Cards (with Period Daily + Written Exam + Period Average + Final Average)
        items(reports, key = { it.studentId }) { rep ->
            val g = rep.baseDailyGrade
            val visibleBreakdowns = if (selectedPeriodFilterId != null) {
                rep.periodBreakdowns.filter { it.period.id == selectedPeriodFilterId }
            } else {
                rep.periodBreakdowns
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStudentProfile(rep.studentId) }
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rep.studentName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "${rep.className} / شعبة ${rep.sectionName}",
                                fontSize = 11.sp,
                                color = Slate600,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = Teal50, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Teal600)) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${GradingService.formatScore(g.totalScore)} / 100",
                                        color = Teal700,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text("اليومي", color = Teal700, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                color = if (rep.finalAverage != null) Emerald50 else Slate100,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (rep.finalAverage != null) Emerald600 else Slate200)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (rep.finalAverage != null) "${GradingService.formatScore(rep.finalAverage)} / 100" else "--",
                                        color = if (rep.finalAverage != null) Emerald700 else Slate600,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "المعدل النهائي",
                                        color = if (rep.finalAverage != null) Emerald700 else Slate600,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "الحضور: ${GradingService.formatScore(g.attendanceScore)}/19 | المشاركة: ${GradingService.formatScore(g.participationScore)}/19 | الدفتر: ${GradingService.formatScore(g.notebookScore)}/19 | الواجب: ${GradingService.formatScore(g.homeworkScore)}/19 | السلوك: ${GradingService.formatScore(g.behaviorScore)}/19 | الاختبار: ${GradingService.formatScore(g.quizScore)}/5",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    // StudyPeriods Breakdown Table for this student
                    if (visibleBreakdowns.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate50, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            visibleBreakdowns.forEach { bd ->
                                val isMidYear = bd.period.stageType == AcademicStage.MID_YEAR
                                val rowTitle = if (isMidYear) "نصف السنة" else bd.period.periodName
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.1f)) {
                                        Text(
                                            text = rowTitle,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = Slate900
                                        )
                                        if (isMidYear) {
                                            Text(
                                                text = if (bd.writtenExamGrade != null) {
                                                    "درجة نصف السنة: ${GradingService.formatScore(bd.writtenExamGrade)}/100"
                                                } else {
                                                    "درجة نصف السنة: غير مدخل"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (bd.writtenExamGrade != null) Emerald700 else Slate600
                                            )
                                        } else {
                                            Text(
                                                text = "يومي: ${GradingService.formatScore(bd.dailyGrade)} | تحريري: ${bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: "--"} | معدل: ${bd.periodAverage?.let { GradingService.formatScore(it) } ?: "--"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate700
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            editingReportStudent = rep
                                            editingPeriodBreakdown = bd
                                            writtenExamInput = bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
                                            writtenExamError = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp), tint = Teal700)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isMidYear) {
                                                if (bd.writtenExamGrade != null) "تعديل نصف السنة" else "إدخال نصف السنة"
                                            } else {
                                                if (bd.writtenExamGrade != null) "تعديل التحريري" else "إدخال التحريري"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Teal700
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Written Exam Entry/Edit Dialog inside ReportsScreen
    if (editingReportStudent != null && editingPeriodBreakdown != null) {
        val targetStudent = editingReportStudent!!
        val bd = editingPeriodBreakdown!!
        val isMidYear = bd.period.stageType == AcademicStage.MID_YEAR
        val periodTitle = if (isMidYear) "نصف السنة" else bd.period.periodName
        AlertDialog(
            onDismissRequest = {
                editingReportStudent = null
                editingPeriodBreakdown = null
            },
            title = {
                Text(
                    text = if (isMidYear) "درجة نصف السنة / 100" else "درجة الامتحان التحريري — ${bd.period.periodName}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "الطالب: ${targetStudent.studentName} (${targetStudent.className} / ${targetStudent.sectionName})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                    if (!isMidYear) {
                        Text(
                            text = "الدرجة اليومية المحسوبة لهذه الفترة: ${GradingService.formatScore(bd.dailyGrade)} / 100",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Teal700
                        )
                    }

                    if (writtenExamError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = writtenExamError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = writtenExamInput,
                        onValueChange = {
                            writtenExamInput = it
                            writtenExamError = null
                        },
                        label = {
                            Text(
                                if (isMidYear) "درجة نصف السنة / 100" else "درجة الامتحان التحريري (من 0 إلى 100)"
                            )
                        },
                        placeholder = { Text(if (isMidYear) "مثال: 75" else "مثال: 70") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedPreview = writtenExamInput.trim().toDoubleOrNull()
                    if (parsedPreview != null && parsedPreview in 0.0..100.0) {
                        Surface(
                            color = Emerald50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isMidYear) {
                                Text(
                                    text = "درجة نصف السنة بعد الحفظ: ${GradingService.formatScore(parsedPreview)}/100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                val previewAvg = GradingService.round2((bd.dailyGrade + parsedPreview) / 2.0)
                                Text(
                                    text = "معدل ${bd.period.periodName} بعد الحفظ: (${GradingService.formatScore(bd.dailyGrade)} + ${GradingService.formatScore(parsedPreview)}) ÷ 2 = ${GradingService.formatScore(previewAvg)} / 100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = writtenExamInput.trim()
                        val numericVal: Double? = if (trimmed.isEmpty()) {
                            null
                        } else {
                            trimmed.toDoubleOrNull()
                        }
                        if (trimmed.isNotEmpty() && numericVal == null) {
                            writtenExamError = "يرجى إدخال رقم صحيح أو عشري صالح بين 0 و 100"
                            return@Button
                        }
                        if (numericVal != null && (numericVal < 0.0 || numericVal > 100.0)) {
                            writtenExamError = "يجب أن تكون الدرجة بين 0 و 100"
                            return@Button
                        }
                        val err = repository.upsertPeriodGrade(
                            studentId = targetStudent.studentId,
                            periodId = bd.period.id,
                            dailyGrade = bd.dailyGrade,
                            writtenExamGrade = numericVal
                        )
                        if (err != null) {
                            writtenExamError = err
                        } else {
                            editingReportStudent = null
                            editingPeriodBreakdown = null
                            localTick++
                            onDataChanged()
                            onShowMessage(
                                if (numericVal != null) {
                                    if (isMidYear) {
                                        "تم حفظ درجة نصف السنة (${GradingService.formatScore(numericVal)}/100) للطالب (${targetStudent.studentName})"
                                    } else {
                                        "تم حفظ درجة الامتحان التحريري للطالب (${targetStudent.studentName}) في $periodTitle"
                                    }
                                } else {
                                    "تم مسح الدرجة للطالب (${targetStudent.studentName}) في $periodTitle"
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("حفظ الدرجة", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingReportStudent = null
                        editingPeriodBreakdown = null
                    }
                ) {
                    Text("إلغاء", color = Slate700, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ============================================================================
// ABSENCE TRACKING SCREEN ("عدد أيام الغياب")
// ============================================================================
@Composable
fun AbsenceTrackingScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var filterClassId by remember { mutableStateOf<Long?>(null) }
    var filterSectionId by remember { mutableStateOf<Long?>(null) }

    val availableSections = remember(sections, filterClassId) {
        if (filterClassId == null) emptyList() else sections.filter { it.classId == filterClassId }
    }

    LaunchedEffect(filterClassId, availableSections) {
        if (filterClassId == null) {
            filterSectionId = null
        } else if (filterSectionId != null && availableSections.none { it.id == filterSectionId }) {
            filterSectionId = null
        }
    }

    val summaries = remember(refreshTick, filterClassId, filterSectionId, classes, sections) {
        GradingService.calculateAbsenceSummaries(
            dbHelper = dbHelper,
            repository = repository,
            classId = filterClassId,
            sectionId = if (filterClassId == null) null else filterSectionId,
            monthPrefix = "ALL"
        )
    }

    val exportAbsenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = ExcelAndBackupService.generateAbsenceXlsx(summaries)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onShowMessage("تم تصدير سجل أيام الغياب إلى Excel بنجاح (${summaries.size} طالب)")
            }.onFailure {
                onShowMessage("تعذر تصدير ملف Excel")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Filters + Export to Excel Card
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Class Filter ("الصف: الكل + جميع الصفوف")
                    Text(
                        text = "الصف:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (filterClassId == null) Slate900 else Slate100,
                            contentColor = if (filterClassId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                filterClassId = null
                                filterSectionId = null
                            }
                        ) {
                            Text(
                                text = "الكل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        classes.forEach { cls ->
                            val isSelected = filterClassId == cls.id
                            Surface(
                                color = if (isSelected) Slate900 else Slate100,
                                contentColor = if (isSelected) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    if (filterClassId != cls.id) {
                                        filterClassId = cls.id
                                        filterSectionId = null
                                        onSelectClass(cls.id)
                                    }
                                }
                            ) {
                                Text(
                                    text = cls.className,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // 2. Section Filter ("الشعبة: الكل + الشعب الموجودة في الصف المحدد")
                    Text(
                        text = "الشعبة:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (filterSectionId == null) Teal700 else Slate100,
                            contentColor = if (filterSectionId == null) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                filterSectionId = null
                            }
                        ) {
                            Text(
                                text = "الكل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        availableSections.forEach { sec ->
                            val isSelected = filterSectionId == sec.id
                            Surface(
                                color = if (isSelected) Teal700 else Slate100,
                                contentColor = if (isSelected) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    filterSectionId = sec.id
                                    onSelectSection(sec.id)
                                }
                            ) {
                                Text(
                                    text = sec.sectionName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    // 3. Export to Excel Button
                    Button(
                        onClick = {
                            val selectedClassObj = classes.find { it.id == filterClassId }
                            val selectedSecObj = availableSections.find { it.id == filterSectionId }
                            val fileName = ExcelAndBackupService.buildAbsenceExportFileName(
                                className = selectedClassObj?.className,
                                sectionName = selectedSecObj?.sectionName
                            )
                            exportAbsenceLauncher.launch(fileName)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تصدير إلى Excel",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Table Header: اسم الطالب | الصف والشعبة | عدد أيام الغياب
        item {
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسم الطالب",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1.3f)
                    )
                    Text(
                        text = "الصف والشعبة",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = "عدد أيام الغياب",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (summaries.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا يوجد طلاب ضمن الفلتر المحدد حالياً",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(summaries, key = { it.studentId }) { item ->
                Surface(
                    color = Color.White,
                    border = BorderStroke(0.5.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.studentName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Slate900,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.3f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${item.className} / ${item.sectionName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate700,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = GradingService.formatAbsenceDaysLabel(item.absentDays),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (item.absentDays > 0) Red700 else Slate700
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// EXCEL & BACKUP / RESTORE SCREEN
// ============================================================================
@Composable
fun ExcelAndBackupScreen(
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var importResult by remember { mutableStateOf<ExcelImportResult?>(null) }
    var isFolderValid by remember { mutableStateOf(ExcelAndBackupService.isBackupFolderValidAndAccessible(context)) }
    var folderDisplayPath by remember { mutableStateOf(ExcelAndBackupService.getBackupFolderDisplayPath(context)) }
    var backups by remember { mutableStateOf(ExcelAndBackupService.listBackups(context)) }
    var lastError by remember { mutableStateOf(ExcelAndBackupService.getLastBackupError(context)) }
    var createBackupAfterFolderPick by remember { mutableStateOf(false) }

    // Confirmation modals for Backup Restore and Delete
    var pendingRestoreBackup by remember { mutableStateOf<BackupFileEntry?>(null) }
    var pendingDeleteBackup by remember { mutableStateOf<BackupFileEntry?>(null) }

    // Protection states for "أدوات الاختبار والتطوير" (إدارة البيانات التجريبية Demo)
    var isDevToolsUnlocked by remember { mutableStateOf(false) }
    var showUnlockWarningDialog by remember { mutableStateOf(false) }
    var unlockAcknowledged by remember { mutableStateOf(false) }

    var showResetDemoDialog by remember { mutableStateOf(false) }
    var demoConfirmText by remember { mutableStateOf("") }

    var showEmptyStartDialog by remember { mutableStateOf(false) }
    var emptyStartConfirmed by remember { mutableStateOf(false) }

    val selectBackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                ExcelAndBackupService.saveBackupFolderUri(context, treeUri)
                isFolderValid = ExcelAndBackupService.isBackupFolderValidAndAccessible(context)
                folderDisplayPath = ExcelAndBackupService.getBackupFolderDisplayPath(context)
                backups = ExcelAndBackupService.listBackups(context)

                if (createBackupAfterFolderPick && isFolderValid) {
                    createBackupAfterFolderPick = false
                    val created = ExcelAndBackupService.createLocalBackup(
                        context,
                        dbHelper,
                        isAutoSafety = false,
                        isAutoDaily = false
                    )
                    backups = ExcelAndBackupService.listBackups(context)
                    onShowMessage("تم تحديد المجلد وإنشاء النسخة الاحتياطية بنجاح (${created.studentsCount} طالب)")
                } else {
                    createBackupAfterFolderPick = false
                    onShowMessage("تم حفظ مجلد النسخ الاحتياطي: ${folderDisplayPath ?: ""}")
                }
            }.onFailure { e ->
                createBackupAfterFolderPick = false
                onShowMessage(e.message ?: "تعذر حفظ صلاحية المجلد المختار")
            }
        } else {
            createBackupAfterFolderPick = false
        }
    }

    val exportTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = ExcelAndBackupService.generateEmptyStudentTemplateXlsx()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onShowMessage("تم حفظ قالب Excel الفارغ بنجاح")
            }.onFailure {
                onShowMessage("تعذر حفظ قالب Excel")
            }
        }
    }

    val importExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val res = ExcelAndBackupService.importStudentsFromXlsxUri(context, uri, repository)
                importResult = res
                onDataChanged()
                onShowMessage("تم استيراد ${res.importedCount} طالب بنجاح")
            }.onFailure {
                onShowMessage("تعذر قراءة ملف Excel المختار")
            }
        }
    }

    val exportResultsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val allGrades = GradingService.calculateGradesForClass(dbHelper, repository)
                val bytes = ExcelAndBackupService.generateResultsXlsx(allGrades)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onShowMessage("تم تصدير جميع النتائج إلى Excel بنجاح")
            }.onFailure {
                onShowMessage("تعذر تصدير النتائج")
            }
        }
    }

    val exportDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                ExcelAndBackupService.exportDatabaseToUri(context, dbHelper, uri)
                onShowMessage("تم تصدير ملف النسخة الاحتياطية (.db) إلى الهاتف")
            }.onFailure { e ->
                ExcelAndBackupService.recordBackupError(context, "exportDatabaseToUri", e)
                lastError = ExcelAndBackupService.getLastBackupError(context)
                onShowMessage("تعذر تصدير النسخة الاحتياطية")
            }
        }
    }

    val restoreDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ExcelAndBackupService.restoreDatabaseFromUri(context, dbHelper, uri)
                backups = ExcelAndBackupService.listBackups(context)
                onDataChanged()
                onShowMessage("تمت استعادة النسخة الاحتياطية بنجاح (مع حفظ نسخة أمان تلقائية للبيانات السابقة)")
            }.onFailure { e ->
                ExcelAndBackupService.recordBackupError(context, "restoreDatabaseFromUri", e)
                lastError = ExcelAndBackupService.getLastBackupError(context)
                onShowMessage(e.message ?: "تعذر استعادة النسخة الاحتياطية")
            }
        }
    }

    val latestBackup = backups.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. النسخ الاحتياطي والاستعادة (Local SQLite Backup & Restore)
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "النسخ الاحتياطي والاستعادة (SQLite)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Slate900
                    )
                    Text(
                        text = "ينشئ التطبيق نسخة احتياطية تلقائياً عند الخروج من التطبيق أو إغلاقه، ويحتفظ بآخر 7 نسخ احتياطية محلية مع حذف الأقدم تلقائياً.",
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    // Selected Backup Folder Status & Picker Card
                    Surface(
                        color = if (isFolderValid) Emerald50 else Amber50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isFolderValid) Emerald600 else Amber600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFolderValid) Icons.Default.Folder else Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    tint = if (isFolderValid) Emerald700 else Amber700,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isFolderValid) {
                                        "مجلد النسخ الاحتياطي الحالي: ${folderDisplayPath ?: "مجلد محدد"}"
                                    } else if (folderDisplayPath != null) {
                                        "المجلد السابق لم يعد متاحاً أو فُقدت صلاحيته: $folderDisplayPath"
                                    } else {
                                        "لم يتم تحديد مكان حفظ النسخ الاحتياطية بعد"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = if (isFolderValid) Emerald700 else Amber700
                                )
                            }
                            Text(
                                text = if (isFolderValid) {
                                    "تُحفظ النسخ اليدوية والتلقائية عند إغلاق التطبيق مباشرة داخل هذا المجلد (مع الاحتفاظ بآخر 7 نسخ)."
                                } else {
                                    "اختر مجلداً في ذاكرة الهاتف (مثل Download/سجل متابعة الطلاب/النسخ الاحتياطية) لحفظ النسخ الاحتياطية واستعادتها منه."
                                },
                                fontSize = 11.sp,
                                color = Slate700
                            )
                            OutlinedButton(
                                onClick = {
                                    createBackupAfterFolderPick = false
                                    selectBackupFolderLauncher.launch(ExcelAndBackupService.getSavedBackupFolderUri(context))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFolderValid) "تغيير مجلد الحفظ" else "تحديد مكان حفظ النسخ الاحتياطية",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    // Last backup summary box
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "آخر نسخة احتياطية: ${latestBackup?.fileName ?: "لا توجد نسخة بعد"}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Slate900
                            )
                            Text(
                                text = "تاريخ ووقت آخر Backup: ${latestBackup?.createdAt ?: "—"}",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                            Text(
                                text = "النسخ المحلية المحفوظة: ${backups.size} / 7",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald700
                            )
                        }
                    }

                    if (lastError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "سجل خطأ النسخ الاحتياطي: $lastError",
                                    fontSize = 11.sp,
                                    color = Red700,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    ExcelAndBackupService.clearLastBackupError(context)
                                    lastError = null
                                }) {
                                    Text("مسح", fontSize = 11.sp, color = Red700)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val currentlyValid = ExcelAndBackupService.isBackupFolderValidAndAccessible(context)
                            isFolderValid = currentlyValid
                            if (!currentlyValid) {
                                createBackupAfterFolderPick = true
                                onShowMessage("يرجى اختيار مجلد لحفظ النسخ الاحتياطية أولاً")
                                selectBackupFolderLauncher.launch(ExcelAndBackupService.getSavedBackupFolderUri(context))
                            } else {
                                runCatching {
                                    val created = ExcelAndBackupService.createLocalBackup(
                                        context,
                                        dbHelper,
                                        isAutoSafety = false,
                                        isAutoDaily = false
                                    )
                                    backups = ExcelAndBackupService.listBackups(context)
                                    onShowMessage("تم إنشاء النسخة الاحتياطية بنجاح (${created.studentsCount} طالب • ${created.classesCount} صف)")
                                }.onFailure { e ->
                                    lastError = ExcelAndBackupService.getLastBackupError(context)
                                    onShowMessage(e.message ?: "فشل إنشاء النسخة الاحتياطية")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ احتياطي الآن (إنشاء نسخة الآن)", fontWeight = FontWeight.ExtraBold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val stamp = java.time.LocalDateTime.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
                                exportDbLauncher.launch("StudentTracker_Backup_${stamp}.db")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ ملف .db للهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { restoreDbLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("استعادة من ملف خارجي", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "قائمة النسخ المحلية الموجودة (${backups.size}):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Slate900
                    )

                    backups.forEach { b ->
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(b.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                    Text(
                                        text = "${b.createdAt} • ${maxOf(1L, b.sizeBytes / 1024)} KB" +
                                            when {
                                                b.isAutoPreRestore -> " • نسخة أمان تلقائية"
                                                b.isAutoDaily -> " • نسخة يومية تلقائية"
                                                else -> ""
                                            },
                                        fontSize = 11.sp,
                                        color = Slate600
                                    )
                                    Text(
                                        text = "تحتوي على: ${b.studentsCount} طالب • ${b.classesCount} صف • ${b.sectionsCount} شعبة • ${b.recordsCount} سجل",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald700
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { pendingRestoreBackup = b },
                                        colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                                    ) {
                                        Text("استعادة نسخة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { pendingDeleteBackup = b }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف نسخة", tint = Red600)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. مركز Excel
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("مركز Excel (قالب • استيراد الطلاب • تصدير النتائج)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
                    Text("الأعمدة المعتمدة في القالب: اسم الطالب | الصف | الشعبة", fontSize = 12.sp, color = Slate600)

                    OutlinedButton(
                        onClick = { exportTemplateLauncher.launch("قالب_استيراد_الطلاب.xlsx") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير قالب Excel فارغ (.xlsx)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            importExcelLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استيراد الطلاب من ملف Excel (.xlsx)", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { exportResultsLauncher.launch("كشف_الدرجات_اليومية.xlsx") },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TableView, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير جميع النتائج والدرجات إلى Excel (.xlsx)", fontWeight = FontWeight.Bold)
                    }

                    val res = importResult
                    if (res != null) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("نتيجة آخر عملية استيراد:", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                Text("• تم استيراد: ${res.importedCount} طالب", fontSize = 12.sp, color = Emerald700, fontWeight = FontWeight.Bold)
                                Text("• مكرر تم تجاوزه: ${res.duplicateCount}", fontSize = 12.sp, color = Amber700)
                                Text("• صفوف فارغة: ${res.emptyRowCount} | أخطاء: ${res.errorCount}", fontSize = 12.sp, color = Red700)
                                res.rowErrors.take(5).forEach { err ->
                                    Text("صف ${err.rowNumber}: ${err.reason}", fontSize = 11.sp, color = Red700)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. قسم متقدم محمي: أدوات الاختبار والتطوير (إدارة البيانات التجريبية Demo)
        item {
            Surface(
                color = Amber50,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Amber600),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Amber700)
                            Column {
                                Text(
                                    text = "أدوات الاختبار والتطوير (إدارة بيانات تجريبية)",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Slate900
                                )
                                Text(
                                    text = "هذه الأدوات مخصصة للاختبار فقط، وقد تؤدي إلى تغيير أو حذف البيانات الحالية.",
                                    fontSize = 11.sp,
                                    color = Amber700,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!isDevToolsUnlocked) {
                            Button(
                                onClick = {
                                    unlockAcknowledged = false
                                    showUnlockWarningDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("فك القفل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(onClick = { isDevToolsUnlocked = false }) {
                                Text("قفل القسم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isDevToolsUnlocked) {
                        Text(
                            text = "حماية تلقائية: يتم أخذ نسخة احتياطية تلقائية قبل أي عملية أدناه، ولا تُنفذ العملية إذا فشل النسخ الاحتياطي.",
                            fontSize = 11.sp,
                            color = Slate700
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    emptyStartConfirmed = false
                                    showEmptyStartDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("بدء فعلي فارغ", fontSize = 12.sp, color = Red700, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    demoConfirmText = ""
                                    showResetDemoDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("إعادة بيانات Demo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Restore Confirmation Dialog (Creates automatic safety backup before restore)
    if (pendingRestoreBackup != null) {
        val target = pendingRestoreBackup!!
        AlertDialog(
            onDismissRequest = { pendingRestoreBackup = null },
            title = { Text("تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("هل أنت متأكد من استعادة النسخة (${target.fileName})؟ سيتم استبدال البيانات الحالية بهذه النسخة.")
                    Text(
                        text = "سيقوم التطبيق تلقائياً بإنشاء نسخة احتياطية كاملة للبيانات الحالية قبل تنفيذ الاستعادة لضمان عدم فقدان أي بيانات.",
                        fontSize = 12.sp,
                        color = Emerald700,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        runCatching {
                            val safety = ExcelAndBackupService.restoreBackup(context, dbHelper, target)
                            backups = ExcelAndBackupService.listBackups(context)
                            pendingRestoreBackup = null
                            onDataChanged()
                            onShowMessage("تمت الاستعادة بنجاح (${target.studentsCount} طالب). حُفظت بياناتك السابقة في (${safety.fileName})")
                        }.onFailure { e ->
                            pendingRestoreBackup = null
                            lastError = ExcelAndBackupService.getLastBackupError(context)
                            onShowMessage(e.message ?: "فشلت استعادة النسخة الاحتياطية")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("تأكيد واستعادة", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreBackup = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Delete Backup Confirmation Dialog
    if (pendingDeleteBackup != null) {
        val target = pendingDeleteBackup!!
        AlertDialog(
            onDismissRequest = { pendingDeleteBackup = null },
            title = { Text("تأكيد حذف النسخة الاحتياطية", fontWeight = FontWeight.ExtraBold, color = Red700) },
            text = { Text("هل تريد حذف ملف النسخة الاحتياطية (${target.fileName}) من الجهاز؟") },
            confirmButton = {
                Button(
                    onClick = {
                        ExcelAndBackupService.deleteBackup(context, target)
                        backups = ExcelAndBackupService.listBackups(context)
                        pendingDeleteBackup = null
                        onShowMessage("تم حذف النسخة الاحتياطية")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("حذف", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBackup = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Step 1 Dialog: Unlock Dev & Demo Tools Warning
    if (showUnlockWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockWarningDialog = false },
            title = { Text("تحذير: أدوات الاختبار والتطوير", fontWeight = FontWeight.ExtraBold, color = Amber700) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "هذه الأدوات مخصصة للاختبار فقط، وقد تؤدي إلى تغيير أو حذف البيانات الحالية.",
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { unlockAcknowledged = !unlockAcknowledged }
                    ) {
                        Checkbox(checked = unlockAcknowledged, onCheckedChange = { unlockAcknowledged = it })
                        Text("أفهم التحذير وأرغب بفتح أدوات الاختبار", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = unlockAcknowledged,
                    onClick = {
                        isDevToolsUnlocked = true
                        showUnlockWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber600)
                ) {
                    Text("تأكيد وفتح القسم", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockWarningDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Step 2A Dialog: Confirm "إعادة بيانات Demo" by typing DEMO
    if (showResetDemoDialog) {
        AlertDialog(
            onDismissRequest = { showResetDemoDialog = false },
            title = { Text("تأكيد إعادة بيانات Demo", fontWeight = FontWeight.ExtraBold, color = Amber700) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تحذير: ستؤدي هذه العملية إلى استبدال جميع البيانات الحالية ببيانات Demo التجريبية. سيتم إنشاء نسخة احتياطية تلقائية لبياناتك الحالية قبل التنفيذ.",
                        fontSize = 13.sp
                    )
                    Text("اكتب كلمة DEMO للتأكيد الصريح:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    OutlinedTextField(
                        value = demoConfirmText,
                        onValueChange = { demoConfirmText = it },
                        placeholder = { Text("DEMO") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = demoConfirmText.trim() == "DEMO",
                    onClick = {
                        runCatching {
                            val safety = ExcelAndBackupService.createLocalBackup(
                                context,
                                dbHelper,
                                isAutoSafety = true,
                                isAutoDaily = false
                            )
                            dbHelper.resetDatabase(includeDemo = true)
                            backups = ExcelAndBackupService.listBackups(context)
                            showResetDemoDialog = false
                            demoConfirmText = ""
                            onDataChanged()
                            onShowMessage("تمت إعادة بيانات Demo. النسخة الاحتياطية محفوظة في: ${safety.filePath}")
                        }.onFailure { e ->
                            onShowMessage("تم إلغاء العملية لأن النسخ الاحتياطي التلقائي فشل: ${e.message}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber600)
                ) {
                    Text("تأكيد إعادة بيانات Demo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDemoDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Step 2B Dialog: Confirm "بدء فعلي فارغ"
    if (showEmptyStartDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyStartDialog = false },
            title = { Text("تأكيد البدء الفعلي الفارغ", fontWeight = FontWeight.ExtraBold, color = Red700) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "تحذير: ستؤدي هذه العملية إلى حذف/إزالة البيانات التجريبية أو الحالية وإعادة تهيئة جداول التطبيق للبدء الفعلي الفارغ. سيتم حفظ نسخة احتياطية تلقائية قبل التنفيذ.",
                        fontSize = 13.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { emptyStartConfirmed = !emptyStartConfirmed }
                    ) {
                        Checkbox(checked = emptyStartConfirmed, onCheckedChange = { emptyStartConfirmed = it })
                        Text("أؤكد رغبتي الصريحة في تفريغ البيانات والبدء الفعلي الفارغ", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = emptyStartConfirmed,
                    onClick = {
                        runCatching {
                            val safety = ExcelAndBackupService.createLocalBackup(
                                context,
                                dbHelper,
                                isAutoSafety = true,
                                isAutoDaily = false
                            )
                            dbHelper.resetDatabase(includeDemo = false)
                            backups = ExcelAndBackupService.listBackups(context)
                            showEmptyStartDialog = false
                            emptyStartConfirmed = false
                            onDataChanged()
                            onShowMessage("تم البدء الفعلي الفارغ. النسخة الاحتياطية محفوظة في: ${safety.filePath}")
                        }.onFailure { e ->
                            onShowMessage("تم إلغاء العملية لأن النسخ الاحتياطي التلقائي فشل: ${e.message}")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("تأكيد البدء الفعلي الفارغ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyStartDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
