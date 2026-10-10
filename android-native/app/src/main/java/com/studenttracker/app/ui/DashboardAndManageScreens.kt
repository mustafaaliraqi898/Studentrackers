package com.studenttracker.app.ui

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.model.*
import com.studenttracker.app.service.GradingService

@Composable
fun DashboardScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    selectedClassId: Long,
    selectedSectionId: Long,
    dbStats: Map<String, Int>,
    savedSessions: List<SavedSessionSummary>,
    onSelectClass: (Long) -> Unit,
    onSelectSection: (Long) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenSavedSession: (Long, Long, String) -> Unit
) {
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "سجل متابعة الطلاب",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "تطبيق Android أصلي • يعمل بدون إنترنت (SQLite)",
                                color = Teal100,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            color = Teal700,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "الدرجة من 100",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            label = "الصفوف والشعب",
                            value = "${dbStats["classes"] ?: 0} / ${dbStats["sections"] ?: 0}"
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            label = "إجمالي الطلاب",
                            value = "${dbStats["students"] ?: 0}"
                        )
                        StatMiniCard(
                            modifier = Modifier.weight(1f),
                            label = "الجلسات المحفوظة",
                            value = "${savedSessions.size}"
                        )
                    }
                }
            }
        }

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
            Button(
                onClick = { onNavigate(AppScreen.DAILY_TRACKING) },
                colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "بدء المتابعة اليومية وتسجيل الحضور والدرجات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        item {
            Text(
                text = "الأقسام والوظائف الأساسية",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "الفترات الدراسية",
                        subtitle = "تحديد بداية ونهاية كل فترة",
                        icon = Icons.Default.DateRange,
                        tint = Emerald700,
                        onClick = { onNavigate(AppScreen.STUDY_PERIODS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "الصفوف والشعب",
                        subtitle = "إضافة وتعديل الصفوف",
                        icon = Icons.Default.Class,
                        tint = Slate800,
                        onClick = { onNavigate(AppScreen.CLASSES_SECTIONS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "إدارة الطلاب",
                        subtitle = "إضافة، تعديل، نقل، حذف",
                        icon = Icons.Default.Groups,
                        tint = Slate800,
                        onClick = { onNavigate(AppScreen.STUDENTS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "البحث السريع",
                        subtitle = "بحث فوري عن أي طالب",
                        icon = Icons.Default.Search,
                        tint = Teal700,
                        onClick = { onNavigate(AppScreen.QUICK_SEARCH) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "المعدل",
                        subtitle = "جدول المعدلات والسعي السنوي والطباعة",
                        icon = Icons.Default.TableChart,
                        tint = Emerald700,
                        onClick = { onNavigate(AppScreen.AVERAGES_TABLE) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "التقارير والنتائج",
                        subtitle = "الدرجة من 100 والتصدير",
                        icon = Icons.Default.Assessment,
                        tint = Emerald700,
                        onClick = { onNavigate(AppScreen.REPORTS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "العطل والمناسبات",
                        subtitle = "الجمعة والسبت + العطل",
                        icon = Icons.Default.EventBusy,
                        tint = Red700,
                        onClick = { onNavigate(AppScreen.HOLIDAYS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "عدد أيام الغياب",
                        subtitle = "اسم الطالب وعدد أيام غيابه",
                        icon = Icons.Default.PersonOff,
                        tint = Red700,
                        onClick = { onNavigate(AppScreen.ABSENCE_TRACKING) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "Excel والنسخ الاحتياطي",
                        subtitle = "قالب واستيراد وتصدير ونسخ",
                        icon = Icons.Default.Storage,
                        tint = Teal700,
                        onClick = { onNavigate(AppScreen.EXCEL_BACKUP) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "الجدول اليومي",
                        subtitle = "جدول الحصص الأسبوعي للمدرس",
                        icon = Icons.Default.Schedule,
                        tint = Teal700,
                        onClick = { onNavigate(AppScreen.DAILY_SCHEDULE) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "السجلات المحفوظة",
                        subtitle = "استعراض وتعديل الأيام",
                        icon = Icons.Default.History,
                        tint = Amber700,
                        onClick = { onNavigate(AppScreen.SAVED_RECORDS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "مقارنة نتائج الفترات",
                        subtitle = "مقارنة التقدم أو التراجع بين فترتين",
                        icon = Icons.Default.SwapHoriz,
                        tint = Teal700,
                        onClick = { onNavigate(AppScreen.PERIOD_COMPARISON) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.fillMaxWidth(),
                        title = "الخطة اليومية",
                        subtitle = "خطة الدرس اليومية والصور لجميع الصفوف والشعب",
                        icon = Icons.Default.CalendarMonth,
                        tint = Emerald700,
                        onClick = { onNavigate(AppScreen.DAILY_PLAN) }
                    )
                }
            }
        }

        if (savedSessions.isNotEmpty()) {
            item {
                Text(
                    text = "أحدث السجلات اليومية المحفوظة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900
                )
            }
            items(savedSessions.take(4)) { session ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenSavedSession(session.classId, session.sectionId, session.recordDate)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${GradingService.getArabicDayName(session.recordDate)} ${GradingService.formatDisplayDate(session.recordDate)}",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Slate900
                            )
                            Text(
                                text = "${session.className} — شعبة ${session.sectionName}",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = Emerald50, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "حضور: ${session.presentCount}",
                                    color = Emerald700,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(color = Red50, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "غياب: ${session.absentCount}",
                                    color = Red700,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniCard(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        color = Slate800,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = label, color = Slate200, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavActionTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Slate100,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = tint)
                }
            }
            Column {
                Text(text = title, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Slate900)
                Text(text = subtitle, fontSize = 11.sp, color = Slate600)
            }
        }
    }
}

// ============================================================================
// CLASSES & SECTIONS SCREEN
// ============================================================================
@Composable
fun ClassesSectionsScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    repository: TrackerRepository,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var newClassName by remember { mutableStateOf("") }
    var selectedClassForSection by remember(classes) {
        mutableStateOf(classes.firstOrNull()?.id ?: 1L)
    }
    var newSectionName by remember { mutableStateOf("") }
    var editingClass by remember { mutableStateOf<ClassEntity?>(null) }
    var editingClassText by remember { mutableStateOf("") }

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
                    Text("إضافة صف دراسي جديد", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newClassName,
                            onValueChange = { newClassName = it },
                            placeholder = { Text("مثال: الخامس العلمي") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    repository.addClass(newClassName)
                                    newClassName = ""
                                    onDataChanged()
                                    onShowMessage("تمت إضافة الصف بنجاح")
                                }.onFailure {
                                    onShowMessage("تعذر إضافة الصف (قد يكون موجوداً مسبقاً)")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة صف", fontWeight = FontWeight.Bold)
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
                    Text("إضافة شعبة إلى صف", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        classes.forEach { cls ->
                            val isSel = cls.id == selectedClassForSection
                            Surface(
                                color = if (isSel) Teal700 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { selectedClassForSection = cls.id }
                            ) {
                                Text(
                                    text = cls.className,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSectionName,
                            onValueChange = { newSectionName = it },
                            placeholder = { Text("مثال: ج أو د") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    repository.addSection(selectedClassForSection, newSectionName)
                                    newSectionName = ""
                                    onDataChanged()
                                    onShowMessage("تمت إضافة الشعبة بنجاح")
                                }.onFailure {
                                    onShowMessage("تعذر إضافة الشعبة (قد تكون موجودة في هذا الصف)")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة شعبة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        items(classes) { cls ->
            val clsSections = sections.filter { it.classId == cls.id }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cls.className,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Slate900
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                editingClass = cls
                                editingClassText = cls.className
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Slate600)
                            }
                            if (classes.size > 1) {
                                IconButton(onClick = {
                                    repository.deleteClass(cls.id)
                                    onDataChanged()
                                    onShowMessage("تم حذف الصف")
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red600)
                                }
                            }
                        }
                    }

                    if (clsSections.isEmpty()) {
                        Text("لا توجد شعب مضافة لهذا الصف", fontSize = 12.sp, color = Slate500)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            clsSections.forEach { sec ->
                                Surface(
                                    color = Slate50,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "شعبة ${sec.sectionName}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Slate800
                                        )
                                        if (clsSections.size > 1) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "حذف الشعبة",
                                                tint = Red600,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable {
                                                        repository.deleteSection(sec.id)
                                                        onDataChanged()
                                                        onShowMessage("تم حذف الشعبة")
                                                    }
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

    if (editingClass != null) {
        AlertDialog(
            onDismissRequest = { editingClass = null },
            title = { Text("تعديل اسم الصف", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editingClassText,
                    onValueChange = { editingClassText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = editingClass
                    if (target != null && editingClassText.isNotBlank()) {
                        repository.updateClass(target.id, editingClassText)
                        editingClass = null
                        onDataChanged()
                        onShowMessage("تم تحديث اسم الصف")
                    }
                }) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingClass = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ============================================================================
// STUDENTS MANAGEMENT SCREEN
// ============================================================================
@Composable
fun StudentsScreen(
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
    onNavigateToExcel: () -> Unit,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    var newStudentName by remember { mutableStateOf("") }
    var showInactive by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var editingStudentName by remember { mutableStateOf("") }
    var studentToDelete by remember { mutableStateOf<StudentEntity?>(null) }
    var studentToTransfer by remember { mutableStateOf<StudentEntity?>(null) }

    val students = remember(refreshTick, selectedClassId, selectedSectionId, showInactive, classes, sections) {
        repository.getStudents(selectedClassId, selectedSectionId, includeInactive = showInactive)
    }
    val gradesMap = remember(refreshTick, selectedClassId, selectedSectionId, students) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = selectedClassId,
            sectionId = selectedSectionId
        ).associateBy { it.studentId }
    }
    val behaviorZeroCounts = remember(refreshTick, students) {
        GradingService.getBehaviorZeroCounts(dbHelper, repository)
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
                        Text(
                            text = "إضافة طالب جديد (${students.size} طالب)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Slate900
                        )
                        TextButton(onClick = onNavigateToExcel) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد من Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newStudentName,
                            onValueChange = { newStudentName = it },
                            placeholder = { Text("الاسم الثلاثي للطالب...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newStudentName.isBlank()) {
                                    onShowMessage("يرجى كتابة اسم الطالب")
                                    return@Button
                                }
                                val (_, isDup) = repository.addStudent(
                                    newStudentName,
                                    selectedClassId,
                                    selectedSectionId
                                )
                                if (isDup) {
                                    onShowMessage("الطالب موجود مسبقاً في نفس الصف (حتى وإن كان في شعبة أخرى)")
                                } else {
                                    newStudentName = ""
                                    onDataChanged()
                                    onShowMessage("تمت إضافة الطالب بنجاح")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(checked = showInactive, onCheckedChange = { showInactive = it })
                        Text("إظهار الطلاب غير النشطين (المنقولين/الموقوفين)", fontSize = 12.sp, color = Slate600)
                    }
                }
            }
        }

        items(students, key = { it.id }) { st ->
            val grade = gradesMap[st.id]
            val behaviorZeroCount = behaviorZeroCounts[st.id] ?: 0
            val hasFrequentBehaviorZero = behaviorZeroCount >= 6
            Surface(
                color = if (st.isActive) Color.White else Slate100,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            fontSize = 15.sp,
                            color = if (hasFrequentBehaviorZero) Red600 else Slate900,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${st.className} - شعبة ${st.sectionName}" +
                                if (!st.isActive) " (غير نشط)" else "",
                            fontSize = 12.sp,
                            color = Slate600,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (grade != null) {
                            Surface(
                                color = Teal50,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Teal100),
                                modifier = Modifier.clickable { onOpenStudentProfile(st.id) }
                            ) {
                                Text(
                                    text = "${GradingService.formatScore(grade.totalScore)} / 100",
                                    color = Teal700,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onOpenStudentProfile(st.id) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "ملف الطالب", tint = Teal700)
                        }
                        IconButton(
                            onClick = {
                                editingStudent = st
                                editingStudentName = st.studentName
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الاسم", tint = Slate600)
                        }
                        IconButton(
                            onClick = { studentToTransfer = st },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "نقل شعبة", tint = Amber700)
                        }
                        IconButton(
                            onClick = { studentToDelete = st },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red600)
                        }
                    }
                }
            }
        }
    }

    if (editingStudent != null) {
        AlertDialog(
            onDismissRequest = { editingStudent = null },
            title = { Text("تعديل بيانات الطالب", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editingStudentName,
                        onValueChange = { editingStudentName = it },
                        singleLine = true,
                        label = { Text("اسم الطالب") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val target = editingStudent!!
                    OutlinedButton(
                        onClick = {
                            repository.toggleStudentActive(target.id, !target.isActive)
                            editingStudent = null
                            onDataChanged()
                            onShowMessage(if (target.isActive) "تم إيقاف تنشيط الطالب" else "تم تفعيل الطالب")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (target.isActive) "تحويل الطالب إلى غير نشط" else "إعادة تفعيل الطالب")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = editingStudent
                    if (target != null && editingStudentName.isNotBlank()) {
                        val updated = repository.updateStudentName(target.id, editingStudentName)
                        if (!updated) {
                            onShowMessage("الاسم موجود مسبقاً لطالب آخر في نفس الصف")
                        } else {
                            editingStudent = null
                            onDataChanged()
                            onShowMessage("تم تحديث اسم الطالب")
                        }
                    }
                }) {
                    Text("حفظ الاسم")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (studentToTransfer != null) {
        val target = studentToTransfer!!
        var targetClsId by remember { mutableStateOf(target.classId) }
        val targetSections = sections.filter { it.classId == targetClsId }
        var targetSecId by remember(targetClsId) {
            mutableStateOf(targetSections.firstOrNull()?.id ?: target.sectionId)
        }
        AlertDialog(
            onDismissRequest = { studentToTransfer = null },
            title = { Text("نقل الطالب: ${target.studentName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر الصف والشعبة الجديدة:", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classes.forEach { c ->
                            Surface(
                                color = if (c.id == targetClsId) Slate900 else Slate100,
                                contentColor = if (c.id == targetClsId) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { targetClsId = c.id }
                            ) {
                                Text(
                                    text = c.className,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        targetSections.forEach { s ->
                            Surface(
                                color = if (s.id == targetSecId) Teal700 else Slate100,
                                contentColor = if (s.id == targetSecId) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { targetSecId = s.id }
                            ) {
                                Text(
                                    text = "شعبة ${s.sectionName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val transferred = repository.transferStudentSection(target.id, targetClsId, targetSecId)
                    if (!transferred) {
                        onShowMessage("يوجد طالب بنفس الاسم في الصف المختار")
                    } else {
                        studentToTransfer = null
                        onDataChanged()
                        onShowMessage("تم نقل الطالب بنجاح")
                    }
                }) {
                    Text("تأكيد النقل")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToTransfer = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (studentToDelete != null) {
        val target = studentToDelete!!
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("تأكيد حذف الطالب", fontWeight = FontWeight.Bold, color = Red700) },
            text = {
                Text("هل أنت متأكد من حذف الطالب (${target.studentName}) وجميع سجلاته اليومية نهائياً؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteStudent(target.id)
                        studentToDelete = null
                        onDataChanged()
                        onShowMessage("تم حذف الطالب")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// ============================================================================
// QUICK SEARCH SCREEN
// ============================================================================
@Composable
fun QuickSearchScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    defaultClassId: Long? = null,
    defaultSectionId: Long? = null,
    selectedDate: String = "2026-10-04",
    holidays: List<HolidayEntity> = emptyList(),
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onOpenStudentProfile: (Long) -> Unit,
    onJumpToDailyTracking: (Long, Long) -> Unit,
    onDataChanged: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var filterClassId by remember(defaultClassId) { mutableStateOf(defaultClassId) }
    var filterSectionId by remember(defaultSectionId) { mutableStateOf(defaultSectionId) }
    var localRefresh by remember { mutableIntStateOf(0) }
    var pendingConfirmAbsentStudent by remember { mutableStateOf<StudentEntity?>(null) }

    val dayStatus = remember(selectedDate, holidays) {
        GradingService.checkDateStatus(selectedDate, holidays)
    }

    val results = remember(refreshTick, localRefresh, query, filterClassId, filterSectionId) {
        repository.searchStudents(query, filterClassId, filterSectionId)
    }
    val allGradesMap = remember(refreshTick, localRefresh, results) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = filterClassId,
            sectionId = filterSectionId
        ).associateBy { it.studentId }
    }

    fun executeDirectAttendanceStatus(st: StudentEntity, targetStatus: AttendanceStatus) {
        repository.setStudentAttendanceImmediately(
            studentId = st.id,
            classId = st.classId,
            sectionId = st.sectionId,
            recordDate = selectedDate,
            targetStatus = targetStatus
        )
        localRefresh++
        onDataChanged()
        if (targetStatus == AttendanceStatus.ABSENT) {
            onShowMessage("تم تسجيل الطالب (${st.studentName}) غائباً لتاريخ ${GradingService.formatDisplayDate(selectedDate)} وحفظه فوراً (درجة الحضور = 0)")
        } else {
            onShowMessage("تم تسجيل الطالب (${st.studentName}) حاضراً لتاريخ ${GradingService.formatDisplayDate(selectedDate)} وحفظه فوراً (درجة الحضور = 19)")
        }
    }

    fun executeDirectAbsent(st: StudentEntity) {
        executeDirectAttendanceStatus(st, AttendanceStatus.ABSENT)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("ابحث باسم الطالب (مثال: أحمد، محمد، علي)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = "كل الصفوف",
                                fontSize = 12.sp,
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (filterClassId != null) {
                        val classSections = sections.filter { it.classId == filterClassId }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = if (filterSectionId == null) Teal700 else Slate100,
                                contentColor = if (filterSectionId == null) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { filterSectionId = null }
                            ) {
                                Text(
                                    text = "كل الشعب",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            classSections.forEach { s ->
                                val isSecSel = filterSectionId == s.id
                                Surface(
                                    color = if (isSecSel) Teal700 else Slate100,
                                    contentColor = if (isSecSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { filterSectionId = s.id }
                                ) {
                                    Text(
                                        text = "شعبة ${s.sectionName}",
                                        fontSize = 12.sp,
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

        item {
            Text(
                text = "نتائج البحث (${results.size} طالب) — تاريخ اليوم المحدد: ${GradingService.formatDisplayDate(selectedDate)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate600
            )
        }

        items(results, key = { it.id }) { st ->
            val g = allGradesMap[st.id]
            val recordedStatus = remember(refreshTick, localRefresh, st.id, selectedDate) {
                repository.getStudentAttendanceStatusForDate(st.id, selectedDate)
            }
            val isAlreadyAbsent = recordedStatus == AttendanceStatus.ABSENT
            val isSavedPresent = recordedStatus == AttendanceStatus.PRESENT

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isAlreadyAbsent) Red600 else Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = st.studentName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${st.className} — شعبة ${st.sectionName}" + when {
                                    isAlreadyAbsent -> " • الحالة اليوم: غائب (0/19)"
                                    isSavedPresent -> " • الحالة اليوم: حاضر"
                                    else -> ""
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isAlreadyAbsent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAlreadyAbsent) Red700 else Slate600
                            )
                        }
                        if (g != null) {
                            Surface(color = Teal50, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "${GradingService.formatScore(g.totalScore)} / 100",
                                    color = Teal700,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!dayStatus.isNonWorkingDay) {
                            if (isAlreadyAbsent) {
                                Button(
                                    onClick = {
                                        executeDirectAttendanceStatus(st, AttendanceStatus.PRESENT)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حاضر", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (isSavedPresent) {
                                            pendingConfirmAbsentStudent = st
                                        } else {
                                            executeDirectAbsent(st)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("غائب", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onOpenStudentProfile(st.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ملف الطالب", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (pendingConfirmAbsentStudent != null) {
        val target = pendingConfirmAbsentStudent!!
        AlertDialog(
            onDismissRequest = { pendingConfirmAbsentStudent = null },
            title = {
                Text("تأكيد تغيير الحالة إلى غائب", fontWeight = FontWeight.ExtraBold, color = Red700)
            },
            text = {
                Text(
                    "الطالب (${target.studentName}) مسجل حاضر مسبقاً لتاريخ ${GradingService.formatDisplayDate(selectedDate)}.\nهل تريد تغيير حالته إلى غائب (درجة الحضور = 0)؟",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmAbsentStudent = null
                        executeDirectAbsent(target)
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
}

// ============================================================================
// HOLIDAYS SCREEN
// ============================================================================
@Composable
fun HolidaysScreen(
    holidays: List<HolidayEntity>,
    repository: TrackerRepository,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun currentDeviceIsoDate(): String {
        return java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
    }

    var holidayDate by remember { mutableStateOf(currentDeviceIsoDate()) }
    var holidayName by remember { mutableStateOf("") }
    var holidayType by remember { mutableStateOf("عطلة رسمية") }
    var holidayNotes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (holidayName.isBlank() && holidayNotes.isBlank()) {
            holidayDate = currentDeviceIsoDate()
        }
    }

    fun openHolidayDatePicker() {
        val parts = holidayDate.trim().split("-")
        val nowCal = java.util.Calendar.getInstance()
        val initYear = parts.getOrNull(0)?.toIntOrNull() ?: nowCal.get(java.util.Calendar.YEAR)
        val initMonth = (parts.getOrNull(1)?.toIntOrNull()?.minus(1))?.coerceIn(0, 11) ?: nowCal.get(java.util.Calendar.MONTH)
        val initDay = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: nowCal.get(java.util.Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                holidayDate = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
            },
            initYear,
            initMonth,
            initDay
        ).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = Amber50,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Amber100),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "نظام العطل الأسبوعية التلقائي (الجمعة والسبت)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Amber700
                    )
                    Text(
                        text = "يعتبر التطبيق يومي الجمعة والسبت عطلة أسبوعية تلقائياً بالإضافة إلى أي عطلة تضيفها هنا. أيام العطل لا يُسجل فيها غياب ولا تدخل ضمن أيام الدوام الفعلية في حساب درجة الحضور (من 19).",
                        fontSize = 12.sp,
                        color = Slate700
                    )
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
                    Text("إضافة عطلة أو مناسبة جديدة", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Slate900)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = holidayDate,
                            onValueChange = { holidayDate = it },
                            label = { Text("تاريخ العطلة (YYYY-MM-DD)") },
                            trailingIcon = {
                                IconButton(onClick = { openHolidayDatePicker() }) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "اختيار التاريخ من التقويم", tint = Teal700)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { openHolidayDatePicker() }
                        )
                    }
                    OutlinedTextField(
                        value = holidayName,
                        onValueChange = { holidayName = it },
                        label = { Text("اسم العطلة أو المناسبة") },
                        placeholder = { Text("مثال: عطلة رسمية / مناسبة وطنية") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("عطلة رسمية", "مناسبة", "تعطيل طارئ").forEach { t ->
                            val isSel = holidayType == t
                            Surface(
                                color = if (isSel) Slate900 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { holidayType = t }
                            ) {
                                Text(
                                    text = t,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = holidayNotes,
                        onValueChange = { holidayNotes = it },
                        label = { Text("ملاحظات إضافية (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (holidayDate.isBlank() || holidayName.isBlank()) {
                                onShowMessage("يرجى إدخال التاريخ واسم العطلة")
                                return@Button
                            }
                            repository.addOrUpdateHoliday(
                                holidayDate = holidayDate,
                                holidayName = holidayName,
                                holidayType = holidayType,
                                notes = holidayNotes
                            )
                            holidayName = ""
                            holidayNotes = ""
                            onDataChanged()
                            onShowMessage("تم حفظ العطلة واستبعادها من أيام الدوام")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة العطلة", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(holidays, key = { it.id }) { h ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${h.holidayName} (${h.holidayType})",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Slate900
                        )
                        Text(
                            text = "${GradingService.getArabicDayName(h.holidayDate)} ${GradingService.formatDisplayDate(h.holidayDate)}" +
                                if (h.notes.isNotBlank()) " — ${h.notes}" else "",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                    IconButton(onClick = {
                        repository.deleteHoliday(h.id)
                        onDataChanged()
                        onShowMessage("تم حذف العطلة")
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Red600)
                    }
                }
            }
        }
    }
}

// ============================================================================
// DAILY SCHEDULE SCREEN ("الجدول اليومي")
// ============================================================================
private val WEEK_DAYS_ORDER = listOf(
    "الأحد",
    "الاثنين",
    "الثلاثاء",
    "الأربعاء",
    "الخميس",
    "الجمعة",
    "السبت"
)

private fun getArabicLessonLabel(lessonNum: Int): String {
    return when (lessonNum) {
        1 -> "الدرس الأول"
        2 -> "الدرس الثاني"
        3 -> "الدرس الثالث"
        4 -> "الدرس الرابع"
        5 -> "الدرس الخامس"
        6 -> "الدرس السادس"
        7 -> "الدرس السابع"
        8 -> "الدرس الثامن"
        9 -> "الدرس التاسع"
        10 -> "الدرس العاشر"
        else -> "الدرس $lessonNum"
    }
}

private fun parseTimeMinutes(timeStr: String): Int? {
    val parts = timeStr.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
fun DailyScheduleScreen(
    classes: List<ClassEntity>,
    sections: List<SectionEntity>,
    repository: TrackerRepository,
    refreshTick: Int,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val scheduleItems = remember(refreshTick, classes, sections) {
        repository.getDailySchedules()
    }

    var editingItem by remember { mutableStateOf<DailyScheduleEntity?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<DailyScheduleEntity?>(null) }

    var selectedDay by remember { mutableStateOf("الأحد") }
    var selectedClassId by remember(classes) {
        mutableStateOf<Long?>(classes.firstOrNull()?.id)
    }

    val availableSections = remember(sections, selectedClassId) {
        if (selectedClassId == null) emptyList() else sections.filter { it.classId == selectedClassId }
    }

    var selectedSectionId by remember(availableSections) {
        mutableStateOf<Long?>(availableSections.firstOrNull()?.id)
    }

    LaunchedEffect(selectedClassId, availableSections) {
        if (selectedSectionId == null || availableSections.none { it.id == selectedSectionId }) {
            selectedSectionId = availableSections.firstOrNull()?.id
        }
    }

    var maxLessonsCount by remember { mutableIntStateOf(8) }
    var selectedLessonNumber by remember { mutableIntStateOf(1) }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("08:45") }

    var formBannerMessage by remember { mutableStateOf<String?>(null) }
    var formBannerIsError by remember { mutableStateOf(false) }

    fun showFormFeedback(msg: String, isError: Boolean) {
        formBannerMessage = msg
        formBannerIsError = isError
    }

    fun resetFormToAddMode() {
        editingItem = null
        selectedLessonNumber = 1
        startTime = "08:00"
        endTime = "08:45"
    }

    fun loadItemForEdit(item: DailyScheduleEntity) {
        editingItem = item
        selectedDay = item.dayOfWeek
        selectedClassId = item.classId
        selectedSectionId = item.sectionId
        if (item.lessonNumber > maxLessonsCount) {
            maxLessonsCount = item.lessonNumber
        }
        selectedLessonNumber = item.lessonNumber
        startTime = item.startTime
        endTime = item.endTime
        formBannerMessage = null
    }

    fun openAndroidTimePicker(currentValue: String, onPicked: (String) -> Unit) {
        val parts = currentValue.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val initMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val formatted = String.format(java.util.Locale.US, "%02d:%02d", hourOfDay, minute)
                onPicked(formatted)
            },
            initHour,
            initMinute,
            true
        ).show()
    }

    val groupedByDay = remember(scheduleItems) {
        WEEK_DAYS_ORDER.mapNotNull { day ->
            val dayEntries = scheduleItems.filter { it.dayOfWeek == day }
            if (dayEntries.isEmpty()) null else day to dayEntries
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Add / Edit Lesson Form Card
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (editingItem != null) Amber600 else Slate200),
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
                            text = if (editingItem != null) "تعديل الحصة المسجلة" else "إضافة حصة إلى الجدول اليومي",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = if (editingItem != null) Amber700 else Slate900
                        )
                        if (editingItem != null) {
                            TextButton(onClick = {
                                resetFormToAddMode()
                                formBannerMessage = null
                            }) {
                                Text("إلغاء التعديل", color = Red700, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (formBannerMessage != null) {
                        Surface(
                            color = if (formBannerIsError) Red50 else Emerald50,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (formBannerIsError) Red600 else Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formBannerMessage!!,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (formBannerIsError) Red700 else Emerald700,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    // Field 1: اليوم
                    Text(
                        text = "اليوم:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WEEK_DAYS_ORDER.forEach { day ->
                            val isSel = selectedDay == day
                            Surface(
                                color = if (isSel) Slate900 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    selectedDay = day
                                    formBannerMessage = null
                                }
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Field 2: الصف
                    Text(
                        text = "الصف:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    if (classes.isEmpty()) {
                        Text(
                            text = "لا توجد صفوف مسجلة في قاعدة البيانات. يرجى إضافة صف أولاً.",
                            fontSize = 12.sp,
                            color = Red700,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            classes.forEach { cls ->
                                val isSel = selectedClassId == cls.id
                                Surface(
                                    color = if (isSel) Slate900 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        if (selectedClassId != cls.id) {
                                            selectedClassId = cls.id
                                            val firstSec = sections.firstOrNull { it.classId == cls.id }
                                            selectedSectionId = firstSec?.id
                                            formBannerMessage = null
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

                    // Field 3: الشعبة (التابعة للصف المحدد فقط)
                    Text(
                        text = "الشعبة:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate800
                    )
                    if (availableSections.isEmpty()) {
                        Text(
                            text = "لا توجد شعب تابعة لهذا الصف.",
                            fontSize = 12.sp,
                            color = Red700,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableSections.forEach { sec ->
                                val isSel = selectedSectionId == sec.id
                                Surface(
                                    color = if (isSel) Teal700 else Slate100,
                                    contentColor = if (isSel) Color.White else Slate800,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        selectedSectionId = sec.id
                                        formBannerMessage = null
                                    }
                                ) {
                                    Text(
                                        text = "شعبة ${sec.sectionName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Field 4: الدرس (الدرس الأول .. الدرس الثامن + قابل للتوسعة)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الدرس:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate800
                        )
                        TextButton(
                            onClick = { maxLessonsCount += 1 },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("+ إضافة رقم حصة إضافي", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Teal700)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll( androidx.compose.foundation.rememberScrollState() ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..maxLessonsCount).forEach { num ->
                            val isSel = selectedLessonNumber == num
                            Surface(
                                color = if (isSel) Slate900 else Slate100,
                                contentColor = if (isSel) Color.White else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    selectedLessonNumber = num
                                    formBannerMessage = null
                                }
                            ) {
                                Text(
                                    text = getArabicLessonLabel(num),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Field 5 & 6: وقت بدء الدرس و وقت انتهاء الدرس (Android Time Picker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "وقت بدء الدرس:",
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
                                        openAndroidTimePicker(startTime) { picked ->
                                            startTime = picked
                                            formBannerMessage = null
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = startTime,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Teal700, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "وقت انتهاء الدرس:",
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
                                        openAndroidTimePicker(endTime) { picked ->
                                            endTime = picked
                                            formBannerMessage = null
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = endTime,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Slate900
                                    )
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = Teal700, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Submit Button (Add or Save Edit)
                    Button(
                        onClick = {
                            if (selectedDay.isBlank()) {
                                showFormFeedback("يرجى اختيار اليوم", isError = true)
                                return@Button
                            }
                            val clsId = selectedClassId
                            if (clsId == null) {
                                showFormFeedback("يرجى اختيار الصف", isError = true)
                                return@Button
                            }
                            val secId = selectedSectionId
                            if (secId == null) {
                                showFormFeedback("يرجى اختيار الشعبة", isError = true)
                                return@Button
                            }
                            if (selectedLessonNumber < 1) {
                                showFormFeedback("يرجى اختيار رقم الدرس", isError = true)
                                return@Button
                            }
                            val startMins = parseTimeMinutes(startTime)
                            val endMins = parseTimeMinutes(endTime)
                            if (startMins == null || endMins == null) {
                                showFormFeedback("يرجى تحديد وقت البداية ووقت النهاية بشكل صحيح", isError = true)
                                return@Button
                            }
                            if (endMins <= startMins) {
                                showFormFeedback("لا يسمح بأن يكون وقت انتهاء الدرس قبل أو مساوياً لوقت بدء الدرس", isError = true)
                                return@Button
                            }

                            val currentEdit = editingItem
                            if (currentEdit == null) {
                                val (_, isDup) = repository.addDailyScheduleEntry(
                                    dayOfWeek = selectedDay,
                                    classId = clsId,
                                    sectionId = secId,
                                    lessonNumber = selectedLessonNumber,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                                if (isDup) {
                                    showFormFeedback(
                                        "هذه الحصة مسجلة مسبقاً لنفس اليوم والصف والشعبة ورقم الدرس (${getArabicLessonLabel(selectedLessonNumber)})",
                                        isError = true
                                    )
                                } else {
                                    onDataChanged()
                                    showFormFeedback("تمت إضافة الحصة إلى الجدول اليومي بنجاح", isError = false)
                                }
                            } else {
                                val isDup = repository.updateDailyScheduleEntry(
                                    id = currentEdit.id,
                                    dayOfWeek = selectedDay,
                                    classId = clsId,
                                    sectionId = secId,
                                    lessonNumber = selectedLessonNumber,
                                    startTime = startTime,
                                    endTime = endTime
                                )
                                if (isDup) {
                                    showFormFeedback(
                                        "يوجد حصة أخرى مسجلة مسبقاً لنفس اليوم والصف والشعبة ورقم الدرس (${getArabicLessonLabel(selectedLessonNumber)})",
                                        isError = true
                                    )
                                } else {
                                    editingItem = null
                                    onDataChanged()
                                    showFormFeedback("تم حفظ تعديلات الحصة بنجاح", isError = false)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingItem != null) Amber600 else Emerald600
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (editingItem != null) Icons.Default.Save else Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (editingItem != null) "حفظ تعديل الحصة" else "إضافة الحصة إلى الجدول",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 2. Display Weekly Schedule Grouped by Day (Mobile-First RTL Cards)
        if (groupedByDay.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "لا توجد حصص مسجلة في الجدول اليومي حالياً",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate800
                        )
                        Text(
                            text = "استخدم النموذج في الأعلى لإضافة حصصك الأسبوعية حسب اليوم والصف والشعبة والوقت",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }
            }
        } else {
            groupedByDay.forEach { (dayName, dayLessons) ->
                item(key = "day_header_$dayName") {
                    Surface(
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayName,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Surface(
                                color = Teal700,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${dayLessons.size} حصة",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                items(dayLessons, key = { it.id }) { lesson ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${lesson.className} / ${lesson.sectionName}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = getArabicLessonLabel(lesson.lessonNumber),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Teal700
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = Slate600,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "${lesson.startTime} - ${lesson.endTime}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = Slate700
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { loadItemForEdit(lesson) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = Slate800)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تعديل", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                    }
                                    OutlinedButton(
                                        onClick = { pendingDeleteItem = lesson },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Red100)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp), tint = Red600)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Red600)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (pendingDeleteItem != null) {
        val target = pendingDeleteItem!!
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = {
                Text(
                    text = "تأكيد حذف الحصة",
                    fontWeight = FontWeight.ExtraBold,
                    color = Red700
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف هذه الحصة؟\n(${target.dayOfWeek} — ${target.className} / ${target.sectionName} — ${getArabicLessonLabel(target.lessonNumber)})",
                    fontSize = 14.sp,
                    color = Slate800
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteDailyScheduleEntry(target.id)
                        if (editingItem?.id == target.id) {
                            resetFormToAddMode()
                        }
                        pendingDeleteItem = null
                        onDataChanged()
                        showFormFeedback("تم حذف الحصة من الجدول اليومي", isError = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("حذف", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }
}

// ============================================================================
// STUDY PERIODS SCREEN ("الفترات الدراسية")
// ============================================================================
@Composable
fun StudyPeriodsScreen(
    repository: TrackerRepository,
    refreshTick: Int,
    onDataChanged: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val academicYears = remember(refreshTick) {
        repository.getAcademicYears()
    }
    var selectedYearId by remember {
        mutableLongStateOf(academicYears.firstOrNull { it.isActive }?.id ?: academicYears.firstOrNull()?.id ?: 1L)
    }
    LaunchedEffect(academicYears) {
        if (academicYears.isNotEmpty() && academicYears.none { it.id == selectedYearId }) {
            selectedYearId = academicYears.firstOrNull { it.isActive }?.id ?: academicYears.first().id
        }
    }
    val selectedYear = remember(academicYears, selectedYearId) {
        academicYears.firstOrNull { it.id == selectedYearId } ?: academicYears.firstOrNull()
    }

    val stagesForYear = remember(refreshTick, selectedYearId) {
        repository.getStagesForAcademicYear(selectedYearId)
    }
    val periodsForYear = remember(refreshTick, selectedYearId) {
        repository.getStudyPeriods(academicYearId = selectedYearId)
    }

    val firstHalfPeriods = remember(periodsForYear) {
        periodsForYear
            .filter { it.stageType == AcademicStage.FIRST_HALF }
            .sortedWith(compareBy<StudyPeriodEntity> { it.sequenceNumber }.thenBy { it.startDate }.thenBy { it.id })
    }
    val midYearPeriods = remember(periodsForYear) {
        periodsForYear
            .filter { it.stageType == AcademicStage.MID_YEAR }
            .sortedWith(compareBy<StudyPeriodEntity> { it.sequenceNumber }.thenBy { it.startDate }.thenBy { it.id })
    }
    val secondHalfPeriods = remember(periodsForYear) {
        periodsForYear
            .filter { it.stageType == AcademicStage.SECOND_HALF }
            .sortedWith(compareBy<StudyPeriodEntity> { it.sequenceNumber }.thenBy { it.startDate }.thenBy { it.id })
    }

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var bannerIsError by remember { mutableStateOf(false) }

    fun showFeedback(msg: String, isError: Boolean) {
        bannerMessage = msg
        bannerIsError = isError
    }

    // Academic Year dropdown & dialogs
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var showAddYearDialog by remember { mutableStateOf(false) }
    var newYearNameInput by remember { mutableStateOf("") }
    var yearDialogError by remember { mutableStateOf<String?>(null) }

    var editingYear by remember { mutableStateOf<AcademicYearEntity?>(null) }
    var editYearNameInput by remember { mutableStateOf("") }

    var deletingYear by remember { mutableStateOf<AcademicYearEntity?>(null) }

    // Period Add / Edit / Delete dialogs
    var addingToStage by remember { mutableStateOf<AcademicStage?>(null) }
    var addPeriodName by remember { mutableStateOf("") }
    var addStartDate by remember { mutableStateOf("") }
    var addEndDate by remember { mutableStateOf("") }
    var periodDialogError by remember { mutableStateOf<String?>(null) }

    var editingPeriod by remember { mutableStateOf<StudyPeriodEntity?>(null) }
    var editPeriodName by remember { mutableStateOf("") }
    var editStartDate by remember { mutableStateOf("") }
    var editEndDate by remember { mutableStateOf("") }

    var deletingPeriod by remember { mutableStateOf<StudyPeriodEntity?>(null) }

    fun openAndroidDatePicker(initialIsoDate: String, onDatePicked: (String) -> Unit) {
        val parts = initialIsoDate.trim().split("-")
        val nowCal = java.util.Calendar.getInstance()
        val initYear = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val initMonth = (parts.getOrNull(1)?.toIntOrNull()?.minus(1))?.coerceIn(0, 11) ?: nowCal.get(java.util.Calendar.MONTH)
        val initDay = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 31) ?: nowCal.get(java.util.Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val iso = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                onDatePicked(iso)
            },
            initYear,
            initMonth,
            initDay
        ).show()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Academic Year Top Container Card ("العام الدراسي")
        item {
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "العام الدراسي",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = "الحاوية الرئيسية للمراحل الثلاث (${stagesForYear.size} مراحل أساسية)",
                                color = Slate200,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                newYearNameInput = ""
                                yearDialogError = null
                                showAddYearDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عام دراسي جديد", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }

                    // Selector Box: [ 2026-2027 ▼ ] + Edit / Delete Year actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = Slate800,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Emerald600),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { yearDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = Emerald100,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = selectedYear?.yearName ?: "2026-2027",
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "اختر العام الدراسي",
                                        tint = Color.White
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = yearDropdownExpanded,
                                onDismissRequest = { yearDropdownExpanded = false }
                            ) {
                                academicYears.forEach { yr ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = yr.yearName,
                                                    fontWeight = if (yr.id == selectedYearId) FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (yr.id == selectedYearId) Teal700 else Slate900
                                                )
                                                if (yr.id == selectedYearId) {
                                                    Text(
                                                        text = "الحالي",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Emerald700
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            yearDropdownExpanded = false
                                            selectedYearId = yr.id
                                            repository.setActiveAcademicYear(yr.id)
                                            onDataChanged()
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedYear != null) {
                            IconButton(
                                onClick = {
                                    editingYear = selectedYear
                                    editYearNameInput = selectedYear.yearName
                                    yearDialogError = null
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل العام الدراسي", tint = Slate200)
                            }
                            IconButton(
                                onClick = {
                                    deletingYear = selectedYear
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف العام الدراسي", tint = Red100)
                            }
                        }
                    }
                }
            }
        }

        // Feedback Banner
        if (bannerMessage != null) {
            item {
                Surface(
                    color = if (bannerIsError) Red50 else Emerald50,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (bannerIsError) Red600 else Emerald600),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerMessage!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (bannerIsError) Red700 else Emerald700,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { bannerMessage = null }) {
                            Text("إغلاق", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                    }
                }
            }
        }

        // 2. Stage 1: النصف الأول (Open/Unlimited number of periods)
        item {
            AcademicStageCard(
                stage = AcademicStage.FIRST_HALF,
                academicYearName = selectedYear?.yearName ?: "",
                periods = firstHalfPeriods,
                onAddPeriodClick = {
                    val suggestedName = repository.getOrdinalPeriodName(firstHalfPeriods.size + 1)
                    addingToStage = AcademicStage.FIRST_HALF
                    addPeriodName = suggestedName
                    addStartDate = ""
                    addEndDate = ""
                    periodDialogError = null
                },
                onEditPeriodClick = { period ->
                    editingPeriod = period
                    editPeriodName = period.periodName
                    editStartDate = period.startDate
                    editEndDate = period.endDate ?: ""
                    periodDialogError = null
                },
                onDeletePeriodClick = { period ->
                    deletingPeriod = period
                }
            )
        }

        // 3. Stage 2: نصف السنة (Strictly 1 period only)
        item {
            AcademicStageCard(
                stage = AcademicStage.MID_YEAR,
                academicYearName = selectedYear?.yearName ?: "",
                periods = midYearPeriods,
                onAddPeriodClick = {
                    if (midYearPeriods.isNotEmpty()) {
                        val msg = "مرحلة نصف السنة تحتوي على فترة واحدة فقط ولا يسمح بإضافة فترة ثانية داخلها"
                        showFeedback(msg, isError = true)
                        onShowMessage(msg)
                    } else {
                        addingToStage = AcademicStage.MID_YEAR
                        addPeriodName = "الفترة"
                        addStartDate = ""
                        addEndDate = ""
                        periodDialogError = null
                    }
                },
                onEditPeriodClick = { period ->
                    editingPeriod = period
                    editPeriodName = period.periodName
                    editStartDate = period.startDate
                    editEndDate = period.endDate ?: ""
                    periodDialogError = null
                },
                onDeletePeriodClick = { period ->
                    deletingPeriod = period
                }
            )
        }

        // 4. Stage 3: النصف الثاني (Open/Unlimited number of periods)
        item {
            AcademicStageCard(
                stage = AcademicStage.SECOND_HALF,
                academicYearName = selectedYear?.yearName ?: "",
                periods = secondHalfPeriods,
                onAddPeriodClick = {
                    val suggestedName = repository.getOrdinalPeriodName(secondHalfPeriods.size + 1)
                    addingToStage = AcademicStage.SECOND_HALF
                    addPeriodName = suggestedName
                    addStartDate = ""
                    addEndDate = ""
                    periodDialogError = null
                },
                onEditPeriodClick = { period ->
                    editingPeriod = period
                    editPeriodName = period.periodName
                    editStartDate = period.startDate
                    editEndDate = period.endDate ?: ""
                    periodDialogError = null
                },
                onDeletePeriodClick = { period ->
                    deletingPeriod = period
                }
            )
        }
    }

    // Dialog 1: Add Academic Year
    if (showAddYearDialog) {
        AlertDialog(
            onDismissRequest = { showAddYearDialog = false },
            title = {
                Text(
                    text = "إضافة عام دراسي جديد",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (yearDialogError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = yearDialogError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newYearNameInput,
                        onValueChange = {
                            newYearNameInput = it
                            yearDialogError = null
                        },
                        label = { Text("اسم العام الدراسي") },
                        placeholder = { Text("مثال: 2026-2027") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "سيتم تلقائياً تجهيز المراحل الأساسية الثلاث (النصف الأول، نصف السنة، النصف الثاني) داخل هذا العام الدراسي دون تكرار.",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (newId, err) = repository.addAcademicYear(newYearNameInput, makeActive = true)
                        if (err != null) {
                            yearDialogError = err
                        } else {
                            showAddYearDialog = false
                            selectedYearId = newId
                            onDataChanged()
                            val msg = "تم إنشاء العام الدراسي «${newYearNameInput.trim()}» بنجاح"
                            showFeedback(msg, isError = false)
                            onShowMessage(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("إضافة العام الدراسي", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddYearDialog = false }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }

    // Dialog 2: Edit Academic Year
    if (editingYear != null) {
        val targetYear = editingYear!!
        AlertDialog(
            onDismissRequest = { editingYear = null },
            title = {
                Text(
                    text = "تعديل العام الدراسي",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (yearDialogError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = yearDialogError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editYearNameInput,
                        onValueChange = {
                            editYearNameInput = it
                            yearDialogError = null
                        },
                        label = { Text("اسم العام الدراسي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = repository.updateAcademicYear(targetYear.id, editYearNameInput)
                        if (err != null) {
                            yearDialogError = err
                        } else {
                            editingYear = null
                            onDataChanged()
                            showFeedback("تم تحديث اسم العام الدراسي بنجاح", isError = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                ) {
                    Text("حفظ التعديلات", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingYear = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }

    // Dialog 3: Delete Academic Year
    if (deletingYear != null) {
        val targetYear = deletingYear!!
        AlertDialog(
            onDismissRequest = { deletingYear = null },
            title = {
                Text(
                    text = "حذف العام الدراسي",
                    fontWeight = FontWeight.ExtraBold,
                    color = Red700
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف العام الدراسي «${targetYear.yearName}»؟\nملاحظة: لا يسمح النظام بحذف العام الدراسي إذا كانت هناك فترات مسجلة داخله حفاظاً على البيانات.",
                    fontSize = 13.sp,
                    color = Slate800
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = repository.deleteAcademicYear(targetYear.id)
                        deletingYear = null
                        if (err != null) {
                            showFeedback(err, isError = true)
                            onShowMessage(err)
                        } else {
                            val remaining = repository.getAcademicYears()
                            selectedYearId = remaining.firstOrNull { it.isActive }?.id ?: remaining.firstOrNull()?.id ?: 1L
                            onDataChanged()
                            showFeedback("تم حذف العام الدراسي بنجاح", isError = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("حذف", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingYear = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }

    // Dialog 4: Add Period to Stage
    if (addingToStage != null) {
        val targetStage = addingToStage!!
        AlertDialog(
            onDismissRequest = { addingToStage = null },
            title = {
                Text(
                    text = "إضافة فترة — ${targetStage.arabicLabel}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "العام الدراسي: ${selectedYear?.yearName ?: ""}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Teal700
                    )

                    if (periodDialogError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = periodDialogError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = addPeriodName,
                        onValueChange = {
                            addPeriodName = it
                            periodDialogError = null
                        },
                        label = { Text("اسم الفترة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "تاريخ البداية (من):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openAndroidDatePicker(addStartDate.ifBlank { "2026-09-01" }) { picked ->
                                    addStartDate = picked
                                    periodDialogError = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (addStartDate.isNotBlank()) GradingService.formatDisplayDate(addStartDate) else "اختر تاريخ البداية",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (addStartDate.isNotBlank()) Slate900 else Slate500
                            )
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal700)
                        }
                    }

                    Text(
                        text = "تاريخ النهاية (إلى):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openAndroidDatePicker(addEndDate.ifBlank { addStartDate.ifBlank { "2026-10-31" } }) { picked ->
                                    addEndDate = picked
                                    periodDialogError = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (addEndDate.isNotBlank()) GradingService.formatDisplayDate(addEndDate) else "اختر تاريخ النهاية",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = if (addEndDate.isNotBlank()) Slate900 else Slate500
                            )
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal700)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (_, err) = repository.addPeriodToStage(
                            academicYearId = selectedYearId,
                            stageType = targetStage,
                            periodName = addPeriodName,
                            startDate = addStartDate,
                            endDate = addEndDate
                        )
                        if (err != null) {
                            periodDialogError = err
                        } else {
                            addingToStage = null
                            onDataChanged()
                            val msg = "تمت إضافة الفترة داخل «${targetStage.arabicLabel}» بنجاح"
                            showFeedback(msg, isError = false)
                            onShowMessage(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Text("حفظ الفترة", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { addingToStage = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }

    // Dialog 5: Edit Period
    if (editingPeriod != null) {
        val target = editingPeriod!!
        AlertDialog(
            onDismissRequest = { editingPeriod = null },
            title = {
                Text(
                    text = "تعديل الفترة — ${target.stageType.arabicLabel}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Slate900
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (periodDialogError != null) {
                        Surface(
                            color = Red50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = periodDialogError!!,
                                color = Red700,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editPeriodName,
                        onValueChange = {
                            editPeriodName = it
                            periodDialogError = null
                        },
                        label = { Text("اسم الفترة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "تاريخ البداية (من):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openAndroidDatePicker(editStartDate) { picked ->
                                    editStartDate = picked
                                    periodDialogError = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editStartDate.isNotBlank()) GradingService.formatDisplayDate(editStartDate) else "اختر تاريخ البداية",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Slate900
                            )
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal700)
                        }
                    }

                    Text(
                        text = "تاريخ النهاية (إلى):",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        color = Slate800
                    )
                    Surface(
                        color = Slate50,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openAndroidDatePicker(editEndDate.ifBlank { editStartDate }) { picked ->
                                    editEndDate = picked
                                    periodDialogError = null
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editEndDate.isNotBlank()) GradingService.formatDisplayDate(editEndDate) else "اختر تاريخ النهاية",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Slate900
                            )
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal700)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = repository.updateStagePeriod(
                            id = target.id,
                            periodName = editPeriodName,
                            startDate = editStartDate,
                            endDate = editEndDate
                        )
                        if (err != null) {
                            periodDialogError = err
                        } else {
                            editingPeriod = null
                            onDataChanged()
                            showFeedback("تم حفظ تعديلات الفترة بنجاح", isError = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal700)
                ) {
                    Text("حفظ التعديلات", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPeriod = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }

    // Dialog 6: Delete Period Confirmation
    if (deletingPeriod != null) {
        val target = deletingPeriod!!
        AlertDialog(
            onDismissRequest = { deletingPeriod = null },
            title = {
                Text(
                    text = "تأكيد حذف الفترة",
                    fontWeight = FontWeight.ExtraBold,
                    color = Red700
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف «${target.periodName}» من مرحلة ${target.stageType.arabicLabel}؟\n(${GradingService.formatDisplayDate(target.startDate)} → ${GradingService.formatDisplayDate(target.endDate ?: target.startDate)})\n\nملاحظة: يتم التحقق من عدم وجود درجات امتحانات مرتبطة قبل الحذف، ولن يتم المساس بأي سجلات حضور أو تقييمات يومية.",
                    fontSize = 13.sp,
                    color = Slate800
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val err = repository.deleteStagePeriodSafely(target.id)
                        deletingPeriod = null
                        if (err != null) {
                            showFeedback(err, isError = true)
                            onShowMessage(err)
                        } else {
                            onDataChanged()
                            showFeedback("تم حذف الفترة وإعادة ترتيب الفترات بنجاح", isError = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red600)
                ) {
                    Text("حذف الفترة", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPeriod = null }) {
                    Text("إلغاء", fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        )
    }
}

@Composable
private fun AcademicStageCard(
    stage: AcademicStage,
    academicYearName: String,
    periods: List<StudyPeriodEntity>,
    onAddPeriodClick: () -> Unit,
    onEditPeriodClick: (StudyPeriodEntity) -> Unit,
    onDeletePeriodClick: (StudyPeriodEntity) -> Unit
) {
    val isSingleOnly = stage.isSinglePeriodOnly
    val canAddMore = !isSingleOnly || periods.isEmpty()

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stage Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stage.arabicLabel,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Slate900
                        )
                        Surface(
                            color = if (isSingleOnly) Amber50 else Emerald50,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSingleOnly) Amber600 else Emerald600)
                        ) {
                            Text(
                                text = if (isSingleOnly) "فترة واحدة فقط" else "عدد مفتوح (${periods.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSingleOnly) Amber700 else Emerald700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (academicYearName.isNotBlank()) {
                        Text(
                            text = "تابع للعام الدراسي $academicYearName",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }
            }

            Divider(color = Slate200)

            // Periods List inside Stage
            if (periods.isEmpty()) {
                Surface(
                    color = Slate50,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSingleOnly) {
                            "لم يتم تحديد فترة نصف السنة بعد. اضغط على «+ إضافة فترة» بالأسفل لتحديدها."
                        } else {
                            "لا توجد فترات مضافة في ${stage.arabicLabel} حتى الآن."
                        },
                        fontSize = 12.sp,
                        color = Slate600,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    periods.forEach { period ->
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = Slate900,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "#${period.sequenceNumber}",
                                                color = Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = period.periodName,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            color = Slate900
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { onEditPeriodClick(period) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "تعديل الفترة",
                                                tint = Slate700,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeletePeriodClick(period) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "حذف الفترة",
                                                tint = Red600,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                val endDisplay = period.endDate?.let { GradingService.formatDisplayDate(it) } ?: "مفتوحة"
                                Text(
                                    text = "${GradingService.formatDisplayDate(period.startDate)} → $endDisplay",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Teal700
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "من: ${GradingService.formatDisplayDate(period.startDate)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate600
                                    )
                                    Text(
                                        text = "إلى: $endDisplay",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate600
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // [ + إضافة فترة ] Button
            if (canAddMore) {
                OutlinedButton(
                    onClick = onAddPeriodClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Teal700),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Teal700, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+ إضافة فترة",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Teal700
                    )
                }
            } else {
                // For MID_YEAR when its single period already exists: show a clear note & disabled-style button that explains the single-period rule if tapped
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddPeriodClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Slate600, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مكتمل (نصف السنة يحتوي على فترة واحدة فقط)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                    }
                }
            }
        }
    }
}

