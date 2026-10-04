package com.studenttracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studenttracker.app.data.AppDatabaseHelper
import com.studenttracker.app.data.TrackerRepository
import com.studenttracker.app.service.DailyBackupWorker
import com.studenttracker.app.service.ExcelAndBackupService
import com.studenttracker.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
        val repository = TrackerRepository(dbHelper)

        // Ensure daily automatic local backup on first launch of the day & schedule background WorkManager job
        ExcelAndBackupService.ensureDailyAutoBackup(applicationContext, dbHelper)
        DailyBackupWorker.scheduleDailyBackup(applicationContext)

        setContent {
            StudentTrackerRtlTheme {
                StudentTrackerMainApp(
                    dbHelper = dbHelper,
                    repository = repository
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTrackerMainApp(
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var refreshTick by remember { mutableIntStateOf(0) }
    val classes = remember(refreshTick) { repository.getClasses() }
    val sections = remember(refreshTick) { repository.getSections() }
    val holidays = remember(refreshTick) { repository.getHolidays() }
    val savedSessions = remember(refreshTick) { repository.getSavedSessionsSummary() }
    val dbStats = remember(refreshTick) { repository.getDatabaseStats() }

    var selectedClassId by remember(classes) {
        mutableLongStateOf(classes.firstOrNull()?.id ?: 1L)
    }
    val currentClassSections = remember(sections, selectedClassId) {
        sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember(currentClassSections) {
        mutableLongStateOf(currentClassSections.firstOrNull()?.id ?: 1L)
    }
    var selectedDate by remember { mutableStateOf("2026-10-04") }
    var selectedStudentProfileId by remember { mutableLongStateOf(1L) }

    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    var isDailyDirty by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun showToastMessage(msg: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    fun guardedAction(action: () -> Unit) {
        if (currentScreen == AppScreen.DAILY_TRACKING && isDailyDirty) {
            pendingNavigation = action
        } else {
            action()
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD) {
        guardedAction {
            currentScreen = AppScreen.DASHBOARD
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = currentScreen.titleAr,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (currentScreen != AppScreen.DASHBOARD) {
                        IconButton(onClick = {
                            guardedAction { currentScreen = AppScreen.DASHBOARD }
                        }) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        guardedAction { currentScreen = AppScreen.QUICK_SEARCH }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "بحث سريع", tint = Color.White)
                    }
                    IconButton(onClick = {
                        guardedAction { currentScreen = AppScreen.EXCEL_BACKUP }
                    }) {
                        Icon(Icons.Default.Storage, contentDescription = "النسخ الاحتياطي وExcel", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(AppScreen.DASHBOARD, "الرئيسية", Icons.Default.Home),
                    Triple(AppScreen.DAILY_TRACKING, "اليومي", Icons.Default.FactCheck),
                    Triple(AppScreen.STUDENTS, "الطلاب", Icons.Default.Groups),
                    Triple(AppScreen.SAVED_RECORDS, "السجلات", Icons.Default.History),
                    Triple(AppScreen.REPORTS, "النتائج", Icons.Default.Assessment)
                )
                navItems.forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            guardedAction { currentScreen = screen }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbStats = dbStats,
                    savedSessions = savedSessions,
                    onSelectClass = { clsId ->
                        selectedClassId = clsId
                        val firstSec = sections.firstOrNull { it.classId == clsId }
                        if (firstSec != null) selectedSectionId = firstSec.id
                    },
                    onSelectSection = { secId -> selectedSectionId = secId },
                    onNavigate = { target -> currentScreen = target },
                    onOpenSavedSession = { clsId, secId, date ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        selectedDate = date
                        currentScreen = AppScreen.DAILY_TRACKING
                    }
                )

                AppScreen.DAILY_TRACKING -> DailyTrackingScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    selectedDate = selectedDate,
                    holidays = holidays,
                    dbHelper = dbHelper,
                    repository = repository,
                    isDirty = isDailyDirty,
                    onDirtyChange = { isDailyDirty = it },
                    onSelectClass = { clsId ->
                        guardedAction {
                            selectedClassId = clsId
                            val firstSec = sections.firstOrNull { it.classId == clsId }
                            if (firstSec != null) selectedSectionId = firstSec.id
                        }
                    },
                    onSelectSection = { secId ->
                        guardedAction { selectedSectionId = secId }
                    },
                    onSelectDate = { d ->
                        guardedAction { selectedDate = d }
                    },
                    onOpenStudentProfile = { stId ->
                        guardedAction {
                            selectedStudentProfileId = stId
                            currentScreen = AppScreen.STUDENT_PROFILE
                        }
                    },
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.STUDENTS -> StudentsScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    onSelectClass = { clsId ->
                        selectedClassId = clsId
                        val firstSec = sections.firstOrNull { it.classId == clsId }
                        if (firstSec != null) selectedSectionId = firstSec.id
                    },
                    onSelectSection = { secId -> selectedSectionId = secId },
                    onOpenStudentProfile = { stId ->
                        selectedStudentProfileId = stId
                        currentScreen = AppScreen.STUDENT_PROFILE
                    },
                    onNavigateToExcel = { currentScreen = AppScreen.EXCEL_BACKUP },
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.CLASSES_SECTIONS -> ClassesSectionsScreen(
                    classes = classes,
                    sections = sections,
                    repository = repository,
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.QUICK_SEARCH -> QuickSearchScreen(
                    classes = classes,
                    sections = sections,
                    dbHelper = dbHelper,
                    repository = repository,
                    onOpenStudentProfile = { stId ->
                        selectedStudentProfileId = stId
                        currentScreen = AppScreen.STUDENT_PROFILE
                    },
                    onJumpToDailyTracking = { clsId, secId ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        currentScreen = AppScreen.DAILY_TRACKING
                    }
                )

                AppScreen.SAVED_RECORDS -> SavedRecordsScreen(
                    classes = classes,
                    sections = sections,
                    savedSessions = savedSessions,
                    onOpenSavedSession = { clsId, secId, date ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        selectedDate = date
                        currentScreen = AppScreen.DAILY_TRACKING
                    }
                )

                AppScreen.STUDENT_PROFILE -> StudentProfileScreen(
                    studentId = selectedStudentProfileId,
                    dbHelper = dbHelper,
                    repository = repository,
                    onBack = { currentScreen = AppScreen.STUDENTS }
                )

                AppScreen.REPORTS -> ReportsScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    onSelectClass = { clsId ->
                        selectedClassId = clsId
                        val firstSec = sections.firstOrNull { it.classId == clsId }
                        if (firstSec != null) selectedSectionId = firstSec.id
                    },
                    onSelectSection = { secId -> selectedSectionId = secId },
                    onOpenStudentProfile = { stId ->
                        selectedStudentProfileId = stId
                        currentScreen = AppScreen.STUDENT_PROFILE
                    },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.HOLIDAYS -> HolidaysScreen(
                    holidays = holidays,
                    repository = repository,
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.EXCEL_BACKUP -> ExcelAndBackupScreen(
                    dbHelper = dbHelper,
                    repository = repository,
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )
            }
        }
    }

    if (pendingNavigation != null) {
        UnsavedChangesAlertDialog(
            onSaveAndContinue = {
                // Trigger user to save on the screen or discard
                pendingNavigation = null
                showToastMessage("يرجى الضغط على زر 'حفظ السجل اليومي' بالأسفل لحفظ التعديلات")
            },
            onDiscardAndContinue = {
                val nav = pendingNavigation
                pendingNavigation = null
                isDailyDirty = false
                nav?.invoke()
            },
            onCancel = {
                pendingNavigation = null
            }
        )
    }
}
