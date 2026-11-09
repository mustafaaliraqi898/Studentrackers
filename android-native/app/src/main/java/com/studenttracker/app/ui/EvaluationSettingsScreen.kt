package com.studenttracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.ClassEntity
import com.studenttracker.app.model.EvaluationSettings

@Composable
fun EvaluationSettingsScreen(
    classes: List<ClassEntity>,
    selectedClassId: Long,
    repository: TrackerRepository,
    onSelectClass: (Long) -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val fields = listOf(
        "الحضور والغياب" to "attendance",
        "المشاركة" to "participation",
        "الدفتر" to "notebook",
        "الواجب" to "homework",
        "السلوك" to "behavior",
        "الاختبار اليومي" to "dailyTest"
    )
    var attendance by remember { mutableStateOf("19") }
    var participation by remember { mutableStateOf("19") }
    var notebook by remember { mutableStateOf("19") }
    var homework by remember { mutableStateOf("19") }
    var behavior by remember { mutableStateOf("19") }
    var dailyTest by remember { mutableStateOf("5") }

    fun parse(value: String): Double? = value
        .trim()
        .replace(',', '.')
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
        .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
        .replace('٨', '8').replace('٩', '9')
        .toDoubleOrNull()

    LaunchedEffect(selectedClassId) {
        val settings = repository.getEvaluationSettings(selectedClassId)
        attendance = GradingDisplay(settings.attendanceMax)
        participation = GradingDisplay(settings.participationMax)
        notebook = GradingDisplay(settings.notebookMax)
        homework = GradingDisplay(settings.homeworkMax)
        behavior = GradingDisplay(settings.behaviorMax)
        dailyTest = GradingDisplay(settings.dailyTestMax)
    }

    val values = listOf(attendance, participation, notebook, homework, behavior, dailyTest)
    val parsed = values.map { parse(it) }
    val total = parsed.filterNotNull().sum()
    val allValid = parsed.all { it != null && it >= 0.0 }
    val isTotalValid = allValid && kotlin.math.abs(total - 100.0) < 0.0001

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(color = Slate900, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Teal100)
                        Text("توزيع درجات التقييم اليومي", color = androidx.compose.ui.graphics.Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("حدد الدرجة القصوى لكل فقرة. يجب أن يكون مجموع الفقرات 100 درجة.", color = Teal100, fontSize = 12.sp)
                }
            }
        }
        item {
            Text("الصف الدراسي", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                classes.forEach { cls ->
                    FilterChip(
                        selected = cls.id == selectedClassId,
                        onClick = { onSelectClass(cls.id) },
                        label = { Text(cls.className) }
                    )
                }
            }
        }
        item {
            Surface(color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("درجات الفقرات", color = Slate900, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    EvaluationWeightField("الحضور والغياب", attendance, { attendance = it }, "درجة")
                    EvaluationWeightField("المشاركة", participation, { participation = it }, "درجة")
                    EvaluationWeightField("الدفتر", notebook, { notebook = it }, "درجة")
                    EvaluationWeightField("الواجب", homework, { homework = it }, "درجة")
                    EvaluationWeightField("السلوك", behavior, { behavior = it }, "درجة")
                    EvaluationWeightField("الاختبار اليومي", dailyTest, { dailyTest = it }, "درجة")
                    HorizontalDivider(color = Slate200)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("المجموع", fontWeight = FontWeight.Bold, color = Slate800)
                        Text(
                            "${GradingDisplay(total)} / 100",
                            color = if (isTotalValid) Teal700 else Red700,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp
                        )
                    }
                    if (!isTotalValid) {
                        Text("عدّل الدرجات حتى يصبح مجموعها 100 بالضبط.", color = Red700, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val settings = EvaluationSettings(
                                classId = selectedClassId,
                                attendanceMax = parsed[0]!!,
                                participationMax = parsed[1]!!,
                                notebookMax = parsed[2]!!,
                                homeworkMax = parsed[3]!!,
                                behaviorMax = parsed[4]!!,
                                dailyTestMax = parsed[5]!!
                            )
                            val error = repository.saveEvaluationSettings(settings)
                            if (error == null) {
                                onDataChanged()
                                onShowMessage("تم حفظ توزيع درجات التقييم للصف بنجاح")
                            } else onShowMessage(error)
                        },
                        enabled = isTotalValid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("حفظ إعدادات التقييم")
                    }
                }
            }
        }
        item {
            Text(
                "تُطبّق هذه الدرجات على حساب المعدل اليومي لهذا الصف فقط. تبقى سجلات الحضور والتقييمات السابقة محفوظة، ويُعاد احتساب نتائجها وفق التوزيع الجديد.",
                color = Slate600,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun EvaluationWeightField(label: String, value: String, onValueChange: (String) -> Unit, suffix: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.matches(Regex("[0-9٠-٩]*[.,]?[0-9٠-٩]*"))) onValueChange(next) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun GradingDisplay(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
