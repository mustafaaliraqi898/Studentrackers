package com.studenttracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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

    var searchQuery by remember(selectedClassId, selectedSectionId, selectedDate) { mutableStateOf("") }

    val filteredStudents = remember(students, searchQuery) {
        val q = TrackerRepository.normalizeStudentName(searchQuery)
        if (q.isEmpty()) {
            students
        } else {
            val qKey = TrackerRepository.canonicalArabicComparisonKey(q)
            val qTokens = qKey.split(" ").filter { it.isNotEmpty() }
            students.filter { st ->
                val stNormalized = TrackerRepository.normalizeStudentName(st.studentName)
                val stKey = TrackerRepository.canonicalArabicComparisonKey(st.studentName)
                stNormalized.contains(q, ignoreCase = true) ||
                    stKey.contains(qKey) ||
                    (qTokens.isNotEmpty() && qTokens.all { tok -> stKey.contains(tok) })
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
        isEditingSaved = true
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

    val dailyWeights = remember(selectedClassId, refreshTick) {
        val result = mutableListOf(10.0, 30.0, 10.0, 15.0, 15.0, 20.0)
        dbHelper.readableDatabase.rawQuery(
            "SELECT AttendanceMax, ParticipationMax, NotebookMax, HomeworkMax, BehaviorMax, DailyTestMax FROM EvaluationSettings WHERE ClassID = ?",
            arrayOf(selectedClassId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                for (i in 0 until 6) result[i] = cursor.getDouble(i)
            }
        }
        result
    }
    val attendanceMax = dailyWeights[0]
    val participationMax = dailyWeights[1]
    val notebookMax = dailyWeights[2]
    val homeworkMax = dailyWeights[3]
    val behaviorMax = dailyWeights[4]
    val dailyTestMax = dailyWeights[5]

    val canEdit = !dayStatus.isNonWorkingDay && isEditingSaved && (periodsInYear.isNotEmpty())

    // Live daily grade uses the saved weight distribution for the selected class.
    fun calculateSingleDayScoreOutOf100(entry: StudentDailyEntry): Double {
        val att = if (entry.attendance == AttendanceStatus.PRESENT) attendanceMax else 0.0
        val part = (entry.participation ?: 0.0) * participationMax
        val note = (entry.notebook ?: 1.0) * notebookMax
        val hw = (entry.homework ?: 0.0) * homeworkMax
        val beh = (entry.behavior ?: 1.0) * behaviorMax
        val quiz = (entry.dailyTestScore ?: 0.0).coerceIn(0.0, dailyTestMax)
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

                            // Quick actions: initialize the selected daily evaluation for every student.
                            // These intentionally replace current values; this workflow starts with a fresh evaluation.
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            entriesMap = entriesMap.toMutableMap().apply {
                                                students.forEach { student ->
                                                    val current = this[student.id] ?: StudentDailyEntry(studentId = student.id)
                                                    put(student.id, current.copy(homework = 1.0))
                                                }
                                            }
                                            isEditingSaved = true
                                            onDirtyChange(true)
                                            showTopNotification("تم تعيين الواجب ممتازاً لجميع الطلبة")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("الواجب: ممتاز للجميع", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                                    Button(
                                        onClick = {
                                            entriesMap = entriesMap.toMutableMap().apply {
                                                students.forEach { student ->
                                                    val current = this[student.id] ?: StudentDailyEntry(studentId = student.id)
                                                    put(student.id, current.copy(notebook = 1.0))
                                                }
                                            }
                                            isEditingSaved = true
                                            onDirtyChange(true)
                                            showTopNotification("تم تعيين الدفتر ممتازاً لجميع الطلبة")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("الدفتر: ممتاز للجميع", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            entriesMap = entriesMap.toMutableMap().apply {
                                                students.forEach { student ->
                                                    val current = this[student.id] ?: StudentDailyEntry(studentId = student.id)
                                                    put(student.id, current.copy(attendance = AttendanceStatus.PRESENT))
                                                }
                                            }
                                            isEditingSaved = true
                                            onDirtyChange(true)
                                            showTopNotification("تم تعيين الحضور لجميع الطلبة")
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald700)
                                    ) { Text("حاضر للجميع", fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                                    Button(
                                        onClick = {
                                            entriesMap = entriesMap.toMutableMap().apply {
                                                students.forEach { student ->
                                                    val current = this[student.id] ?: StudentDailyEntry(studentId = student.id)
                                                    put(student.id, current.copy(behavior = 1.0))
                                                }
                                            }
                                            isEditingSaved = true
                                            onDirtyChange(true)
                                            showTopNotification("تم تعيين السلوك ممتازاً لجميع الطلبة")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("السلوك: ممتاز للجميع", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث باسم الطالب أو جزء من اسمه داخل الشعبة...") },
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (filteredStudents.isEmpty()) {
                                            "لا يوجد طالب مطابق للاسم \"$searchQuery\" في هذه الشعبة"
                                        } else {
                                            "نتائج البحث (${filteredStudents.size} من ${students.size} طالب) — يمكنك تسجيل الحضور/الغياب وتعديل جميع العناصر مباشرة:"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (filteredStudents.isEmpty()) Red700 else Slate700,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { searchQuery = "" }) {
                                        Text("عرض الكل", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Teal700)
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
                                        text = "الطالب غائب اليوم (الحضور = 0/" + GradingService.formatScore(attendanceMax) + ") — تبقى باقي العناصر على قيمها الافتراضية ويمكنك تعديل أي عنصر يدوياً لهذا اليوم:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Red700,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Evaluations & Daily Quiz remain visible and editable even when the student is ABSENT or when searching
                            val evalEnabled = !dayStatus.isNonWorkingDay && (isEditingSaved || searchQuery.trim().isNotEmpty())
                            ThreeStateRatingRow(
                                label = "المشاركة",
                                currentValue = entry.participation,
                                enabled = evalEnabled,
                                scorePreviewText = entry.participation?.let { "(${GradingService.formatScore(it * participationMax)} / ${GradingService.formatScore(participationMax)})" } ?: "غير مسجل",
                                ratingType = "five",
                                onValueChange = { v ->
                                    isEditingSaved = true
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
                                scorePreviewText = entry.notebook?.let { "(${GradingService.formatScore(it * notebookMax)} / ${GradingService.formatScore(notebookMax)})" } ?: "كامل افتراضياً",
                                ratingType = "five",
                                onValueChange = { v ->
                                    isEditingSaved = true
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
                                scorePreviewText = entry.homework?.let { "(${GradingService.formatScore(it * homeworkMax)} / ${GradingService.formatScore(homeworkMax)})" } ?: "غير مسجل",
                                ratingType = "five",
                                onValueChange = { v ->
                                    isEditingSaved = true
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
                                scorePreviewText = entry.behavior?.let { "(${GradingService.formatScore(it * behaviorMax)} / ${GradingService.formatScore(behaviorMax)})" } ?: "كامل افتراضياً",
                                ratingType = "behavior",
                                onValueChange = { v ->
                                    isEditingSaved = true
                                    entriesMap = entriesMap.toMutableMap().apply {
                                        put(st.id, entry.copy(behavior = v))
                                    }
                                    onDirtyChange(true)
                                }
                            )

                            // Daily Test Score uses the configured class maximum; blank means unrecorded.
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .background(Slate50, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "الامتحان اليومي (من " + GradingService.formatScore(dailyTestMax) + ")",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Slate800
                                )
                                OutlinedTextField(
                                    value = entry.dailyTestScore?.let { GradingService.formatScore(it) } ?: "",
                                    onValueChange = { raw ->
                                        val clean = raw.trim()
                                        val parsed = if (clean.isEmpty()) null else clean.toDoubleOrNull()
                                        if (clean.isEmpty() || (parsed != null && parsed in 0.0..dailyTestMax)) {
                                            isEditingSaved = true
                                            entriesMap = entriesMap.toMutableMap().apply {
                                                put(st.id, entry.copy(dailyTestScore = parsed))
                                            }
                                            onDirtyChange(true)
                                        }
                                    },
                                    enabled = evalEnabled,
                                    singleLine = true,
                                    label = { Text("أدخل درجة من 0 إلى " + GradingService.formatScore(dailyTestMax)) },
                                    placeholder = { Text("غير مسجل") },
                                    supportingText = {
                                        Text(
                                            if (entry.dailyTestScore == null) "غير مسجل — لا يدخل في المتوسط"
                                            else "الدرجة المسجلة: ${GradingService.formatScore(entry.dailyTestScore)} / 20"
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
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
    val profileWeights = remember(grade?.classId, refreshTick, localTick) {
        grade?.let { GradingService.getEvaluationWeights(dbHelper, it.classId) }
            ?: GradingService.EvaluationWeights()
    }

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
        // 1. Student Identity + Stage Averages & Annual Effort + Period Filter Header
        item {
            val behaviorZeroCount = grade.behaviorRatings.count { it.value == 0.0 }
            val hasFrequentBehaviorZero = behaviorZeroCount >= 6
            val firstHalfAvg = comprehensiveReport?.firstHalfAverage
            val midYearScore = comprehensiveReport?.midYearGrade
            val secondHalfAvg = comprehensiveReport?.secondHalfAverage
            val annualEffort = comprehensiveReport?.annualEffort
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: اسم الطالب | الصف | الشعبة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = grade.studentName,
                            color = if (hasFrequentBehaviorZero) Red600 else Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = grade.className,
                                color = Teal100,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "شعبة ${grade.sectionName}",
                                color = Teal100,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Row 2: معدل النصف الأول | درجة نصف السنة | معدل النصف الثاني | السعي السنوي
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "معدل النصف الأول",
                                    color = Slate200,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (firstHalfAvg != null) GradingService.formatRoundedFinalGrade(firstHalfAvg) else "غير مكتمل",
                                    color = if (firstHalfAvg != null) Color.White else Slate200,
                                    fontSize = if (firstHalfAvg != null) 15.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "درجة نصف السنة",
                                    color = Slate200,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (midYearScore != null) GradingService.formatRoundedFinalGrade(midYearScore) else "غير مدخل",
                                    color = if (midYearScore != null) Color.White else Slate200,
                                    fontSize = if (midYearScore != null) 15.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "معدل النصف الثاني",
                                    color = Slate200,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (secondHalfAvg != null) GradingService.formatRoundedFinalGrade(secondHalfAvg) else "غير مكتمل",
                                    color = if (secondHalfAvg != null) Color.White else Slate200,
                                    fontSize = if (secondHalfAvg != null) 15.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            color = if (annualEffort != null) Emerald700 else Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "السعي السنوي",
                                    color = if (annualEffort != null) Emerald50 else Slate200,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (annualEffort != null) GradingService.formatRoundedFinalGrade(annualEffort) else "غير مكتمل",
                                    color = Color.White,
                                    fontSize = if (annualEffort != null) 15.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
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

        // 2. Study Periods Grades & Written Exam Card
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
                                text = "لم يتم إنشاء فترات دراسية بعد. يمكنك إضافة فترة دراسية من قسم «الفترات الدراسية» لحساب درجة كل فترة وإدخال درجة الامتحان التحريري وحساب السعي السنوي.",
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
                                                    text = if (bd.writtenExamGrade != null) "${GradingService.formatRoundedFinalGrade(bd.writtenExamGrade)}/100" else "غير مدخل",
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
                                                        text = if (bd.periodAverage != null) "${GradingService.formatRoundedFinalGrade(bd.periodAverage)} / 100" else "--",
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
                        ProfileScoreRow("1. الحضور (${grade.presentDays} حضور / ${grade.absentDays} غياب من أصل ${grade.totalWorkingDays} يوم دوام)", grade.attendanceScore, profileWeights.attendance)
                        ProfileScoreRow("2. المشاركة (${grade.participationRatings.size} تقييم مسجل)", grade.participationScore.takeIf { grade.participationRatings.isNotEmpty() }, profileWeights.participation)
                        ProfileScoreRow("3. الدفتر (${grade.notebookRatings.count { it.value == 0.0 }} يوم بدرجة صفر)", grade.notebookScore, profileWeights.notebook)
                        ProfileScoreRow("4. الواجب (${grade.homeworkRatings.size} تقييم مسجل)", grade.homeworkScore.takeIf { grade.homeworkRatings.isNotEmpty() }, profileWeights.homework)
                        ProfileScoreRow("5. السلوك (كامل افتراضياً، ويُخصم فقط عند تسجيل صفر)", grade.behaviorScore, profileWeights.behavior)
                        ProfileScoreRow("6. الاختبار اليومي (${grade.dailyTests.size} اختبار)", grade.quizScore.takeIf { grade.dailyTests.isNotEmpty() }, profileWeights.dailyTest)
                    }
                }
            }

            item {
                val studentDailyPlans = remember(refreshTick, localTick, grade.classId, grade.sectionId, selectedPeriodId, studyPeriods) {
                    val allPlans = repository.getDailyPlansForClassAndSection(grade.classId, grade.sectionId)
                    val activePeriod = studyPeriods.firstOrNull { it.id == selectedPeriodId }
                    if (activePeriod != null) {
                        allPlans.filter { p ->
                            p.planDate >= activePeriod.startDate &&
                                (activePeriod.endDate.isNullOrBlank() || p.planDate <= activePeriod.endDate)
                        }
                    } else {
                        allPlans
                    }
                }

                StudentUnifiedEvaluationAndPlanLogCard(
                    grade = grade,
                    dailyPlans = studentDailyPlans
                )
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
                                    text = "درجة نصف السنة بعد الحفظ: ${GradingService.formatRoundedFinalGrade(parsedPreview)}/100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                val previewAvg = (bd.dailyGrade + parsedPreview) / 2.0
                                Text(
                                    text = "معدل ${bd.period.periodName} بعد الحفظ: (${GradingService.formatScore(bd.dailyGrade)} + ${GradingService.formatScore(parsedPreview)}) ÷ 2 = ${GradingService.formatRoundedFinalGrade(previewAvg)} / 100",
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
private fun ProfileScoreRow(title: String, score: Double?, maxScore: Double) {
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
            text = score?.let { "${GradingService.formatScore(it)} / ${GradingService.formatScore(maxScore)}" } ?: "غير مسجل",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (score == null) Slate500 else Teal700
        )
    }
}

private data class StudentDailyCombinedLogEntry(
    val date: String,
    val attendance: AttendanceStatus?,
    val participation: Double?,
    val notebook: Double?,
    val homework: Double?,
    val behavior: Double?,
    val dailyTest: Double?,
    val plansForDate: List<DailyPlanEntity>
)

@Composable
private fun StudentUnifiedEvaluationAndPlanLogCard(
    grade: StudentCalculatedGrade,
    dailyPlans: List<DailyPlanEntity>
) {
    var previewPlanImage by remember { mutableStateOf<DailyPlanImageEntity?>(null) }

    val combinedEntries = remember(grade, dailyPlans) {
        val attByDate = grade.attendanceHistory.toMap()
        val partByDate = grade.participationRatings.associate { it.date to it.value }
        val noteByDate = grade.notebookRatings.associate { it.date to it.value }
        val hwByDate = grade.homeworkRatings.associate { it.date to it.value }
        val behByDate = grade.behaviorRatings.associate { it.date to it.value }
        val testByDate = grade.dailyTests.associate { it.date to it.value }
        val plansByDate = dailyPlans.groupBy { it.planDate }

        val allDates = buildSet {
            addAll(attByDate.keys)
            addAll(partByDate.keys)
            addAll(noteByDate.keys)
            addAll(hwByDate.keys)
            addAll(behByDate.keys)
            addAll(testByDate.keys)
            addAll(plansByDate.keys)
        }.sortedDescending()

        allDates.map { d ->
            StudentDailyCombinedLogEntry(
                date = d,
                attendance = attByDate[d],
                participation = partByDate[d],
                notebook = noteByDate[d],
                homework = hwByDate[d],
                behavior = behByDate[d],
                dailyTest = testByDate[d],
                plansForDate = plansByDate[d] ?: emptyList()
            )
        }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "سجل تقييم الطالب مع الخطة اليومية",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Slate900
                    )
                    Text(
                        text = "التقييم اليومي وموضوع الدرس والصور المرفقة (${grade.className} — شعبة ${grade.sectionName})",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
                Surface(
                    color = Teal50,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Teal600)
                ) {
                    Text(
                        text = "${combinedEntries.size} يوم",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Teal700,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (combinedEntries.isEmpty()) {
                Surface(
                    color = Slate50,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد تقييمات يومية أو خطط دروس مسجلة لهذا الطالب حالياً.",
                        fontSize = 12.sp,
                        color = Slate500,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                combinedEntries.forEach { entry ->
                    val mergedTopics = entry.plansForDate
                        .map { it.lessonTopic.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString(" | ")
                    val allPlanImages = entry.plansForDate.flatMap { it.images }

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
                            // 1. Date & Day Header + Attendance Badge if recorded
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DateRange,
                                        contentDescription = null,
                                        tint = Teal700,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${GradingService.getArabicDayName(entry.date)} — ${GradingService.formatDisplayDate(entry.date)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Slate900
                                    )
                                }

                                if (entry.attendance != null) {
                                    val isPresent = entry.attendance == AttendanceStatus.PRESENT
                                    Surface(
                                        color = if (isPresent) Emerald50 else Red50,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (isPresent) Emerald600 else Red600)
                                    ) {
                                        Text(
                                            text = if (isPresent) "الحضور: حاضر" else "الحضور: غائب",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isPresent) Emerald700 else Red700,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // 2. Lesson Topic from Daily Plan
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (mergedTopics.isNotEmpty()) Teal600.copy(alpha = 0.4f) else Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "موضوع الدرس (الخطة اليومية):",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Teal700
                                    )
                                    Text(
                                        text = if (mergedTopics.isNotEmpty()) mergedTopics else "لا توجد خطة مسجلة لهذا اليوم",
                                        fontSize = 12.sp,
                                        fontWeight = if (mergedTopics.isNotEmpty()) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (mergedTopics.isNotEmpty()) Slate900 else Slate500
                                    )
                                }
                            }

                            // 3. Student Evaluation Elements for that Day (Participation, Notebook, Homework, Behavior, Daily Test)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DailyEvaluationBadgeCell(
                                        label = "المشاركة",
                                        ratingValue = entry.participation,
                                        isTest = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    DailyEvaluationBadgeCell(
                                        label = "الدفتر",
                                        ratingValue = entry.notebook,
                                        isTest = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    DailyEvaluationBadgeCell(
                                        label = "الواجب",
                                        ratingValue = entry.homework,
                                        isTest = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DailyEvaluationBadgeCell(
                                        label = "السلوك",
                                        ratingValue = entry.behavior,
                                        isTest = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    DailyEvaluationBadgeCell(
                                        label = "الاختبار اليومي",
                                        ratingValue = entry.dailyTest,
                                        isTest = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // 4. Attached Lesson Images (if any)
                            if (allPlanImages.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "صور الدرس المرفقة (${allPlanImages.size}):",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate600
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        allPlanImages.forEach { img ->
                                            DailyPlanThumbnailItem(
                                                imageEntity = img,
                                                onClick = { previewPlanImage = img }
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
    }

    if (previewPlanImage != null) {
        DailyPlanFullScreenImageDialog(
            imageEntity = previewPlanImage!!,
            onDismiss = { previewPlanImage = null }
        )
    }
}

@Composable
private fun DailyEvaluationBadgeCell(
    label: String,
    ratingValue: Double?,
    isTest: Boolean,
    modifier: Modifier = Modifier
) {
    val valueText = if (ratingValue == null) {
        "غير مسجل"
    } else if (isTest) {
        "${GradingService.formatScore(ratingValue)} / 20"
    } else {
        GradingService.getRatingLabel(ratingValue)
    }

    val valueColor = when {
        ratingValue == null -> Slate500
        isTest -> Teal700
        ratingValue == 1.0 -> Emerald700
        ratingValue == 0.5 -> Amber600
        else -> Red700
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Slate600,
                maxLines = 1
            )
            Text(
                text = valueText,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1
            )
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
            return rep.annualEffort ?: rep.finalAverage ?: rep.baseDailyGrade.totalScore
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
                                text = "جميع الفترات والسعي السنوي",
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
                                "${sp.stageType.arabicLabel} - ${sp.periodName}"
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
                                        text = "${GradingService.formatRoundedFinalGrade(g.totalScore)} / 100",
                                        color = Teal700,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text("اليومي", color = Teal700, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                color = if (rep.annualEffort != null) Emerald50 else Slate100,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (rep.annualEffort != null) Emerald600 else Slate200)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (rep.annualEffort != null) "${GradingService.formatRoundedFinalGrade(rep.annualEffort)} / 100" else "غير مكتمل",
                                        color = if (rep.annualEffort != null) Emerald700 else Slate600,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "السعي السنوي",
                                        color = if (rep.annualEffort != null) Emerald700 else Slate600,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "الحضور: ${GradingService.formatScore(g.attendanceScore)}/10 | المشاركة: ${GradingService.formatScore(g.participationScore)}/30 | الدفتر: ${GradingService.formatScore(g.notebookScore)}/10 | الواجب: ${GradingService.formatScore(g.homeworkScore)}/15 | السلوك: ${GradingService.formatScore(g.behaviorScore)}/15 | الاختبار: ${GradingService.formatScore(g.quizScore)}/20",
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
                                val rowTitle = if (isMidYear) "نصف السنة" else "${bd.period.stageType.arabicLabel} — ${bd.period.periodName}"
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
                                                    "درجة نصف السنة: ${GradingService.formatRoundedFinalGrade(bd.writtenExamGrade)}/100"
                                                } else {
                                                    "درجة نصف السنة: غير مدخل"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (bd.writtenExamGrade != null) Emerald700 else Slate600
                                            )
                                        } else {
                                            Text(
                                                text = "يومي: ${GradingService.formatScore(bd.dailyGrade)} | تحريري: ${bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: "--"} | معدل: ${bd.periodAverage?.let { GradingService.formatRoundedFinalGrade(it) } ?: "--"}",
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

                            // Stage Summary & Annual Effort (السعي السنوي)
                            if (selectedPeriodFilterId == null) {
                                Divider(color = Slate200, thickness = 1.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("معدل النصف الأول", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = rep.firstHalfAverage?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (rep.firstHalfAverage != null) Slate900 else Slate500
                                            )
                                        }
                                    }

                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("درجة نصف السنة", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = rep.midYearGrade?.let { "${GradingService.formatRoundedFinalGrade(it)}/100" } ?: "غير مدخل",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (rep.midYearGrade != null) Slate900 else Slate500
                                            )
                                        }
                                    }

                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("معدل النصف الثاني", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = rep.secondHalfAverage?.let { GradingService.formatRoundedFinalGrade(it) } ?: "غير مكتمل",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (rep.secondHalfAverage != null) Slate900 else Slate500
                                            )
                                        }
                                    }

                                    Surface(
                                        color = if (rep.annualEffort != null) Emerald50 else Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (rep.annualEffort != null) Emerald600 else Slate200),
                                        modifier = Modifier.weight(1.1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("السعي السنوي", fontSize = 10.sp, color = if (rep.annualEffort != null) Emerald700 else Slate700, fontWeight = FontWeight.ExtraBold)
                                            Text(
                                                text = rep.annualEffort?.let { "${GradingService.formatRoundedFinalGrade(it)} / 100" } ?: "غير مكتمل",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (rep.annualEffort != null) Emerald700 else Slate500
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
                                    text = "درجة نصف السنة بعد الحفظ: ${GradingService.formatRoundedFinalGrade(parsedPreview)}/100",
                                    color = Emerald700,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                val previewAvg = (bd.dailyGrade + parsedPreview) / 2.0
                                Text(
                                    text = "معدل ${bd.period.periodName} بعد الحفظ: (${GradingService.formatScore(bd.dailyGrade)} + ${GradingService.formatScore(parsedPreview)}) ÷ 2 = ${GradingService.formatRoundedFinalGrade(previewAvg)} / 100",
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
            .filter { it.absentDays > 0 }
            .sortedWith(
                compareByDescending<StudentAbsenceSummary> { it.absentDays }
                    .thenBy { it.attendancePercentage }
                    .thenBy { it.studentName }
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
                        text = "لا يوجد طلاب لديهم غياب مسجل حالياً",
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
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var importResult by remember { mutableStateOf<ExcelImportResult?>(null) }
    var isFolderValid by remember { mutableStateOf(ExcelAndBackupService.isBackupFolderValidAndAccessible(context)) }
    var folderDisplayPath by remember { mutableStateOf(ExcelAndBackupService.getBackupFolderDisplayPath(context)) }
    var backups by remember { mutableStateOf(ExcelAndBackupService.listBackups(context)) }
    var lastError by remember { mutableStateOf(ExcelAndBackupService.getLastBackupError(context)) }
    var createBackupAfterFolderPick by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isFolderValid = ExcelAndBackupService.isBackupFolderValidAndAccessible(context)
                folderDisplayPath = ExcelAndBackupService.getBackupFolderDisplayPath(context)
                backups = ExcelAndBackupService.listBackups(context)
                lastError = ExcelAndBackupService.getLastBackupError(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

// ============================================================================
// AVERAGES TABLE SCREEN ("المعدل") + A4 LANDSCAPE PRINTING
// ============================================================================
@Composable
fun AveragesTableScreen(
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
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val currentClass = classes.find { it.id == selectedClassId }
    val classSections = remember(sections, selectedClassId) {
        sections.filter { it.classId == selectedClassId }
    }
    LaunchedEffect(classSections, selectedSectionId) {
        if (classSections.isNotEmpty() && classSections.none { it.id == selectedSectionId }) {
            onSelectSection(classSections.first().id)
        }
    }
    val currentSection = classSections.find { it.id == selectedSectionId }
    val activeAcademicYear = remember(refreshTick) {
        repository.getActiveAcademicYear()
    }

    // Use the exact same calculation service & data source as StudentProfileScreen
    val reports = remember(refreshTick, selectedClassId, selectedSectionId) {
        if (currentClass == null || currentSection == null) {
            emptyList()
        } else {
            GradingService.calculateComprehensiveStudentReports(
                dbHelper = dbHelper,
                repository = repository,
                classId = selectedClassId,
                sectionId = selectedSectionId,
                baseDateFilter = "ALL"
            )
        }
    }

    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Filters (الصف + الشعبة) + Print Action Header
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "المعدل",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                        Text(
                            text = "${currentClass?.className ?: "—"} / شعبة ${currentSection?.sectionName ?: "—"} • عدد الطلاب: ${reports.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            enabled = reports.isNotEmpty() && currentClass != null && currentSection != null,
                            onClick = {
                                if (currentClass != null && currentSection != null) {
                                    printStudentNamesRosterSinglePageA4Portrait(
                                        context = context,
                                        className = currentClass.className,
                                        sectionName = currentSection.sectionName,
                                        studentNames = reports.map { it.studentName },
                                        onError = onShowMessage
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة سجل أسماء الطلبة", modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("طباعة سجل أسماء الطلبة", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        Button(
                            enabled = reports.isNotEmpty() && currentClass != null && currentSection != null,
                            onClick = {
                                if (currentClass != null && currentSection != null) {
                                    printAveragesReportA4Portrait(
                                        context = context,
                                        className = currentClass.className,
                                        sectionName = currentSection.sectionName,
                                        academicYearName = activeAcademicYear.yearName,
                                        reports = reports,
                                        onError = onShowMessage
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طباعة", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }

                // Class filter row
                Text(
                    text = "الصف:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    classes.forEach { cls ->
                        val isSelected = cls.id == selectedClassId
                        Surface(
                            color = if (isSelected) Slate900 else Slate100,
                            contentColor = if (isSelected) Color.White else Slate800,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { onSelectClass(cls.id) }
                        ) {
                            Text(
                                text = cls.className,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Section filter row (filtered strictly by selectedClassId)
                Text(
                    text = "الشعبة:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600
                )
                if (classSections.isEmpty()) {
                    Text(
                        text = "لا توجد شعب مضافة لهذا الصف.",
                        fontSize = 12.sp,
                        color = Red700,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        classSections.forEach { sec ->
                            val isSelected = sec.id == selectedSectionId
                            Surface(
                                color = if (isSelected) Teal700 else Slate100,
                                contentColor = if (isSelected) Color.White else Slate800,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.clickable { onSelectSection(sec.id) }
                            ) {
                                Text(
                                    text = "شعبة ${sec.sectionName}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Actual RTL Table (with horizontal scroll support so student name is never cramped)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (reports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا يوجد طلاب في هذا الصف والشعبة حالياً.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val minTableWidth = maxOf(maxWidth, 640.dp)
                    val nameColWidth = maxOf(minTableWidth - 400.dp, 220.dp)
                    val scoreColWidth = 100.dp

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Table Header Row: اسم الطالب | معدل النصف الأول | درجة نصف السنة | معدل النصف الثاني | السعي السنوي
                        Row(
                            modifier = Modifier
                                .width(nameColWidth + scoreColWidth * 4)
                                .background(Slate900)
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اسم الطالب",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(nameColWidth)
                                    .padding(horizontal = 6.dp)
                            )
                            AveragesTableHeaderCell("معدل النصف الأول", scoreColWidth)
                            AveragesTableHeaderCell("درجة نصف السنة", scoreColWidth)
                            AveragesTableHeaderCell("معدل النصف الثاني", scoreColWidth)
                            AveragesTableHeaderCell("السعي السنوي", scoreColWidth)
                        }

                        // Table Data Rows
                        LazyColumn(
                            modifier = Modifier
                                .width(nameColWidth + scoreColWidth * 4)
                                .weight(1f)
                        ) {
                            items(reports, key = { it.studentId }) { rep ->
                                val firstHalfText = if (rep.firstHalfAverage != null) {
                                    GradingService.formatRoundedFinalGrade(rep.firstHalfAverage)
                                } else {
                                    "غير مكتمل"
                                }
                                val midYearText = if (rep.midYearGrade != null) {
                                    GradingService.formatRoundedFinalGrade(rep.midYearGrade)
                                } else {
                                    "غير مدخل"
                                }
                                val secondHalfText = if (rep.secondHalfAverage != null) {
                                    GradingService.formatRoundedFinalGrade(rep.secondHalfAverage)
                                } else {
                                    "غير مكتمل"
                                }
                                val annualEffortText = if (rep.annualEffort != null) {
                                    GradingService.formatRoundedFinalGrade(rep.annualEffort)
                                } else {
                                    "غير مكتمل"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenStudentProfile(rep.studentId) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = rep.studentName,
                                            color = Slate900,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .width(nameColWidth)
                                                .padding(horizontal = 6.dp)
                                        )
                                        AveragesTableValueCell(
                                            text = firstHalfText,
                                            isComplete = rep.firstHalfAverage != null,
                                            isHighlight = false,
                                            width = scoreColWidth
                                        )
                                        AveragesTableValueCell(
                                            text = midYearText,
                                            isComplete = rep.midYearGrade != null,
                                            isHighlight = false,
                                            width = scoreColWidth
                                        )
                                        AveragesTableValueCell(
                                            text = secondHalfText,
                                            isComplete = rep.secondHalfAverage != null,
                                            isHighlight = false,
                                            width = scoreColWidth
                                        )
                                        AveragesTableValueCell(
                                            text = annualEffortText,
                                            isComplete = rep.annualEffort != null,
                                            isHighlight = true,
                                            width = scoreColWidth
                                        )
                                    }
                                    HorizontalDivider(color = Slate200, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AveragesTableHeaderCell(
    title: String,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AveragesTableValueCell(
    text: String,
    isComplete: Boolean,
    isHighlight: Boolean,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isHighlight && isComplete) {
            Surface(
                color = Emerald50,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Emerald600)
            ) {
                Text(
                    text = text,
                    color = Emerald700,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        } else {
            Text(
                text = text,
                color = when {
                    isComplete -> Slate900
                    else -> Slate500
                },
                fontSize = if (isComplete) 13.sp else 11.sp,
                fontWeight = if (isComplete) FontWeight.ExtraBold else FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

private fun escapeHtmlText(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

/**
 * Prints the currently filtered class/section student averages table in A4 Portrait RTL format,
 * using the exact same calculated values and rounding rules shown in the "المعدل" screen and StudentProfileScreen.
 * Dynamically adapts vertical spacing and font size to fit all section students onto a single A4 Portrait sheet
 * whenever possible, while repeating the table header (`<thead>`) and avoiding split rows if a very large section spans 2 pages.
 */
private fun printAveragesReportA4Portrait(
    context: android.content.Context,
    className: String,
    sectionName: String,
    academicYearName: String,
    reports: List<StudentComprehensiveGradeReport>,
    onError: (String) -> Unit
) {
    runCatching {
        val todayStr = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        val displayDate = GradingService.formatDisplayDate(todayStr)
        val studentCount = reports.size

        // Adaptive portrait density so typical sections (up to ~50-55 students) fit cleanly on 1 A4 Portrait page
        val pageMargin = when {
            studentCount > 45 -> "5mm 6mm"
            studentCount > 35 -> "6mm 7mm"
            else -> "8mm 8mm"
        }
        val headerPadding = when {
            studentCount > 40 -> "5px 10px"
            studentCount > 28 -> "6px 12px"
            else -> "8px 14px"
        }
        val headerMarginBottom = when {
            studentCount > 40 -> "5px"
            studentCount > 28 -> "7px"
            else -> "10px"
        }
        val titleFontSize = when {
            studentCount > 40 -> "15px"
            studentCount > 28 -> "16px"
            else -> "18px"
        }
        val metaFontSize = when {
            studentCount > 40 -> "10.5px"
            studentCount > 28 -> "11px"
            else -> "12px"
        }
        val thPadding = when {
            studentCount > 45 -> "4px 4px"
            studentCount > 35 -> "5px 5px"
            else -> "6px 6px"
        }
        val tdPadding = when {
            studentCount > 48 -> "2.5px 5px"
            studentCount > 38 -> "3.5px 6px"
            studentCount > 28 -> "4.5px 6px"
            else -> "6px 7px"
        }
        val tableFontSize = when {
            studentCount > 48 -> "10px"
            studentCount > 38 -> "10.5px"
            studentCount > 28 -> "11.5px"
            else -> "12.5px"
        }
        val thFontSize = when {
            studentCount > 40 -> "10.5px"
            studentCount > 28 -> "11px"
            else -> "12px"
        }
        val rowLineHeight = when {
            studentCount > 40 -> "1.15"
            else -> "1.25"
        }

        val rowsHtml = StringBuilder()
        reports.forEach { rep ->
            val firstHalfText = if (rep.firstHalfAverage != null) {
                GradingService.formatRoundedFinalGrade(rep.firstHalfAverage)
            } else {
                "غير مكتمل"
            }
            val midYearText = if (rep.midYearGrade != null) {
                GradingService.formatRoundedFinalGrade(rep.midYearGrade)
            } else {
                "غير مدخل"
            }
            val secondHalfText = if (rep.secondHalfAverage != null) {
                GradingService.formatRoundedFinalGrade(rep.secondHalfAverage)
            } else {
                "غير مكتمل"
            }
            val annualEffortText = if (rep.annualEffort != null) {
                GradingService.formatRoundedFinalGrade(rep.annualEffort)
            } else {
                "غير مكتمل"
            }

            rowsHtml.append(
                """
                <tr>
                    <td class="student-name">${escapeHtmlText(rep.studentName)}</td>
                    <td class="score-cell ${if (rep.firstHalfAverage == null) "incomplete" else ""}">${escapeHtmlText(firstHalfText)}</td>
                    <td class="score-cell ${if (rep.midYearGrade == null) "incomplete" else ""}">${escapeHtmlText(midYearText)}</td>
                    <td class="score-cell ${if (rep.secondHalfAverage == null) "incomplete" else ""}">${escapeHtmlText(secondHalfText)}</td>
                    <td class="score-cell annual-cell ${if (rep.annualEffort == null) "incomplete" else ""}">${escapeHtmlText(annualEffortText)}</td>
                </tr>
                """.trimIndent()
            )
        }

        val htmlDocument = """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8" />
                <title>المعدل - ${escapeHtmlText(className)} - شعبة ${escapeHtmlText(sectionName)}</title>
                <style>
                    @page {
                        size: A4 portrait;
                        margin: $pageMargin;
                    }
                    * {
                        box-sizing: border-box;
                        -webkit-print-color-adjust: exact !important;
                        print-color-adjust: exact !important;
                    }
                    body {
                        font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                        direction: rtl;
                        text-align: right;
                        color: #0F172A;
                        margin: 0;
                        padding: 0;
                    }
                    .report-header {
                        border: 1.5px solid #0F172A;
                        border-radius: 6px;
                        padding: $headerPadding;
                        margin-bottom: $headerMarginBottom;
                        background-color: #F8FAFC;
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }
                    .report-title {
                        font-size: $titleFontSize;
                        font-weight: 800;
                        text-align: center;
                        margin: 0 0 4px 0;
                        color: #0F172A;
                        line-height: 1.2;
                    }
                    .meta-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: $metaFontSize;
                        font-weight: 700;
                        color: #1E293B;
                        flex-wrap: wrap;
                        gap: 8px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        table-layout: fixed;
                        direction: rtl;
                    }
                    thead {
                        display: table-header-group;
                    }
                    tfoot {
                        display: table-footer-group;
                    }
                    tr {
                        page-break-inside: avoid;
                        break-inside: avoid;
                    }
                    th, td {
                        border: 1px solid #334155;
                        font-size: $tableFontSize;
                        line-height: $rowLineHeight;
                        vertical-align: middle;
                    }
                    th {
                        background-color: #0F172A;
                        color: #FFFFFF;
                        font-size: $thFontSize;
                        font-weight: 800;
                        text-align: center;
                        padding: $thPadding;
                    }
                    td {
                        padding: $tdPadding;
                    }
                    th.name-header {
                        width: 38%;
                        text-align: right;
                        padding-right: 8px;
                    }
                    th.score-header {
                        width: 15.5%;
                        text-align: center;
                    }
                    td.student-name {
                        font-weight: 800;
                        text-align: right;
                        padding-right: 8px;
                        white-space: normal;
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }
                    td.score-cell {
                        text-align: center;
                        font-weight: 800;
                        white-space: nowrap;
                    }
                    td.annual-cell {
                        background-color: #F0FDF4;
                        color: #15803D;
                    }
                    td.incomplete {
                        color: #64748B;
                        font-weight: 600;
                        font-size: 92%;
                        background-color: #FFFFFF;
                    }
                    tbody tr:nth-child(even) td:not(.annual-cell) {
                        background-color: #F8FAFC;
                    }
                </style>
            </head>
            <body>
                <div class="report-header">
                    <div class="report-title">المعدل</div>
                    <div class="meta-row">
                        <span>الصف: ${escapeHtmlText(className)}</span>
                        <span>الشعبة: ${escapeHtmlText(sectionName)}</span>
                        <span>العام الدراسي: ${escapeHtmlText(academicYearName)}</span>
                        <span>التاريخ: ${escapeHtmlText(displayDate)}</span>
                    </div>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th class="name-header">اسم الطالب</th>
                            <th class="score-header">معدل النصف الأول</th>
                            <th class="score-header">درجة نصف السنة</th>
                            <th class="score-header">معدل النصف الثاني</th>
                            <th class="score-header">السعي السنوي</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rowsHtml
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val webView = android.webkit.WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
                if (printManager == null) {
                    onError("خدمة الطباعة غير متوفرة على هذا الجهاز")
                    return
                }
                val jobName = "المعدل_${className}_شعبة_${sectionName}"
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val printAttributes = android.print.PrintAttributes.Builder()
                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4.asPortrait())
                    .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                    .build()
                printManager.print(jobName, printAdapter, printAttributes)
            }
        }
        webView.loadDataWithBaseURL(null, htmlDocument, "text/html", "UTF-8", null)
    }.onFailure { e ->
        onError("تعذر بدء الطباعة: ${e.message ?: "خطأ غير معروف"}")
    }
}

/**
 * Prints a formal student names roster ("طباعة سجل أسماء الطلبة") in A4 Portrait RTL format.
 * Uses Android's native [android.graphics.pdf.PdfDocument] + [android.print.PrintDocumentAdapter]
 * with exact A4 Portrait dimensions (595 x 842 pt) instead of WebView so that:
 * 1. The roster is guaranteed to be strictly 1 page (`setPageCount(1)`).
 * 2. The available vertical space after the top header is accurately measured and distributed across all student rows,
 *    preventing the table from being squished at the top of the page or rendered with tiny unreadable text.
 * 3. Top-Right: "المدرسة:" with a blank dotted line for manual writing.
 * 4. Top-Center: "أسماء الطلبة للعام الدراسي" with a blank dotted space for manual writing.
 * 5. Top-Left: Selected Class and Section clearly displayed.
 * 6. Two-column RTL table ("ت" on the right, "اسم الطالب" on the left) numbered 1..N in the exact current order.
 */
private fun printStudentNamesRosterSinglePageA4Portrait(
    context: android.content.Context,
    className: String,
    sectionName: String,
    studentNames: List<String>,
    onError: (String) -> Unit
) {
    runCatching {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null) {
            onError("خدمة الطباعة غير متوفرة على هذا الجهاز")
            return
        }

        val jobName = "سجل_أسماء_الطلبة_${className}_شعبة_${sectionName}"
        val cleanNames = studentNames.toList()

        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback
            ) {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                try {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }

                    // Standard ISO A4 Portrait in PostScript points (1/72 inch): 595 x 842 pt
                    val pageWidth = 595
                    val pageHeight = 842
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // White page background
                    canvas.drawColor(android.graphics.Color.WHITE)

                    val totalStudents = cleanNames.size.coerceAtLeast(1)

                    // Balanced margins in points (adaptive for very large sections while utilizing full A4 height)
                    val marginH = if (totalStudents > 45) 22f else 28f
                    val marginTop = if (totalStudents > 45) 20f else 26f
                    val marginBottom = if (totalStudents > 45) 20f else 26f

                    val contentLeft = marginH
                    val contentRight = pageWidth - marginH
                    val contentWidth = contentRight - contentLeft

                    // 1. Header Box dimensions
                    val headerHeight = when {
                        totalStudents > 50 -> 42f
                        totalStudents > 38 -> 46f
                        else -> 52f
                    }
                    val headerBottomGap = when {
                        totalStudents > 50 -> 8f
                        else -> 10f
                    }
                    val headerTop = marginTop
                    val headerBottom = headerTop + headerHeight
                    val headerRect = android.graphics.RectF(contentLeft, headerTop, contentRight, headerBottom)

                    val headerBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = android.graphics.Color.parseColor("#F8FAFC")
                    }
                    val headerBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.5f
                        color = android.graphics.Color.parseColor("#0F172A")
                    }
                    canvas.drawRoundRect(headerRect, 6f, 6f, headerBgPaint)
                    canvas.drawRoundRect(headerRect, 6f, 6f, headerBorderPaint)

                    val boldTypeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )

                    val headerSideFontSize = when {
                        totalStudents > 50 -> 11f
                        totalStudents > 38 -> 11.5f
                        else -> 12.5f
                    }
                    val headerCenterFontSize = when {
                        totalStudents > 50 -> 13f
                        totalStudents > 38 -> 14f
                        else -> 15f
                    }

                    val headerCenterY = headerTop + headerHeight / 2f

                    // Top-Right: "المدرسة: ......................"
                    val rightHeaderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = headerSideFontSize
                        color = android.graphics.Color.parseColor("#0F172A")
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val rightFontMetrics = rightHeaderPaint.fontMetrics
                    val rightBaselineY = headerCenterY - (rightFontMetrics.ascent + rightFontMetrics.descent) / 2f
                    val schoolLabel = "المدرسة: "
                    val schoolRightX = contentRight - 10f
                    canvas.drawText(schoolLabel, schoolRightX, rightBaselineY, rightHeaderPaint)

                    val schoolLabelWidth = rightHeaderPaint.measureText(schoolLabel)
                    val dottedLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.1f
                        color = android.graphics.Color.parseColor("#475569")
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
                    }
                    val schoolLineEndX = schoolRightX - schoolLabelWidth - 2f
                    val schoolLineStartX = contentRight - contentWidth * 0.28f
                    if (schoolLineEndX > schoolLineStartX) {
                        canvas.drawLine(
                            schoolLineStartX,
                            rightBaselineY + 3f,
                            schoolLineEndX,
                            rightBaselineY + 3f,
                            dottedLinePaint
                        )
                    }

                    // Top-Center: "أسماء الطلبة للعام الدراسي ................"
                    val centerHeaderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = headerCenterFontSize
                        color = android.graphics.Color.parseColor("#0F172A")
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val centerFontMetrics = centerHeaderPaint.fontMetrics
                    val centerBaselineY = headerCenterY - (centerFontMetrics.ascent + centerFontMetrics.descent) / 2f
                    val centerTitleText = "أسماء الطلبة للعام الدراسي                        "
                    val centerX = (contentLeft + contentRight) / 2f
                    canvas.drawText(centerTitleText, centerX, centerBaselineY, centerHeaderPaint)

                    // Draw dotted blank line for the academic year on the left side of the centered title
                    val fullCenterWidth = centerHeaderPaint.measureText(centerTitleText)
                    val titleOnlyWidth = centerHeaderPaint.measureText("أسماء الطلبة للعام الدراسي ")
                    val yearLineRightX = (centerX + fullCenterWidth / 2f) - titleOnlyWidth
                    val yearLineLeftX = centerX - fullCenterWidth / 2f + 4f
                    if (yearLineRightX > yearLineLeftX) {
                        canvas.drawLine(
                            yearLineLeftX,
                            centerBaselineY + 3f,
                            yearLineRightX,
                            centerBaselineY + 3f,
                            dottedLinePaint
                        )
                    }

                    // Top-Left: "الصف: ... / شعبة: ..."
                    val leftHeaderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = headerSideFontSize
                        color = android.graphics.Color.parseColor("#0F172A")
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                    val classSectionLabel = "الصف: $className / شعبة: $sectionName"
                    val maxLeftWidth = contentWidth * 0.28f
                    var leftTextSize = headerSideFontSize
                    while (leftHeaderPaint.measureText(classSectionLabel) > maxLeftWidth && leftTextSize > 8.5f) {
                        leftTextSize -= 0.5f
                        leftHeaderPaint.textSize = leftTextSize
                    }
                    val leftBaselineY = headerCenterY - (leftHeaderPaint.fontMetrics.ascent + leftHeaderPaint.fontMetrics.descent) / 2f
                    canvas.drawText(classSectionLabel, contentLeft + 10f, leftBaselineY, leftHeaderPaint)

                    // 2. Calculate available vertical space for the table on this single A4 Portrait page
                    val tableTop = headerBottom + headerBottomGap
                    val maxTableBottom = pageHeight - marginBottom
                    val availableTableHeight = (maxTableBottom - tableTop).coerceAtLeast(200f)

                    val tableHeaderHeight = when {
                        totalStudents > 50 -> 22f
                        totalStudents > 38 -> 25f
                        else -> 28f
                    }
                    val availableBodyHeight = (availableTableHeight - tableHeaderHeight).coerceAtLeast(100f)

                    // Distribute row height across the available vertical space on the A4 sheet:
                    // - For small sections (e.g. 10-22 students), rows expand up to 31pt (~11mm) so the table is generous, clear, and well-proportioned.
                    // - For medium/standard sections (25-40 students), rows fill nearly the entire A4 sheet height.
                    // - For large sections (45-65+ students), rows scale down smoothly so every student strictly stays on Page 1.
                    val rawRowHeight = availableBodyHeight / totalStudents.toFloat()
                    val rowHeight = rawRowHeight.coerceIn(9.0f, 31.0f)

                    // Verify total height never exceeds availableTableHeight (strict single-page guarantee)
                    val finalRowHeight = if (tableHeaderHeight + rowHeight * totalStudents > availableTableHeight) {
                        (availableTableHeight - tableHeaderHeight) / totalStudents.toFloat()
                    } else {
                        rowHeight
                    }

                    // Choose a clear, readable font size proportional to the row height
                    val bodyFontSize = (finalRowHeight * 0.54f).coerceIn(8.0f, 14.5f)
                    val tableHeaderFontSize = (bodyFontSize + 0.8f).coerceIn(9.5f, 14.5f)

                    // RTL Table Column Geometry:
                    // Right column ("ت"): 12% of width (from columnDividerX to contentRight)
                    // Left column ("اسم الطالب"): 88% of width (from contentLeft to columnDividerX)
                    val seqColWidth = (contentWidth * 0.12f).coerceIn(48f, 68f)
                    val columnDividerX = contentRight - seqColWidth

                    // 3. Draw Table Header Row ("ت" | "اسم الطالب")
                    val thBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = android.graphics.Color.parseColor("#0F172A")
                    }
                    val gridBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.0f
                        color = android.graphics.Color.parseColor("#1E293B")
                    }
                    val outerTableBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.4f
                        color = android.graphics.Color.parseColor("#0F172A")
                    }

                    val thRect = android.graphics.RectF(contentLeft, tableTop, contentRight, tableTop + tableHeaderHeight)
                    canvas.drawRect(thRect, thBgPaint)
                    canvas.drawRect(thRect, gridBorderPaint)

                    val thTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = tableHeaderFontSize
                        color = android.graphics.Color.WHITE
                    }
                    val thCenterY = tableTop + tableHeaderHeight / 2f
                    val thBaselineY = thCenterY - (thTextPaint.fontMetrics.ascent + thTextPaint.fontMetrics.descent) / 2f

                    // "ت" centered in right column
                    thTextPaint.textAlign = android.graphics.Paint.Align.CENTER
                    canvas.drawText("ت", (columnDividerX + contentRight) / 2f, thBaselineY, thTextPaint)

                    // "اسم الطالب" right-aligned in name column
                    thTextPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas.drawText("اسم الطالب", columnDividerX - 10f, thBaselineY, thTextPaint)

                    // Header vertical divider
                    val headerDividerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.0f
                        color = android.graphics.Color.parseColor("#475569")
                    }
                    canvas.drawLine(columnDividerX, tableTop, columnDividerX, tableTop + tableHeaderHeight, headerDividerPaint)

                    // 4. Draw Student Rows (1..N)
                    val evenRowBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = android.graphics.Color.parseColor("#F8FAFC")
                    }
                    val seqTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = bodyFontSize
                        color = android.graphics.Color.parseColor("#0F172A")
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val nameTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = boldTypeface
                        textSize = bodyFontSize
                        color = android.graphics.Color.parseColor("#0F172A")
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    val maxNameAvailableWidth = (columnDividerX - contentLeft) - 20f

                    cleanNames.forEachIndexed { index, studentName ->
                        val rowTop = tableTop + tableHeaderHeight + (index * finalRowHeight)
                        val rowBottom = rowTop + finalRowHeight

                        if (index % 2 == 1) {
                            canvas.drawRect(contentLeft, rowTop, contentRight, rowBottom, evenRowBgPaint)
                        }

                        // Row horizontal border
                        canvas.drawLine(contentLeft, rowBottom, contentRight, rowBottom, gridBorderPaint)

                        val rowCenterY = (rowTop + rowBottom) / 2f

                        // Draw Sequence Number ("ت")
                        val seqBaselineY = rowCenterY - (seqTextPaint.fontMetrics.ascent + seqTextPaint.fontMetrics.descent) / 2f
                        canvas.drawText(
                            (index + 1).toString(),
                            (columnDividerX + contentRight) / 2f,
                            seqBaselineY,
                            seqTextPaint
                        )

                        // Draw Full Student Name (auto-fit font size only if an unusually long name exceeds column width, never truncating)
                        nameTextPaint.textSize = bodyFontSize
                        while (nameTextPaint.measureText(studentName) > maxNameAvailableWidth && nameTextPaint.textSize > 7.5f) {
                            nameTextPaint.textSize -= 0.5f
                        }
                        val nameBaselineY = rowCenterY - (nameTextPaint.fontMetrics.ascent + nameTextPaint.fontMetrics.descent) / 2f
                        canvas.drawText(
                            studentName,
                            columnDividerX - 10f,
                            nameBaselineY,
                            nameTextPaint
                        )
                    }

                    val finalTableBottom = tableTop + tableHeaderHeight + (cleanNames.size * finalRowHeight)

                    // Vertical column divider through all body rows
                    canvas.drawLine(columnDividerX, tableTop + tableHeaderHeight, columnDividerX, finalTableBottom, gridBorderPaint)

                    // Crisp outer border around the entire table
                    canvas.drawRect(contentLeft, tableTop, contentRight, finalTableBottom, outerTableBorderPaint)

                    pdfDocument.finishPage(page)

                    java.io.FileOutputStream(destination.fileDescriptor).use { outStream ->
                        pdfDocument.writeTo(outStream)
                    }
                    callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message ?: "خطأ في إنشاء ملف الطباعة")
                } finally {
                    pdfDocument.close()
                }
            }
        }

        val printAttributes = android.print.PrintAttributes.Builder()
            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4.asPortrait())
            .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, printAdapter, printAttributes)
    }.onFailure { e ->
        onError("تعذر بدء طباعة سجل الأسماء: ${e.message ?: "خطأ غير معروف"}")
    }
}

// ============================================================================
// PERIOD COMPARISON SCREEN ("مقارنة نتائج الفترات")
// ============================================================================
private data class StudentPeriodComparisonItem(
    val sequence: Int,
    val studentId: Long,
    val studentName: String,
    val firstScore: Double?,
    val secondScore: Double?,
    val difference: Double?,
    val status: String // "improved", "declined", "unchanged", "incomplete"
)

@Composable
fun PeriodComparisonScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit
) {
    val currentClass = classes.find { it.id == selectedClassId } ?: classes.firstOrNull()
    val classSections = remember(sections, currentClass?.id) {
        sections.filter { it.classId == currentClass?.id }
    }
    LaunchedEffect(classSections, selectedSectionId) {
        if (classSections.isNotEmpty() && classSections.none { it.id == selectedSectionId }) {
            onSelectSection(classSections.first().id)
        }
    }
    val currentSection = classSections.find { it.id == selectedSectionId } ?: classSections.firstOrNull()

    val studyPeriods = remember(refreshTick) {
        runCatching { repository.getStudyPeriods() }.getOrElse { emptyList() }
    }

    var firstPeriodId by remember(studyPeriods) {
        mutableLongStateOf(studyPeriods.getOrNull(0)?.id ?: 0L)
    }
    var secondPeriodId by remember(studyPeriods) {
        mutableLongStateOf(
            studyPeriods.getOrNull(1)?.id
                ?: studyPeriods.getOrNull(0)?.id
                ?: 0L
        )
    }

    var classDropdownExpanded by remember { mutableStateOf(false) }
    var firstPeriodExpanded by remember { mutableStateOf(false) }
    var secondPeriodExpanded by remember { mutableStateOf(false) }

    val firstPeriod = studyPeriods.find { it.id == firstPeriodId } ?: studyPeriods.firstOrNull()
    val secondPeriod = studyPeriods.find { it.id == secondPeriodId }
        ?: studyPeriods.getOrNull(1)
        ?: studyPeriods.firstOrNull()

    fun getPeriodDisplayLabel(period: StudyPeriodEntity?): String {
        if (period == null) return "—"
        return if (period.stageType == AcademicStage.MID_YEAR) {
            if (period.periodName.isBlank() || period.periodName == "الفترة" || period.periodName == "نصف السنة") {
                "نصف السنة"
            } else {
                "${period.stageType.arabicLabel} — ${period.periodName}"
            }
        } else {
            "${period.stageType.arabicLabel} — ${period.periodName}"
        }
    }

    val isSamePeriodSelected = firstPeriod != null && secondPeriod != null && firstPeriod.id == secondPeriod.id

    val reports = remember(refreshTick, currentClass?.id, currentSection?.id) {
        if (currentClass == null || currentSection == null) {
            emptyList()
        } else {
            runCatching {
                GradingService.calculateComprehensiveStudentReports(
                    dbHelper = dbHelper,
                    repository = repository,
                    classId = currentClass.id,
                    sectionId = currentSection.id,
                    baseDateFilter = "ALL"
                )
            }.getOrElse { emptyList() }
        }
    }

    fun resolvePeriodComparedScore(bd: StudentPeriodGradeBreakdown?): Double? {
        if (bd == null) return null
        val isMidYear = bd.period.stageType == AcademicStage.MID_YEAR
        return if (isMidYear) {
            bd.writtenExamGrade
        } else {
            bd.periodAverage ?: bd.dailyGrade
        }
    }

    val comparisonRows = remember(reports, firstPeriod?.id, secondPeriod?.id, isSamePeriodSelected) {
        if (firstPeriod == null || secondPeriod == null || isSamePeriodSelected) {
            emptyList()
        } else {
            reports.mapIndexed { idx, rep ->
                val b1 = rep.periodBreakdowns.find { it.period.id == firstPeriod.id }
                val b2 = rep.periodBreakdowns.find { it.period.id == secondPeriod.id }

                val s1 = resolvePeriodComparedScore(b1)
                val s2 = resolvePeriodComparedScore(b2)

                val diff = if (s1 != null && s2 != null) {
                    Math.round((s2 - s1) * 100.0) / 100.0
                } else {
                    null
                }

                val status = when {
                    diff == null -> "incomplete"
                    diff > 0.0001 -> "improved"
                    diff < -0.0001 -> "declined"
                    else -> "unchanged"
                }

                StudentPeriodComparisonItem(
                    sequence = idx + 1,
                    studentId = rep.studentId,
                    studentName = rep.studentName,
                    firstScore = s1,
                    secondScore = s2,
                    difference = diff,
                    status = status
                )
            }
        }
    }

    val improvedCount = remember(comparisonRows) { comparisonRows.count { it.status == "improved" } }
    val declinedCount = remember(comparisonRows) { comparisonRows.count { it.status == "declined" } }
    val unchangedCount = remember(comparisonRows) { comparisonRows.count { it.status == "unchanged" } }

    fun formatDiff(diff: Double?): String {
        if (diff == null) return "—"
        val absFormatted = GradingService.formatScore(kotlin.math.abs(diff))
        return when {
            diff > 0.0001 -> "+$absFormatted"
            diff < -0.0001 -> "-$absFormatted"
            else -> "0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Filters Card (الصف، الشعبة، الفترة الأولى، الفترة الثانية)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "مقارنة نتائج الفترات",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                        Text(
                            text = "${currentClass?.className ?: "—"} / شعبة ${currentSection?.sectionName ?: "—"} • عدد الطلاب: ${reports.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                    }
                }

                // Filter 1: الصف
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الصف:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700,
                        modifier = Modifier.width(80.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (classes.isNotEmpty()) classDropdownExpanded = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentClass?.className ?: "اختر الصف",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "اختر الصف",
                                    tint = Slate700
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false }
                        ) {
                            classes.forEach { cls ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cls.className,
                                            fontWeight = if (cls.id == currentClass?.id) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (cls.id == currentClass?.id) Teal700 else Slate900
                                        )
                                    },
                                    onClick = {
                                        onSelectClass(cls.id)
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Filter 2: الشعبة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الشعبة:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700,
                        modifier = Modifier.width(80.dp)
                    )
                    if (classSections.isEmpty()) {
                        Text(
                            text = "لا توجد شعب مضافة لهذا الصف",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Red700
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            classSections.forEach { sec ->
                                val isSelected = sec.id == currentSection?.id
                                Surface(
                                    color = if (isSelected) Slate900 else Slate100,
                                    contentColor = if (isSelected) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { onSelectSection(sec.id) }
                                ) {
                                    Text(
                                        text = "شعبة ${sec.sectionName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter 3: الفترة الأولى
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الفترة الأولى:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700,
                        modifier = Modifier.width(80.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (studyPeriods.isNotEmpty()) firstPeriodExpanded = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (firstPeriod != null) getPeriodDisplayLabel(firstPeriod) else "لا توجد فترات دراسية",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "اختر الفترة الأولى",
                                    tint = Slate700
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = firstPeriodExpanded,
                            onDismissRequest = { firstPeriodExpanded = false }
                        ) {
                            studyPeriods.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getPeriodDisplayLabel(p),
                                            fontWeight = if (p.id == firstPeriod?.id) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (p.id == firstPeriod?.id) Teal700 else Slate900
                                        )
                                    },
                                    onClick = {
                                        firstPeriodId = p.id
                                        firstPeriodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Filter 4: الفترة الثانية
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الفترة الثانية:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate700,
                        modifier = Modifier.width(80.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (studyPeriods.isNotEmpty()) secondPeriodExpanded = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (secondPeriod != null) getPeriodDisplayLabel(secondPeriod) else "لا توجد فترات دراسية",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "اختر الفترة الثانية",
                                    tint = Slate700
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = secondPeriodExpanded,
                            onDismissRequest = { secondPeriodExpanded = false }
                        ) {
                            studyPeriods.forEach { p ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = getPeriodDisplayLabel(p),
                                            fontWeight = if (p.id == secondPeriod?.id) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (p.id == secondPeriod?.id) Teal700 else Slate900
                                        )
                                    },
                                    onClick = {
                                        secondPeriodId = p.id
                                        secondPeriodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Handle special states
        if (studyPeriods.isEmpty()) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد فترات دراسية مضافة حالياً للمقارنة",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (isSamePeriodSelected) {
            Surface(
                color = Amber50,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Amber600),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (studyPeriods.size == 1) {
                            "يوجد فترة دراسية واحدة فقط حالياً. يرجى إضافة فترة ثانية من قسم الفترات الدراسية للمقارنة."
                        } else {
                            "يرجى اختيار فترتين مختلفتين للمقارنة"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Amber700,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 2. Summary Strip (3 cards on a single row: Improved, Declined, Unchanged)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Improved
                Surface(
                    color = Emerald50,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Emerald600),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = improvedCount.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald700
                        )
                        Text(
                            text = "تحسن (↑)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Emerald700
                        )
                    }
                }

                // Declined
                Surface(
                    color = Red50,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Red600),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = declinedCount.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Red700
                        )
                        Text(
                            text = "تراجع (↓)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Red700
                        )
                    }
                }

                // Unchanged
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = unchangedCount.toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate700
                        )
                        Text(
                            text = "ثابت (=)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate700
                        )
                    }
                }
            }

            // 3. Students Comparison Table
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (comparisonRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا يوجد طلاب مسجلين في هذا الصف والشعبة",
                            color = Slate500,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Table Header
                        item {
                            Surface(
                                color = Slate900,
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ت",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.width(26.dp)
                                    )
                                    Text(
                                        text = "اسم الطالب",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .weight(1.9f)
                                            .padding(end = 4.dp)
                                    )
                                    Text(
                                        text = firstPeriod?.periodName ?: "الفترة 1",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.95f)
                                    )
                                    Text(
                                        text = secondPeriod?.periodName ?: "الفترة 2",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(0.95f)
                                    )
                                    Text(
                                        text = "الفرق",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(0.85f)
                                    )
                                    Text(
                                        text = "الحالة",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1.05f)
                                    )
                                }
                            }
                        }

                        // Table Rows
                        items(comparisonRows, key = { it.studentId }) { item ->
                            val rowBg = if (item.sequence % 2 == 1) Color.White else Slate50
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Sequence
                                Text(
                                    text = item.sequence.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(26.dp)
                                )

                                // 2. Student Name
                                Text(
                                    text = item.studentName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1.9f)
                                        .padding(end = 4.dp)
                                )

                                // 3. First Period Score
                                Text(
                                    text = if (item.firstScore != null) GradingService.formatScore(item.firstScore) else "—",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.firstScore != null) Slate800 else Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.95f)
                                )

                                // 4. Second Period Score
                                Text(
                                    text = if (item.secondScore != null) GradingService.formatScore(item.secondScore) else "—",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.secondScore != null) Slate800 else Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.95f)
                                )

                                // 5. Difference
                                val diffColor = when (item.status) {
                                    "improved" -> Emerald700
                                    "declined" -> Red700
                                    else -> Slate600
                                }
                                Text(
                                    text = formatDiff(item.difference),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = diffColor,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.85f)
                                )

                                // 6. Status Badge
                                Box(
                                    modifier = Modifier.weight(1.05f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (item.status) {
                                        "improved" -> {
                                            Surface(
                                                color = Emerald50,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Emerald600)
                                            ) {
                                                Text(
                                                    text = "↑ تحسن",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Emerald700,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        "declined" -> {
                                            Surface(
                                                color = Red50,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Red600)
                                            ) {
                                                Text(
                                                    text = "↓ تراجع",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Red700,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        "unchanged" -> {
                                            Surface(
                                                color = Slate100,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Slate200)
                                            ) {
                                                Text(
                                                    text = "= ثابت",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Slate700,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = "—",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Slate100)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// DAILY PLAN SCREEN ("الخطة اليومية" - Independent Section for All Classes & Sections)
// ============================================================================
private data class DraftPlanImageItem(
    val fileName: String,
    val imageBase64: String
)

private fun decodePlanBitmap(
    context: android.content.Context,
    repository: TrackerRepository,
    fileName: String,
    imageBase64: String
): ImageBitmap? {
    return runCatching {
        val dir = repository.getDailyPlanImagesDir(context)
        val file = if (fileName.isNotBlank()) java.io.File(dir, fileName) else null
        val bmp: android.graphics.Bitmap? = if (file != null && file.exists() && file.length() > 0L) {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } else if (imageBase64.isNotBlank()) {
            val bytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            null
        }
        bmp?.asImageBitmap()
    }.getOrNull()
}

private fun encodeUriToCompressedBase64(
    context: android.content.Context,
    uri: android.net.Uri
): Pair<String, String>? {
    return runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val originalBmp = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
        val maxDim = 1280
        val w = originalBmp.width
        val h = originalBmp.height
        val scaledBmp = if (w > maxDim || h > maxDim) {
            val ratio = minOf(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
            val nw = (w * ratio).toInt().coerceAtLeast(1)
            val nh = (h * ratio).toInt().coerceAtLeast(1)
            android.graphics.Bitmap.createScaledBitmap(originalBmp, nw, nh, true)
        } else {
            originalBmp
        }
        val baos = java.io.ByteArrayOutputStream()
        scaledBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
        val compressedBytes = baos.toByteArray()
        val b64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
        val fileName = "plan_img_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        fileName to b64
    }.getOrNull()
}

@Composable
fun DailyPlanThumbnailItem(
    imageEntity: DailyPlanImageEntity,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dbHelper = remember { AppDatabaseHelper.getInstance(context) }
    val repository = remember { TrackerRepository(dbHelper) }
    val imageBitmap = remember(imageEntity.fileName, imageEntity.imageBase64) {
        decodePlanBitmap(context, repository, imageEntity.fileName, imageEntity.imageBase64)
    }

    Box(
        modifier = Modifier
            .size(84.dp)
    ) {
        Surface(
            color = Slate100,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() }
        ) {
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = "صورة الدرس",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("صورة", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (onDelete != null) {
            Surface(
                color = Red600,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(22.dp)
                    .clickable { onDelete() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "حذف الصورة",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DailyPlanFullScreenImageDialog(
    imageEntity: DailyPlanImageEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dbHelper = remember { AppDatabaseHelper.getInstance(context) }
    val repository = remember { TrackerRepository(dbHelper) }
    val imageBitmap = remember(imageEntity.fileName, imageEntity.imageBase64) {
        decodePlanBitmap(context, repository, imageEntity.fileName, imageEntity.imageBase64)
    }

    var scale by remember(imageEntity.id, imageEntity.fileName) { mutableFloatStateOf(1f) }
    var offset by remember(imageEntity.id, imageEntity.fileName) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(targetOffset: Offset, targetScale: Float): Offset {
        if (targetScale <= 1f || containerSize.width <= 0 || containerSize.height <= 0) {
            return Offset.Zero
        }
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = targetOffset.x.coerceIn(-maxX, maxX),
            y = targetOffset.y.coerceIn(-maxY, maxY)
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xEE0F172A),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "معاينة صورة الدرس",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Red600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إغلاق", fontWeight = FontWeight.ExtraBold)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clipToBounds()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(imageEntity.id, imageEntity.fileName) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            )
                        }
                        .pointerInput(imageEntity.id, imageEntity.fileName) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                if (newScale <= 1.01f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    val scaledOffset = offset + pan
                                    scale = newScale
                                    offset = clampOffset(scaledOffset, newScale)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = "صورة الدرس بالحجم الكامل",
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        )
                    } else {
                        Text(
                            text = "تعذر عرض الصورة",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DailyPlanScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    repository: TrackerRepository,
    refreshTick: Int,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var localTick by remember { mutableIntStateOf(0) }

    // Form State (Create or Edit Plan)
    var editingPlanId by remember { mutableStateOf<Long?>(null) }
    var formDate by remember {
        mutableStateOf(java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString())
    }
    var formClassId by remember(classes) {
        mutableLongStateOf(classes.firstOrNull()?.id ?: 0L)
    }
    LaunchedEffect(classes) {
        if (classes.isNotEmpty() && classes.none { it.id == formClassId }) {
            formClassId = classes.first().id
        }
    }
    val formClassSections = remember(sections, formClassId) {
        sections.filter { it.classId == formClassId }
    }
    var formSelectedSectionIds by remember {
        mutableStateOf<Set<Long>>(emptySet())
    }
    LaunchedEffect(formClassId, formClassSections) {
        val validIds = formClassSections.map { it.id }.toSet()
        val intersection = formSelectedSectionIds.intersect(validIds)
        formSelectedSectionIds = if (intersection.isNotEmpty()) {
            intersection
        } else if (editingPlanId == null && formClassSections.isNotEmpty()) {
            setOf(formClassSections.first().id)
        } else {
            emptySet()
        }
    }

    var formLessonTopic by remember { mutableStateOf("") }
    var formImages by remember { mutableStateOf<List<DraftPlanImageItem>>(emptyList()) }
    var formError by remember { mutableStateOf<String?>(null) }

    // Filter State for Saved Daily Plans
    var filterDate by remember { mutableStateOf("") } // empty = جميع التواريخ
    var filterClassId by remember { mutableStateOf<Long?>(null) } // null = جميع الصفوف
    var filterSectionId by remember { mutableStateOf<Long?>(null) } // null = جميع الشعب
    var searchTopicQuery by remember { mutableStateOf("") }

    val filterAvailableSections = remember(sections, filterClassId) {
        if (filterClassId == null) emptyList() else sections.filter { it.classId == filterClassId }
    }
    LaunchedEffect(filterClassId, filterAvailableSections) {
        if (filterClassId == null) {
            filterSectionId = null
        } else if (filterSectionId != null && filterAvailableSections.none { it.id == filterSectionId }) {
            filterSectionId = null
        }
    }

    val savedPlans = remember(
        refreshTick,
        localTick,
        filterDate,
        filterClassId,
        filterSectionId,
        searchTopicQuery,
        classes,
        sections
    ) {
        repository.getDailyPlans(
            filterDate = filterDate.takeIf { it.isNotBlank() },
            filterClassId = filterClassId,
            filterSectionId = filterSectionId,
            searchTopic = searchTopicQuery.takeIf { it.isNotBlank() }
        )
    }

    var previewImage by remember { mutableStateOf<DailyPlanImageEntity?>(null) }
    var pendingDeletePlan by remember { mutableStateOf<DailyPlanEntity?>(null) }

    // Multi-image picker from phone storage
    val pickMultipleImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val encoded = uris.mapNotNull { u ->
                encodeUriToCompressedBase64(context, u)?.let { (fName, b64) ->
                    DraftPlanImageItem(fileName = fName, imageBase64 = b64)
                }
            }
            if (encoded.isNotEmpty()) {
                formImages = formImages + encoded
                formError = null
            } else {
                onShowMessage("تعذر قراءة الصور المختارة")
            }
        }
    }

    fun openDatePickerDialog(initialIso: String, onPicked: (String) -> Unit) {
        val parsed = runCatching { java.time.LocalDate.parse(initialIso) }
            .getOrElse { java.time.LocalDate.now(java.time.ZoneId.systemDefault()) }
        android.app.DatePickerDialog(
            context,
            { _, year, monthZeroBased, dayOfMonth ->
                val picked = java.time.LocalDate.of(year, monthZeroBased + 1, dayOfMonth).toString()
                onPicked(picked)
            },
            parsed.year,
            parsed.monthValue - 1,
            parsed.dayOfMonth
        ).show()
    }

    fun resetFormToNew() {
        editingPlanId = null
        formDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        val firstCls = classes.firstOrNull()?.id ?: 0L
        formClassId = firstCls
        val firstSec = sections.firstOrNull { it.classId == firstCls }?.id
        formSelectedSectionIds = if (firstSec != null) setOf(firstSec) else emptySet()
        formLessonTopic = ""
        formImages = emptyList()
        formError = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Banner
        item {
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الخطة اليومية للدروس",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "قسم مستقل يدعم جميع الصفوف والشعب مع إرفاق عدد غير محدود من الصور",
                                color = Teal100,
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            color = Emerald700,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${savedPlans.size} خطة محفوظة",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Create / Edit Daily Plan Form Card
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (editingPlanId != null) Amber600 else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingPlanId != null) "تعديل الخطة اليومية" else "إضافة خطة يومية جديدة",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (editingPlanId != null) Amber700 else Slate900
                        )
                        if (editingPlanId != null) {
                            TextButton(onClick = { resetFormToNew() }) {
                                Text("إلغاء التعديل", color = Red600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (formError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Date Picker Field
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "1. التاريخ:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate800
                        )
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openDatePickerDialog(formDate) { picked ->
                                        formDate = picked
                                        formError = null
                                    }
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
                                    text = "${GradingService.getArabicDayName(formDate)} — ${GradingService.formatDisplayDate(formDate)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate900
                                )
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal700)
                            }
                        }
                    }

                    // Class Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "2. الصف الدراسي:",
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
                            classes.forEach { cls ->
                                val isSel = formClassId == cls.id
                                Surface(
                                    color = if (isSel) Slate900 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        if (formClassId != cls.id) {
                                            formClassId = cls.id
                                            val secs = sections.filter { it.classId == cls.id }
                                            formSelectedSectionIds = if (secs.isNotEmpty()) setOf(secs.first().id) else emptySet()
                                            formError = null
                                        }
                                    }
                                ) {
                                    Text(
                                        text = cls.className,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Multi-Section Selector (supports selecting one or multiple sections of the same class)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "3. الشعبة أو الشعب المشمولة بالخطة (يمكن اختيار أكثر من شعبة):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Slate800,
                                modifier = Modifier.weight(1f)
                            )
                            if (formClassSections.size > 1) {
                                val allSelected = formSelectedSectionIds.size == formClassSections.size
                                TextButton(
                                    onClick = {
                                        formSelectedSectionIds = if (allSelected) {
                                            emptySet()
                                        } else {
                                            formClassSections.map { it.id }.toSet()
                                        }
                                        formError = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (allSelected) "إلغاء تحديد الكل" else "تحديد كل الشعب",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Teal700
                                    )
                                }
                            }
                        }

                        if (formClassSections.isEmpty()) {
                            Text("لا توجد شعب مضافة لهذا الصف", fontSize = 12.sp, color = Slate500)
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                formClassSections.forEach { sec ->
                                    val isChecked = sec.id in formSelectedSectionIds
                                    Surface(
                                        color = if (isChecked) Teal700 else Slate100,
                                        contentColor = if (isChecked) Color.White else Slate800,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (isChecked) Teal700 else Slate200),
                                        modifier = Modifier.clickable {
                                            formSelectedSectionIds = if (isChecked) {
                                                formSelectedSectionIds - sec.id
                                            } else {
                                                formSelectedSectionIds + sec.id
                                            }
                                            formError = null
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "شعبة ${sec.sectionName}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Lesson Topic Text Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "4. عنوان الدرس أو الموضوع:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate800
                        )
                        OutlinedTextField(
                            value = formLessonTopic,
                            onValueChange = {
                                formLessonTopic = it
                                formError = null
                            },
                            placeholder = { Text("اكتب عنوان الدرس، الموضوع، أو تفاصيل الخطة اليومية...") },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Unlimited Images Attachment Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "5. صور الدرس المرفقة (${formImages.size} صورة):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate800
                                )
                                Text(
                                    text = "يمكنك إرفاق عدة صور للدرس الواحد من الهاتف ومعاينتها أو حذف أي صورة",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                            OutlinedButton(
                                onClick = { pickMultipleImagesLauncher.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Teal700)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Teal700, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة صور", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Teal700)
                            }
                        }

                        if (formImages.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                formImages.forEachIndexed { idx, draftImg ->
                                    val tempEntity = DailyPlanImageEntity(
                                        id = idx.toLong(),
                                        planId = editingPlanId ?: 0L,
                                        fileName = draftImg.fileName,
                                        imageBase64 = draftImg.imageBase64,
                                        sortOrder = idx,
                                        createdDate = ""
                                    )
                                    DailyPlanThumbnailItem(
                                        imageEntity = tempEntity,
                                        onClick = { previewImage = tempEntity },
                                        onDelete = {
                                            formImages = formImages.filterIndexed { i, _ -> i != idx }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            val orderedSecIds = formClassSections
                                .filter { it.id in formSelectedSectionIds }
                                .map { it.id }
                            val (_, err) = repository.saveDailyPlan(
                                context = context,
                                existingPlanId = editingPlanId,
                                planDate = formDate,
                                classId = formClassId,
                                sectionIds = orderedSecIds,
                                lessonTopic = formLessonTopic,
                                images = formImages.map { it.fileName to it.imageBase64 }
                            )
                            if (err != null) {
                                formError = err
                            } else {
                                val wasEditing = editingPlanId != null
                                resetFormToNew()
                                localTick++
                                onDataChanged()
                                onShowMessage(
                                    if (wasEditing) "تم حفظ تعديلات الخطة اليومية بنجاح"
                                    else "تم حفظ الخطة اليومية في قاعدة البيانات بنجاح"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingPlanId != null) Amber700 else Emerald600
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editingPlanId != null) "حفظ التعديلات على الخطة" else "حفظ الخطة اليومية",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. Search & Filters Card for Saved Plans
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الفلترة والبحث في الخطط المحفوظة",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Slate900
                        )
                        if (filterDate.isNotBlank() || filterClassId != null || filterSectionId != null || searchTopicQuery.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    filterDate = ""
                                    filterClassId = null
                                    filterSectionId = null
                                    searchTopicQuery = ""
                                }
                            ) {
                                Text("إعادة ضبط الفلاتر", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Teal700)
                            }
                        }
                    }

                    // Search by Lesson Topic
                    OutlinedTextField(
                        value = searchTopicQuery,
                        onValueChange = { searchTopicQuery = it },
                        placeholder = { Text("بحث في عنوان الدرس أو الموضوع...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Teal700) },
                        trailingIcon = {
                            if (searchTopicQuery.isNotBlank()) {
                                IconButton(onClick = { searchTopicQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Filter by Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("التاريخ:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate800)
                        Surface(
                            color = if (filterDate.isBlank()) Slate900 else Slate100,
                            contentColor = if (filterDate.isBlank()) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { filterDate = "" }
                        ) {
                            Text(
                                text = "الكل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                        Surface(
                            color = if (filterDate.isNotBlank()) Teal700 else Slate100,
                            contentColor = if (filterDate.isNotBlank()) Color.White else Slate800,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                val init = filterDate.ifBlank {
                                    java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
                                }
                                openDatePickerDialog(init) { picked -> filterDate = picked }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(
                                    text = if (filterDate.isNotBlank()) GradingService.formatDisplayDate(filterDate) else "تحديد تاريخ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Filter by Class
                    Text("الصف:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate800)
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

                    // Filter by Section (when Class is selected)
                    if (filterClassId != null && filterAvailableSections.isNotEmpty()) {
                        Text("الشعبة:", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Slate800)
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
                            filterAvailableSections.forEach { sec ->
                                val isSel = filterSectionId == sec.id
                                Surface(
                                    color = if (isSel) Teal700 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { filterSectionId = sec.id }
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
                    }
                }
            }
        }

        // 4. Saved Daily Plans List
        if (savedPlans.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد خطط يومية محفوظة تطابق البحث أو الفلتر المحدد حالياً.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(savedPlans, key = { it.id }) { plan ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${GradingService.getArabicDayName(plan.planDate)} — ${GradingService.formatDisplayDate(plan.planDate)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Teal700
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(color = Slate900, shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = plan.className,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Surface(
                                        color = Teal50,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Teal600)
                                    ) {
                                        Text(
                                            text = "الشعب: ${plan.sectionNames.joinToString("، ")}",
                                            color = Teal700,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        editingPlanId = plan.id
                                        formDate = plan.planDate
                                        formClassId = plan.classId
                                        formSelectedSectionIds = plan.sectionIds.toSet()
                                        formLessonTopic = plan.lessonTopic
                                        formImages = plan.images.map {
                                            DraftPlanImageItem(fileName = it.fileName, imageBase64 = it.imageBase64)
                                        }
                                        formError = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تعديل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { pendingDeletePlan = plan },
                                    border = BorderStroke(1.dp, Red600),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red600, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حذف", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Red700)
                                }
                            }
                        }

                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = plan.lessonTopic,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        if (plan.images.isNotEmpty()) {
                            Text(
                                text = "الصور المرفقة (${plan.images.size}) — اضغط على أي صورة لعرضها بالحجم الكامل:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                plan.images.forEach { img ->
                                    DailyPlanThumbnailItem(
                                        imageEntity = img,
                                        onClick = { previewImage = img }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewImage != null) {
        DailyPlanFullScreenImageDialog(
            imageEntity = previewImage!!,
            onDismiss = { previewImage = null }
        )
    }

    if (pendingDeletePlan != null) {
        val target = pendingDeletePlan!!
        AlertDialog(
            onDismissRequest = { pendingDeletePlan = null },
            title = {
                Text("تأكيد حذف الخطة اليومية", fontWeight = FontWeight.ExtraBold, color = Red700)
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف الخطة اليومية لتاريخ ${GradingService.formatDisplayDate(target.planDate)} (${target.className} — الشعب: ${target.sectionNames.joinToString("، ")})؟\nسيتم حذف الخطة وجميع الصور المرفقة بها.",
                    fontSize = 13.sp,
                    color = Slate800
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteDailyPlan(context, target.id)
                        if (editingPlanId == target.id) {
                            resetFormToNew()
                        }
                        pendingDeletePlan = null
                        localTick++
                        onDataChanged()
                        onShowMessage("تم حذف الخطة اليومية بنجاح")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("تأكيد الحذف", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePlan = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }
}
