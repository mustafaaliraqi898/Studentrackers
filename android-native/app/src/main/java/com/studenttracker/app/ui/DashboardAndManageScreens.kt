package com.studenttracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
                        title = "البحث السريع",
                        subtitle = "بحث فوري عن أي طالب",
                        icon = Icons.Default.Search,
                        tint = Teal700,
                        onClick = { onNavigate(AppScreen.QUICK_SEARCH) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "إدارة الطلاب",
                        subtitle = "إضافة، تعديل، نقل، حذف",
                        icon = Icons.Default.Groups,
                        tint = Slate800,
                        onClick = { onNavigate(AppScreen.STUDENTS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "الصفوف والشعب",
                        subtitle = "إضافة وتعديل الصفوف",
                        icon = Icons.Default.Class,
                        tint = Slate800,
                        onClick = { onNavigate(AppScreen.CLASSES_SECTIONS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "السجلات المحفوظة",
                        subtitle = "استعراض وتعديل الأيام",
                        icon = Icons.Default.History,
                        tint = Amber700,
                        onClick = { onNavigate(AppScreen.SAVED_RECORDS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "التقارير والنتائج",
                        subtitle = "الدرجة من 100 والتصدير",
                        icon = Icons.Default.Assessment,
                        tint = Emerald700,
                        onClick = { onNavigate(AppScreen.REPORTS) }
                    )
                    NavActionTile(
                        modifier = Modifier.weight(1f),
                        title = "العطل والمناسبات",
                        subtitle = "الجمعة والسبت + العطل",
                        icon = Icons.Default.EventBusy,
                        tint = Red700,
                        onClick = { onNavigate(AppScreen.HOLIDAYS) }
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
                                    onShowMessage("الطالب موجود مسبقاً في نفس الصف والشعبة")
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
            Surface(
                color = if (st.isActive) Color.White else Slate100,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Text(
                            text = "${st.className} - شعبة ${st.sectionName}" +
                                if (!st.isActive) " (غير نشط)" else "",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        IconButton(onClick = { onOpenStudentProfile(st.id) }) {
                            Icon(Icons.Default.Visibility, contentDescription = "ملف الطالب", tint = Teal700)
                        }
                        IconButton(onClick = {
                            editingStudent = st
                            editingStudentName = st.studentName
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "تعديل الاسم", tint = Slate600)
                        }
                        IconButton(onClick = { studentToTransfer = st }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "نقل شعبة", tint = Amber700)
                        }
                        IconButton(onClick = { studentToDelete = st }) {
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
                        repository.updateStudentName(target.id, editingStudentName)
                        editingStudent = null
                        onDataChanged()
                        onShowMessage("تم تحديث اسم الطالب")
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
                    repository.transferStudentSection(target.id, targetClsId, targetSecId)
                    studentToTransfer = null
                    onDataChanged()
                    onShowMessage("تم نقل الطالب بنجاح")
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
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    refreshTick: Int,
    onOpenStudentProfile: (Long) -> Unit,
    onJumpToDailyTracking: (Long, Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterClassId by remember { mutableStateOf<Long?>(null) }
    var filterSectionId by remember { mutableStateOf<Long?>(null) }

    val results = remember(refreshTick, query, filterClassId, filterSectionId) {
        repository.searchStudents(query, filterClassId, filterSectionId)
    }
    val allGradesMap = remember(refreshTick, results) {
        GradingService.calculateGradesForClass(
            dbHelper = dbHelper,
            repository = repository,
            classId = filterClassId,
            sectionId = filterSectionId
        ).associateBy { it.studentId }
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
                }
            }
        }

        item {
            Text(
                text = "نتائج البحث (${results.size} طالب)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate600
            )
        }

        items(results, key = { it.id }) { st ->
            val g = allGradesMap[st.id]
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
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
                                text = st.studentName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            Text(
                                text = "${st.className} — شعبة ${st.sectionName}",
                                fontSize = 12.sp,
                                color = Slate600
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
                        OutlinedButton(
                            onClick = { onOpenStudentProfile(st.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ملف الطالب والدرجات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onJumpToDailyTracking(st.classId, st.sectionId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal700),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("فتح المتابعة اليومية", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
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
    var holidayDate by remember { mutableStateOf("2026-10-15") }
    var holidayName by remember { mutableStateOf("") }
    var holidayType by remember { mutableStateOf("عطلة رسمية") }
    var holidayNotes by remember { mutableStateOf("") }

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
                    OutlinedTextField(
                        value = holidayDate,
                        onValueChange = { holidayDate = it },
                        label = { Text("تاريخ العطلة (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
