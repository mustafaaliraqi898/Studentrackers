import React, { useEffect, useMemo, useState } from 'react';
import {
  CalendarRange,
  Edit3,
  Lock,
  PlusCircle,
  Trash2,
} from 'lucide-react';
import { StudyPeriodEntity } from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';
import { formatDisplayDate } from '../services/gradingService';

interface StudyPeriodsViewProps {
  recordsVersion: number;
  onPeriodsChanged: () => Promise<void>;
}

function addOneDayIso(isoDate: string): string {
  const parts = isoDate.trim().split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return '2026-10-05';
  const dt = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
  dt.setUTCDate(dt.getUTCDate() + 1);
  return dt.toISOString().slice(0, 10);
}

export const StudyPeriodsView: React.FC<StudyPeriodsViewProps> = ({
  recordsVersion,
  onPeriodsChanged,
}) => {
  const [periods, setPeriods] = useState<StudyPeriodEntity[]>([]);
  const [bannerMessage, setBannerMessage] = useState<string | null>(null);
  const [bannerIsError, setBannerIsError] = useState<boolean>(false);

  // Dialog states
  const [showStartModal, setShowStartModal] = useState<boolean>(false);
  const [newPeriodName, setNewPeriodName] = useState<string>('');
  const [newStartDate, setNewStartDate] = useState<string>('2026-10-05');
  const [startModalError, setStartModalError] = useState<string | null>(null);

  const [closingPeriod, setClosingPeriod] = useState<StudyPeriodEntity | null>(
    null
  );
  const [closeEndDate, setCloseEndDate] = useState<string>('');
  const [closeModalError, setCloseModalError] = useState<string | null>(null);

  const [editingPeriod, setEditingPeriod] = useState<StudyPeriodEntity | null>(
    null
  );
  const [editPeriodName, setEditPeriodName] = useState<string>('');
  const [editStartDate, setEditStartDate] = useState<string>('');
  const [editEndDate, setEditEndDate] = useState<string>('');
  const [editModalError, setEditModalError] = useState<string | null>(null);

  const [deletingPeriod, setDeletingPeriod] =
    useState<StudyPeriodEntity | null>(null);

  const loadPeriods = async () => {
    const list = await TrackerRepository.getStudyPeriods();
    setPeriods(list);
  };

  useEffect(() => {
    void loadPeriods();
  }, [recordsVersion]);

  const openPeriod = useMemo(
    () => periods.find((p) => p.status === 'OPEN') || null,
    [periods]
  );

  const closedPeriods = useMemo(
    () =>
      periods
        .filter((p) => p.status === 'CLOSED')
        .sort((a, b) => b.startDate.localeCompare(a.startDate)),
    [periods]
  );

  const showFeedback = (msg: string, isError: boolean) => {
    setBannerMessage(msg);
    setBannerIsError(isError);
  };

  const getSuggestedNextStartDate = (): string => {
    const closedWithEnd = periods.filter(
      (p) => p.status === 'CLOSED' && p.endDate
    );
    if (closedWithEnd.length === 0) return '2026-10-05';
    const latestEnd = closedWithEnd.reduce(
      (max, p) => (p.endDate && p.endDate > max ? p.endDate : max),
      closedWithEnd[0].endDate || '2026-10-05'
    );
    return addOneDayIso(latestEnd);
  };

  const handleOpenStartModal = async () => {
    if (openPeriod) {
      showFeedback(
        'توجد فترة دراسية مفتوحة حالياً. يجب إنهاؤها أولاً قبل بدء فترة جديدة.',
        true
      );
      return;
    }
    const suggestedName = await TrackerRepository.getSuggestedNextPeriodName();
    setNewPeriodName(suggestedName);
    setNewStartDate(getSuggestedNextStartDate());
    setStartModalError(null);
    setShowStartModal(true);
  };

  const handleConfirmStartPeriod = async () => {
    const res = await TrackerRepository.startNewStudyPeriod(
      newPeriodName,
      newStartDate
    );
    if (res.error) {
      setStartModalError(res.error);
      return;
    }
    setShowStartModal(false);
    await loadPeriods();
    await onPeriodsChanged();
    showFeedback('تم بدء الفترة الدراسية الجديدة بنجاح (مفتوحة)', false);
  };

  const handleConfirmClosePeriod = async () => {
    if (!closingPeriod) return;
    const res = await TrackerRepository.closeStudyPeriod(
      closingPeriod.id,
      closeEndDate
    );
    if (res.error) {
      setCloseModalError(res.error);
      return;
    }
    const closedName = closingPeriod.periodName;
    setClosingPeriod(null);
    await loadPeriods();
    await onPeriodsChanged();
    showFeedback(
      `تم إنهاء «${closedName}» وتحويل حالتها إلى مغلقة`,
      false
    );
  };

  const handleConfirmEditPeriod = async () => {
    if (!editingPeriod) return;
    const isOpen = editingPeriod.status === 'OPEN';
    const res = await TrackerRepository.updateStudyPeriod(
      editingPeriod.id,
      editPeriodName,
      editStartDate,
      isOpen ? null : editEndDate
    );
    if (res.error) {
      setEditModalError(res.error);
      return;
    }
    setEditingPeriod(null);
    await loadPeriods();
    await onPeriodsChanged();
    showFeedback('تم حفظ تعديلات الفترة بنجاح', false);
  };

  const handleConfirmDeletePeriod = async () => {
    if (!deletingPeriod) return;
    const id = deletingPeriod.id;
    setDeletingPeriod(null);
    await TrackerRepository.deleteStudyPeriod(id);
    await loadPeriods();
    await onPeriodsChanged();
    showFeedback('تم حذف الفترة الدراسية دون المساس بسجلات الطلاب', false);
  };

  return (
    <div className="space-y-4 pb-16">
      {/* Header & Start New Period Action */}
      <section className="bg-slate-900 text-white rounded-2xl p-5 shadow-sm space-y-3">
        <div>
          <h2 className="text-base sm:text-lg font-extrabold">
            نظام الفترات الدراسية
          </h2>
          <p className="text-xs text-slate-300 mt-1 leading-relaxed">
            حدد تاريخ بداية ونهاية كل فترة دراسية بنفسك دون تقيد بشهر ميلادي ثابت.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void handleOpenStartModal()}
          className={`w-full min-h-[48px] px-4 py-2.5 rounded-xl text-sm font-extrabold flex items-center justify-center gap-2 transition-colors ${
            openPeriod
              ? 'bg-slate-700 hover:bg-slate-600 text-white'
              : 'bg-emerald-600 hover:bg-emerald-500 text-white'
          }`}
        >
          <PlusCircle className="w-4 h-4" />
          <span>بدء فترة جديدة</span>
        </button>
      </section>

      {/* Feedback Banner */}
      {bannerMessage && (
        <div
          role="status"
          className={`rounded-xl px-4 py-3 border flex items-center justify-between gap-2 text-xs font-extrabold ${
            bannerIsError
              ? 'bg-rose-50 border-rose-300 text-rose-800'
              : 'bg-emerald-50 border-emerald-300 text-emerald-800'
          }`}
        >
          <span>{bannerMessage}</span>
          <button
            type="button"
            onClick={() => setBannerMessage(null)}
            className="text-[11px] font-bold text-slate-600 hover:underline shrink-0"
          >
            إغلاق
          </button>
        </div>
      )}

      {/* Section 1: الفترة الحالية */}
      <section className="space-y-2.5">
        <h3 className="text-sm font-extrabold text-slate-900">الفترة الحالية</h3>

        {openPeriod ? (
          <div className="bg-white rounded-2xl border-2 border-emerald-600 p-4 sm:p-5 space-y-3 shadow-2xs">
            <div className="flex items-center justify-between">
              <h4 className="text-base font-extrabold text-slate-900">
                {openPeriod.periodName}
              </h4>
              <span className="px-2.5 py-1 rounded-lg bg-emerald-100 border border-emerald-600 text-emerald-800 text-xs font-extrabold">
                مفتوحة
              </span>
            </div>

            <div className="bg-emerald-50/80 rounded-xl p-3.5 space-y-1.5 border border-emerald-200/80">
              <p className="text-sm font-extrabold text-emerald-800 font-mono-num">
                {formatDisplayDate(openPeriod.startDate)} → مفتوحة
              </p>
              <p className="text-xs font-bold text-slate-800 font-mono-num">
                من: {formatDisplayDate(openPeriod.startDate)}
              </p>
              <p className="text-xs font-bold text-slate-800">إلى: مفتوحة</p>
              <p className="text-xs font-extrabold text-emerald-700">
                الحالة: مفتوحة
              </p>
            </div>

            <div className="flex items-center gap-2 pt-1">
              <button
                type="button"
                onClick={() => {
                  setClosingPeriod(openPeriod);
                  setCloseEndDate(openPeriod.startDate);
                  setCloseModalError(null);
                }}
                className="flex-1 min-h-[44px] px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-extrabold flex items-center justify-center gap-1.5"
              >
                <Lock className="w-4 h-4" />
                <span>إنهاء الفترة</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setEditingPeriod(openPeriod);
                  setEditPeriodName(openPeriod.periodName);
                  setEditStartDate(openPeriod.startDate);
                  setEditEndDate('');
                  setEditModalError(null);
                }}
                className="min-h-[44px] px-3.5 py-2 border border-slate-200 hover:bg-slate-50 text-slate-800 rounded-xl text-xs font-bold flex items-center gap-1"
              >
                <Edit3 className="w-3.5 h-3.5" />
                <span>تعديل</span>
              </button>

              <button
                type="button"
                onClick={() => setDeletingPeriod(openPeriod)}
                className="min-h-[44px] px-3 py-2 border border-rose-200 hover:bg-rose-50 text-rose-600 rounded-xl text-xs font-bold flex items-center justify-center"
                title="حذف الفترة"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        ) : (
          <div className="bg-white rounded-2xl border border-slate-200/80 p-6 text-center space-y-2">
            <CalendarRange className="w-7 h-7 text-slate-400 mx-auto" />
            <p className="text-sm font-extrabold text-slate-800">
              لا توجد فترة دراسية مفتوحة حالياً
            </p>
            <p className="text-xs text-slate-500">
              اضغط على «بدء فترة جديدة» في الأعلى لتحديد تاريخ البداية وفتح فترة جديدة
            </p>
          </div>
        )}
      </section>

      {/* Section 2: الفترات السابقة */}
      <section className="space-y-2.5">
        <h3 className="text-sm font-extrabold text-slate-900">
          الفترات السابقة ({closedPeriods.length})
        </h3>

        {closedPeriods.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200/80 p-4 text-xs text-slate-600 font-medium">
            لا توجد فترات دراسية مغلقة حتى الآن.
          </div>
        ) : (
          <div className="space-y-2.5">
            {closedPeriods.map((period) => (
              <div
                key={period.id}
                className="bg-white rounded-2xl border border-slate-200/80 p-4 space-y-2.5 shadow-2xs"
              >
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-extrabold text-slate-900">
                    {period.periodName}
                  </h4>
                  <span className="px-2.5 py-1 rounded-lg bg-slate-100 border border-slate-200 text-slate-700 text-[11px] font-extrabold">
                    مغلقة
                  </span>
                </div>

                <p className="text-sm font-extrabold text-teal-700 font-mono-num">
                  {formatDisplayDate(period.startDate)} →{' '}
                  {formatDisplayDate(period.endDate || '')}
                </p>

                <div className="flex items-center justify-between gap-2 pt-1">
                  <p className="text-xs text-slate-600 font-mono-num">
                    البداية: {formatDisplayDate(period.startDate)} · النهاية:{' '}
                    {formatDisplayDate(period.endDate || '')}
                  </p>

                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => {
                        setEditingPeriod(period);
                        setEditPeriodName(period.periodName);
                        setEditStartDate(period.startDate);
                        setEditEndDate(period.endDate || period.startDate);
                        setEditModalError(null);
                      }}
                      className="px-3 py-1.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-xs font-bold text-slate-800 flex items-center gap-1"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>تعديل</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeletingPeriod(period)}
                      className="px-3 py-1.5 rounded-lg border border-rose-200 hover:bg-rose-50 text-xs font-bold text-rose-600 flex items-center gap-1"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>حذف</span>
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Modal 1: بدء فترة جديدة */}
      {showStartModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <h3 className="text-base font-extrabold text-slate-900">
              بدء فترة دراسية جديدة
            </h3>

            {startModalError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-300 text-xs font-extrabold text-rose-800">
                {startModalError}
              </div>
            )}

            <div className="space-y-1.5">
              <label className="block text-xs font-extrabold text-slate-800">
                اسم الفترة الدراسية:
              </label>
              <input
                type="text"
                value={newPeriodName}
                onChange={(e) => {
                  setNewPeriodName(e.target.value);
                  setStartModalError(null);
                }}
                className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-bold text-slate-900"
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-extrabold text-slate-800">
                تاريخ البداية (مطلوب):
              </label>
              <input
                type="date"
                value={newStartDate}
                onChange={(e) => {
                  setNewStartDate(e.target.value);
                  setStartModalError(null);
                }}
                className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono-num font-extrabold text-slate-900"
              />
              {newStartDate && (
                <p className="text-xs font-bold text-teal-700 font-mono-num">
                  التاريخ المحدد: {formatDisplayDate(newStartDate)}
                </p>
              )}
            </div>

            <p className="text-[11px] text-slate-500 leading-relaxed">
              ستصبح هذه الفترة «مفتوحة» مباشرة، ولا يُطلب تحديد تاريخ النهاية إلا عند الضغط على «إنهاء الفترة».
            </p>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => void handleConfirmStartPeriod()}
                className="flex-1 min-h-[42px] px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-extrabold"
              >
                بدء الفترة
              </button>
              <button
                type="button"
                onClick={() => setShowStartModal(false)}
                className="flex-1 min-h-[42px] px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-extrabold"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal 2: إنهاء الفترة */}
      {closingPeriod && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <h3 className="text-base font-extrabold text-slate-900">
              إنهاء {closingPeriod.periodName}
            </h3>

            {closeModalError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-300 text-xs font-extrabold text-rose-800">
                {closeModalError}
              </div>
            )}

            <p className="text-xs font-bold text-slate-700 font-mono-num">
              تاريخ البداية المسجل: {formatDisplayDate(closingPeriod.startDate)}
            </p>

            <div className="space-y-1.5">
              <label className="block text-xs font-extrabold text-slate-800">
                حدد تاريخ النهاية:
              </label>
              <input
                type="date"
                value={closeEndDate}
                onChange={(e) => {
                  setCloseEndDate(e.target.value);
                  setCloseModalError(null);
                }}
                className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono-num font-extrabold text-slate-900"
              />
              {closeEndDate && (
                <p className="text-xs font-bold text-amber-700 font-mono-num">
                  التاريخ المحدد: {formatDisplayDate(closeEndDate)}
                </p>
              )}
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => void handleConfirmClosePeriod()}
                className="flex-1 min-h-[42px] px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white rounded-xl text-xs font-extrabold"
              >
                حفظ وإنهاء الفترة
              </button>
              <button
                type="button"
                onClick={() => setClosingPeriod(null)}
                className="flex-1 min-h-[42px] px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-extrabold"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal 3: تعديل الفترة */}
      {editingPeriod && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <h3 className="text-base font-extrabold text-slate-900">
              تعديل بيانات الفترة
            </h3>

            {editModalError && (
              <div className="p-3 rounded-xl bg-rose-50 border border-rose-300 text-xs font-extrabold text-rose-800">
                {editModalError}
              </div>
            )}

            <div className="space-y-1.5">
              <label className="block text-xs font-extrabold text-slate-800">
                اسم الفترة:
              </label>
              <input
                type="text"
                value={editPeriodName}
                onChange={(e) => {
                  setEditPeriodName(e.target.value);
                  setEditModalError(null);
                }}
                className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-bold text-slate-900"
              />
            </div>

            {editingPeriod.status === 'OPEN' ? (
              <div className="space-y-1.5">
                <label className="block text-xs font-extrabold text-slate-800">
                  تاريخ البداية (الفترة مفتوحة):
                </label>
                <input
                  type="date"
                  value={editStartDate}
                  onChange={(e) => {
                    setEditStartDate(e.target.value);
                    setEditModalError(null);
                  }}
                  className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono-num font-extrabold text-slate-900"
                />
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-xs font-bold text-slate-600 font-mono-num">
                  تاريخ البداية (ثابت بعد الإغلاق):{' '}
                  {formatDisplayDate(editingPeriod.startDate)}
                </p>
                <div className="space-y-1.5">
                  <label className="block text-xs font-extrabold text-slate-800">
                    تعديل تاريخ النهاية (للفترة المغلقة):
                  </label>
                  <input
                    type="date"
                    value={editEndDate}
                    onChange={(e) => {
                      setEditEndDate(e.target.value);
                      setEditModalError(null);
                    }}
                    className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono-num font-extrabold text-slate-900"
                  />
                </div>
              </div>
            )}

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => void handleConfirmEditPeriod()}
                className="flex-1 min-h-[42px] px-4 py-2 bg-teal-700 hover:bg-teal-800 text-white rounded-xl text-xs font-extrabold"
              >
                حفظ التعديلات
              </button>
              <button
                type="button"
                onClick={() => setEditingPeriod(null)}
                className="flex-1 min-h-[42px] px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-extrabold"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal 4: تأكيد حذف الفترة */}
      {deletingPeriod && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <h3 className="text-base font-extrabold text-rose-700">
              تأكيد حذف الفترة
            </h3>
            <p className="text-sm text-slate-800 font-medium leading-relaxed">
              هل أنت متأكد من حذف هذه الفترة؟
              <br />({deletingPeriod.periodName}:{' '}
              {formatDisplayDate(deletingPeriod.startDate)} →{' '}
              {deletingPeriod.endDate
                ? formatDisplayDate(deletingPeriod.endDate)
                : 'مفتوحة'}
              )
              <br />
              <span className="text-xs text-slate-500">
                ملاحظة: لن يتم حذف أي سجلات طلاب أو حضور أو تقييمات عند حذف الفترة.
              </span>
            </p>
            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => void handleConfirmDeletePeriod()}
                className="flex-1 min-h-[42px] px-4 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                حذف الفترة
              </button>
              <button
                type="button"
                onClick={() => setDeletingPeriod(null)}
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
