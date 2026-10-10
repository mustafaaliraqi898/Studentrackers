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
import com.studenttracker.app.service.AppExitBackupService
import com.studenttracker.app.service.DailyBackupWorker
import com.studenttracker.app.service.ExcelAndBackupService
import com.studenttracker.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
        val repository = TrackerRepository(dbHelper)

        // Start lightweight task-removal watcher service (does NOT create a backup on launch;
        // only triggers onTaskRemoved when the user swipes the app away from Recents or taps "Close All")
        AppExitBackupService.ensureRunning(applicationContext)

        setContent {
            StudentTrackerRtlTheme {
                StudentTrackerMainApp(
                    dbHelper = dbHelper,
                    repository = repository,
                    onExitApp = {
                        val db = AppDatabaseHelper.getInstance(applicationContext)
                        ExcelAndBackupService.createAutoBackupOnAppExit(applicationContext, db)
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppExitBackupService.ensureRunning(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
        runCatching { dbHelper.flushAndCheckpoint() }
        if (isFinishing && !isChangingConfigurations) {
            ExcelAndBackupService.createAutoBackupOnAppExit(applicationContext, dbHelper)
        }
    }

    override fun onStop() {
        super.onStop()
        // Flush SQLite WAL to main DB file whenever the Activity goes to background so the file on disk is 100% up-to-date.
        // Only create an actual Backup file here if the Activity is explicitly finishing (e.g. Back button exit).
        // Going to Home screen or switching apps has isFinishing == false, so NO backup is created then.
        val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
        runCatching { dbHelper.flushAndCheckpoint() }

        if (isFinishing && !isChangingConfigurations) {
            ExcelAndBackupService.createAutoBackupOnAppExit(applicationContext, dbHelper)
        }
    }

    override fun onDestroy() {
        // When the user swipes the app from Recents while it was in background, onDestroy() may be called
        // with !isChangingConfigurations (alongside AppExitBackupService.onTaskRemoved).
        // The 10-second persistent lock in createAutoBackupOnAppExit guarantees only 1 backup is created.
        if (!isChangingConfigurations) {
            val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
            ExcelAndBackupService.createAutoBackupOnAppExit(applicationContext, dbHelper)
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTrackerMainApp(
    dbHelper: AppDatabaseHelper,
    repository: TrackerRepository,
    onExitApp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var refreshTick by remember { mutableIntStateOf(0) }
    val classes = remember(refreshTick) { repository.getClasses() }
    val sections = remember(refreshTick) { repository.getSections() }
    val holidays = remember(refreshTick) { repository.getHolidays() }
    val savedSessions = remember(refreshTick) { repository.getSavedSessionsSummary() }
    val dbStats = remember(refreshTick) { repository.getDatabaseStats() }

    var selectedClassId by remember {
        mutableLongStateOf(classes.firstOrNull()?.id ?: 1L)
    }
    LaunchedEffect(classes) {
        if (classes.isNotEmpty() && classes.none { it.id == selectedClassId }) {
            selectedClassId = classes.first().id
        }
    }
    val currentClassSections = remember(sections, selectedClassId) {
        sections.filter { it.classId == selectedClassId }
    }
    var selectedSectionId by remember {
        mutableLongStateOf(currentClassSections.firstOrNull()?.id ?: 1L)
    }
    LaunchedEffect(currentClassSections) {
        if (currentClassSections.isNotEmpty() && currentClassSections.none { it.id == selectedSectionId }) {
            selectedSectionId = currentClassSections.first().id
        }
    }
    var selectedDate by remember {
        mutableStateOf(java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString())
    }
    var explicitSavedSessionJump by remember { mutableStateOf(false) }
    var selectedStudentProfileId by remember { mutableLongStateOf(1L) }

    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    var isDailyDirty by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun showToastMessage(msg: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(Unit) {
        val migrationWarn = AppDatabaseHelper.lastMigrationDuplicateWarning
        if (!migrationWarn.isNullOrBlank()) {
            snackbarHostState.showSnackbar(migrationWarn, duration = SnackbarDuration.Long)
            AppDatabaseHelper.lastMigrationDuplicateWarning = null
        }
    }

    fun guardedAction(action: () -> Unit) {
        if (currentScreen == AppScreen.DAILY_TRACKING && isDailyDirty) {
            pendingNavigation = action
        } else {
            action()
        }
    }

    BackHandler(enabled = true) {
        if (currentScreen != AppScreen.DASHBOARD) {
            guardedAction {
                currentScreen = AppScreen.DASHBOARD
            }
        } else {
            onExitApp()
        }
    }

    Scaffold(
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
                    Triple(AppScreen.AVERAGES_TABLE, "المعدل", Icons.Default.TableChart),
                    Triple(AppScreen.REPORTS, "النتائج", Icons.Default.Assessment)
                )
                navItems.forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            guardedAction {
                                if (screen == AppScreen.DAILY_TRACKING && currentScreen != AppScreen.DAILY_TRACKING) {
                                    selectedDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
                                    explicitSavedSessionJump = false
                                }
                                currentScreen = screen
                            }
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
                    onNavigate = { target ->
                        if (target == AppScreen.DAILY_TRACKING) {
                            selectedDate = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
                            explicitSavedSessionJump = false
                        }
                        currentScreen = target
                    },
                    onOpenSavedSession = { clsId, secId, date ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        selectedDate = date
                        explicitSavedSessionJump = true
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
                    refreshTick = refreshTick,
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
                    onShowMessage = ::showToastMessage,
                    preserveInitialDateFromSavedSession = explicitSavedSessionJump,
                    onConsumeSavedSessionJump = { explicitSavedSessionJump = false }
                )

                AppScreen.ABSENCE_TRACKING -> AbsenceTrackingScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
                    onSelectClass = { clsId ->
                        selectedClassId = clsId
                        val firstSec = sections.firstOrNull { it.classId == clsId }
                        if (firstSec != null) selectedSectionId = firstSec.id
                    },
                    onSelectSection = { secId -> selectedSectionId = secId },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.DAILY_SCHEDULE -> DailyScheduleScreen(
                    classes = classes,
                    sections = sections,
                    repository = repository,
                    refreshTick = refreshTick,
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.DAILY_PLAN -> DailyPlanScreen(
                    classes = classes,
                    sections = sections,
                    repository = repository,
                    refreshTick = refreshTick,
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.STUDY_PERIODS -> StudyPeriodsScreen(
                    repository = repository,
                    refreshTick = refreshTick,
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
                    refreshTick = refreshTick,
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
                    defaultClassId = selectedClassId,
                    defaultSectionId = selectedSectionId,
                    selectedDate = selectedDate,
                    holidays = holidays,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
                    onOpenStudentProfile = { stId ->
                        selectedStudentProfileId = stId
                        currentScreen = AppScreen.STUDENT_PROFILE
                    },
                    onJumpToDailyTracking = { clsId, secId ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        currentScreen = AppScreen.DAILY_TRACKING
                    },
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.SAVED_RECORDS -> SavedRecordsScreen(
                    classes = classes,
                    sections = sections,
                    savedSessions = savedSessions,
                    repository = repository,
                    refreshTick = refreshTick,
                    onOpenSavedSession = { clsId, secId, date ->
                        selectedClassId = clsId
                        selectedSectionId = secId
                        selectedDate = date
                        explicitSavedSessionJump = true
                        currentScreen = AppScreen.DAILY_TRACKING
                    },
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.STUDENT_PROFILE -> StudentProfileScreen(
                    studentId = selectedStudentProfileId,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
                    onBack = { currentScreen = AppScreen.STUDENTS },
                    onDataChanged = { refreshTick++ },
                    onShowMessage = ::showToastMessage
                )

                AppScreen.AVERAGES_TABLE -> AveragesTableScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
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

                AppScreen.PERIOD_COMPARISON -> PeriodComparisonScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
                    onSelectClass = { clsId ->
                        selectedClassId = clsId
                        val firstSec = sections.firstOrNull { it.classId == clsId }
                        if (firstSec != null) selectedSectionId = firstSec.id
                    },
                    onSelectSection = { secId -> selectedSectionId = secId }
                )

                AppScreen.REPORTS -> ReportsScreen(
                    classes = classes,
                    sections = sections,
                    selectedClassId = selectedClassId,
                    selectedSectionId = selectedSectionId,
                    dbHelper = dbHelper,
                    repository = repository,
                    refreshTick = refreshTick,
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
                    onDataChanged = { refreshTick++ },
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

            // Centralized system-wide notifications & alerts positioned at the top of the screen
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
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
