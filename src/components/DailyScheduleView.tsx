import React, { useEffect, useMemo, useState } from 'react';
import {
  Bell,
  BellOff,
  Calendar,
  Clock,
  Edit3,
  Plus,
  Save,
  Trash2,
} from 'lucide-react';
import {
  ClassEntity,
  DailyScheduleEntity,
  SectionEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';

interface DailyScheduleViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  recordsVersion: number;
  onScheduleChanged: () => Promise<void>;
}

const WEEK_DAYS_ORDER = [
  'الأحد',
  'الاثنين',
  'الثلاثاء',
  'الأربعاء',
  'الخميس',
  'الجمعة',
  'السبت',
];

const WORKING_DAYS_ORDER = [
  'الأحد',
  'الاثنين',
  'الثلاثاء',
  'الأربعاء',
  'الخميس',
];

const GLOBAL_REMINDER_ENABLED_KEY = 'student_tracker_global_schedule_reminder_enabled';
const GLOBAL_REMINDER_MINUTES_KEY = 'student_tracker_global_schedule_reminder_minutes';

function parsePositiveIntegerMinutes(rawInput: string): number | null {
  const normalized = rawInput
    .trim()
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - '٠'.charCodeAt(0)))
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - '۰'.charCodeAt(0)));
  if (!normalized || !/^\d+$/.test(normalized)) return null;
  const num = Number(normalized);
  if (!Number.isSafeInteger(num) || num <= 0) return null;
  return num;
}

function getArabicTodayName(): string {
  const jsDay = new Date().getDay(); // 0 = Sunday .. 6 = Saturday
  return WEEK_DAYS_ORDER[jsDay] ?? 'الأحد';
}

function getArabicLessonLabel(lessonNum: number): string {
  switch (lessonNum) {
    case 1:
      return 'الدرس الأول';
    case 2:
      return 'الدرس الثاني';
    case 3:
      return 'الدرس الثالث';
    case 4:
      return 'الدرس الرابع';
    case 5:
      return 'الدرس الخامس';
    case 6:
      return 'الدرس السادس';
    case 7:
      return 'الدرس السابع';
    case 8:
      return 'الدرس الثامن';
    case 9:
      return 'الدرس التاسع';
    case 10:
      return 'الدرس العاشر';
    default:
      return `الدرس ${lessonNum}`;
  }
}

function parseTimeMinutes(timeStr: string): number | null {
  const parts = timeStr.trim().split(':');
  if (parts.length !== 2) return null;
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  if (Number.isNaN(h) || Number.isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) {
    return null;
  }
  return h * 60 + m;
}

function formatTime12HourAr(timeStr: string): string {
  const parts = timeStr.trim().split(':');
  if (parts.length !== 2) return timeStr;
  const h = Number(parts[0]);
  const m = Number(parts[1]);
  if (Number.isNaN(h) || Number.isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) {
    return timeStr;
  }
  const hour12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
  const minuteStr = String(m).padStart(2, '0');
  const periodLabel =
    h === 0 && m === 0 ? 'منتصف الليل' : h < 12 ? 'صباحاً' : 'مساءً';
  return `${hour12}:${minuteStr} ${periodLabel}`;
}

export const DailyScheduleView: React.FC<DailyScheduleViewProps> = ({
  classes,
  sections,
  recordsVersion,
  onScheduleChanged,
}) => {
  const [scheduleItems, setScheduleItems] = useState<DailyScheduleEntity[]>([]);
  const [editingItem, setEditingItem] = useState<DailyScheduleEntity | null>(null);
  const [pendingDeleteItem, setPendingDeleteItem] =
    useState<DailyScheduleEntity | null>(null);

  const [selectedDay, setSelectedDay] = useState<string>('الأحد');
  const [selectedClassId, setSelectedClassId] = useState<number | null>(
    classes[0]?.id ?? null
  );

  const availableSections = useMemo(() => {
    if (selectedClassId === null) return [];
    return sections.filter((s) => s.classId === selectedClassId);
  }, [sections, selectedClassId]);

  const [selectedSectionId, setSelectedSectionId] = useState<number | null>(
    availableSections[0]?.id ?? null
  );

  useEffect(() => {
    if (selectedClassId === null && classes.length > 0) {
      setSelectedClassId(classes[0].id);
    }
  }, [classes, selectedClassId]);

  useEffect(() => {
    if (
      selectedSectionId === null ||
      !availableSections.some((s) => s.id === selectedSectionId)
    ) {
      setSelectedSectionId(availableSections[0]?.id ?? null);
    }
  }, [selectedClassId, availableSections, selectedSectionId]);

  const [maxLessonsCount, setMaxLessonsCount] = useState<number>(8);
  const [selectedLessonNumber, setSelectedLessonNumber] = useState<number>(1);
  const [startTime, setStartTime] = useState<string>('08:00');
  const [endTime, setEndTime] = useState<string>('08:45');

  // Day filter state ("ALL" or specific Arabic day name; defaults to current local day on open)
  const [todayNameAr, setTodayNameAr] = useState<string>(() => getArabicTodayName());
  const [selectedDayFilter, setSelectedDayFilter] = useState<string>(() =>
    getArabicTodayName()
  );

  useEffect(() => {
    const timer = window.setInterval(() => {
      const latestToday = getArabicTodayName();
      setTodayNameAr((prevToday) => {
        if (latestToday !== prevToday) {
          setSelectedDayFilter((prevFilter) =>
            prevFilter === prevToday ? latestToday : prevFilter
          );
          return latestToday;
        }
        return prevToday;
      });
    }, 30_000);
    return () => window.clearInterval(timer);
  }, []);

  // Global reminder settings state (persisted in localStorage, no forced default)
  const [globalReminderMinutes, setGlobalReminderMinutes] = useState<number | null>(() => {
    try {
      const saved = localStorage.getItem(GLOBAL_REMINDER_MINUTES_KEY);
      if (!saved) return null;
      return parsePositiveIntegerMinutes(saved);
    } catch {
      return null;
    }
  });
  const [reminderMinutesInput, setReminderMinutesInput] = useState<string>(() => {
    try {
      const saved = localStorage.getItem(GLOBAL_REMINDER_MINUTES_KEY);
      if (!saved) return '';
      const parsed = parsePositiveIntegerMinutes(saved);
      return parsed !== null ? String(parsed) : '';
    } catch {
      return '';
    }
  });
  const [globalReminderEnabled, setGlobalReminderEnabled] = useState<boolean>(() => {
    try {
      const savedEnabled = localStorage.getItem(GLOBAL_REMINDER_ENABLED_KEY) === 'true';
      const savedMins = localStorage.getItem(GLOBAL_REMINDER_MINUTES_KEY);
      const parsedMins = savedMins ? parsePositiveIntegerMinutes(savedMins) : null;
      return savedEnabled && parsedMins !== null;
    } catch {
      return false;
    }
  });
  const [reminderInputError, setReminderInputError] = useState<string | null>(null);

  const persistReminderState = (nextEnabled: boolean, nextMinutes: number | null) => {
    const effectiveEnabled = nextEnabled && nextMinutes !== null && nextMinutes > 0;
    setGlobalReminderEnabled(effectiveEnabled);
    setGlobalReminderMinutes(nextMinutes);
    try {
      localStorage.setItem(GLOBAL_REMINDER_ENABLED_KEY, String(effectiveEnabled));
      if (nextMinutes !== null && nextMinutes > 0) {
        localStorage.setItem(GLOBAL_REMINDER_MINUTES_KEY, String(nextMinutes));
      } else {
        localStorage.removeItem(GLOBAL_REMINDER_MINUTES_KEY);
      }
    } catch {
      // ignore storage errors
    }
  };

  const handleReminderMinutesInputChange = (rawText: string) => {
    setReminderMinutesInput(rawText);
    const trimmed = rawText.trim();
    if (!trimmed) {
      if (globalReminderEnabled) {
        setReminderInputError(
          'يرجى إدخال مدة التذكير بالدقائق (رقم صحيح موجب) لجدولة التنبيهات'
        );
      } else {
        setReminderInputError(null);
      }
      persistReminderState(false, null);
      return;
    }

    const parsed = parsePositiveIntegerMinutes(trimmed);
    if (parsed === null) {
      setReminderInputError(
        'يرجى إدخال رقم صحيح موجب للدقائق (أكبر من صفر، مثل: 2 أو 7 أو 15)'
      );
      persistReminderState(false, null);
      return;
    }

    setReminderInputError(null);
    persistReminderState(globalReminderEnabled, parsed);
  };

  const handleToggleGlobalReminder = async (nextChecked: boolean) => {
    if (!nextChecked) {
      setReminderInputError(null);
      const validMins = parsePositiveIntegerMinutes(reminderMinutesInput);
      persistReminderState(false, validMins);
      return;
    }

    const trimmed = reminderMinutesInput.trim();
    if (!trimmed) {
      setReminderInputError(
        'يرجى إدخال مدة التذكير بالدقائق أولاً (مثل: 5 أو 10 أو 25) قبل تفعيل التنبيهات'
      );
      persistReminderState(false, null);
      return;
    }

    const parsed = parsePositiveIntegerMinutes(trimmed);
    if (parsed === null) {
      setReminderInputError(
        'يرجى إدخال رقم صحيح موجب للدقائق (أكبر من صفر، مثل: 2 أو 7 أو 15)'
      );
      persistReminderState(false, null);
      return;
    }

    setReminderInputError(null);
    if (
      typeof window !== 'undefined' &&
      'Notification' in window &&
      Notification.permission === 'default'
    ) {
      try {
        await Notification.requestPermission();
      } catch {
        // ignore permission prompt error in iframe
      }
    }
    persistReminderState(true, parsed);
  };

  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);
  const [feedbackIsError, setFeedbackIsError] = useState<boolean>(false);

  const loadSchedules = async () => {
    const list = await TrackerRepository.getDailySchedules();
    setScheduleItems(list);
  };

  useEffect(() => {
    void loadSchedules();
  }, [recordsVersion, classes.length, sections.length]);

  const showFeedback = (msg: string, isError: boolean) => {
    setFeedbackMessage(msg);
    setFeedbackIsError(isError);
  };

  const resetFormToAddMode = () => {
    setEditingItem(null);
    setSelectedLessonNumber(1);
    setStartTime('08:00');
    setEndTime('08:45');
  };

  const loadItemForEdit = (item: DailyScheduleEntity) => {
    setEditingItem(item);
    setSelectedDay(item.dayOfWeek);
    setSelectedClassId(item.classId);
    setSelectedSectionId(item.sectionId);
    if (item.lessonNumber > maxLessonsCount) {
      setMaxLessonsCount(item.lessonNumber);
    }
    setSelectedLessonNumber(item.lessonNumber);
    setStartTime(item.startTime);
    setEndTime(item.endTime);
    setFeedbackMessage(null);
  };

  const handleSubmit = async () => {
    if (!selectedDay.trim()) {
      showFeedback('يرجى اختيار اليوم', true);
      return;
    }
    if (selectedClassId === null) {
      showFeedback('يرجى اختيار الصف', true);
      return;
    }
    if (selectedSectionId === null) {
      showFeedback('يرجى اختيار الشعبة', true);
      return;
    }
    if (selectedLessonNumber < 1) {
      showFeedback('يرجى اختيار رقم الدرس', true);
      return;
    }
    const startMins = parseTimeMinutes(startTime);
    const endMins = parseTimeMinutes(endTime);
    if (startMins === null || endMins === null) {
      showFeedback('يرجى تحديد وقت البداية ووقت النهاية بشكل صحيح', true);
      return;
    }
    if (endMins <= startMins) {
      showFeedback(
        'لا يسمح بأن يكون وقت انتهاء الدرس قبل أو مساوياً لوقت بدء الدرس',
        true
      );
      return;
    }

    if (!editingItem) {
      const res = await TrackerRepository.addDailyScheduleEntry(
        selectedDay,
        selectedClassId,
        selectedSectionId,
        selectedLessonNumber,
        startTime,
        endTime
      );
      if (res.isDuplicate) {
        showFeedback(
          `هذه الحصة مسجلة مسبقاً لنفس اليوم والصف والشعبة ورقم الدرس (${getArabicLessonLabel(
            selectedLessonNumber
          )})`,
          true
        );
        return;
      }
      await loadSchedules();
      await onScheduleChanged();
      showFeedback('تمت إضافة الحصة إلى الجدول اليومي بنجاح', false);
    } else {
      const res = await TrackerRepository.updateDailyScheduleEntry(
        editingItem.id,
        selectedDay,
        selectedClassId,
        selectedSectionId,
        selectedLessonNumber,
        startTime,
        endTime
      );
      if (res.isDuplicate) {
        showFeedback(
          `يوجد حصة أخرى مسجلة مسبقاً لنفس اليوم والصف والشعبة ورقم الدرس (${getArabicLessonLabel(
            selectedLessonNumber
          )})`,
          true
        );
        return;
      }
      setEditingItem(null);
      await loadSchedules();
      await onScheduleChanged();
      showFeedback('تم حفظ تعديلات الحصة بنجاح', false);
    }
  };

  const groupedByDay = useMemo(() => {
    const daysToConsider =
      selectedDayFilter === 'ALL'
        ? WORKING_DAYS_ORDER
        : [selectedDayFilter];

    return daysToConsider
      .map((day) => ({
        day,
        lessons: scheduleItems
          .filter((item) => item.dayOfWeek === day)
          .sort((a, b) => {
            const startA = parseTimeMinutes(a.startTime) ?? Number.MAX_SAFE_INTEGER;
            const startB = parseTimeMinutes(b.startTime) ?? Number.MAX_SAFE_INTEGER;
            if (startA !== startB) {
              return startA - startB;
            }
            if (a.startTime !== b.startTime) {
              return a.startTime.localeCompare(b.startTime);
            }
            if (a.lessonNumber !== b.lessonNumber) {
              return a.lessonNumber - b.lessonNumber;
            }
            return a.id - b.id;
          }),
      }))
      .filter((g) => g.lessons.length > 0);
  }, [scheduleItems, selectedDayFilter]);

  const dayFilterOptions = useMemo(
    () => [
      { key: 'ALL', label: 'كل الأيام' },
      ...WORKING_DAYS_ORDER.map((d) => ({ key: d, label: d })),
    ],
    []
  );

  return (
    <div className="space-y-3 pb-16">
      {/* 1. Reorganized Add / Edit Form Card */}
      <section
        className={`bg-white rounded-2xl p-3.5 sm:p-4 border shadow-2xs space-y-2.5 ${
          editingItem ? 'border-amber-500' : 'border-slate-200/80'
        }`}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <Calendar
              className={`w-4 h-4 ${
                editingItem ? 'text-amber-700' : 'text-teal-700'
              }`}
            />
            <h2
              className={`text-sm font-extrabold ${
                editingItem ? 'text-amber-700' : 'text-slate-900'
              }`}
            >
              {editingItem
                ? 'تعديل الحصة المسجلة'
                : 'إضافة حصة إلى الجدول اليومي'}
            </h2>
          </div>
          {editingItem && (
            <button
              type="button"
              onClick={() => {
                resetFormToAddMode();
                setFeedbackMessage(null);
              }}
              className="text-xs font-extrabold text-rose-700 hover:underline"
            >
              إلغاء التعديل
            </button>
          )}
        </div>

        {feedbackMessage && (
          <div
            role="status"
            className={`rounded-xl px-3 py-2 border text-xs font-extrabold ${
              feedbackIsError
                ? 'bg-rose-50 border-rose-300 text-rose-800'
                : 'bg-emerald-50 border-emerald-300 text-emerald-800'
            }`}
          >
            {feedbackMessage}
          </div>
        )}

        {/* Group A: 1. اختيار اليوم */}
        <div className="bg-slate-50 rounded-xl p-2.5 border border-slate-200/80 space-y-1.5">
          <label className="block text-[11px] font-extrabold text-slate-700">
            1. اختيار اليوم:
          </label>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
            {WEEK_DAYS_ORDER.map((day) => {
              const isSelected = selectedDay === day;
              return (
                <button
                  key={day}
                  type="button"
                  onClick={() => {
                    setSelectedDay(day);
                    setFeedbackMessage(null);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 border transition-colors ${
                    isSelected
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-800 border-slate-200 hover:bg-slate-100'
                  }`}
                >
                  {day}
                </button>
              );
            })}
          </div>
        </div>

        {/* Group B: 2. اختيار الصف & 3. اختيار الشعبة */}
        <div className="bg-slate-50 rounded-xl p-2.5 border border-slate-200/80 space-y-2">
          <div className="space-y-1">
            <label className="block text-[11px] font-extrabold text-slate-700">
              2. اختيار الصف:
            </label>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
              {classes.map((cls) => {
                const isSelected = selectedClassId === cls.id;
                return (
                  <button
                    key={cls.id}
                    type="button"
                    onClick={() => {
                      if (selectedClassId !== cls.id) {
                        setSelectedClassId(cls.id);
                        const firstSec = sections.find(
                          (s) => s.classId === cls.id
                        );
                        setSelectedSectionId(firstSec?.id ?? null);
                        setFeedbackMessage(null);
                      }
                    }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 border transition-colors ${
                      isSelected
                        ? 'bg-slate-900 text-white border-slate-900'
                        : 'bg-white text-slate-800 border-slate-200 hover:bg-slate-100'
                    }`}
                  >
                    {cls.className}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1">
            <label className="block text-[11px] font-extrabold text-slate-700">
              3. اختيار الشعبة:
            </label>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
              {availableSections.map((sec) => {
                const isSelected = selectedSectionId === sec.id;
                return (
                  <button
                    key={sec.id}
                    type="button"
                    onClick={() => {
                      setSelectedSectionId(sec.id);
                      setFeedbackMessage(null);
                    }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 border transition-colors ${
                      isSelected
                        ? 'bg-teal-700 text-white border-teal-700'
                        : 'bg-white text-slate-800 border-slate-200 hover:bg-slate-100'
                    }`}
                  >
                    شعبة {sec.sectionName}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Group C: 4. رقم الدرس + 5 & 6. وقت البداية والنهاية */}
        <div className="bg-slate-50 rounded-xl p-2.5 border border-slate-200/80 space-y-2">
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <label className="block text-[11px] font-extrabold text-slate-700">
                4. اختيار رقم الدرس:
              </label>
              <button
                type="button"
                onClick={() => setMaxLessonsCount((c) => c + 1)}
                className="px-2 py-0.5 rounded-md bg-white border border-teal-700/40 text-[10px] font-extrabold text-teal-700 hover:bg-teal-50"
              >
                + إضافة رقم حصة إضافي
              </button>
            </div>
            <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
              {Array.from({ length: maxLessonsCount }, (_, i) => i + 1).map(
                (num) => {
                  const isSelected = selectedLessonNumber === num;
                  return (
                    <button
                      key={num}
                      type="button"
                      onClick={() => {
                        setSelectedLessonNumber(num);
                        setFeedbackMessage(null);
                      }}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 border transition-colors ${
                        isSelected
                          ? 'bg-slate-900 text-white border-slate-900'
                          : 'bg-white text-slate-800 border-slate-200 hover:bg-slate-100'
                      }`}
                    >
                      {getArabicLessonLabel(num)}
                    </button>
                  );
                }
              )}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label className="block text-[11px] font-extrabold text-slate-700 mb-1">
                5. وقت بدء الدرس:
              </label>
              <div className="relative">
                <div className="w-full min-h-[38px] px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-extrabold text-slate-900 flex items-center justify-between pointer-events-none">
                  <span>{formatTime12HourAr(startTime)}</span>
                  <Clock className="w-3.5 h-3.5 text-teal-700 shrink-0" />
                </div>
                <input
                  type="time"
                  value={startTime}
                  aria-label="وقت بدء الدرس"
                  onChange={(e) => {
                    setStartTime(e.target.value);
                    setFeedbackMessage(null);
                  }}
                  onClick={(e) => {
                    try {
                      if ('showPicker' in HTMLInputElement.prototype) {
                        (e.currentTarget as HTMLInputElement).showPicker();
                      }
                    } catch {
                      // Fallback to native input behavior
                    }
                  }}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                />
              </div>
            </div>
            <div>
              <label className="block text-[11px] font-extrabold text-slate-700 mb-1">
                6. وقت انتهاء الدرس:
              </label>
              <div className="relative">
                <div className="w-full min-h-[38px] px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-extrabold text-slate-900 flex items-center justify-between pointer-events-none">
                  <span>{formatTime12HourAr(endTime)}</span>
                  <Clock className="w-3.5 h-3.5 text-teal-700 shrink-0" />
                </div>
                <input
                  type="time"
                  value={endTime}
                  aria-label="وقت انتهاء الدرس"
                  onChange={(e) => {
                    setEndTime(e.target.value);
                    setFeedbackMessage(null);
                  }}
                  onClick={(e) => {
                    try {
                      if ('showPicker' in HTMLInputElement.prototype) {
                        (e.currentTarget as HTMLInputElement).showPicker();
                      }
                    } catch {
                      // Fallback to native input behavior
                    }
                  }}
                  className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                />
              </div>
            </div>
          </div>
        </div>

        {/* 7. Submit Button */}
        <button
          type="button"
          onClick={handleSubmit}
          className={`w-full min-h-[42px] px-4 py-2 rounded-xl text-white text-xs font-extrabold flex items-center justify-center gap-1.5 transition-colors shadow-2xs ${
            editingItem
              ? 'bg-amber-600 hover:bg-amber-700'
              : 'bg-emerald-600 hover:bg-emerald-700'
          }`}
        >
          {editingItem ? (
            <>
              <Save className="w-4 h-4" />
              <span>حفظ تعديل الحصة</span>
            </>
          ) : (
            <>
              <Plus className="w-4 h-4" />
              <span>إضافة الحصة إلى الجدول</span>
            </>
          )}
        </button>
      </section>

      {/* 2. Global Reminder Settings Card (إعداد التنبيه العام لجميع الحصص) */}
      <section
        className={`bg-white rounded-2xl p-3.5 border shadow-2xs space-y-2.5 ${
          globalReminderEnabled && globalReminderMinutes !== null
            ? 'border-teal-700'
            : 'border-slate-200/80'
        }`}
      >
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <div
              className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                globalReminderEnabled && globalReminderMinutes !== null
                  ? 'bg-emerald-50 text-teal-700'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {globalReminderEnabled && globalReminderMinutes !== null ? (
                <Bell className="w-4 h-4" />
              ) : (
                <BellOff className="w-4 h-4" />
              )}
            </div>
            <div>
              <h3 className="text-xs font-extrabold text-slate-900">
                تفعيل التنبيه لجميع الدروس
              </h3>
              <p
                className={`text-[11px] ${
                  globalReminderEnabled && globalReminderMinutes !== null
                    ? 'text-teal-700 font-bold'
                    : 'text-slate-500'
                }`}
              >
                {globalReminderEnabled && globalReminderMinutes !== null
                  ? `مفعّل: إشعار قبل بداية كل حصة بـ ${globalReminderMinutes} دقيقة`
                  : globalReminderMinutes !== null
                    ? `متوقف حالياً (المدة المحفوظة: ${globalReminderMinutes} دقيقة قبل الدرس)`
                    : 'أدخل مدة التذكير بالدقائق ثم فعّل المفتاح لجدولة تنبيهات الحصص'}
              </p>
            </div>
          </div>

          <button
            type="button"
            role="switch"
            aria-checked={globalReminderEnabled && globalReminderMinutes !== null}
            onClick={() => void handleToggleGlobalReminder(!globalReminderEnabled)}
            className={`px-3 py-1.5 rounded-lg text-xs font-extrabold transition-colors shrink-0 ${
              globalReminderEnabled && globalReminderMinutes !== null
                ? 'bg-teal-700 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            {globalReminderEnabled && globalReminderMinutes !== null ? 'مفعّل' : 'إيقاف'}
          </button>
        </div>

        <div className="flex items-center justify-between gap-2 pt-1.5 border-t border-slate-100">
          <label
            htmlFor="global-reminder-minutes-input"
            className="text-[11px] font-extrabold text-slate-700"
          >
            مدة التذكير قبل الدرس (بالدقائق):
          </label>
          <div className="flex items-center gap-1.5">
            <input
              id="global-reminder-minutes-input"
              type="text"
              inputMode="numeric"
              value={reminderMinutesInput}
              onChange={(e) => handleReminderMinutesInputChange(e.target.value)}
              placeholder="مثال: 7 أو 15"
              className={`w-28 px-2.5 py-1.5 rounded-lg border text-xs font-extrabold text-slate-900 text-center focus:outline-none ${
                reminderInputError
                  ? 'border-rose-500 bg-rose-50/40'
                  : 'border-slate-200 bg-slate-50 focus:border-teal-700 focus:bg-white'
              }`}
            />
            <span className="text-[11px] font-bold text-slate-600">دقيقة</span>
          </div>
        </div>

        {reminderInputError && (
          <div
            role="alert"
            className="rounded-lg px-2.5 py-1.5 bg-rose-50 border border-rose-300 text-[11px] font-extrabold text-rose-700"
          >
            {reminderInputError}
          </div>
        )}
      </section>

      {/* 3. Day Filter Bar (فلترة الأيام في عرض الجدول) */}
      <section className="bg-white rounded-xl p-3 border border-slate-200/80 shadow-2xs space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-extrabold text-slate-800">
            تصفية عرض الجدول حسب اليوم:
          </span>
          <span className="text-[11px] font-bold text-teal-700">
            اليوم الحالي: {todayNameAr}
          </span>
        </div>
        <div className="flex items-center gap-1.5 overflow-x-auto pb-0.5">
          {dayFilterOptions.map((item) => {
            const isSelected = selectedDayFilter === item.key;
            return (
              <button
                key={item.key}
                type="button"
                onClick={() => setSelectedDayFilter(item.key)}
                className={`px-3 py-1.5 rounded-lg text-[11px] font-bold shrink-0 transition-colors ${
                  isSelected
                    ? 'bg-slate-900 text-white'
                    : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                }`}
              >
                {item.label}
              </button>
            );
          })}
        </div>
      </section>

      {/* 4. Schedule Display Grouped by Day (Compact Cards) */}
      {groupedByDay.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-6 text-center space-y-1.5">
          <Clock className="w-7 h-7 text-slate-400 mx-auto" />
          <p className="text-xs font-extrabold text-slate-800">
            {selectedDayFilter === 'ALL'
              ? 'لا توجد حصص مسجلة لأيام الدوام (الأحد - الخميس) حالياً'
              : selectedDayFilter === todayNameAr
                ? `لا توجد حصص مسجلة لليوم الحالي (${todayNameAr})`
                : `لا توجد حصص مسجلة ليوم ${selectedDayFilter}`}
          </p>
          <p className="text-[11px] text-slate-500">
            {selectedDayFilter === 'ALL'
              ? 'استخدم النموذج في الأعلى لإضافة حصصك الأسبوعية حسب اليوم والصف والشعبة والوقت'
              : 'يمكنك اختيار «كل الأيام» لعرض جميع حصص أيام الدوام أو إضافة حصة جديدة لهذا اليوم'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {groupedByDay.map(({ day, lessons }) => (
            <div key={day} className="space-y-1.5">
              <div className="px-3.5 py-2 bg-slate-900 text-white rounded-xl flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-extrabold">{day}</span>
                  {day === todayNameAr && (
                    <span className="px-2 py-0.5 bg-emerald-600 text-white rounded-md text-[10px] font-bold">
                      اليوم
                    </span>
                  )}
                </div>
                <span className="px-2 py-0.5 bg-teal-700 text-white rounded-md text-[11px] font-bold">
                  {lessons.length} حصة
                </span>
              </div>

              <div className="space-y-1.5">
                {lessons.map((lesson) => (
                  <div
                    key={lesson.id}
                    className="bg-white rounded-xl border border-slate-200/80 px-3 py-2.5 flex items-center justify-between gap-2.5 shadow-2xs"
                  >
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <span className="px-2 py-0.5 rounded-md bg-slate-100 text-teal-700 text-[11px] font-extrabold shrink-0">
                          {getArabicLessonLabel(lesson.lessonNumber)}
                        </span>
                        <p className="text-xs font-extrabold text-slate-900 truncate">
                          {lesson.className} / شعبة {lesson.sectionName}
                        </p>
                      </div>
                      <p className="text-[11px] font-extrabold text-slate-700 flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                        <span>
                          {formatTime12HourAr(lesson.startTime)} -{' '}
                          {formatTime12HourAr(lesson.endTime)}
                        </span>
                        {globalReminderEnabled && globalReminderMinutes !== null && (
                          <Bell className="w-3 h-3 text-teal-700 mr-1 shrink-0" />
                        )}
                      </p>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() => loadItemForEdit(lesson)}
                        className="px-2.5 py-1.5 rounded-lg border border-slate-200 text-[11px] font-bold text-slate-800 hover:bg-slate-50 flex items-center gap-1"
                      >
                        <Edit3 className="w-3 h-3" />
                        <span>تعديل</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setPendingDeleteItem(lesson)}
                        className="px-2.5 py-1.5 rounded-lg border border-rose-200 text-[11px] font-bold text-rose-600 hover:bg-rose-50 flex items-center gap-1"
                      >
                        <Trash2 className="w-3 h-3" />
                        <span>حذف</span>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {pendingDeleteItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <h3 className="text-base font-extrabold text-rose-700">
              تأكيد حذف الحصة
            </h3>
            <p className="text-sm text-slate-800 font-medium leading-relaxed">
              هل أنت متأكد من حذف هذه الحصة؟
              <br />({pendingDeleteItem.dayOfWeek} —{' '}
              {pendingDeleteItem.className} / {pendingDeleteItem.sectionName} —{' '}
              {getArabicLessonLabel(pendingDeleteItem.lessonNumber)} —{' '}
              {formatTime12HourAr(pendingDeleteItem.startTime)} -{' '}
              {formatTime12HourAr(pendingDeleteItem.endTime)})
            </p>
            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={async () => {
                  const target = pendingDeleteItem;
                  setPendingDeleteItem(null);
                  if (target) {
                    await TrackerRepository.deleteDailyScheduleEntry(target.id);
                    if (editingItem?.id === target.id) {
                      resetFormToAddMode();
                    }
                    await loadSchedules();
                    await onScheduleChanged();
                    showFeedback('تم حذف الحصة من الجدول اليومي', false);
                  }
                }}
                className="flex-1 min-h-[42px] px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                حذف
              </button>
              <button
                type="button"
                onClick={() => setPendingDeleteItem(null)}
                className="flex-1 min-h-[42px] px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-extrabold"
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
