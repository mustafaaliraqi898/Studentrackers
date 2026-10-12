import React, { useEffect, useState } from 'react';
import {
  Calendar,
  CalendarOff,
  Check,
  CheckCircle2,
  Edit3,
  Eye,
  Lock,
  Save,
  UserCheck,
  UserX,
} from 'lucide-react';
import {
  AppScreen,
  AttendanceStatus,
  ClassEntity,
  HolidayEntity,
  RatingValue,
  SectionEntity,
  StudentCalculatedGrade,
  StudentDailyEntry,
  StudentEntity,
} from '../types';
import {
  GradingService,
  checkDateHolidayStatus,
  createDefaultStudentEntry,
  formatDisplayDate,
  formatScore,
  getArabicDayName,
} from '../services/gradingService';
import { TrackerRepository } from '../repositories/trackerRepository';
import { RatingButtons, NotebookRatingButtons } from './RatingButtons';

interface DailyTrackingViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  students: StudentEntity[];
  holidays: HolidayEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  selectedDate: string; // YYYY-MM-DD
  isDirty: boolean;
  setIsDirty: (dirty: boolean) => void;
  onSelectClass: (classId: number) => void;
  onSelectSection: (sectionId: number) => void;
  onSelectDate: (date: string) => void;
  onSaveComplete: () => Promise<void>;
  onNavigate: (screen: AppScreen, params?: { studentId?: number }) => void;
  registerPendingSaveHandler: (fn: () => Promise<void>) => void;
}

const QUICK_OCT_DATES = [
  '2026-10-01', // الخميس (دوام)
  '2026-10-02', // الجمعة (عطلة أسبوعية)
  '2026-10-03', // السبت (عطلة أسبوعية)
  '2026-10-04', // الأحد (دوام)
  '2026-10-05', // الإثنين (دوام)
  '2026-10-06', // الثلاثاء (دوام)
  '2026-10-07', // الأربعاء (دوام)
  '2026-10-08', // الخميس (دوام)
  '2026-10-10', // السبت (10/10 عطلة رسمية مسجلة)
];

export const DailyTrackingView: React.FC<DailyTrackingViewProps> = ({
  classes,
  sections,
  students,
  holidays,
  selectedClassId,
  selectedSectionId,
  selectedDate,
  isDirty,
  setIsDirty,
  onSelectClass,
  onSelectSection,
  onSelectDate,
  onSaveComplete,
  onNavigate,
  registerPendingSaveHandler,
}) => {
  const currentClass = classes.find((c) => c.id === selectedClassId) || classes[0];
  const classSections = sections.filter((s) => s.classId === currentClass?.id);
  const currentSection =
    classSections.find((s) => s.id === selectedSectionId) || classSections[0];

  const sectionStudents = students.filter(
    (s) => s.classId === currentClass?.id && s.sectionId === currentSection?.id
  );

  const [isSavedRecord, setIsSavedRecord] = useState(false);
  const [isEditingSaved, setIsEditingSaved] = useState(true);
  const [savedEntriesMap, setSavedEntriesMap] = useState<Record<number, StudentDailyEntry>>({});
  const [draftEntries, setDraftEntries] = useState<Record<number, StudentDailyEntry>>({});
  const [draftQuizStrings, setDraftQuizStrings] = useState<Record<number, string>>({});
  const [liveGrades, setLiveGrades] = useState<Record<number, StudentCalculatedGrade>>({});
  const [saveBannerMessage, setSaveBannerMessage] = useState<string | null>(null);
  const [saveBannerIsWarning, setSaveBannerIsWarning] = useState<boolean>(false);
  const [savedDatesSet, setSavedDatesSet] = useState<Set<string>>(new Set());
  const [searchText, setSearchText] = useState<string>('');
  const [showAbsentOnly, setShowAbsentOnly] = useState<boolean>(false);
  const [explicitlyMarkedPresentIds, setExplicitlyMarkedPresentIds] = useState<Set<number>>(new Set());
  const [pendingConfirmAbsentStudent, setPendingConfirmAbsentStudent] = useState<StudentEntity | null>(null);
  const bannerTimerRef = React.useRef<ReturnType<typeof setTimeout> | null>(null);

  const showTopBanner = (msg: string, isWarning = false) => {
    if (bannerTimerRef.current) {
      clearTimeout(bannerTimerRef.current);
    }
    setSaveBannerMessage(msg);
    setSaveBannerIsWarning(isWarning);
    bannerTimerRef.current = setTimeout(() => {
      setSaveBannerMessage(null);
    }, 2800);
  };

  const dayStatus = checkDateHolidayStatus(selectedDate, holidays);

  // Load session from SQLite when Class, Section, or Date changes
  useEffect(() => {
    let active = true;
    async function loadSession() {
      if (!currentClass || !currentSection) return;

      const hasSaved = await TrackerRepository.hasSavedSession(
        currentClass.id,
        currentSection.id,
        selectedDate
      );
      const savedMap = await TrackerRepository.getSessionEntries(
        currentClass.id,
        currentSection.id,
        selectedDate
      );
      const allSessions = await TrackerRepository.getSavedSessionsSummary(
        currentClass.id,
        currentSection.id
      );

      if (!active) return;

      setSavedDatesSet(new Set(allSessions.map((s) => s.recordDate)));

      const nextDraft: Record<number, StudentDailyEntry> = {};
      const nextQuizStr: Record<number, string> = {};

      for (const st of sectionStudents) {
        if (savedMap[st.id]) {
          nextDraft[st.id] = { ...savedMap[st.id] };
          nextQuizStr[st.id] =
            savedMap[st.id].dailyTestScore !== null &&
            savedMap[st.id].dailyTestScore !== undefined
              ? String(savedMap[st.id].dailyTestScore)
              : '';
        } else {
          nextDraft[st.id] = createDefaultStudentEntry(st.id);
          nextQuizStr[st.id] = '';
        }
      }

      setSavedEntriesMap(savedMap);
      setDraftEntries(nextDraft);
      setDraftQuizStrings(nextQuizStr);
      setExplicitlyMarkedPresentIds(new Set());
      setIsSavedRecord(hasSaved);
      setIsEditingSaved(!hasSaved);
      setIsDirty(false);
    }

    loadSession();
    return () => {
      active = false;
    };
  }, [currentClass?.id, currentSection?.id, selectedDate, students.length]);

  // Recalculate live grades whenever draftEntries change
  useEffect(() => {
    let active = true;
    async function recompute() {
      if (!currentClass || !currentSection) return;
      const monthPrefix = selectedDate.slice(0, 7);
      const gradesList = await GradingService.calculateGradesForClass(
        currentClass.id,
        currentSection.id,
        monthPrefix,
        dayStatus.isNonWorkingDay
          ? undefined
          : {
              recordDate: selectedDate,
              entries: draftEntries,
            }
      );
      if (!active) return;
      const gMap: Record<number, StudentCalculatedGrade> = {};
      for (const g of gradesList) {
        gMap[g.studentId] = g;
      }
      setLiveGrades(gMap);
    }
    recompute();
    return () => {
      active = false;
    };
  }, [draftEntries, currentClass?.id, currentSection?.id, selectedDate, holidays]);

  const handleSaveSession = async () => {
    if (!currentClass || !currentSection || dayStatus.isNonWorkingDay) return;

    const wasSaved = isSavedRecord;
    await TrackerRepository.saveDailySession(
      currentClass.id,
      currentSection.id,
      selectedDate,
      draftEntries
    );
    await onSaveComplete();

    setSavedEntriesMap({ ...draftEntries });
    setIsSavedRecord(true);
    setIsEditingSaved(false);
    setIsDirty(false);
    setSavedDatesSet((prev) => new Set([...prev, selectedDate]));
    showTopBanner(
      wasSaved ? 'تم حفظ التعديلات وإعادة حساب الدرجات بنجاح' : 'تم حفظ السجل اليومي بنجاح',
      false
    );
  };

  const executeImmediateAttendanceStatus = async (
    student: StudentEntity,
    targetStatus: AttendanceStatus
  ) => {
    if (!currentClass || !currentSection || dayStatus.isNonWorkingDay) return;

    const currentDraftStudent =
      draftEntries[student.id] || createDefaultStudentEntry(student.id);
    const currentSavedStudent =
      savedEntriesMap[student.id] || createDefaultStudentEntry(student.id);

    const updatedDraftStudent: StudentDailyEntry =
      targetStatus === 'absent'
        ? {
            ...currentDraftStudent,
            studentId: student.id,
            attendance: 'absent',
          }
        : {
            ...currentDraftStudent,
            studentId: student.id,
            attendance: 'present',
          };

    const updatedSavedStudent: StudentDailyEntry =
      targetStatus === 'absent'
        ? {
            ...currentSavedStudent,
            studentId: student.id,
            attendance: 'absent',
          }
        : {
            ...currentSavedStudent,
            studentId: student.id,
            attendance: 'present',
          };

    const nextDraft: Record<number, StudentDailyEntry> = { ...draftEntries };
    const nextDbEntries: Record<number, StudentDailyEntry> = { ...savedEntriesMap };

    for (const st of sectionStudents) {
      if (!nextDraft[st.id]) {
        nextDraft[st.id] = createDefaultStudentEntry(st.id);
      }
      if (!nextDbEntries[st.id]) {
        nextDbEntries[st.id] = createDefaultStudentEntry(st.id);
      }
    }
    nextDraft[student.id] = updatedDraftStudent;
    nextDbEntries[student.id] = updatedSavedStudent;

    setDraftEntries(nextDraft);

    await TrackerRepository.saveDailySession(
      currentClass.id,
      currentSection.id,
      selectedDate,
      nextDbEntries
    );
    setSavedEntriesMap(nextDbEntries);
    setIsSavedRecord(true);
    setIsEditingSaved(true);
    setSavedDatesSet((prev) => new Set([...prev, selectedDate]));
    if (targetStatus === 'absent') {
      setExplicitlyMarkedPresentIds((prev) => {
        const next = new Set(prev);
        next.delete(student.id);
        return next;
      });
    } else {
      setExplicitlyMarkedPresentIds((prev) => new Set([...prev, student.id]));
    }
    await onSaveComplete();

    showTopBanner(
      targetStatus === 'absent'
        ? `تم تسجيل الغياب للطالب (${student.studentName}) وحفظه في قاعدة البيانات`
        : `تم تسجيل الحضور للطالب (${student.studentName}) وحفظه في قاعدة البيانات (19/19)`,
      targetStatus === 'absent'
    );
  };

  const executeImmediateAbsent = async (student: StudentEntity) => {
    await executeImmediateAttendanceStatus(student, 'absent');
  };

  const handleQuickAbsentFromSearch = async (student: StudentEntity) => {
    const currentEntry =
      draftEntries[student.id] || createDefaultStudentEntry(student.id);
    if (currentEntry.attendance === 'absent') {
      return;
    }
    const wasPreviouslySavedPresent =
      savedEntriesMap[student.id]?.attendance === 'present' ||
      explicitlyMarkedPresentIds.has(student.id);

    if (wasPreviouslySavedPresent) {
      setPendingConfirmAbsentStudent(student);
    } else {
      await executeImmediateAbsent(student);
    }
  };

  // Clear search text when Class, Section, or Date changes
  useEffect(() => {
    setSearchText('');
  }, [currentClass?.id, currentSection?.id, selectedDate]);

  useEffect(() => {
    registerPendingSaveHandler(handleSaveSession);
  });

  const isLocked =
    dayStatus.isNonWorkingDay || (isSavedRecord && !isEditingSaved && searchText.trim() === '');

  const updateAttendance = (studentId: number, status: AttendanceStatus) => {
    if (dayStatus.isNonWorkingDay) return;
    const st = sectionStudents.find((s) => s.id === studentId);
    if (!st) return;

    if (searchText.trim() !== '' && status === 'absent') {
      void handleQuickAbsentFromSearch(st);
      return;
    }
    void executeImmediateAttendanceStatus(st, status);
  };

  const updateRating = (
    studentId: number,
    field: 'participation' | 'notebook' | 'homework' | 'behavior',
    value: RatingValue
  ) => {
    if (dayStatus.isNonWorkingDay) return;
    if (!isEditingSaved) setIsEditingSaved(true);
    setDraftEntries((prev) => ({
      ...prev,
      [studentId]: {
        ...(prev[studentId] || createDefaultStudentEntry(studentId)),
        [field]: value,
      },
    }));
    setIsDirty(true);
    setSaveBannerMessage(null);
  };

  const updateQuizInput = (studentId: number, rawVal: string) => {
    if (dayStatus.isNonWorkingDay) return;
    if (!isEditingSaved) setIsEditingSaved(true);
    if (rawVal === '') {
      setDraftQuizStrings((prev) => ({ ...prev, [studentId]: '' }));
      setDraftEntries((prev) => ({
        ...prev,
        [studentId]: {
          ...(prev[studentId] || createDefaultStudentEntry(studentId)),
          dailyTestScore: null,
        },
      }));
      setIsDirty(true);
      return;
    }

    const num = Number(rawVal);
    if (!Number.isNaN(num) && num >= 0 && num <= 20) {
      setDraftQuizStrings((prev) => ({ ...prev, [studentId]: rawVal }));
      setDraftEntries((prev) => ({
        ...prev,
        [studentId]: {
          ...(prev[studentId] || createDefaultStudentEntry(studentId)),
          dailyTestScore: num,
        },
      }));
      setIsDirty(true);
    }
  };

  const matchesStudentSearch = (studentName: string, query: string): boolean => {
    const q = TrackerRepository.normalizeStudentName(query);
    if (!q) return true;
    const qKey = TrackerRepository.canonicalArabicComparisonKey(q);
    const qTokens = qKey.split(' ').filter(Boolean);
    const stNorm = TrackerRepository.normalizeStudentName(studentName);
    const stKey = TrackerRepository.canonicalArabicComparisonKey(studentName);
    return (
      stNorm.includes(q) ||
      stKey.includes(qKey) ||
      (qTokens.length > 0 && qTokens.every((tok) => stKey.includes(tok)))
    );
  };

  const filteredStudents = sectionStudents.filter((st) => {
    const matchesSearch = matchesStudentSearch(st.studentName, searchText);
    const entry = draftEntries[st.id] || createDefaultStudentEntry(st.id);
    const matchesAbsent = !showAbsentOnly || entry.attendance === 'absent';
    return matchesSearch && matchesAbsent;
  });

  const allQuickDates = Array.from(
    new Set([...QUICK_OCT_DATES, ...Array.from(savedDatesSet), selectedDate])
  ).sort();

  return (
    <div className="space-y-5 pb-24">
      {/* Control Header: Select Class, Section, Date */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs space-y-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {/* Class Select */}
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الصف الدراسي
            </label>
            <select
              value={currentClass?.id || ''}
              onChange={(e) => onSelectClass(Number(e.target.value))}
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          {/* Section Select */}
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الشعبة
            </label>
            <div className="flex items-center gap-1.5 p-1 bg-slate-100 rounded-xl min-h-[44px]">
              {classSections.map((sec) => {
                const active = sec.id === currentSection?.id;
                return (
                  <button
                    key={sec.id}
                    type="button"
                    onClick={() => onSelectSection(sec.id)}
                    className={`flex-1 min-h-[36px] px-3 py-1.5 rounded-lg text-xs font-extrabold transition-all whitespace-nowrap ${
                      active
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    شعبة {sec.sectionName}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Date Picker */}
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              التاريخ ({getArabicDayName(selectedDate)})
            </label>
            <div className="relative">
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => {
                  if (e.target.value) onSelectDate(e.target.value);
                }}
                className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono-num font-extrabold text-slate-900"
              />
            </div>
          </div>
        </div>

        {/* Quick Horizontal Dates Scroller (shows working days, Friday/Saturday weekends, and 10/10 holiday) */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-xs font-bold text-slate-600">
              شريط التواريخ السريع (جرّب أيام الدوام والجمعة/السبت وعطلة 10/10):
            </span>
          </div>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
            {allQuickDates.map((d) => {
              const isSelected = d === selectedDate;
              const isSaved = savedDatesSet.has(d);
              const dStat = checkDateHolidayStatus(d, holidays);
              const dayName = getArabicDayName(d);

              return (
                <button
                  key={d}
                  type="button"
                  onClick={() => onSelectDate(d)}
                  className={`min-h-[44px] px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 flex flex-col items-center justify-center border ${
                    isSelected
                      ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                      : dStat.isNonWorkingDay
                      ? 'bg-amber-50/70 text-amber-900 border-amber-200/80 hover:bg-amber-100/70'
                      : 'bg-slate-50 text-slate-700 border-slate-200/80 hover:bg-slate-100'
                  }`}
                >
                  <div className="flex items-center gap-1">
                    <span>{dayName}</span>
                    {isSaved && (
                      <span
                        className={`w-2 h-2 rounded-full ${
                          isSelected ? 'bg-emerald-400' : 'bg-emerald-600'
                        }`}
                      />
                    )}
                  </div>
                  <span className="font-mono-num text-[11px] opacity-85">
                    {formatDisplayDate(d)}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Record Status Bar: محفوظ vs غير محفوظ */}
        {!dayStatus.isNonWorkingDay && (
          <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-xs">
              <span className="text-slate-500 font-bold">حالة السجل:</span>
              {isDirty ? (
                <span className="font-extrabold text-amber-700">
                  غير محفوظ (تعديلات معلقة)
                </span>
              ) : isSavedRecord ? (
                <span className="font-extrabold text-emerald-700 flex items-center gap-1">
                  <Lock className="w-3.5 h-3.5" />
                  <span>محفوظ في SQLite</span>
                </span>
              ) : (
                <span className="font-extrabold text-slate-700">
                  غير محفوظ (سجل يوم جديد)
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              {isSavedRecord && isLocked && (
                <button
                  type="button"
                  onClick={() => {
                    setIsEditingSaved(true);
                    showTopBanner('تم تفعيل وضع تعديل التقييمات اليومية', false);
                  }}
                  className="min-h-[40px] px-4 py-1.5 bg-amber-500 hover:bg-amber-600 text-white font-extrabold text-xs rounded-xl flex items-center gap-1.5"
                >
                  <Edit3 className="w-3.5 h-3.5" />
                  <span>تعديل السجل</span>
                </button>
              )}
            </div>
          </div>
        )}
      </section>

      {/* Compact Top Notification Banner (below selection bar, never covers bottom save button) */}
      {saveBannerMessage && (
        <div
          role="status"
          className={`rounded-xl px-3.5 py-2.5 border flex items-center justify-between gap-2 text-xs font-extrabold transition-all ${
            saveBannerIsWarning
              ? 'bg-rose-50 border-rose-300 text-rose-800'
              : 'bg-emerald-50 border-emerald-300 text-emerald-800'
          }`}
        >
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{saveBannerMessage}</span>
          </div>
          <span className="text-[11px] opacity-80 font-mono-num shrink-0">
            {formatDisplayDate(selectedDate)}
          </span>
        </div>
      )}

      {/* IF WEEKEND OR HOLIDAY: Show Holiday/Weekend Lock Screen */}
      {dayStatus.isNonWorkingDay ? (
        <section className="bg-amber-50 border-2 border-amber-200 rounded-2xl p-6 sm:p-8 text-center space-y-3">
          <div className="w-14 h-14 rounded-2xl bg-amber-500/15 text-amber-700 flex items-center justify-center mx-auto">
            <CalendarOff className="w-8 h-8" />
          </div>
          <h3 className="text-xl font-extrabold text-amber-950">{dayStatus.title}</h3>
          <p className="text-sm font-bold text-amber-900 max-w-md mx-auto leading-relaxed">
            {dayStatus.subtitle}
          </p>
          <p className="text-xs text-amber-800 max-w-lg mx-auto">
            وفق قواعد النظام، لا يُسمح بتسجيل غياب أو درجات يومية في أيام العطل الأسبوعية (الجمعة والسبت) أو العطل الرسمية المسجلة، ولا يؤثر هذا اليوم سلباً على نسبة حضور الطلاب.
          </p>
          <div className="pt-2 flex items-center justify-center gap-2">
            <button
              type="button"
              onClick={() => onSelectDate('2026-10-04')}
              className="min-h-[42px] px-4 py-2 bg-slate-900 text-white rounded-xl text-xs font-bold flex items-center gap-1.5"
            >
              <Calendar className="w-4 h-4" />
              <span>الانتقال إلى يوم دوام (الأحد 04/10/2026)</span>
            </button>
          </div>
        </section>
      ) : (
        /* WORKING DAY: Search & Absent-Only Filter + Student Cards */
        <div className="space-y-4">
          {/* Fast Search & Absent-Only Filter Bar */}
          <div className="bg-white rounded-2xl p-3.5 sm:p-4 border border-slate-200/80 space-y-3">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="relative flex-1">
                <input
                  type="text"
                  value={searchText}
                  onChange={(e) => setSearchText(e.target.value)}
                  placeholder="ابحث باسم الطالب كاملاً أو بجزء من اسمه داخل الصف والشعبة..."
                  className="w-full min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900 focus:outline-none focus:border-emerald-600 focus:bg-white"
                />
                {searchText && (
                  <button
                    type="button"
                    onClick={() => setSearchText('')}
                    className="absolute left-2.5 top-1/2 -translate-y-1/2 text-xs font-bold text-slate-500 hover:text-slate-900 px-2 py-1 bg-slate-200/70 rounded-lg"
                  >
                    مسح
                  </button>
                )}
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  type="button"
                  onClick={() => setShowAbsentOnly(!showAbsentOnly)}
                  className={`min-h-[44px] px-4 py-2 rounded-xl text-xs font-extrabold flex items-center gap-1.5 transition-all whitespace-nowrap border ${
                    showAbsentOnly
                      ? 'bg-rose-600 text-white border-rose-600 shadow-xs'
                      : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                  }`}
                >
                  <UserX className="w-4 h-4" />
                  <span>عرض الغائبين فقط</span>
                  <span
                    className={`px-1.5 py-0.5 rounded-md font-mono-num text-[11px] ${
                      showAbsentOnly ? 'bg-rose-700 text-white' : 'bg-slate-200 text-slate-800'
                    }`}
                  >
                    {
                      sectionStudents.filter(
                        (st) =>
                          (draftEntries[st.id]?.attendance || 'present') === 'absent'
                      ).length
                    }
                  </span>
                </button>
              </div>
            </div>

            {searchText.trim() !== '' && (
              <div className="pt-2 border-t border-slate-100 flex items-center justify-between gap-2">
                <p
                  className={`text-xs font-extrabold ${
                    filteredStudents.length === 0 ? 'text-rose-700' : 'text-slate-700'
                  }`}
                >
                  {filteredStudents.length === 0
                    ? `لا يوجد طالب مطابق للاسم "${searchText}" في هذه الشعبة`
                    : `نتائج البحث (${filteredStudents.length} من ${sectionStudents.length} طالب) — يمكنك تسجيل الحضور/الغياب وتعديل جميع العناصر مباشرة:`}
                </p>
                <button
                  type="button"
                  onClick={() => setSearchText('')}
                  className="text-xs font-extrabold text-emerald-700 hover:text-emerald-800 shrink-0"
                >
                  عرض الكل
                </button>
              </div>
            )}
          </div>

          {filteredStudents.map((student, index) => {
            const entry =
              draftEntries[student.id] || createDefaultStudentEntry(student.id);
            const rawQuizStr = draftQuizStrings[student.id] ?? '';
            const liveGrade = liveGrades[student.id];
            const isPresent = entry.attendance === 'present';

            return (
              <article
                key={student.id}
                className={`bg-white rounded-2xl p-4 sm:p-5 border transition-all ${
                  isPresent
                    ? 'border-slate-200/90 shadow-2xs'
                    : 'border-rose-200 bg-rose-50/20'
                }`}
              >
                {/* Card Header: Student Name & Attendance [ حاضر ] [ غائب ] */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-100">
                  <div className="flex items-center gap-2.5">
                    <span className="w-7 h-7 rounded-lg bg-slate-900 text-white font-mono-num text-xs font-bold flex items-center justify-center shrink-0">
                      {index + 1}
                    </span>
                    <div>
                      <button
                        type="button"
                        onClick={() =>
                          onNavigate('student-profile', { studentId: student.id })
                        }
                        className="text-base font-extrabold text-slate-900 hover:text-emerald-700 transition-colors text-right flex items-center gap-1.5"
                      >
                        <span>{student.studentName}</span>
                        <Eye className="w-3.5 h-3.5 text-slate-400" />
                      </button>
                      {liveGrade && (
                        <p className="text-xs text-slate-500 font-mono-num mt-0.5">
                          دوام فعلي: {liveGrade.presentDays} حضور من {liveGrade.totalWorkingDays} أيام (
                          {formatScore(liveGrade.attendancePercentage)}%)
                        </p>
                      )}
                    </div>
                  </div>

                  {/* Attendance Buttons (Always enabled on working days & saved immediately to SQLite) */}
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-slate-500 ml-1">
                      الحضور (10):
                    </span>
                    <div className="grid grid-cols-2 gap-1.5 p-1 bg-slate-100 rounded-xl border border-slate-200/70 w-full sm:w-auto min-w-[190px]">
                      <button
                        type="button"
                        disabled={dayStatus.isNonWorkingDay}
                        onClick={() => updateAttendance(student.id, 'present')}
                        className={`min-h-[44px] px-3.5 py-2 rounded-lg text-xs font-extrabold transition-all flex items-center justify-center gap-1.5 whitespace-nowrap ${
                          isPresent
                            ? 'bg-emerald-600 text-white shadow-xs'
                            : 'text-slate-600 hover:text-slate-900'
                        } active:scale-[0.98]`}
                      >
                        <UserCheck className="w-4 h-4" />
                        <span>حاضر (10/10)</span>
                      </button>

                      <button
                        type="button"
                        disabled={dayStatus.isNonWorkingDay}
                        onClick={() => updateAttendance(student.id, 'absent')}
                        className={`min-h-[44px] px-3.5 py-2 rounded-lg text-xs font-extrabold transition-all flex items-center justify-center gap-1.5 whitespace-nowrap ${
                          !isPresent
                            ? 'bg-rose-600 text-white shadow-xs'
                            : 'text-slate-600 hover:text-slate-900'
                        } active:scale-[0.98]`}
                      >
                        <UserX className="w-4 h-4" />
                        <span>غائب (0/10)</span>
                      </button>
                    </div>
                  </div>
                </div>

                {/* 4 Evaluation Elements: المشاركة (30)، الدفتر (10)، الواجب (15)، السلوك (15) */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5 py-4 border-b border-slate-100">
                  <RatingButtons
                    label="المشاركة (30)"
                    value={entry.participation}
                    onChange={(val) => updateRating(student.id, 'participation', val)}
                    disabled={isLocked}
                    calculatedScoreText={
                      liveGrade ? `${formatScore(liveGrade.participationScore)} / 30` : undefined
                    }
                  />

                  <NotebookRatingButtons
                    label="الدفتر (10)"
                    value={entry.notebook}
                    onChange={(val) => updateRating(student.id, 'notebook', val)}
                    disabled={isLocked}
                    calculatedScoreText={
                      liveGrade ? `${formatScore(liveGrade.notebookScore)} / 10` : undefined
                    }
                  />

                  <RatingButtons
                    label="الواجب (15)"
                    value={entry.homework}
                    onChange={(val) => updateRating(student.id, 'homework', val)}
                    disabled={isLocked}
                    calculatedScoreText={
                      liveGrade ? `${formatScore(liveGrade.homeworkScore)} / 15` : undefined
                    }
                  />

                  <RatingButtons
                    label="السلوك (15)"
                    value={entry.behavior}
                    onChange={(val) => updateRating(student.id, 'behavior', val)}
                    disabled={isLocked}
                    calculatedScoreText={
                      liveGrade ? `${formatScore(liveGrade.behaviorScore)} / 15` : undefined
                    }
                  />
                </div>

                {/* Daily Test (0 to 20, decimals allowed) + Live Calculated Grade Breakdown */}
                <div className="pt-4 grid grid-cols-1 lg:grid-cols-12 gap-4 items-center">
                  <div className="lg:col-span-4 flex items-center justify-between sm:justify-start gap-3 bg-slate-50 p-3 rounded-xl border border-slate-200/70">
                    <div>
                      <label
                        htmlFor={`quiz-${student.id}`}
                        className="block text-xs font-bold text-slate-800"
                      >
                        الامتحان اليومي (من 20)
                      </label>
                      <span className="text-[11px] text-slate-500">
                        يقبل الدرجات حتى 20 والكسور مثل 17.5
                      </span>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0" dir="ltr">
                      <input
                        id={`quiz-${student.id}`}
                        type="number"
                        min={0}
                        max={20}
                        step={0.5}
                        disabled={isLocked}
                        value={rawQuizStr}
                        onChange={(e) => updateQuizInput(student.id, e.target.value)}
                        placeholder="—"
                        className="w-16 min-h-[42px] px-2 py-1 text-center font-mono-num text-base font-extrabold bg-white border border-slate-300 rounded-lg text-slate-900 focus:outline-none focus:border-emerald-600 disabled:bg-slate-100"
                      />
                      <span className="text-sm font-bold text-slate-500 font-mono-num">
                        / 20
                      </span>
                    </div>
                  </div>

                  {liveGrade && (
                    <div className="lg:col-span-8 bg-slate-900 text-white rounded-xl p-3.5 sm:p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                      <div className="flex items-baseline gap-2 shrink-0">
                        <span className="text-xs text-slate-300 font-semibold">
                          درجة اليومي:
                        </span>
                        <span className="text-xl sm:text-2xl font-extrabold text-emerald-400 font-mono-num">
                          {formatScore(liveGrade.totalScore)} / 100
                        </span>
                      </div>

                      <div className="grid grid-cols-3 sm:grid-cols-6 gap-x-3 gap-y-1.5 text-[11px] border-t sm:border-t-0 pt-2.5 sm:pt-0 border-slate-800">
                        <div>
                          <span className="text-slate-400 block">الحضور</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.attendanceScore)} / 10
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">المشاركة</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.participationScore)} / 30
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">الدفتر</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.notebookScore)} / 10
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">الواجب</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.homeworkScore)} / 15
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">السلوك</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.behaviorScore)} / 15
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 block">الامتحان</span>
                          <span className="font-mono-num font-bold text-white">
                            {formatScore(liveGrade.quizScore)} / 20
                          </span>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Sticky Bottom Save / Edit Action Bar (Only on working days) */}
      {!dayStatus.isNonWorkingDay && (
        <div className="fixed bottom-16 left-0 right-0 z-30 pointer-events-none px-4">
          <div className="max-w-3xl mx-auto pointer-events-auto">
            <div className="bg-white/95 backdrop-blur-md border border-slate-200/90 shadow-lg rounded-2xl p-3 flex items-center justify-between gap-3">
              <div className="text-xs min-w-0">
                <p className="font-extrabold text-slate-900 truncate">
                  {currentClass?.className} (شعبة {currentSection?.sectionName}) ·{' '}
                  {formatDisplayDate(selectedDate)}
                </p>
                <p className="text-slate-500 truncate">
                  {isDirty
                    ? 'الحالة: غير محفوظ — اضغط لحفظ السجل في SQLite'
                    : isSavedRecord
                    ? 'الحالة: محفوظ في قاعدة بيانات SQLite'
                    : 'الحالة: غير محفوظ'}
                </p>
              </div>

              {isSavedRecord && isLocked ? (
                <button
                  type="button"
                  onClick={() => setIsEditingSaved(true)}
                  className="min-h-[46px] px-6 py-2.5 bg-amber-500 hover:bg-amber-600 text-white font-extrabold text-sm rounded-xl transition-all flex items-center gap-2 shrink-0 shadow-xs whitespace-nowrap"
                >
                  <Edit3 className="w-4 h-4" />
                  <span>تعديل</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleSaveSession}
                  className="min-h-[46px] px-6 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-sm rounded-xl transition-all flex items-center gap-2 shrink-0 shadow-xs whitespace-nowrap"
                >
                  {isSavedRecord ? (
                    <>
                      <Check className="w-4 h-4" />
                      <span>حفظ التعديلات</span>
                    </>
                  ) : (
                    <>
                      <Save className="w-4 h-4" />
                      <span>حفظ السجل</span>
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      )}
      {/* Confirmation Modal for Changing Present Student to Absent */}
      {pendingConfirmAbsentStudent && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 flex items-center justify-center shrink-0">
                <UserX className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-slate-900">
                  تأكيد تغيير الحالة إلى غائب
                </h3>
                <p className="text-sm text-slate-700 mt-1 leading-relaxed font-medium">
                  الطالب ({pendingConfirmAbsentStudent.studentName}) مسجل حاضر مسبقاً لهذا اليوم (
                  {formatDisplayDate(selectedDate)}). هل تريد تغيير حالته من حاضر إلى غائب (درجة حضور اليوم = 0)؟
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={async () => {
                  const target = pendingConfirmAbsentStudent;
                  setPendingConfirmAbsentStudent(null);
                  if (target) {
                    await executeImmediateAbsent(target);
                  }
                }}
                className="flex-1 min-h-[44px] bg-rose-600 hover:bg-rose-700 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                تأكيد الغياب
              </button>
              <button
                type="button"
                onClick={() => setPendingConfirmAbsentStudent(null)}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
