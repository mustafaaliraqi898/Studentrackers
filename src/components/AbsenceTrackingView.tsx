import React, { useEffect, useState } from 'react';
import {
  CalendarX2,
  ChevronLeft,
  Search,
  UserCheck,
  UserX,
  X,
} from 'lucide-react';
import {
  ClassEntity,
  SectionEntity,
  StudentAbsenceSummary,
} from '../types';
import {
  GradingService,
  formatDisplayDate,
  formatScore,
  getArabicDayName,
} from '../services/gradingService';
import { TrackerRepository } from '../repositories/trackerRepository';

interface AbsenceTrackingViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  recordsVersion: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
}

export const AbsenceTrackingView: React.FC<AbsenceTrackingViewProps> = ({
  classes,
  sections,
  selectedClassId,
  selectedSectionId,
  recordsVersion,
  onSelectClass,
  onSelectSection,
}) => {
  const [filterClassId, setFilterClassId] = useState<number | 'ALL'>(
    selectedClassId
  );
  const [filterSectionId, setFilterSectionId] = useState<number | 'ALL'>(
    selectedSectionId
  );
  const [filterMonth, setFilterMonth] = useState<string>('2026-10');
  const [showAbsentOnly, setShowAbsentOnly] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [summaries, setSummaries] = useState<StudentAbsenceSummary[]>([]);
  const [selectedStudentDetails, setSelectedStudentDetails] =
    useState<StudentAbsenceSummary | null>(null);

  const availableSections =
    filterClassId === 'ALL'
      ? []
      : sections.filter((s) => s.classId === filterClassId);

  useEffect(() => {
    let active = true;
    async function loadAbsenceSummaries() {
      const clsParam = filterClassId === 'ALL' ? undefined : filterClassId;
      const secParam =
        filterClassId === 'ALL' || filterSectionId === 'ALL'
          ? undefined
          : filterSectionId;

      const list = await GradingService.calculateAbsenceSummaries(
        clsParam,
        secParam,
        filterMonth
      );
      if (!active) return;
      setSummaries(list);
    }
    loadAbsenceSummaries();
    return () => {
      active = false;
    };
  }, [
    filterClassId,
    filterSectionId,
    filterMonth,
    recordsVersion,
    classes.length,
    sections.length,
  ]);

  const filteredSummaries = summaries.filter((item) => {
    const matchesAbsentFilter = !showAbsentOnly || item.absentDays > 0;
    const q = searchQuery.trim();
    if (!q) return matchesAbsentFilter;
    const qKey = TrackerRepository.canonicalArabicComparisonKey(q);
    const matchesSearch =
      item.studentName.includes(q) ||
      TrackerRepository.canonicalArabicComparisonKey(item.studentName).includes(
        qKey
      );
    return matchesAbsentFilter && matchesSearch;
  });

  const absentStudentsCount = summaries.filter((s) => s.absentDays > 0).length;

  const activeDetailItem = selectedStudentDetails
    ? summaries.find((s) => s.studentId === selectedStudentDetails.studentId) ||
      selectedStudentDetails
    : null;

  return (
    <div className="space-y-5 pb-12">
      {/* Header & Filters Card */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs space-y-4">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            متابعة الغياب
          </h2>
          <p className="text-xs text-slate-500 mt-0.5 leading-relaxed">
            حساب ديناميكي لأيام غياب كل طالب اعتماداً على أيام الدوام الفعلية المسجلة فقط (يُستبعد يومي الجمعة والسبت والعطل المسجلة تلقائياً). مرتب افتراضياً من الأكثر غياباً إلى الأقل غياباً.
          </p>
        </div>

        {/* 3 Filters: Class, Section, Period/Month */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 border-t border-slate-100">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الصف
            </label>
            <select
              value={filterClassId}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ALL') {
                  setFilterClassId('ALL');
                  setFilterSectionId('ALL');
                } else {
                  const num = Number(v);
                  setFilterClassId(num);
                  setFilterSectionId('ALL');
                  onSelectClass(num);
                }
              }}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              <option value="ALL">جميع الصفوف</option>
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الشعبة
            </label>
            <select
              value={filterSectionId}
              disabled={filterClassId === 'ALL'}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ALL') {
                  setFilterSectionId('ALL');
                } else {
                  const num = Number(v);
                  setFilterSectionId(num);
                  onSelectSection(num);
                }
              }}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900 disabled:opacity-50"
            >
              <option value="ALL">جميع الشعب</option>
              {availableSections.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  شعبة {sec.sectionName}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الفترة الزمنية / الشهر
            </label>
            <select
              value={filterMonth}
              onChange={(e) => setFilterMonth(e.target.value)}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900 font-mono-num"
            >
              <option value="2026-10">تشرين الأول (10 / 2026)</option>
              <option value="2026-11">تشرين الثاني (11 / 2026)</option>
              <option value="ALL">كامل الفترة المسجلة</option>
            </select>
          </div>
        </div>

        {/* Search Input + "الغائبون فقط" Filter Toggle */}
        <div className="pt-3 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="relative flex-1">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="ابحث باسم الطالب ضمن الفلاتر الحالية..."
              className="w-full min-h-[44px] pr-9 pl-9 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900 focus:outline-none focus:border-emerald-600 focus:bg-white"
            />
            <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="p-1 text-slate-400 hover:text-slate-700 absolute left-2.5 top-1/2 -translate-y-1/2"
                aria-label="مسح البحث"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={() => setShowAbsentOnly(!showAbsentOnly)}
            className={`min-h-[44px] px-4 py-2 rounded-xl text-xs font-extrabold flex items-center justify-center gap-1.5 transition-all whitespace-nowrap border shrink-0 ${
              showAbsentOnly
                ? 'bg-rose-600 text-white border-rose-600 shadow-xs'
                : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
            }`}
          >
            <UserX className="w-4 h-4" />
            <span>الغائبون فقط</span>
            <span
              className={`px-1.5 py-0.5 rounded-md font-mono-num text-[11px] ${
                showAbsentOnly
                  ? 'bg-rose-700 text-white'
                  : 'bg-slate-200 text-slate-800'
              }`}
            >
              {absentStudentsCount}
            </span>
          </button>
        </div>
      </section>

      {/* Students Absence List */}
      {filteredSummaries.length === 0 ? (
        <section className="bg-white rounded-2xl p-8 border border-slate-200/80 text-center space-y-2">
          <div className="w-12 h-12 rounded-2xl bg-slate-100 text-slate-500 flex items-center justify-center mx-auto">
            <CalendarX2 className="w-6 h-6" />
          </div>
          <h3 className="text-sm font-extrabold text-slate-900">
            {showAbsentOnly
              ? 'لا يوجد طلاب غائبون ضمن الفترة والفلاتر المحددة'
              : 'لا توجد نتائج مطابقة للبحث'}
          </h3>
          <p className="text-xs text-slate-500">
            جرّب تغيير فلتر الصف أو الشعبة أو إلغاء تفعيل &quot;الغائبون فقط&quot;
          </p>
        </section>
      ) : (
        <div className="space-y-3">
          {filteredSummaries.map((item, idx) => {
            const hasAbsences = item.absentDays > 0;
            return (
              <button
                key={item.studentId}
                type="button"
                onClick={() => setSelectedStudentDetails(item)}
                className={`w-full text-right bg-white hover:bg-slate-50/70 rounded-2xl p-4 border transition-all space-y-3 block ${
                  hasAbsences
                    ? 'border-rose-200 shadow-2xs'
                    : 'border-slate-200/80'
                }`}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <span
                      className={`w-7 h-7 rounded-lg font-mono-num text-xs font-extrabold flex items-center justify-center shrink-0 ${
                        hasAbsences
                          ? 'bg-rose-600 text-white'
                          : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {idx + 1}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm sm:text-base font-extrabold text-slate-900 truncate">
                        {item.studentName}
                      </p>
                      <p className="text-xs text-slate-500 mt-0.5 truncate">
                        {item.className} · شعبة {item.sectionName}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <div
                      className={`px-3 py-1.5 rounded-xl border text-center ${
                        hasAbsences
                          ? 'bg-rose-50 border-rose-200 text-rose-700'
                          : 'bg-emerald-50 border-emerald-200 text-emerald-700'
                      }`}
                    >
                      <span className="text-[11px] font-bold block">
                        أيام الغياب
                      </span>
                      <span className="text-base font-extrabold font-mono-num">
                        {item.absentDays}
                      </span>
                    </div>
                    <ChevronLeft className="w-4 h-4 text-slate-400" />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-2 pt-2.5 border-t border-slate-100 text-xs">
                  <div className="bg-slate-50 rounded-xl p-2 border border-slate-200/60">
                    <span className="text-slate-500 block text-[11px]">
                      أيام الحضور
                    </span>
                    <span className="font-mono-num font-extrabold text-emerald-700 mt-0.5 block">
                      {item.presentDays}{' '}
                      <span className="text-[10px] text-slate-400 font-normal">
                        / {item.totalWorkingDays} دوام
                      </span>
                    </span>
                  </div>

                  <div className="bg-slate-50 rounded-xl p-2 border border-slate-200/60">
                    <span className="text-slate-500 block text-[11px]">
                      نسبة الحضور
                    </span>
                    <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                      {formatScore(item.attendancePercentage)}%
                    </span>
                  </div>

                  <div className="bg-slate-50 rounded-xl p-2 border border-slate-200/60">
                    <span className="text-slate-500 block text-[11px]">
                      آخر تاريخ غياب
                    </span>
                    <span
                      className={`font-mono-num font-extrabold mt-0.5 block truncate ${
                        item.lastAbsentDate
                          ? 'text-rose-700'
                          : 'text-slate-400'
                      }`}
                    >
                      {item.lastAbsentDate
                        ? formatDisplayDate(item.lastAbsentDate)
                        : 'لا يوجد'}
                    </span>
                  </div>
                </div>
              </button>
            );
          })}
        </div>
      )}

      {/* Student Absence Details Modal */}
      {activeDetailItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs overflow-y-auto">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden my-4"
          >
            <div className="p-4 bg-slate-900 text-white flex items-center justify-between gap-3">
              <div className="min-w-0">
                <h3 className="text-base font-extrabold truncate">
                  {activeDetailItem.studentName}
                </h3>
                <p className="text-xs text-slate-300 mt-0.5">
                  {activeDetailItem.className} / شعبة{' '}
                  {activeDetailItem.sectionName}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setSelectedStudentDetails(null)}
                className="min-h-[36px] min-w-[36px] rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center justify-center shrink-0"
                aria-label="إغلاق التفاصيل"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="p-4 sm:p-5 space-y-4">
              {/* Summary Metrics */}
              <div className="grid grid-cols-2 gap-2.5 text-xs">
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                  <span className="text-slate-500 block">
                    إجمالي أيام الدوام
                  </span>
                  <span className="text-base font-extrabold text-slate-900 font-mono-num mt-0.5 block">
                    {activeDetailItem.totalWorkingDays}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-emerald-50/70 border border-emerald-200">
                  <span className="text-emerald-800 block">أيام الحضور</span>
                  <span className="text-base font-extrabold text-emerald-700 font-mono-num mt-0.5 block">
                    {activeDetailItem.presentDays}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-rose-50/80 border border-rose-200">
                  <span className="text-rose-800 block">أيام الغياب</span>
                  <span className="text-base font-extrabold text-rose-700 font-mono-num mt-0.5 block">
                    {activeDetailItem.absentDays}
                  </span>
                </div>

                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200/80">
                  <span className="text-slate-500 block">نسبة الحضور</span>
                  <span className="text-base font-extrabold text-slate-900 font-mono-num mt-0.5 block">
                    {formatScore(activeDetailItem.attendancePercentage)}%
                  </span>
                </div>
              </div>

              {/* Absence Dates List (Newest to Oldest) */}
              <div className="space-y-2">
                <h4 className="text-xs font-extrabold text-slate-800">
                  قائمة أيام الغياب (من الأحدث إلى الأقدم):
                </h4>

                {activeDetailItem.absentDatesNewestFirst.length === 0 ? (
                  <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-bold flex items-center gap-2">
                    <UserCheck className="w-4 h-4 shrink-0" />
                    <span>
                      لا يوجد أي يوم غياب مسجل لهذا الطالب في الفترة المحددة.
                    </span>
                  </div>
                ) : (
                  <div className="max-h-60 overflow-y-auto space-y-1.5 pr-0.5">
                    {activeDetailItem.absentDatesNewestFirst.map((dateStr) => (
                      <div
                        key={dateStr}
                        className="p-2.5 rounded-xl bg-rose-50/80 border border-rose-200 flex items-center justify-between text-xs"
                      >
                        <div className="flex items-center gap-2">
                          <span className="font-mono-num font-extrabold text-slate-900">
                            {formatDisplayDate(dateStr)}
                          </span>
                          <span className="text-slate-600 font-bold">
                            — {getArabicDayName(dateStr)}
                          </span>
                        </div>
                        <span className="px-2.5 py-1 rounded-lg bg-rose-600 text-white font-extrabold text-[11px]">
                          الحالة: غائب
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <button
                type="button"
                onClick={() => setSelectedStudentDetails(null)}
                className="w-full min-h-[42px] bg-slate-900 hover:bg-slate-800 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                إغلاق
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
