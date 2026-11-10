package com.studenttracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import com.studenttracker.app.service.GradingService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyGradesScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    repository: TrackerRepository,
    dbHelper: AppDatabaseHelper,
    refreshTick: Int,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val periods = remember(refreshTick) {
        repository.getStudyPeriods().sortedWith(
            compareBy<StudyPeriodEntity> { it.academicYearId }
                .thenBy { it.stageType.stageOrder }
                .thenBy { it.sequenceNumber }
                .thenBy { it.startDate }
        )
    }
    var selectedPeriodId by remember(periods) { mutableStateOf(periods.firstOrNull()?.id) }
    val selectedPeriod = periods.firstOrNull { it.id == selectedPeriodId }
    val students = remember(refreshTick, selectedClassId, selectedSectionId) {
        repository.getStudents(selectedClassId, selectedSectionId, includeInactive = false)
    }
    var searchQuery by remember(selectedClassId, selectedSectionId) { mutableStateOf("") }
    val filteredStudents = remember(students, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) students
        else students.filter { it.studentName.contains(query, ignoreCase = true) }
    }
    val savedGrades = remember(refreshTick, selectedPeriodId) {
        selectedPeriodId?.let { repository.getPeriodGrades(periodId = it).associateBy { grade -> grade.studentId } } ?: emptyMap()
    }
    val calculatedDailyGrades = remember(refreshTick, selectedClassId, selectedSectionId, selectedPeriodId) {
        val id = selectedPeriodId
        if (id == null) emptyMap()
        else GradingService.calculateGradesForClass(
            dbHelper = dbHelper, repository = repository, classId = selectedClassId,
            sectionId = selectedSectionId, monthPrefix = "PERIOD:$id"
        ).associate { it.studentId to it.totalScore }
    }
    val manualDailyStudents = remember(refreshTick, selectedPeriodId) {
        selectedPeriodId?.let { repository.getManualPeriodDailyGradeStudentIds(it) } ?: emptySet()
    }
    val gradeInputs = remember(refreshTick, selectedPeriodId, students, savedGrades) {
        mutableStateMapOf<Long, String>().apply {
            students.forEach { student ->
                this[student.id] = savedGrades[student.id]?.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
            }
        }
    }
    val dailyInputs = remember(refreshTick, selectedPeriodId, students, savedGrades, calculatedDailyGrades, manualDailyStudents) {
        mutableStateMapOf<Long, String>().apply {
            students.forEach { student ->
                val daily = if (manualDailyStudents.contains(student.id)) {
                    savedGrades[student.id]?.dailyGrade
                } else {
                    calculatedDailyGrades[student.id] ?: savedGrades[student.id]?.dailyGrade
                }
                this[student.id] = daily?.let { GradingService.formatScore(it) } ?: ""
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(color = Teal50, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Teal100), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("الدرجات الشهرية", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text("إدخال درجة الامتحان التحريري مباشرة لجميع طلبة الصف والشعبة المحددين", fontSize = 12.sp, color = Slate600)
                }
            }
        }
        item {
            ClassSectionFilterBar(classes, sections, selectedClassId, selectedSectionId, onSelectClass, onSelectSection)
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الفترة الدراسية", fontWeight = FontWeight.Bold, color = Slate700, fontSize = 13.sp)
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedPeriod?.let { "${it.periodName} — ${it.stageType.arabicLabel}" } ?: "لا توجد فترات دراسية",
                            onValueChange = {}, readOnly = true, enabled = periods.isNotEmpty(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            periods.forEach { period ->
                                DropdownMenuItem(
                                    text = { Text("${period.periodName} — ${period.stageType.arabicLabel}") },
                                    onClick = { selectedPeriodId = period.id; expanded = false }
                                )
                            }
                        }
                    }
                    Text("الدرجة من 100، وتحفظ في نفس سجل الامتحان التحريري المستخدم في قسم النتائج", fontSize = 11.sp, color = Slate500)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("قائمة الطلبة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                Text("${filteredStudents.size} من ${students.size} طالب", fontSize = 12.sp, color = Slate600)
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ابحث عن اسم الطالب...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { searchQuery = "" }) { Text("مسح") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
        if (selectedPeriod == null) {
            item {
                Surface(color = Amber50, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("أضف فترة دراسية أولاً من قسم الفترات الدراسية حتى تتمكن من إدخال الدرجات", modifier = Modifier.padding(14.dp), color = Amber700, fontSize = 13.sp)
                }
            }
        } else if (students.isEmpty()) {
            item { Text("لا يوجد طلبة في الصف والشعبة المحددين", color = Slate600, modifier = Modifier.padding(12.dp)) }
        } else if (filteredStudents.isEmpty()) {
            item { Text("ماكو طالب بهذا الاسم", color = Slate600, modifier = Modifier.padding(12.dp)) }
        } else {
            items(filteredStudents, key = { it.id }) { student ->
                val savedGrade = savedGrades[student.id]
                var saving by remember(student.id, selectedPeriodId) { mutableStateOf(false) }
                val isMidYear = selectedPeriod.stageType == AcademicStage.MID_YEAR
                val writtenValue = gradeInputs[student.id].orEmpty()
                val dailyValue = dailyInputs[student.id].orEmpty()
                val liveAverage = if (isMidYear) {
                    writtenValue.toDoubleOrNull()
                } else {
                    val d = dailyValue.toDoubleOrNull()
                    val w = writtenValue.toDoubleOrNull()
                    if (d != null && w != null) (d + w) / 2.0 else null
                }
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(student.studentName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Slate900, fontSize = 14.sp)
                            if (savedGrade?.writtenExamGrade != null) {
                                AssistChip(onClick = {}, label = { Text("محفوظة", fontSize = 10.sp) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) })
                            }
                        }
                        if (!isMidYear) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = dailyValue,
                                    onValueChange = { raw ->
                                        if (raw.isEmpty() || raw.matches(Regex("^\\d{0,3}([.]\\d{0,2})?$"))) {
                                            val n = raw.toDoubleOrNull()
                                            if (n == null || n <= 100.0) dailyInputs[student.id] = raw
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("درجة اليومي") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                OutlinedTextField(
                                    value = writtenValue,
                                    onValueChange = { raw ->
                                        if (raw.isEmpty() || raw.matches(Regex("^\\d{0,3}([.]\\d{0,2})?$"))) {
                                            val n = raw.toDoubleOrNull()
                                            if (n == null || n <= 100.0) gradeInputs[student.id] = raw
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("الامتحان التحريري") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (manualDailyStudents.contains(student.id)) "اليومي معدّل يدوياً" else "اليومي محسوب تلقائياً",
                                    color = if (manualDailyStudents.contains(student.id)) Teal700 else Slate500,
                                    fontSize = 11.sp
                                )
                                TextButton(
                                    enabled = !saving && manualDailyStudents.contains(student.id),
                                    onClick = {
                                        val calculated = calculatedDailyGrades[student.id] ?: 0.0
                                        val error = repository.setPeriodDailyGrade(student.id, selectedPeriod.id, calculated, manual = false)
                                        if (error == null) {
                                            dailyInputs[student.id] = GradingService.formatScore(calculated)
                                            onShowMessage("تمت استعادة درجة اليومي المحسوبة تلقائياً")
                                            onDataChanged()
                                        } else onShowMessage(error)
                                    }
                                ) { Text("استعادة التلقائي") }
                            }
                        } else {
                            OutlinedTextField(
                                value = writtenValue,
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw.matches(Regex("^\\d{0,3}([.]\\d{0,2})?$"))) {
                                        val n = raw.toDoubleOrNull()
                                        if (n == null || n <= 100.0) gradeInputs[student.id] = raw
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("درجة امتحان نصف السنة من 100") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "المعدل: ${liveAverage?.let { GradingService.formatScore(it) } ?: "—"}",
                                modifier = Modifier.weight(1f),
                                color = Slate700,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                enabled = !saving,
                                onClick = {
                                    val writtenRaw = gradeInputs[student.id].orEmpty().trim()
                                    val written = if (writtenRaw.isEmpty()) null else writtenRaw.toDoubleOrNull()
                                    val daily = if (isMidYear) 0.0 else dailyInputs[student.id].orEmpty().trim().toDoubleOrNull()
                                    val invalidWritten = writtenRaw.isNotEmpty() && (written == null || written !in 0.0..100.0)
                                    val invalidDaily = !isMidYear && (daily == null || daily !in 0.0..100.0)
                                    if (invalidDaily) {
                                        onShowMessage("درجة اليومي للطالب ${student.studentName} يجب أن تكون بين 0 و 100")
                                    } else if (invalidWritten) {
                                        onShowMessage("درجة التحريري للطالب ${student.studentName} يجب أن تكون بين 0 و 100")
                                    } else {
                                        saving = true
                                        val calculatedDaily = calculatedDailyGrades[student.id] ?: 0.0
                                        val dailyWasManuallyChanged = !isMidYear && (
                                            manualDailyStudents.contains(student.id) ||
                                            kotlin.math.abs(daily!! - calculatedDaily) > 0.001
                                        )
                                        val dailyError = if (dailyWasManuallyChanged) repository.setPeriodDailyGrade(
                                            student.id, selectedPeriod.id, daily!!, manual = true
                                        ) else null
                                        val error = dailyError ?: repository.upsertPeriodGrade(
                                            student.id, selectedPeriod.id,
                                            if (isMidYear) 0.0 else daily!!, written
                                        )
                                        saving = false
                                        if (error == null) {
                                            onShowMessage("تم حفظ درجات ${student.studentName}")
                                            onDataChanged()
                                        } else onShowMessage(error)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("حفظ")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
