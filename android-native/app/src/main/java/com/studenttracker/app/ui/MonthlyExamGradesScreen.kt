package com.studenttracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import com.studenttracker.app.service.GradingService

@Composable
fun MonthlyExamGradesScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val years = remember(refreshTick) { repository.getAcademicYears() }
    var selectedYearId by remember(years) {
        mutableLongStateOf(years.firstOrNull { it.isActive }?.id ?: years.firstOrNull()?.id ?: 1L)
    }
    val stageOptions = listOf(AcademicStage.FIRST_HALF, AcademicStage.MID_YEAR, AcademicStage.SECOND_HALF)
    var selectedStage by remember { mutableStateOf(AcademicStage.FIRST_HALF) }

    val classSections = remember(sections, selectedClassId) { sections.filter { it.classId == selectedClassId } }
    LaunchedEffect(classSections, selectedSectionId) {
        if (classSections.isNotEmpty() && classSections.none { it.id == selectedSectionId }) onSelectSection(classSections.first().id)
    }
    val periods = remember(refreshTick, selectedYearId, selectedStage) {
        repository.getStudyPeriods(academicYearId = selectedYearId, stageType = selectedStage)
    }
    var selectedPeriodId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(periods) {
        selectedPeriodId = periods.firstOrNull { it.id == selectedPeriodId }?.id ?: periods.firstOrNull()?.id
    }

    val students = remember(refreshTick, selectedClassId, selectedSectionId) {
        repository.getStudents(classId = selectedClassId, sectionId = selectedSectionId, includeInactive = false)
    }
    val storedGrades = remember(refreshTick, selectedPeriodId) {
        selectedPeriodId?.let { repository.getPeriodGrades(periodId = it).associateBy { row -> row.studentId } } ?: emptyMap()
    }
    val dailyGrades = remember(refreshTick, selectedPeriodId, selectedClassId, selectedSectionId, selectedStage) {
        if (selectedPeriodId == null || selectedStage == AcademicStage.MID_YEAR) emptyMap()
        else GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = selectedClassId,
            sectionId = selectedSectionId,
            monthPrefix = "PERIOD:$selectedPeriodId"
        ).associateBy { it.studentId }
    }
    val gradeInputs = remember(refreshTick, selectedPeriodId, students, storedGrades) {
        mutableStateMapOf<Long, String>().apply {
            students.forEach { student ->
                put(student.id, storedGrades[student.id]?.writtenExamGrade?.let { GradingService.formatScore(it) } ?: "")
            }
        }
    }
    val focusRequesters = remember(students, selectedPeriodId) { students.map { FocusRequester() } }

    fun saveStudentGrade(index: Int) {
        val student = students.getOrNull(index) ?: return
        val periodId = selectedPeriodId ?: run { onShowMessage("يرجى اختيار الفترة الدراسية أولاً"); return }
        val raw = gradeInputs[student.id].orEmpty().trim()
        val score = if (raw.isEmpty()) null else raw.toDoubleOrNull()
        if (raw.isNotEmpty() && score == null) {
            onShowMessage("درجة \${student.studentName}: أدخل رقماً صحيحاً")
            return
        }
        if (score != null && (score < 0.0 || score > 100.0)) {
            onShowMessage("درجة الامتحان يجب أن تكون بين 0 و100")
            return
        }
        val dailyScore = if (selectedStage == AcademicStage.MID_YEAR) 0.0 else dailyGrades[student.id]?.totalScore ?: 0.0
        val error = repository.upsertPeriodGrade(student.id, periodId, dailyScore, score)
        if (error != null) onShowMessage(error)
        else {
            onDataChanged()
            onShowMessage(if (score == null) "تم مسح درجة \${student.studentName}" else "تم حفظ درجة \${student.studentName}: \${GradingService.formatScore(score)}")
        }
    }

    val enteredCount = students.count { storedGrades[it.id]?.writtenExamGrade != null }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = Slate900, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("درجة الامتحانات الشهرية", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("إدخال درجات الطلبة بالتسلسل وحفظها مباشرة في سجل النتائج", color = Teal100, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamStatMiniCard(Modifier.weight(1f), "عدد الطلبة", students.size.toString())
                    ExamStatMiniCard(Modifier.weight(1f), "الدرجات المدخلة", enteredCount.toString())
                    ExamStatMiniCard(Modifier.weight(1f), "المتبقية", (students.size - enteredCount).toString())
                }
            }
        }
        FilterChipRow("الصف الدراسي", classes.map { it.id to it.className }, selectedClassId, onSelectClass)
        FilterChipRow("الشعبة", classSections.map { it.id to "شعبة \${it.sectionName}" }, selectedSectionId, onSelectSection)
        FilterChipRow("السنة الدراسية", years.map { it.id to it.yearName }, selectedYearId) { selectedYearId = it }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("الفصل الدراسي", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                stageOptions.forEach { stage ->
                    val selected = selectedStage == stage
                    Surface(color = if (selected) Teal700 else Slate100, contentColor = if (selected) Color.White else Slate800, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { selectedStage = stage }) {
                        Text(stage.arabicLabel, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        FilterChipRow(if (selectedStage == AcademicStage.MID_YEAR) "امتحان نصف السنة" else "الشهر / الفترة", periods.map { it.id to it.periodName }, selectedPeriodId) { selectedPeriodId = it }

        Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("تسلسل الطلبة", color = Slate900, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("الدرجة من 100", color = Teal700, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (selectedPeriodId == null) {
                    Text("لا توجد فترات معرفة لهذا الفصل الدراسي. أضف الفترة أولاً من قسم الفترات الدراسية.", color = Slate600, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                } else if (students.isEmpty()) {
                    Text("لا يوجد طلبة نشطون في هذه الشعبة.", color = Slate600, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                } else {
                    Text("اضغط Enter بعد إدخال الدرجة للحفظ والانتقال إلى الطالب التالي. ترك الحقل فارغاً يمسح الدرجة، والصفر يُحفظ كدرجة فعلية.", color = Slate600, fontSize = 11.sp)
                    students.forEachIndexed { index, student ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text((index + 1).toString(), color = Slate500, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                            Text(student.studentName, color = Slate900, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = gradeInputs[student.id].orEmpty(),
                                onValueChange = { gradeInputs[student.id] = it },
                                modifier = Modifier.width(112.dp).focusRequester(focusRequesters[index]),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Slate900),
                                placeholder = { Text("—", fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (index < students.lastIndex) ImeAction.Next else ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onNext = { saveStudentGrade(index); focusRequesters.getOrNull(index + 1)?.requestFocus() },
                                    onDone = { saveStudentGrade(index) }
                                ),
                                trailingIcon = {
                                    TextButton(onClick = { saveStudentGrade(index) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                        Text("حفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            )
                        }
                        if (index < students.lastIndex) HorizontalDivider(color = Slate100)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipRow(title: String, values: List<Pair<Long, String>>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
        if (values.isEmpty()) Text("لا توجد خيارات متاحة", fontSize = 12.sp, color = Slate500)
        else Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            values.forEach { (id, label) ->
                val selected = selectedId == id
                Surface(color = if (selected) Slate900 else Slate100, contentColor = if (selected) Color.White else Slate800, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable { onSelect(id) }) {
                    Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ExamStatMiniCard(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(color = Slate800, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = Slate200, fontSize = 11.sp)
        }
    }
}
