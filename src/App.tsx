/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  ArchiveRestore,
  ClipboardList,
  Database,
  FolderClock,
  Home,
  Search,
  Users,
} from 'lucide-react';
import {
  AppScreen,
  AttendanceStatus,
  ClassEntity,
  HolidayEntity,
  SectionEntity,
  StudentEntity,
} from './types';
import { clearAllAndResetDatabase, getDatabase } from './db/sqliteEngine';
import { TrackerRepository } from './repositories/trackerRepository';
import { BackupService } from './services/backupService';
import { createDefaultStudentEntry } from './services/gradingService';
import { DashboardView } from './components/DashboardView';
import { ClassesSectionsView } from './components/ClassesSectionsView';
import { StudentsView } from './components/StudentsView';
import { DailyTrackingView } from './components/DailyTrackingView';
import { HolidaysView } from './components/HolidaysView';
import { SavedRecordsView } from './components/SavedRecordsView';
import { StudentProfileView } from './components/StudentProfileView';
import { ReportsView } from './components/ReportsView';
import { ExcelCenterView } from './components/ExcelCenterView';
import { BackupRestoreView } from './components/BackupRestoreView';
import { SchemaAndSettingsView } from './components/SchemaAndSettingsView';
import { UnsavedChangesModal } from './components/UnsavedChangesModal';
import { StudentSearchModal } from './components/StudentSearchModal';
import { OfflineIndicator, PWAInstallButton } from './components/PWAInstallButton';

export default function App() {
  const [isDbReady, setIsDbReady] = useState(false);
  const [dbError, setDbError] = useState<string | null>(null);

  const [classes, setClasses] = useState<ClassEntity[]>([]);
  const [sections, setSections] = useState<SectionEntity[]>([]);
  const [students, setStudents] = useState<StudentEntity[]>([]);
  const [holidays, setHolidays] = useState<HolidayEntity[]>([]);
  const [stats, setStats] = useState({
    classesCount: 0,
    sectionsCount: 0,
    studentsCount: 0,
    recordsCount: 0,
    holidaysCount: 0,
  });

  // Active Navigation & Selection Context
  const [currentScreen, setCurrentScreen] = useState<AppScreen>('dashboard');
  const [selectedClassId, setSelectedClassId] = useState<number>(1);
  const [selectedSectionId, setSelectedSectionId] = useState<number>(1);
  const [selectedDate, setSelectedDate] = useState<string>('2026-10-04'); // Sunday working day
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(1);

  // Global Fast Student Search Modal State
  const [isSearchModalOpen, setIsSearchModalOpen] = useState<boolean>(false);

  // Unsaved Changes Protection State
  const [isDirty, setIsDirty] = useState<boolean>(false);
  const [pendingAction, setPendingAction] = useState<(() => void) | null>(null);
  const pendingSaveHandlerRef = useRef<(() => Promise<void>) | null>(null);

  const registerPendingSaveHandler = (fn: () => Promise<void>) => {
    pendingSaveHandlerRef.current = fn;
  };

  const refreshAllFromSQLite = async () => {
    const [clsList, secList, stList, holList, dbStats] = await Promise.all([
      TrackerRepository.getClasses(),
      TrackerRepository.getSections(),
      TrackerRepository.getStudents(undefined, undefined, true),
      TrackerRepository.getHolidays(),
      TrackerRepository.getDatabaseStats(),
    ]);

    setClasses(clsList);
    setSections(secList);
    setStudents(stList);
    setHolidays(holList);
    setStats(dbStats);

    if (clsList.length > 0 && !clsList.some((c) => c.id === selectedClassId)) {
      const firstCls = clsList[0].id;
      setSelectedClassId(firstCls);
      const firstSec = secList.find((s) => s.classId === firstCls);
      if (firstSec) setSelectedSectionId(firstSec.id);
    } else {
      const validSecs = secList.filter((s) => s.classId === selectedClassId);
      if (validSecs.length > 0 && !validSecs.some((s) => s.id === selectedSectionId)) {
        setSelectedSectionId(validSecs[0].id);
      }
    }
  };

  useEffect(() => {
    let mounted = true;
    async function bootstrapSQLite() {
      try {
        await getDatabase();
        if (!mounted) return;
        await refreshAllFromSQLite();
        // Automatically check and create daily backup on first app launch each day (max 1 auto backup per day)
        await BackupService.ensureDailyAutoBackup();
        if (mounted) setIsDbReady(true);
      } catch (err: unknown) {
        console.error('SQLite Initialization error:', err);
        if (mounted) {
          setDbError(
            err instanceof Error
              ? err.message
              : 'تعذر تهيئة محرك قاعدة بيانات SQLite المحلي.'
          );
        }
      }
    }
    bootstrapSQLite();
    return () => {
      mounted = false;
    };
  }, []);

  const guardAction = (action: () => void) => {
    if (currentScreen === 'daily-tracking' && isDirty) {
      setPendingAction(() => action);
    } else {
      action();
    }
  };

  const handleNavigate = (
    screen: AppScreen,
    params?: { date?: string; studentId?: number }
  ) => {
    guardAction(() => {
      if (params?.date) setSelectedDate(params.date);
      if (params?.studentId !== undefined) setSelectedStudentId(params.studentId);
      setCurrentScreen(screen);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  };

  const handleSelectClass = (classId: number) => {
    if (classId === selectedClassId) return;
    guardAction(() => {
      setSelectedClassId(classId);
      const classSecs = sections.filter((s) => s.classId === classId);
      if (classSecs.length > 0) {
        setSelectedSectionId(classSecs[0].id);
      }
    });
  };

  const handleSelectSection = (sectionId: number) => {
    if (sectionId === selectedSectionId) return;
    guardAction(() => {
      setSelectedSectionId(sectionId);
    });
  };

  const handleSelectDate = (date: string) => {
    if (date === selectedDate) return;
    guardAction(() => {
      setSelectedDate(date);
    });
  };

  const handleOpenSavedRecord = (
    classId: number,
    sectionId: number,
    date: string
  ) => {
    guardAction(() => {
      setSelectedClassId(classId);
      setSelectedSectionId(sectionId);
      setSelectedDate(date);
      setCurrentScreen('daily-tracking');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  };

  // Quick Attendance Marking from Global Search Modal
  const handleQuickAttendanceToggleFromSearch = async (
    student: StudentEntity,
    status: AttendanceStatus
  ) => {
    const existingMap = await TrackerRepository.getSessionEntries(
      student.classId,
      student.sectionId,
      selectedDate
    );
    const currentEntry =
      existingMap[student.id] || createDefaultStudentEntry(student.id);
    const updatedMap = {
      ...existingMap,
      [student.id]: {
        ...currentEntry,
        attendance: status,
      },
    };
    await TrackerRepository.saveDailySession(
      student.classId,
      student.sectionId,
      selectedDate,
      updatedMap
    );
    await refreshAllFromSQLite();
  };

  // Unsaved Changes Modal Handlers
  const handleModalSaveAndExit = async () => {
    if (pendingSaveHandlerRef.current) {
      await pendingSaveHandlerRef.current();
    }
    setIsDirty(false);
    if (pendingAction) {
      pendingAction();
      setPendingAction(null);
    }
  };

  const handleModalExitWithoutSaving = () => {
    setIsDirty(false);
    if (pendingAction) {
      pendingAction();
      setPendingAction(null);
    }
  };

  const handleModalCancel = () => {
    setPendingAction(null);
  };

  if (dbError) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-slate-50">
        <div className="bg-white rounded-2xl p-6 max-w-md w-full border border-rose-200 text-center space-y-3">
          <h2 className="text-base font-extrabold text-rose-800">
            خطأ في تهيئة قاعدة البيانات
          </h2>
          <p className="text-xs text-slate-600">{dbError}</p>
        </div>
      </div>
    );
  }

  if (!isDbReady) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-slate-50">
        <div className="flex flex-col items-center gap-3 text-slate-700">
          <Database className="w-8 h-8 text-emerald-600 animate-pulse" />
          <p className="text-sm font-extrabold">
            جاري تهيئة قاعدة بيانات SQLite المحلية...
          </p>
        </div>
      </div>
    );
  }

  const activeStudent = students.find((s) => s.id === selectedStudentId);
  const activeStudentsOnly = students.filter((s) => s.isActive);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 flex flex-col">
      {/* Top Bar Contract: Zone 1 Brand (single text element) — Zone 2 Nav Links — Zone 3 Primary Actions */}
      <header className="sticky top-0 z-40 h-14 bg-white/95 backdrop-blur-md border-b border-slate-200/80 px-4 sm:px-6 flex items-center justify-between">
        {/* Zone 1: Single text element wordmark */}
        <a
          href="#dashboard"
          onClick={(e) => {
            e.preventDefault();
            handleNavigate('dashboard');
          }}
          className="text-base sm:text-lg font-extrabold tracking-tight text-slate-900 whitespace-nowrap"
        >
          سجل متابعة الطلاب
        </a>

        {/* Zone 2: 5 Single-Line Navigation Links */}
        <nav className="hidden md:flex items-center gap-5 text-xs font-bold text-slate-600">
          <a
            href="#dashboard"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate('dashboard');
            }}
            className={`transition-colors whitespace-nowrap hover:underline underline-offset-8 ${
              currentScreen === 'dashboard'
                ? 'text-emerald-700 underline'
                : 'hover:text-slate-900'
            }`}
          >
            الرئيسية
          </a>
          <a
            href="#students"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate('students');
            }}
            className={`transition-colors whitespace-nowrap hover:underline underline-offset-8 ${
              currentScreen === 'students' ||
              currentScreen === 'student-profile' ||
              currentScreen === 'classes-sections'
                ? 'text-emerald-700 underline'
                : 'hover:text-slate-900'
            }`}
          >
            الطلاب والشعب
          </a>
          <a
            href="#daily-tracking"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate('daily-tracking');
            }}
            className={`transition-colors whitespace-nowrap hover:underline underline-offset-8 ${
              currentScreen === 'daily-tracking'
                ? 'text-emerald-700 underline'
                : 'hover:text-slate-900'
            }`}
          >
            المتابعة اليومية
          </a>
          <a
            href="#reports"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate('reports');
            }}
            className={`transition-colors whitespace-nowrap hover:underline underline-offset-8 ${
              currentScreen === 'reports' || currentScreen === 'excel-center'
                ? 'text-emerald-700 underline'
                : 'hover:text-slate-900'
            }`}
          >
            التقارير وExcel
          </a>
          <a
            href="#backup-restore"
            onClick={(e) => {
              e.preventDefault();
              handleNavigate('backup-restore');
            }}
            className={`transition-colors whitespace-nowrap hover:underline underline-offset-8 ${
              currentScreen === 'backup-restore'
                ? 'text-emerald-700 underline'
                : 'hover:text-slate-900'
            }`}
          >
            النسخ الاحتياطي
          </a>
        </nav>

        {/* Zone 3: 1-2 Primary Actions (Fast Student Search + Install App) */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setIsSearchModalOpen(true)}
            className="min-h-[38px] px-3 py-1.5 text-xs font-extrabold text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-xl flex items-center gap-1.5 transition-colors whitespace-nowrap"
          >
            <Search className="w-3.5 h-3.5 text-emerald-700" />
            <span>بحث عن طالب</span>
          </button>
          <PWAInstallButton variant="compact" />
        </div>
      </header>

      {/* Main Content Container */}
      <main className="flex-1 w-full max-w-3xl mx-auto px-4 pt-4 pb-20">
        {currentScreen === 'dashboard' && (
          <DashboardView
            classes={classes}
            sections={sections}
            students={activeStudentsOnly}
            holidays={holidays}
            stats={stats}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onNavigate={handleNavigate}
            onOpenSearch={() => setIsSearchModalOpen(true)}
          />
        )}

        {currentScreen === 'classes-sections' && (
          <ClassesSectionsView
            classes={classes}
            sections={sections}
            students={activeStudentsOnly}
            onAddClass={async (name) => {
              const newId = await TrackerRepository.addClass(name);
              await TrackerRepository.addSection(newId, 'أ');
              await refreshAllFromSQLite();
            }}
            onUpdateClass={async (id, name) => {
              await TrackerRepository.updateClass(id, name);
              await refreshAllFromSQLite();
            }}
            onDeleteClass={async (id) => {
              await TrackerRepository.deleteClass(id);
              await refreshAllFromSQLite();
            }}
            onAddSection={async (clsId, secName) => {
              await TrackerRepository.addSection(clsId, secName);
              await refreshAllFromSQLite();
            }}
            onUpdateSection={async (id, secName) => {
              await TrackerRepository.updateSection(id, secName);
              await refreshAllFromSQLite();
            }}
            onDeleteSection={async (id) => {
              await TrackerRepository.deleteSection(id);
              await refreshAllFromSQLite();
            }}
          />
        )}

        {currentScreen === 'students' && (
          <StudentsView
            classes={classes}
            sections={sections}
            students={students}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onAddStudent={async (name, clsId, secId) => {
              const res = await TrackerRepository.addStudent(name, clsId, secId);
              await refreshAllFromSQLite();
              return { isDuplicate: res.isDuplicate };
            }}
            onUpdateStudentName={async (stId, newName) => {
              await TrackerRepository.updateStudentName(stId, newName);
              await refreshAllFromSQLite();
            }}
            onToggleStudentActive={async (stId, isActive) => {
              await TrackerRepository.toggleStudentActiveStatus(stId, isActive);
              await refreshAllFromSQLite();
            }}
            onTransferStudent={async (stId, targetClsId, targetSecId) => {
              await TrackerRepository.transferStudentSection(
                stId,
                targetClsId,
                targetSecId
              );
              await refreshAllFromSQLite();
            }}
            onDeleteStudent={async (stId) => {
              await TrackerRepository.deleteStudent(stId);
              await refreshAllFromSQLite();
            }}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'daily-tracking' && (
          <DailyTrackingView
            classes={classes}
            sections={sections}
            students={activeStudentsOnly}
            holidays={holidays}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            selectedDate={selectedDate}
            isDirty={isDirty}
            setIsDirty={setIsDirty}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onSelectDate={handleSelectDate}
            onSaveComplete={refreshAllFromSQLite}
            onNavigate={handleNavigate}
            registerPendingSaveHandler={registerPendingSaveHandler}
          />
        )}

        {currentScreen === 'holidays' && (
          <HolidaysView
            holidays={holidays}
            onAddHoliday={async (d, name, type, notes) => {
              await TrackerRepository.addHoliday(d, name, type, notes);
              await refreshAllFromSQLite();
            }}
            onUpdateHoliday={async (id, d, name, type, notes) => {
              await TrackerRepository.updateHoliday(id, d, name, type, notes);
              await refreshAllFromSQLite();
            }}
            onDeleteHoliday={async (id) => {
              await TrackerRepository.deleteHoliday(id);
              await refreshAllFromSQLite();
            }}
          />
        )}

        {currentScreen === 'saved-records' && (
          <SavedRecordsView
            classes={classes}
            sections={sections}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onOpenRecord={handleOpenSavedRecord}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'student-profile' && (
          <StudentProfileView
            student={activeStudent}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'reports' && (
          <ReportsView
            classes={classes}
            sections={sections}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onNavigate={handleNavigate}
          />
        )}

        {currentScreen === 'excel-center' && (
          <ExcelCenterView
            classes={classes}
            sections={sections}
            selectedClassId={selectedClassId}
            selectedSectionId={selectedSectionId}
            onSelectClass={handleSelectClass}
            onSelectSection={handleSelectSection}
            onRefreshData={refreshAllFromSQLite}
          />
        )}

        {currentScreen === 'backup-restore' && (
          <BackupRestoreView onDatabaseRestored={refreshAllFromSQLite} />
        )}

        {(currentScreen === 'schema-architecture' ||
          currentScreen === 'settings') && (
          <SchemaAndSettingsView
            mode={currentScreen === 'schema-architecture' ? 'schema' : 'settings'}
            stats={stats}
            onResetDemoData={async () => {
              await clearAllAndResetDatabase(true);
              await refreshAllFromSQLite();
            }}
            onClearAllForProduction={async () => {
              await clearAllAndResetDatabase(false);
              const cId = await TrackerRepository.addClass('الثالث المتوسط');
              await TrackerRepository.addSection(cId, 'أ');
              await refreshAllFromSQLite();
            }}
          />
        )}
      </main>

      {/* Fixed Bottom Tab Bar for Mobile Thumb Navigation */}
      <nav
        aria-label="التنقل السفلي"
        className="fixed bottom-0 left-0 right-0 z-40 h-16 bg-white/95 backdrop-blur-md border-t border-slate-200/80 px-2"
      >
        <div className="max-w-3xl mx-auto h-full grid grid-cols-5 items-center">
          <button
            type="button"
            onClick={() => handleNavigate('dashboard')}
            className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
              currentScreen === 'dashboard'
                ? 'text-emerald-700 font-extrabold'
                : 'text-slate-500 hover:text-slate-900 font-medium'
            }`}
          >
            <Home className="w-5 h-5" />
            <span className="text-[11px] mt-1 whitespace-nowrap">الرئيسية</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('students')}
            className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
              currentScreen === 'students' ||
              currentScreen === 'student-profile' ||
              currentScreen === 'classes-sections'
                ? 'text-emerald-700 font-extrabold'
                : 'text-slate-500 hover:text-slate-900 font-medium'
            }`}
          >
            <Users className="w-5 h-5" />
            <span className="text-[11px] mt-1 whitespace-nowrap">الطلاب</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('daily-tracking')}
            className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
              currentScreen === 'daily-tracking'
                ? 'text-emerald-700 font-extrabold'
                : 'text-slate-500 hover:text-slate-900 font-medium'
            }`}
          >
            <ClipboardList className="w-5 h-5" />
            <span className="text-[11px] mt-1 whitespace-nowrap">المتابعة</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('saved-records')}
            className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
              currentScreen === 'saved-records'
                ? 'text-emerald-700 font-extrabold'
                : 'text-slate-500 hover:text-slate-900 font-medium'
            }`}
          >
            <FolderClock className="w-5 h-5" />
            <span className="text-[11px] mt-1 whitespace-nowrap">السجلات</span>
          </button>

          <button
            type="button"
            onClick={() => handleNavigate('backup-restore')}
            className={`min-h-[48px] flex flex-col items-center justify-center transition-colors ${
              currentScreen === 'backup-restore'
                ? 'text-emerald-700 font-extrabold'
                : 'text-slate-500 hover:text-slate-900 font-medium'
            }`}
          >
            <ArchiveRestore className="w-5 h-5" />
            <span className="text-[11px] mt-1 whitespace-nowrap">النسخ الاحتياطي</span>
          </button>
        </div>
      </nav>

      {/* Offline Status Toast Indicator */}
      <OfflineIndicator />

      {/* Fast Student Search Modal */}
      <StudentSearchModal
        isOpen={isSearchModalOpen}
        onClose={() => setIsSearchModalOpen(false)}
        classes={classes}
        sections={sections}
        defaultClassId="ALL"
        defaultSectionId="ALL"
        currentDate={selectedDate}
        onOpenStudentProfile={(st) => {
          handleNavigate('student-profile', { studentId: st.id });
        }}
        onOpenStudentInDailyTracking={(st) => {
          guardAction(() => {
            setSelectedClassId(st.classId);
            setSelectedSectionId(st.sectionId);
            setCurrentScreen('daily-tracking');
            window.scrollTo({ top: 0, behavior: 'smooth' });
          });
        }}
        onQuickAttendanceToggle={handleQuickAttendanceToggleFromSearch}
      />

      {/* Unsaved Changes Protection Modal */}
      <UnsavedChangesModal
        isOpen={Boolean(pendingAction)}
        onSaveAndExit={handleModalSaveAndExit}
        onExitWithoutSaving={handleModalExitWithoutSaving}
        onCancel={handleModalCancel}
      />
    </div>
  );
}
