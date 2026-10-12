package com.studenttracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.model.ClassEntity
import com.studenttracker.app.model.SectionEntity

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val Teal700 = Color(0xFF0F766E)
val Teal600 = Color(0xFF0D9488)
val Teal100 = Color(0xFFCCFBF1)
val Teal50 = Color(0xFFF0FDFA)

val Emerald700 = Color(0xFF15803D)
val Emerald600 = Color(0xFF16A34A)
val Emerald100 = Color(0xFFDCFCE7)
val Emerald50 = Color(0xFFF0FDF4)

val Amber700 = Color(0xFFB45309)
val Amber600 = Color(0xFFD97706)
val Amber100 = Color(0xFFFEF3C7)
val Amber50 = Color(0xFFFFFBEB)

val Red700 = Color(0xFFB91C1C)
val Red600 = Color(0xFFDC2626)
val Red100 = Color(0xFFFEE2E2)
val Red50 = Color(0xFFFEF2F2)

enum class AppScreen(val titleAr: String) {
    DASHBOARD("الرئيسية"),
    DAILY_TRACKING("المتابعة اليومية"),
    ABSENCE_TRACKING("عدد أيام الغياب"),
    DAILY_SCHEDULE("الجدول اليومي"),
    DAILY_PLAN("الخطة اليومية"),
    STUDY_PERIODS("الفترات الدراسية"),
    STUDENTS("الطلاب"),
    CLASSES_SECTIONS("الصفوف والشعب"),
    QUICK_SEARCH("البحث السريع"),
    SAVED_RECORDS("السجلات المحفوظة"),
    STUDENT_PROFILE("ملف الطالب"),
    AVERAGES_TABLE("المعدل"),
    PERIOD_COMPARISON("مقارنة نتائج الفترات"),
    REPORTS("التقارير والنتائج"),
    HOLIDAYS("العطل والمناسبات"),
    EXCEL_BACKUP("Excel والنسخ الاحتياطي")
}

private val AppColorScheme = lightColorScheme(
    primary = Slate900,
    onPrimary = Color.White,
    secondary = Teal700,
    onSecondary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900
)

@Composable
fun StudentTrackerRtlTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            content = content
        )
    }
}

@Composable
fun ClassSectionFilterBar(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit
) {
    val classSections = sections.filter { it.classId == selectedClassId }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "الصف الدراسي:",
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

            Text(
                text = "الشعبة:",
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

@Composable
fun ThreeStateRatingRow(
    label: String,
    currentValue: Double?,
    enabled: Boolean,
    scorePreviewText: String,
    onValueChange: (Double?) -> Unit,
    ratingType: String = "five"
) {
    val options = when (ratingType) {
        "behavior" -> listOf(
            Triple(1.0, "كامل", Triple(Emerald600, Emerald50, Emerald700)),
            Triple(0.0, "صفر", Triple(Red600, Red50, Red700))
        )
        "attendance" -> listOf(
            Triple(1.0, "حاضر", Triple(Emerald600, Emerald50, Emerald700)),
            Triple(0.0, "غائب", Triple(Red600, Red50, Red700))
        )
        else -> listOf(
            Triple(1.0, "ممتاز", Triple(Emerald600, Emerald50, Emerald700)),
            Triple(0.8, "جيد جداً", Triple(Teal600, Teal50, Teal700)),
            Triple(0.6, "جيد", Triple(Amber600, Amber50, Amber700)),
            Triple(0.4, "مقبول", Triple(Slate600, Slate100, Slate700)),
            Triple(0.0, "ضعيف", Triple(Red600, Red50, Red700))
        )
    }
    val selectedLabel = options.firstOrNull { it.first == currentValue }?.second
        ?: if (currentValue == null) "غير مسجل" else "قيمة قديمة"
    val selectedColor = when {
        currentValue == null -> Slate500
        currentValue == 0.0 -> Red700
        currentValue == 1.0 -> Emerald700
        else -> Teal700
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(Slate50, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                Text(text = scorePreviewText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
            }
            Text(text = selectedLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = selectedColor)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (optVal, optLabel, colors) ->
                val isSelected = currentValue == optVal
                val (activeBg, inactiveBg, inactiveText) = colors
                Surface(
                    color = when { !enabled -> Slate100; isSelected -> activeBg; else -> inactiveBg },
                    contentColor = when { !enabled -> Slate500; isSelected -> Color.White; else -> inactiveText },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected && enabled) activeBg else Slate200),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) {
                        onValueChange(if (isSelected) null else optVal)
                    }
                ) {
                    Text(text = optLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp))
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesAlertDialog(
    onSaveAndContinue: () -> Unit,
    onDiscardAndContinue: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "تغييرات غير محفوظة",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Slate900
            )
        },
        text = {
            Text(
                text = "لديك تعديلات في سجل المتابعة اليومي لم يتم حفظها بعد. هل تريد حفظ التعديلات قبل المغادرة؟",
                fontSize = 14.sp,
                color = Slate700
            )
        },
        confirmButton = {
            Button(
                onClick = onSaveAndContinue,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("حفظ والانتقال", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscardAndContinue) {
                    Text("تجاهل التغييرات", color = Red700, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onCancel) {
                    Text("البقاء هنا", color = Slate700, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
