package com.studenttracker.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import com.studenttracker.app.service.GradingService


private fun htmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun openMonthlyGradesPrintPreview(
    context: Context,
    className: String,
    sectionName: String,
    periodName: String,
    stageName: String,
    rows: List<Pair<String, String>>
) {
    val rowCount = rows.size
    val rightRows = rows.take((rowCount + 1) / 2)
    val leftRows = rows.drop((rowCount + 1) / 2)

    fun renderTableRows(items: List<Pair<String, String>>, startIndex: Int): String =
        items.mapIndexed { index, row ->
            """
            <tr>
              <td class="number">${startIndex + index + 1}</td>
              <td class="student">${htmlEscape(row.first)}</td>
              <td class="grade">${htmlEscape(row.second)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

    fun renderTable(items: List<Pair<String, String>>, startIndex: Int): String =
        """
        <table>
          <colgroup><col class="number"><col class="student"><col class="grade"></colgroup>
          <thead>
            <tr><th>ت</th><th>اسم الطالب</th><th>المعدل</th></tr>
          </thead>
          <tbody>${renderTableRows(items, startIndex)}</tbody>
        </table>
        """.trimIndent()

    val html = """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            @page { size: A4 portrait; margin: 5mm; }
            * { box-sizing: border-box; }
            html, body { margin: 0; padding: 0; direction: rtl; color: #111827; font-family: sans-serif; }
            body { width: 100%; }
            .heading { text-align: center; margin: 0 0 2mm; }
            h1 { font-size: 12pt; margin: 0 0 1mm; }
            .meta { font-size: 7pt; line-height: 1.25; }
            .columns { display: flex; flex-direction: row; direction: rtl; align-items: flex-start; gap: 8mm; width: 100%; }
            .column { width: calc(50% - 3.5mm); min-width: 0; }
            table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 6.5pt; }
            tr { page-break-inside: avoid; break-inside: avoid; }
            th, td { border: 1px solid #374151; padding: 0.45mm 0.55mm; text-align: center; vertical-align: middle; line-height: 1.1; }
            th { background: #e5e7eb; font-weight: bold; white-space: nowrap; }
            .number { width: 11%; }
            .student { width: 67%; text-align: right; }
            .grade { width: 22%; }
            td.student { overflow-wrap: anywhere; }
            .footer { margin-top: 2mm; font-size: 6.5pt; text-align: left; }
          </style>
        </head>
        <body>
          <div class="heading">
            <h1>كشف الدرجات الشهرية</h1>
            <div class="meta">
              الصف: ${htmlEscape(className)} &nbsp; | &nbsp; الشعبة: ${htmlEscape(sectionName)}<br>
              الفترة: ${htmlEscape(periodName)} — ${htmlEscape(stageName)}
            </div>
          </div>
          <div class="columns">
            <div class="column">${renderTable(rightRows, 0)}</div>
            <div class="column">${renderTable(leftRows, rightRows.size)}</div>
          </div>
          <div class="footer">عدد الطلبة: ${rowCount}</div>
        </body>
        </html>
    """.trimIndent()

    val jobName = "الدرجات الشهرية - $className - $sectionName"
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = false
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val adapter = view.createPrintDocumentAdapter(jobName)
            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("monthly_grades", "Monthly Grades", 300, 300))
                .setMinMargins(PrintAttributes.Margins(20, 20, 20, 20))
                .build()
            printManager.print(jobName, adapter, attributes)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private fun normalizeWholeGradeInput(raw: String): String? {
    val normalized = normalizeGradeInput(raw) ?: return null
    return normalized.takeIf { !it.contains('.') }
}

private fun normalizeGradeInput(raw: String): String? {
    val normalized = buildString(raw.length) {
        raw.forEach { char ->
            append(
                when (char) {
                    in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
                    in '۰'..'۹' -> ('0'.code + (char.code - '۰'.code)).toChar()
                    '٫', ',' -> '.'
                    else -> char
                }
            )
        }
    }
    if (normalized.isEmpty()) return normalized
    if (!normalized.matches(Regex("^\\d{0,3}([.]\\d{0,2})?$"))) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return normalized.takeIf { value <= 100.0 }
}

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
    val context = LocalContext.current
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
    var gradeFilterMode by remember(selectedClassId, selectedSectionId) { mutableStateOf("ALL") }
    var minimumGradeDifference by remember(selectedClassId, selectedSectionId) { mutableStateOf("20") }
    val filteredStudents = remember(students, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) students
        else students.filter { it.studentName.contains(query, ignoreCase = true) }
    }
    val savedGrades = remember(refreshTick, selectedPeriodId) {
        selectedPeriodId?.let { repository.getPeriodGrades(periodId = it).associateBy { grade -> grade.studentId } } ?: emptyMap()
    }
    val calculatedPeriodGrades = remember(refreshTick, selectedClassId, selectedSectionId, selectedPeriodId) {
        val id = selectedPeriodId
        if (id == null) emptyMap()
        else GradingService.calculateGradesForClass(
            dbHelper = dbHelper, repository = repository, classId = selectedClassId,
            sectionId = selectedSectionId, monthPrefix = "PERIOD:$id"
        ).associateBy { it.studentId }
    }
    val calculatedDailyGrades = remember(calculatedPeriodGrades) {
        calculatedPeriodGrades.mapValues { GradingService.roundHalfUpInt(it.value.totalScore).toDouble() }
    }
    val dailyTestMax = remember(refreshTick, selectedClassId) {
        GradingService.getEvaluationWeights(dbHelper, selectedClassId, selectedPeriodId).dailyTest
    }
    val manualDailyStudents = remember(refreshTick, selectedPeriodId) {
        selectedPeriodId?.let { repository.getManualPeriodDailyGradeStudentIds(it) } ?: emptySet()
    }
    val gradeInputs = remember(refreshTick, selectedPeriodId, students, savedGrades) {
        mutableStateMapOf<Long, String>().apply {
            students.forEach { student ->
                this[student.id] = savedGrades[student.id]?.writtenExamGrade?.let { GradingService.roundHalfUpInt(it).toString() } ?: ""
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
                this[student.id] = daily?.let { GradingService.roundHalfUpInt(it).toString() } ?: ""
            }
        }
    }
    // Filtering always uses persisted/calculated values, never temporary text being edited.
    val committedDailyGrades = remember(students, savedGrades, calculatedDailyGrades, manualDailyStudents) {
        students.associate { student ->
            val daily = if (manualDailyStudents.contains(student.id)) {
                savedGrades[student.id]?.dailyGrade
            } else {
                calculatedDailyGrades[student.id] ?: savedGrades[student.id]?.dailyGrade
            }
            student.id to daily?.let { GradingService.roundHalfUpInt(it).toDouble() }
        }
    }
    val committedWrittenGrades = remember(students, savedGrades) {
        students.associate { student -> student.id to savedGrades[student.id]?.writtenExamGrade?.let { GradingService.roundHalfUpInt(it).toDouble() } }
    }
    val editingStudents = remember { mutableStateMapOf<Long, Boolean>() }
    val differenceThreshold = minimumGradeDifference.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 20.0
    val gradeFilteredStudents = filteredStudents.filter { student ->
        if (gradeFilterMode == "ALL") {
            true
        } else {
            val daily = committedDailyGrades[student.id]
            val written = committedWrittenGrades[student.id]
            if (daily == null || written == null) {
                false
            } else {
                val difference = daily - written
                when (gradeFilterMode) {
                    "DAILY_HIGHER" -> difference >= differenceThreshold
                    "WRITTEN_HIGHER" -> -difference >= differenceThreshold
                    "CLOSE" -> kotlin.math.abs(difference) < differenceThreshold
                    else -> true
                }
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
                Column {
                    Text("قائمة الطلبة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
                    Text("${gradeFilteredStudents.size} من ${students.size} طالب", fontSize = 12.sp, color = Slate600)
                }
                OutlinedButton(
                    enabled = selectedPeriod != null && students.isNotEmpty(),
                    onClick = {
                        val period = selectedPeriod
                        if (period != null) {
                            if (students.size > 78) {
                                onShowMessage("عدد الطلبة ${students.size} يتجاوز الحد المستهدف لصفحة A4 واحدة (78 طالباً). لم يتم استبعاد أي طالب؛ يمكنك الطباعة على دفعات.")
                            } else {
                                val printRows = students.map { student ->
                                    val daily = committedDailyGrades[student.id]
                                    val written = committedWrittenGrades[student.id]
                                    val average = if (period.stageType == AcademicStage.MID_YEAR) {
                                        written
                                    } else if (daily != null && written != null) {
                                        GradingService.roundHalfUpInt((daily + written) / 2.0).toDouble()
                                    } else {
                                        null
                                    }
                                    student.studentName to (average?.let { GradingService.roundHalfUpInt(it).toString() } ?: "")
                                }
                                val className = classes.firstOrNull { it.id == selectedClassId }?.className ?: ""
                                val sectionName = sections.firstOrNull { it.id == selectedSectionId }?.sectionName ?: ""
                                openMonthlyGradesPrintPreview(
                                    context = context,
                                    className = className,
                                    sectionName = sectionName,
                                    periodName = period.periodName,
                                    stageName = period.stageType.arabicLabel,
                                    rows = printRows
                                )
                            }
                        }
                    }
                ) {
                    Text("طباعة")
                }
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
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فلترة حسب فرق الدرجات", fontWeight = FontWeight.Bold, color = Slate700, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = gradeFilterMode == "ALL",
                            onClick = { gradeFilterMode = "ALL" },
                            label = { Text("الكل") }
                        )
                        FilterChip(
                            selected = gradeFilterMode == "DAILY_HIGHER",
                            onClick = { gradeFilterMode = "DAILY_HIGHER" },
                            label = { Text("اليومي أعلى") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = gradeFilterMode == "CLOSE",
                            onClick = { gradeFilterMode = "CLOSE" },
                            label = { Text("متقاربة") }
                        )
                        FilterChip(
                            selected = gradeFilterMode == "WRITTEN_HIGHER",
                            onClick = { gradeFilterMode = "WRITTEN_HIGHER" },
                            label = { Text("التحريري أعلى") }
                        )
                    }
                    OutlinedTextField(
                        value = minimumGradeDifference,
                        onValueChange = { raw ->
                            val normalized = normalizeGradeInput(raw)
                            if (normalized != null && normalized.length <= 6) minimumGradeDifference = normalized
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("الحد الأدنى للفرق بالدرجات") },
                        supportingText = { Text("القيمة الافتراضية 20 درجة، ويمكنك تغييرها متى تشاء") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
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
         } else if (gradeFilteredStudents.isEmpty()) {
            item {
                Text(
                    if (filteredStudents.isEmpty()) "ماكو طالب بهذا الاسم" else "ماكو طالب يطابق شروط الفلترة",
                    color = Slate600,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            items(gradeFilteredStudents, key = { it.id }) { student ->
                val savedGrade = savedGrades[student.id]
                var saving by remember(student.id, selectedPeriodId) { mutableStateOf(false) }
                val isEditing = editingStudents[student.id] == true
                val isMidYear = selectedPeriod.stageType == AcademicStage.MID_YEAR
                val writtenValue = gradeInputs[student.id].orEmpty()
                val dailyValue = dailyInputs[student.id].orEmpty()
                val liveAverage = if (isMidYear) {
                    writtenValue.toDoubleOrNull()
                } else {
                    val d = dailyValue.toDoubleOrNull()
                    val w = writtenValue.toDoubleOrNull()
                    if (d != null && w != null) GradingService.roundHalfUpInt((GradingService.roundHalfUpInt(d) + GradingService.roundHalfUpInt(w)) / 2.0).toDouble() else null
                }
                Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(student.studentName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Slate900, fontSize = 14.sp)
                            if (isEditing) {
                                Text("قيد التعديل", color = Teal700, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else if (savedGrade?.writtenExamGrade != null) {
                                AssistChip(onClick = {}, label = { Text("محفوظة", fontSize = 10.sp) },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) })
                            }
                        }
                        if (!isMidYear) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = dailyValue,
                                    onValueChange = { raw ->
                                        normalizeWholeGradeInput(raw)?.let { dailyInputs[student.id] = it }
                                    },
                                    enabled = isEditing,
                                    readOnly = !isEditing,
                                    modifier = Modifier.weight(1f),
                                    label = { Text("درجة اليومي") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    OutlinedTextField(
                                        value = writtenValue,
                                        onValueChange = { raw ->
                                            normalizeWholeGradeInput(raw)?.let { gradeInputs[student.id] = it }
                                        },
                                        enabled = isEditing,
                                        readOnly = !isEditing,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("الامتحان التحريري") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                    )
                                    val periodDailyTests = calculatedPeriodGrades[student.id]?.dailyTests.orEmpty()
                                    Text(
                                        text = if (periodDailyTests.isNotEmpty()) {
                                            "الامتحان اليومي: ${GradingService.formatScore(calculatedPeriodGrades[student.id]?.quizScore ?: 0.0)} من ${GradingService.formatScore(dailyTestMax)}"
                                        } else {
                                            "الامتحان اليومي: غير مسجل"
                                        },
                                        color = Red700,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (manualDailyStudents.contains(student.id)) "اليومي معدّل يدوياً" else "اليومي محسوب تلقائياً",
                                    color = if (manualDailyStudents.contains(student.id)) Teal700 else Slate500,
                                    fontSize = 11.sp
                                )
                                TextButton(
                                    enabled = isEditing && !saving && manualDailyStudents.contains(student.id),
                                    onClick = {
                                        val calculated = calculatedDailyGrades[student.id] ?: 0.0
                                        val error = repository.setPeriodDailyGrade(student.id, selectedPeriod.id, calculated, manual = false)
                                        if (error == null) {
                                            dailyInputs[student.id] = GradingService.roundHalfUpInt(calculated).toString()
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
                                    normalizeGradeInput(raw)?.let { gradeInputs[student.id] = it }
                                },
                                enabled = isEditing,
                                readOnly = !isEditing,
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
                            if (!isEditing) {
                                OutlinedButton(
                                    onClick = {
                                        // Start editing from the currently committed values.
                                        dailyInputs[student.id] = committedDailyGrades[student.id]?.let { GradingService.roundHalfUpInt(it).toString() }.orEmpty()
                                        gradeInputs[student.id] = committedWrittenGrades[student.id]?.let { GradingService.roundHalfUpInt(it).toString() }.orEmpty()
                                        editingStudents[student.id] = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("تعديل")
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        enabled = !saving,
                                        onClick = {
                                            dailyInputs[student.id] = committedDailyGrades[student.id]?.let { GradingService.formatScore(it) }.orEmpty()
                                            gradeInputs[student.id] = committedWrittenGrades[student.id]?.let { GradingService.formatScore(it) }.orEmpty()
                                            editingStudents.remove(student.id)
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                    ) { Text("إلغاء") }
                                    Button(
                                enabled = !saving,
                                onClick = {
                                    val writtenRaw = gradeInputs[student.id].orEmpty().trim()
                                    val written = if (writtenRaw.isEmpty()) null else writtenRaw.toDoubleOrNull()?.let { GradingService.roundHalfUpInt(it).toDouble() }
                                    val daily = if (isMidYear) 0.0 else dailyInputs[student.id].orEmpty().trim().toDoubleOrNull()?.let { GradingService.roundHalfUpInt(it).toDouble() }
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
                                            editingStudents.remove(student.id)
                                            onShowMessage("تم حفظ درجات ${student.studentName}")
                                            onDataChanged()
                                        } else onShowMessage(error)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("حفظ")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
