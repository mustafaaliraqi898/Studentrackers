package com.studenttracker.app.ui

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.model.ClassEntity
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationSettingsScreen(
    classes: List<ClassEntity>,
    dbHelper: AppDatabaseHelper,
    onShowMessage: (String) -> Unit
) {
    var selectedClassId by remember(classes) { mutableLongStateOf(classes.firstOrNull()?.id ?: 0L) }
    var attendance by remember { mutableStateOf("10") }
    var participation by remember { mutableStateOf("30") }
    var notebook by remember { mutableStateOf("10") }
    var homework by remember { mutableStateOf("15") }
    var behavior by remember { mutableStateOf("15") }
    var dailyTest by remember { mutableStateOf("20") }
    var loadedClassId by remember { mutableLongStateOf(-1L) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedClassId, classes) {
        if (selectedClassId <= 0L) return@LaunchedEffect
        var found = false
        dbHelper.readableDatabase.rawQuery(
            "SELECT AttendanceMax, ParticipationMax, NotebookMax, HomeworkMax, BehaviorMax, DailyTestMax FROM EvaluationSettings WHERE ClassID = ?",
            arrayOf(selectedClassId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                attendance = cursor.getDouble(0).toString().removeSuffix(".0")
                participation = cursor.getDouble(1).toString().removeSuffix(".0")
                notebook = cursor.getDouble(2).toString().removeSuffix(".0")
                homework = cursor.getDouble(3).toString().removeSuffix(".0")
                behavior = cursor.getDouble(4).toString().removeSuffix(".0")
                dailyTest = cursor.getDouble(5).toString().removeSuffix(".0")
                found = true
            }
        }
        if (!found) {
            attendance = "10"; participation = "30"; notebook = "10"
            homework = "15"; behavior = "15"; dailyTest = "20"
        }
        loadedClassId = selectedClassId
    }

    val numbers = listOf(attendance, participation, notebook, homework, behavior, dailyTest).map { it.toDoubleOrNull() }
    val sum = numbers.filterNotNull().sum()
    val valid = selectedClassId > 0L && numbers.all { it != null && it >= 0.0 } && kotlin.math.abs(sum - 100.0) < 0.0001

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("إعدادات التقييم", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("اكتب الدرجة القصوى لكل عنصر. التوزيع خاص بالصف ويُطبّق على جميع شعبه.")
        if (classes.isEmpty()) {
            Text("أضف صفاً أولاً حتى تتمكن من إعداد توزيع الدرجات.")
        } else {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = classes.firstOrNull { it.id == selectedClassId }?.className.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("الصف الدراسي") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    classes.forEach { cls ->
                        DropdownMenuItem(text = { Text(cls.className) }, onClick = {
                            selectedClassId = cls.id
                            expanded = false
                        })
                    }
                }
            }
            @Composable
            fun gradeField(label: String, value: String, onChange: (String) -> Unit) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("\\d{0,3}(\\.\\d{0,2})?"))) onChange(it) },
                    label = { Text(label + " — الدرجة القصوى") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            gradeField("الحضور", attendance) { attendance = it }
            gradeField("المشاركة", participation) { participation = it }
            gradeField("الدفتر", notebook) { notebook = it }
            gradeField("الواجب", homework) { homework = it }
            gradeField("السلوك", behavior) { behavior = it }
            gradeField("الامتحان اليومي", dailyTest) { dailyTest = it }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("مجموع التوزيع", fontWeight = FontWeight.Bold)
                    Text(sum.toString() + " / 100", style = MaterialTheme.typography.headlineMedium,
                        color = if (kotlin.math.abs(sum - 100.0) < 0.0001) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    if (kotlin.math.abs(sum - 100.0) >= 0.0001) Text("لازم يكون المجموع 100 حتى ينحفظ التوزيع.", color = MaterialTheme.colorScheme.error)
                }
            }
            Button(
                enabled = valid && loadedClassId == selectedClassId,
                onClick = {
                    val cv = ContentValues().apply {
                        put("ClassID", selectedClassId)
                        put("AttendanceMax", attendance.toDouble())
                        put("ParticipationMax", participation.toDouble())
                        put("NotebookMax", notebook.toDouble())
                        put("HomeworkMax", homework.toDouble())
                        put("BehaviorMax", behavior.toDouble())
                        put("DailyTestMax", dailyTest.toDouble())
                        put("UpdatedAt", Instant.now().toString())
                    }
                    val result = dbHelper.writableDatabase.insertWithOnConflict(
                        "EvaluationSettings", null, cv, SQLiteDatabase.CONFLICT_REPLACE
                    )
                    onShowMessage(if (result == -1L) "تعذر حفظ التوزيع." else "تم حفظ توزيع درجات الصف لجميع شعبه.")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("حفظ التوزيع")
            }
        }
    }
}
