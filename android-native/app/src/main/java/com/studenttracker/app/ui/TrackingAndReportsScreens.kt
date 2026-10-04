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
    isDirty: Boolean,
    onDirtyChange: (Boolean) -> Unit,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onSelectDate: (String) -> Unit,
    onOpenStudentProfile: (Long) -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val students = remember(selectedClassId, selectedSectionId, classes, sections) {
        repository.getStudents(selectedClassId, selectedSectionId, includeInactive = false)
    }
    var hasSaved by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(repository.hasSavedSession(selectedClassId, selectedSectionId, selectedDate))
    }
    var isEditingSaved by remember(selectedClassId, selectedSectionId, selectedDate) {
        mutableStateOf(!hasSaved)
    }

    var entriesMap by remember(selectedClassId, selectedSectionId, selectedDate, students) {
        val saved = repository.getSessionEntries(selectedClassId, selectedSectionId, selectedDate)
        val initial = mutableMapOf<Long, StudentDailyEntry>()
        students.forEach { st ->
            initial[st.id] = saved[st.id] ?: StudentDailyEntry(studentId = st.id)
        }
        mutableStateOf<Map<Long, StudentDailyEntry>>(initial)
    }

    val dayStatus = remember(selectedDate, holidays) {
        GradingService.checkDateStatus(selectedDate, holidays)
    }

    val liveGradesMap = remember(selectedClassId, selectedSectionId, selectedDate, entriesMap, holidays) {
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
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                    text = "لإجراء أي تعديل على هذا اليوم اضغط على زر تعديل السجل",
                                    fontSize = 11.sp,
                                    color = Slate700
                                )
                            }
                            Button(
                                onClick = { isEditingSaved = true },
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

            items(students, key = { it.id }) { st ->
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

                        // Attendance Present / Absent Toggle
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
                                    .clickable(enabled = canEdit) {
                                        entriesMap = entriesMap.toMutableMap().apply {
                                            put(st.id, entry.copy(attendance = AttendanceStatus.PRESENT))
                                        }
                                        onDirtyChange(true)
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
                                    .clickable(enabled = canEdit) {
                                        entriesMap = entriesMap.toMutableMap().apply {
                                            put(
                                                st.id,
                                                entry.copy(
                                                    attendance = AttendanceStatus.ABSENT,
                                                    participation = null,
                                                    notebook = null,
                                                    homework = null,
                                                    behavior = null,
                                                    dailyTestScore = null
                                                )
                                            )
                                        }
                                        onDirtyChange(true)
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
                            hasSaved = true
                            isEditingSaved = false
                            onDirtyChange(false)
                            onDataChanged()
                            onShowMessage("تم حفظ السجل اليومي (${GradingService.formatDisplayDate(selectedDate)}) بنجاح")
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
                        onShowMessage("يرجى إدخال التاريخ بصيغة YYYY-MM-DD")
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
    onBack: () -> Unit
) {
    var selectedMonth by remember { mutableStateOf("ALL") }
    val allGrades = remember(studentId, selectedMonth) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            monthPrefix = selectedMonth
        )
    }
    val grade = allGrades.find { it.studentId == studentId }

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
        item {
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = grade.studentName,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${grade.className} — شعبة ${grade.sectionName}",
                                color = Teal100,
                                fontSize = 13.sp
                            )
                        }
                        Surface(color = Teal700, shape = RoundedCornerShape(12.dp)) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${GradingService.formatScore(grade.totalScore)} / 100",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text("الدرجة النهائية", color = Teal100, fontSize = 10.sp)
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
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onOpenStudentProfile: (Long) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var sortMode by remember { mutableStateOf("HIGHEST") }

    val grades = remember(selectedClassId, selectedSectionId, sortMode, classes, sections) {
        val raw = GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = selectedClassId,
            sectionId = selectedSectionId
        )
        when (sortMode) {
            "HIGHEST" -> raw.sortedByDescending { it.totalScore }
            "LOWEST" -> raw.sortedBy { it.totalScore }
            else -> raw.sortedBy { it.studentName }
        }
    }

    val exportResultsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = ExcelAndBackupService.generateResultsXlsx(grades)
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                onShowMessage("تم تصدير النتائج إلى ملف Excel بنجاح")
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
                    onClick = { exportResultsLauncher.launch("نتائج_الطلاب_اليومي.xlsx") },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تصدير Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(grades, key = { it.studentId }) { g ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStudentProfile(g.studentId) }
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = g.studentName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Surface(color = Teal50, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "${GradingService.formatScore(g.totalScore)} / 100",
                                color = Teal700,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "الحضور: ${GradingService.formatScore(g.attendanceScore)}/19 | المشاركة: ${GradingService.formatScore(g.participationScore)}/19 | الدفتر: ${GradingService.formatScore(g.notebookScore)}/19 | الواجب: ${GradingService.formatScore(g.homeworkScore)}/19 | السلوك: ${GradingService.formatScore(g.behaviorScore)}/19 | الاختبار: ${GradingService.formatScore(g.quizScore)}/5",
                        fontSize = 11.sp,
                        color = Slate600
                    )
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
    var backups by remember { mutableStateOf(ExcelAndBackupService.listBackups(context)) }

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
            }.onFailure {
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
                onShowMessage("تمت استعادة النسخة الاحتياطية بنجاح (مع حفظ نسخة أمان تلقائية)")
            }.onFailure { e ->
                onShowMessage(e.message ?: "تعذر استعادة النسخة الاحتياطية")
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. مركز Excel (قالب • استيراد الطلاب • تصدير النتائج)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
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

        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. النسخ الاحتياطي والاستعادة (SQLite)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
                    Text("قبل أي عملية استعادة، يقوم التطبيق تلقائياً بإنشاء نسخة احتياطية آمنة للبيانات الحالية.", fontSize = 12.sp, color = Slate600)

                    Button(
                        onClick = {
                            ExcelAndBackupService.createLocalBackup(context, dbHelper, isAutoSafety = false)
                            backups = ExcelAndBackupService.listBackups(context)
                            onShowMessage("تم إنشاء نسخة احتياطية محلية بنجاح")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إنشاء نسخة احتياطية داخلية الآن", fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportDbLauncher.launch("student_tracker_backup.db") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ ملف .db للهاتف", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { restoreDbLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("استعادة من ملف .db", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900)
                                    Text("${b.sizeBytes / 1024} KB", fontSize = 11.sp, color = Slate600)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            ExcelAndBackupService.restoreBackup(context, dbHelper, b)
                                            backups = ExcelAndBackupService.listBackups(context)
                                            onDataChanged()
                                            onShowMessage("تمت استعادة النسخة الاحتياطية بنجاح")
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                                    ) {
                                        Text("استعادة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = {
                                        ExcelAndBackupService.deleteBackup(b)
                                        backups = ExcelAndBackupService.listBackups(context)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red600)
                                    }
                                }
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
                    Text("3. إدارة البيانات التجريبية (Demo)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                dbHelper.resetDatabase(includeDemo = false)
                                onDataChanged()
                                onShowMessage("تم مسح البيانات التجريبية والبدء بقاعدة بيانات فارغة")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("بدء فعلي فارغ", fontSize = 12.sp, color = Red700, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                dbHelper.resetDatabase(includeDemo = true)
                                onDataChanged()
                                onShowMessage("تمت استعادة البيانات التجريبية الافتراضية")
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
