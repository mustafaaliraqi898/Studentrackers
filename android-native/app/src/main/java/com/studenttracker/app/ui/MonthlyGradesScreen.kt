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
    val gradeInputs = remember(refreshTick, selectedPeriodId, students, savedGrades) {
        mutableStateMapOf<Long, String>().apply {
            students.forEach { student ->
                this[student.id] = savedGrades[student.id]?.writtenExamGrade?.let { GradingService.formatScore(it) } ?: ""
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
                Text("${students.size} طالب", fontSize = 12.sp, color = Slate600)
            }
        }
        if (selectedPeriod == null) {
            item {
                Surface(color = Amber50, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("أضف فترة دراسية أولاً من قسم الفترات الدراسية حتى تتمكن من إدخال الدرجات", modifier = Modifier.padding(14.dp), color = Amber700, fontSize = 13.sp)
                }
            }
        } else if (students.isEmpty()) {
            item { Text("لا يوجد طلبة في الصف والشعبة المحددين", color = Slate600, modifier = Modifier.padding(12.dp)) }
        } else {
            items(students, key = { it.id }) { student ->
                val saved = savedGrades[student.id]?.writtenExamGrade
                var saving by remember(student.id, selectedPeriodId) { mutableStateOf(false) }
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(student.studentName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Slate900, fontSize = 14.sp)
                            if (saved != null) {
                                AssistChip(onClick = {}, label = { Text("محفوظة", fontSize = 10.sp) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = gradeInputs[student.id].orEmpty(),
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw.matches(Regex("^\\d{0,3}([.]\\d{0,2})?$"))) {
                                        val n = raw.toDoubleOrNull()
                                        if (n == null || n <= 100.0) gradeInputs[student.id] = raw
                                    }
                                },
                                modifier = Modifier.weight(1f), label = { Text("الدرجة من 100") }, placeholder = { Text("—") },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            Button(
                                enabled = !saving,
                                onClick = {
                                    val raw = gradeInputs[student.id].orEmpty().trim()
                                    val grade = if (raw.isEmpty()) null else raw.toDoubleOrNull()
                                    if (raw.isNotEmpty() && (grade == null || grade !in 0.0..100.0)) {
                                        onShowMessage("درجة ${student.studentName}: أدخل رقماً من 0 إلى 100")
                                    } else {
                                        saving = true
                                        val daily = if (selectedPeriod.stageType == AcademicStage.MID_YEAR) 0.0 else calculatedDailyGrades[student.id] ?: 0.0
                                        val error = repository.upsertPeriodGrade(student.id, selectedPeriod.id, daily, grade)
                                        saving = false
                                        if (error == null) {
                                            onShowMessage(if (grade == null) "تم مسح درجة ${student.studentName}" else "تم حفظ درجة ${student.studentName}")
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
