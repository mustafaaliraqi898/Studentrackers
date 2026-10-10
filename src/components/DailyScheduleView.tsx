import React, { useEffect, useMemo, useState } from 'react';
import {
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
    return WEEK_DAYS_ORDER.map((day) => ({
      day,
      lessons: scheduleItems.filter((item) => item.dayOfWeek === day),
    })).filter((g) => g.lessons.length > 0);
  }, [scheduleItems]);

  return (
    <div className="space-y-4 pb-16">
      {/* Add / Edit Form Card */}
      <section
        className={`bg-white rounded-2xl p-4 sm:p-5 border shadow-2xs space-y-4 ${
          editingItem ? 'border-amber-500' : 'border-slate-200/80'
        }`}
      >
        <div className="flex items-center justify-between">
          <h2
            className={`text-base font-extrabold ${
              editingItem ? 'text-amber-700' : 'text-slate-900'
            }`}
          >
            {editingItem
              ? 'تعديل الحصة المسجلة'
              : 'إضافة حصة إلى الجدول اليومي'}
          </h2>
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
            className={`rounded-xl px-3.5 py-2.5 border text-xs font-extrabold ${
              feedbackIsError
                ? 'bg-rose-50 border-rose-300 text-rose-800'
                : 'bg-emerald-50 border-emerald-300 text-emerald-800'
            }`}
          >
            {feedbackMessage}
          </div>
        )}

        {/* 1. اليوم */}
        <div className="space-y-1.5">
          <label className="block text-xs font-extrabold text-slate-800">
            اليوم:
          </label>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
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
                  className={`px-3 py-2 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                    isSelected
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                  }`}
                >
                  {day}
                </button>
              );
            })}
          </div>
        </div>

        {/* 2. الصف */}
        <div className="space-y-1.5">
          <label className="block text-xs font-extrabold text-slate-800">
            الصف:
          </label>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
            {classes.map((cls) => {
              const isSelected = selectedClassId === cls.id;
              return (
                <button
                  key={cls.id}
                  type="button"
                  onClick={() => {
                    if (selectedClassId !== cls.id) {
                      setSelectedClassId(cls.id);
                      const firstSec = sections.find((s) => s.classId === cls.id);
                      setSelectedSectionId(firstSec?.id ?? null);
                      setFeedbackMessage(null);
                    }
                  }}
                  className={`px-3 py-2 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                    isSelected
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                  }`}
                >
                  {cls.className}
                </button>
              );
            })}
          </div>
        </div>

        {/* 3. الشعبة */}
        <div className="space-y-1.5">
          <label className="block text-xs font-extrabold text-slate-800">
            الشعبة:
          </label>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
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
                  className={`px-3 py-2 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                    isSelected
                      ? 'bg-teal-700 text-white'
                      : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                  }`}
                >
                  شعبة {sec.sectionName}
                </button>
              );
            })}
          </div>
        </div>

        {/* 4. الدرس */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="block text-xs font-extrabold text-slate-800">
              الدرس:
            </label>
            <button
              type="button"
              onClick={() => setMaxLessonsCount((c) => c + 1)}
              className="text-[11px] font-extrabold text-teal-700 hover:underline"
            >
              + إضافة رقم حصة إضافي
            </button>
          </div>
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
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
                    className={`px-3 py-2 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                      isSelected
                        ? 'bg-slate-900 text-white'
                        : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                    }`}
                  >
                    {getArabicLessonLabel(num)}
                  </button>
                );
              }
            )}
          </div>
        </div>

        {/* 5 & 6. وقت بدء الدرس و وقت انتهاء الدرس (نظام 12 ساعة) */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-extrabold text-slate-800 mb-1">
              وقت بدء الدرس:
            </label>
            <div className="relative">
              <div className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-extrabold text-slate-900 flex items-center justify-between pointer-events-none">
                <span>{formatTime12HourAr(startTime)}</span>
                <Clock className="w-4 h-4 text-teal-700 shrink-0" />
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
            <label className="block text-xs font-extrabold text-slate-800 mb-1">
              وقت انتهاء الدرس:
            </label>
            <div className="relative">
              <div className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-extrabold text-slate-900 flex items-center justify-between pointer-events-none">
                <span>{formatTime12HourAr(endTime)}</span>
                <Clock className="w-4 h-4 text-teal-700 shrink-0" />
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

        <button
          type="button"
          onClick={handleSubmit}
          className={`w-full min-h-[48px] px-4 py-2.5 rounded-xl text-white text-sm font-extrabold flex items-center justify-center gap-2 transition-colors shadow-2xs ${
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

      {/* Schedule Display Grouped by Day */}
      {groupedByDay.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-8 text-center space-y-2">
          <Clock className="w-8 h-8 text-slate-400 mx-auto" />
          <p className="text-sm font-extrabold text-slate-800">
            لا توجد حصص مسجلة في الجدول اليومي حالياً
          </p>
          <p className="text-xs text-slate-500">
            استخدم النموذج في الأعلى لإضافة حصصك الأسبوعية حسب اليوم والصف والشعبة والوقت
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {groupedByDay.map(({ day, lessons }) => (
            <div key={day} className="space-y-2">
              <div className="px-4 py-3 bg-slate-900 text-white rounded-xl flex items-center justify-between">
                <span className="text-sm font-extrabold">{day}</span>
                <span className="px-2.5 py-1 bg-teal-700 text-white rounded-lg text-xs font-bold">
                  {lessons.length} حصة
                </span>
              </div>

              <div className="space-y-2">
                {lessons.map((lesson) => (
                  <div
                    key={lesson.id}
                    className="bg-white rounded-xl border border-slate-200/80 p-4 flex items-start justify-between gap-3 shadow-2xs"
                  >
                    <div className="space-y-1">
                      <p className="text-sm font-extrabold text-slate-900">
                        {lesson.className} / {lesson.sectionName}
                      </p>
                      <p className="text-xs font-bold text-teal-700">
                        {getArabicLessonLabel(lesson.lessonNumber)}
                      </p>
                      <p className="text-xs font-extrabold text-slate-700 flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-500" />
                        <span>
                          {formatTime12HourAr(lesson.startTime)} -{' '}
                          {formatTime12HourAr(lesson.endTime)}
                        </span>
                      </p>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        onClick={() => loadItemForEdit(lesson)}
                        className="px-3 py-1.5 rounded-lg border border-slate-200 text-xs font-bold text-slate-800 hover:bg-slate-50 flex items-center gap-1"
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                        <span>تعديل</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setPendingDeleteItem(lesson)}
                        className="px-3 py-1.5 rounded-lg border border-rose-200 text-xs font-bold text-rose-600 hover:bg-rose-50 flex items-center gap-1"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
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
