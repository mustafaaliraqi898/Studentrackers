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
    onShowMessage: (String) -> Unit
) {
    val students = remember(refreshTick, selectedClassId, selectedSectionId, classes, sections) {
        repository.getStudents(selectedClassId, selectedSectionId, includeInactive = false)
    }
    var hasSaved by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(repository.hasSavedSession(selectedClassId, selectedSectionId, selectedDate))
    }
    var isEditingSaved by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(!hasSaved)
    }

    var savedEntriesSnapshot by remember(selectedClassId, selectedSectionId, selectedDate, students) {
        mutableStateOf(repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate))
    }

    var entriesMap by remember(selectedClassId, selectedSectionId, selectedDate, students) {
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
            currentDraftEntries = entriesMap
        )
        entriesMap = updatedEntries
        savedEntriesSnapshot = repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate)
        hasSaved = true
        if (targetStatus == AttendanceStatus.ABSENT) {
            explicitlyTouchedPresentIds = explicitlyTouchedPresentIds - student.id
            showTopNotification(
                msg = "تم تسجيل الغياب للطالب (${student.studentName}) وحفظه في قاعدة البيانات",
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

    val liveGradesMap = remember(refreshTick, selectedClassId, selectedSectionId, selectedDate, entriesMap, holidays) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = selectedClassId,
            sectionId = selectedSectionId,
            liveOverrideDate = if (dayStatus.isNonWorkingDay) null else selectedDate,
            liveOverrideEntries = if (dayStatus.isNonWorkingDay) null else entriesMap
        ).associateBy { it.studentId }
    }

    var showDateDialog by remember { mutableStateOf(false) }
    var tempDateInput by remember(selectedDate) { mutableStateOf(selectedDate) }

    val canEdit = !dayStatus.isNonWorkingDay && isEditingSaved

    Column(modifier = Modifier.fillMaxSize()) {
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

            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "تاريخ المتابعة اليومية",
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
                            OutlinedButton(onClick = {
                                tempDateInput = selectedDate
                                showDateDialog = true
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تغيير التاريخ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickDates = listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-08")
                            quickDates.forEach { d ->
                                val isSel = d == selectedDate
                                Surface(
                                    color = if (isSel) Slate900 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { onSelectDate(d) }
                                ) {
                                    Text(
                                        text = "${GradingService.getArabicDayName(d)} ${d.substring(8)}",
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

            if (!dayStatus.isNonWorkingDay) {
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
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث عن طالب بالاسم لتسجيله غائباً مباشرة...") },
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
                                                                else -> "الحالة الحالية: غير مسجل / حاضر افتراضياً (19 / 19)"
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

            items(filteredStudents, key = { it.id }) { st ->
                val entry = entriesMap[st.id] ?: StudentDailyEntry(studentId = st.id)
                val grade = liveGradesMap[st.id]
                val isPresent = entry.attendance == AttendanceStatus.PRESENT

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (isPresent) Slate200 else Red100),
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
                                if (grade != null) {
                                    Text(
                                        text = "حضور ${GradingService.formatScore(grade.attendanceScore)} | مشاركة ${GradingService.formatScore(grade.participationScore)} | دفتر ${GradingService.formatScore(grade.notebookScore)} | واجب ${GradingService.formatScore(grade.homeworkScore)} | سلوك ${GradingService.formatScore(grade.behaviorScore)} | اختبار ${GradingService.formatScore(grade.quizScore)}",
                                        fontSize = 11.sp,
                                        color = Slate600
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
                                            fontSize = 14.sp,
                                            color = Teal700
                                        )
                                        Text(
                                            text = "الدرجة اليومية",
                                            fontSize = 10.sp,
                                            color = Teal700
                                        )
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

                        if (isPresent) {
                            val evalEnabled = canEdit && isPresent
                            ThreeStateRatingRow(
                                label = "المشاركة",
                                currentValue = entry.participation,
                                enabled = evalEnabled,
                                scorePreviewText = "(${grade?.let { GradingService.formatScore(it.participationScore) } ?: "19"} / 19)",
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
                                scorePreviewText = "(${grade?.let { GradingService.formatScore(it.notebookScore) } ?: "19"} / 19)",
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
                                scorePreviewText = "(${grade?.let { GradingService.formatScore(it.homeworkScore) } ?: "19"} / 19)",
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
                                scorePreviewText = "(${grade?.let { GradingService.formatScore(it.behaviorScore) } ?: "19"} / 19)",
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
                                            "لا يوجد اختبار مسجل اليوم"
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
        if (!dayStatus.isNonWorkingDay) {
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
                            repository.saveDailySession(
                                classId = selectedClassId,
                                sectionId = selectedSectionId,
                                recordDate = selectedDate,
                                entries = entriesMap
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
// SAVED RECORDS SCREEN
// ============================================================================
@Composable
fun SavedRecordsScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    savedSessions: List<SavedSessionSummary>,
    onOpenSavedSession: (Long, Long, String) -> Unit
) {
    var filterClassId by remember { mutableStateOf<Long?>(null) }
    val filtered = remember(savedSessions, filterClassId) {
        if (filterClassId == null) savedSessions else savedSessions.filter { it.classId == filterClassId }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = if (filterClassId == null) Slate900 else Slate100,
                    contentColor = if (filterClassId == null) Color.White else Slate800,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { filterClassId = null }
                ) {
                    Text(
                        text = "جميع الصفوف (${savedSessions.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                classes.forEach { c ->
                    val isSel = filterClassId == c.id
                    Surface(
                        color = if (isSel) Slate900 else Slate100,
                        contentColor = if (isSel) Color.White else Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { filterClassId = c.id }
                    ) {
                        Text(
                            text = c.className,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        items(filtered) { s ->
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
                        Column {
                            Text(
                                text = "${GradingService.getArabicDayName(s.recordDate)} — ${GradingService.formatDisplayDate(s.recordDate)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "${s.className} • شعبة ${s.sectionName}",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                        Button(
                            onClick = { onOpenSavedSession(s.classId, s.sectionId, s.recordDate) },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                        ) {
                            Text("فتح / تعديل", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("الطلاب: ${s.studentCount}", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.Bold)
                        Text("حضور: ${s.presentCount}", fontSize = 12.sp, color = Emerald700, fontWeight = FontWeight.Bold)
                        Text("غياب: ${s.absentCount}", fontSize = 12.sp, color = Red700, fontWeight = FontWeight.Bold)
                        Text("تقييمات: ${s.evaluationsCount}", fontSize = 12.sp, color = Teal700, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
    var selectedFilter by remember { mutableStateOf("ALL") }

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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Teal700, shape = RoundedCornerShape(12.dp)) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${GradingService.formatScore(grade.totalScore)} / 100",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text("الدرجة اليومية", color = Teal100, fontSize = 10.sp)
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

                    // Filter chips for Daily Details: All + StudyPeriods
                    if (studyPeriods.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isAll = selectedFilter == "ALL"
                            Surface(
                                color = if (isAll) Emerald600 else Slate800,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedFilter = "ALL" }
                            ) {
                                Text(
                                    text = "كامل السجلات",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            studyPeriods.forEach { sp ->
                                val key = "PERIOD:${sp.id}"
                                val isSel = selectedFilter == key
                                Surface(
                                    color = if (isSel) Emerald600 else Slate800,
                                    contentColor = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { selectedFilter = key }
                                ) {
                                    Text(
                                        text = sp.periodName,
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

                    val breakdowns = comprehensiveReport?.periodBreakdowns ?: emptyList()
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
                                                    text = period.periodName,
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
                                                text = if (bd.writtenExamGrade != null) "تعديل التحريري" else "إدخال التحريري",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }

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

                        // Final Average Summary Bar
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

    // Written Exam Entry/Edit Dialog
    if (editingPeriodBreakdown != null) {
        val bd = editingPeriodBreakdown!!
        AlertDialog(
            onDismissRequest = { editingPeriodBreakdown = null },
            title = {
                Text(
                    text = "درجة الامتحان التحريري — ${bd.period.periodName}",
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
                    Text(
                        text = "الدرجة اليومية المحسوبة لهذه الفترة: ${GradingService.formatScore(bd.dailyGrade)} / 100",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Teal700
                    )

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
                        label = { Text("درجة الامتحان التحريري (من 0 إلى 100)") },
                        placeholder = { Text("مثال: 70") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedPreview = writtenExamInput.trim().toDoubleOrNull()
                    if (parsedPreview != null && parsedPreview in 0.0..100.0) {
                        val previewAvg = GradingService.round2((bd.dailyGrade + parsedPreview) / 2.0)
                        Surface(
                            color = Emerald50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                            writtenExamError = "يجب أن تكون درجة الامتحان التحريري بين 0 و 100"
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
                                    "تم حفظ درجة الامتحان التحريري وحساب معدل ${bd.period.periodName} في قاعدة البيانات"
                                } else {
                                    "تم مسح درجة الامتحان التحريري لـ ${bd.period.periodName}"
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
                                onShowMessage("تم مسح درجة الامتحان التحريري لـ ${bd.period.periodName}")
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
                            Surface(
                                color = if (isSel) Emerald700 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedPeriodFilterId = sp.id }
                            ) {
                                Text(
                                    text = "${sp.periodName} (${sp.status.arabicLabel})",
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.1f)) {
                                        Text(
                                            text = bd.period.periodName,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "يومي: ${GradingService.formatScore(bd.dailyGrade)} | تحريري: ${bd.writtenExamGrade?.let { GradingService.formatScore(it) } ?: "--"} | معدل: ${bd.periodAverage?.let { GradingService.formatScore(it) } ?: "--"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate700
                                        )
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
                                            text = if (bd.writtenExamGrade != null) "تعديل التحريري" else "إدخال التحريري",
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
        AlertDialog(
            onDismissRequest = {
                editingReportStudent = null
                editingPeriodBreakdown = null
            },
            title = {
                Text(
                    text = "درجة الامتحان التحريري — ${bd.period.periodName}",
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
                    Text(
                        text = "الدرجة اليومية المحسوبة لهذه الفترة: ${GradingService.formatScore(bd.dailyGrade)} / 100",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Teal700
                    )

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
                        label = { Text("درجة الامتحان التحريري (من 0 إلى 100)") },
                        placeholder = { Text("مثال: 70") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val parsedPreview = writtenExamInput.trim().toDoubleOrNull()
                    if (parsedPreview != null && parsedPreview in 0.0..100.0) {
                        val previewAvg = GradingService.round2((bd.dailyGrade + parsedPreview) / 2.0)
                        Surface(
                            color = Emerald50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                            writtenExamError = "يجب أن تكون درجة الامتحان التحريري بين 0 و 100"
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
                                    "تم حفظ درجة الامتحان التحريري للطالب (${targetStudent.studentName}) في ${bd.period.periodName}"
                                } else {
                                    "تم مسح درجة الامتحان التحريري للطالب (${targetStudent.studentName}) في ${bd.period.periodName}"
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
