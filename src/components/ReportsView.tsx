import React, { useEffect, useState } from 'react';
import {
  ArrowDownWideNarrow,
  ArrowUpNarrowWide,
  Edit3,
  Eye,
  FileDown,
  ListOrdered,
  Trophy,
} from 'lucide-react';
import {
  AppScreen,
  ClassEntity,
  SectionEntity,
  StudentComprehensiveGradeReport,
  StudentPeriodGradeBreakdown,
  StudyPeriodEntity,
} from '../types';
import { GradingService, formatScore, round2 } from '../services/gradingService';
import { ExcelService } from '../services/excelService';
import { TrackerRepository } from '../repositories/trackerRepository';

interface ReportsViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
  onNavigate: (screen: AppScreen, params?: { studentId?: number }) => void;
  onDataChanged?: () => Promise<void>;
}

type SortMode = 'score-desc' | 'score-asc' | 'name';

export const ReportsView: React.FC<ReportsViewProps> = ({
  classes,
  sections,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
  onNavigate,
  onDataChanged,
}) => {
  const [filterClassId, setFilterClassId] = useState<number | 'ALL'>(
    selectedClassId
  );
  const [filterSectionId, setFilterSectionId] = useState<number | 'ALL'>(
    selectedSectionId
  );
  const [filterPeriod, setFilterPeriod] = useState<string>('ALL');
  const [sortMode, setSortMode] = useState<SortMode>('score-desc');
  const [reports, setReports] = useState<StudentComprehensiveGradeReport[]>([]);
  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodEntity[]>([]);
  const [reloadTick, setReloadTick] = useState<number>(0);

  // Written exam modal state
  const [editingStudent, setEditingStudent] =
    useState<StudentComprehensiveGradeReport | null>(null);
  const [editingBreakdown, setEditingBreakdown] =
    useState<StudentPeriodGradeBreakdown | null>(null);
  const [writtenInput, setWrittenInput] = useState<string>('');
  const [writtenError, setWrittenError] = useState<string | null>(null);

  const availableSections =
    filterClassId === 'ALL'
      ? []
      : sections.filter((s) => s.classId === filterClassId);

  useEffect(() => {
    let active = true;
    async function loadReport() {
      const clsParam = filterClassId === 'ALL' ? undefined : filterClassId;
      const secParam =
        filterClassId === 'ALL' || filterSectionId === 'ALL'
          ? undefined
          : filterSectionId;
      const [periods, list] = await Promise.all([
        TrackerRepository.getStudyPeriods(),
        GradingService.calculateComprehensiveStudentReports(
          clsParam,
          secParam,
          filterPeriod
        ),
      ]);
      if (!active) return;
      setStudyPeriods(periods);
      setReports(list);
    }
    loadReport();
    return () => {
      active = false;
    };
  }, [filterClassId, filterSectionId, filterPeriod, reloadTick]);

  const getPrimarySortScore = (rep: StudentComprehensiveGradeReport): number => {
    if (filterPeriod.startsWith('PERIOD:')) {
      const pid = Number(filterPeriod.replace('PERIOD:', ''));
      const bd = rep.periodBreakdowns.find((b) => b.period.id === pid);
      if (bd) {
        return bd.periodAverage ?? bd.dailyGrade;
      }
    }
    return rep.finalAverage ?? rep.baseDailyGrade.totalScore;
  };

  const sortedReports = [...reports].sort((a, b) => {
    if (sortMode === 'score-desc') {
      const diff = getPrimarySortScore(b) - getPrimarySortScore(a);
      return diff !== 0 ? diff : b.baseDailyGrade.totalScore - a.baseDailyGrade.totalScore;
    }
    if (sortMode === 'score-asc') {
      const diff = getPrimarySortScore(a) - getPrimarySortScore(b);
      return diff !== 0 ? diff : a.baseDailyGrade.totalScore - b.baseDailyGrade.totalScore;
    }
    return a.studentName.localeCompare(b.studentName, 'ar');
  });

  const handleDirectExcelExport = () => {
    const clsName =
      filterClassId === 'ALL'
        ? 'جميع_الصفوف'
        : classes.find((c) => c.id === filterClassId)?.className || 'الصف';
    const secLabel =
      filterClassId === 'ALL' || filterSectionId === 'ALL'
        ? 'جميع_الشعب'
        : `شعبة_${
            sections.find((s) => s.id === filterSectionId)?.sectionName || ''
          }`;
    const periodsToExport = filterPeriod.startsWith('PERIOD:')
      ? studyPeriods.filter(
          (p) => p.id === Number(filterPeriod.replace('PERIOD:', ''))
        )
      : studyPeriods;
    const periodLabel =
      filterPeriod === 'ALL'
        ? 'جميع_الفترات'
        : periodsToExport[0]?.periodName || filterPeriod;

    ExcelService.exportComprehensiveResultsToExcel(
      sortedReports,
      periodsToExport,
      clsName,
      secLabel,
      periodLabel
    );
  };

  const handleSaveWrittenExam = async () => {
    if (!editingStudent || !editingBreakdown) return;
    const trimmed = writtenInput.trim();
    const numericVal = trimmed === '' ? null : Number(trimmed);
    if (trimmed !== '' && (numericVal === null || Number.isNaN(numericVal))) {
      setWrittenError('يرجى إدخال رقم صحيح أو عشري صالح بين 0 و 100');
      return;
    }
    if (numericVal !== null && (numericVal < 0 || numericVal > 100)) {
      setWrittenError('يجب أن تكون درجة الامتحان التحريري بين 0 و 100');
      return;
    }

    const res = await TrackerRepository.upsertPeriodGrade(
      editingStudent.studentId,
      editingBreakdown.period.id,
      editingBreakdown.dailyGrade,
      numericVal
    );
    if (res.error) {
      setWrittenError(res.error);
      return;
    }

    setEditingStudent(null);
    setEditingBreakdown(null);
    setReloadTick((t) => t + 1);
  };

  return (
    <div className="space-y-5 pb-10">
      {/* Header & Filter Controls */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-extrabold text-slate-900">
              التقارير والنتائج ومعدلات الفترات الدراسية (من 100)
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              الدرجة اليومية + درجة الامتحان التحريري + معدل كل فترة + المعدل النهائي
            </p>
          </div>

          <button
            type="button"
            onClick={handleDirectExcelExport}
            className="min-h-[42px] px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-extrabold flex items-center gap-1.5 self-start sm:self-auto whitespace-nowrap"
          >
            <FileDown className="w-4 h-4" />
            <span>تصدير النتائج إلى Excel</span>
          </button>
        </div>

        {/* 3 Filters: Class, Section, StudyPeriod */}
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
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
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
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
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
              الفترة الدراسية
            </label>
            <select
              value={filterPeriod}
              onChange={(e) => setFilterPeriod(e.target.value)}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold font-mono-num"
            >
              <option value="ALL">جميع الفترات والمعدل النهائي</option>
              {studyPeriods.map((sp) => (
                <option key={sp.id} value={`PERIOD:${sp.id}`}>
                  {sp.periodName} ({sp.status === 'OPEN' ? 'مفتوحة' : 'مغلقة'})
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Sort Controls */}
        <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
          <span className="text-xs font-bold text-slate-600">
            ترتيب الطلاب حسب الدرجة / المعدل:
          </span>

          <div className="flex items-center gap-1.5 p-1 bg-slate-100 rounded-xl">
            <button
              type="button"
              onClick={() => setSortMode('score-desc')}
              className={`min-h-[38px] px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all whitespace-nowrap ${
                sortMode === 'score-desc'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ArrowDownWideNarrow className="w-3.5 h-3.5" />
              <span>من الأعلى إلى الأقل</span>
            </button>

            <button
              type="button"
              onClick={() => setSortMode('score-asc')}
              className={`min-h-[38px] px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all whitespace-nowrap ${
                sortMode === 'score-asc'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ArrowUpNarrowWide className="w-3.5 h-3.5" />
              <span>من الأقل إلى الأعلى</span>
            </button>

            <button
              type="button"
              onClick={() => setSortMode('name')}
              className={`min-h-[38px] px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all whitespace-nowrap ${
                sortMode === 'name'
                  ? 'bg-white text-slate-900 shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ListOrdered className="w-3.5 h-3.5" />
              <span>أبجدياً</span>
            </button>
          </div>
        </div>
      </section>

      {/* Students Report Cards */}
      <div className="space-y-3">
        {sortedReports.map((rep, index) => {
          const g = rep.baseDailyGrade;
          const isTopThree = sortMode === 'score-desc' && index < 3;
          const visibleBreakdowns = filterPeriod.startsWith('PERIOD:')
            ? rep.periodBreakdowns.filter(
                (b) => b.period.id === Number(filterPeriod.replace('PERIOD:', ''))
              )
            : rep.periodBreakdowns;

          return (
            <div
              key={rep.studentId}
              className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs space-y-3.5"
            >
              <div className="flex items-center justify-between gap-3 border-b border-slate-100 pb-3">
                <div className="flex items-center gap-3 min-w-0">
                  <span
                    className={`w-7 h-7 rounded-lg font-mono-num text-xs font-extrabold flex items-center justify-center shrink-0 ${
                      isTopThree
                        ? 'bg-emerald-600 text-white'
                        : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {index + 1}
                  </span>
                  <div className="min-w-0">
                    <button
                      type="button"
                      onClick={() =>
                        onNavigate('student-profile', { studentId: rep.studentId })
                      }
                      className="text-base font-extrabold text-slate-900 hover:text-emerald-700 transition-colors text-right flex items-center gap-1.5 truncate"
                    >
                      <span className="truncate">{rep.studentName}</span>
                      {index === 0 && sortMode === 'score-desc' && (
                        <Trophy className="w-4 h-4 text-amber-500 shrink-0" />
                      )}
                    </button>
                    <p className="text-xs text-slate-500 font-mono-num mt-0.5">
                      {rep.className} / الشعبة {rep.sectionName} · حضور {g.presentDays}/
                      {g.totalWorkingDays} أيام ({formatScore(g.attendancePercentage)}%)
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="text-left bg-teal-50 border border-teal-200 px-3 py-1.5 rounded-xl">
                    <span className="text-[10px] text-teal-800 font-bold block">
                      اليومي
                    </span>
                    <div className="font-mono-num flex items-baseline gap-1">
                      <span className="text-base font-extrabold text-teal-700">
                        {formatScore(g.totalScore)}
                      </span>
                      <span className="text-[10px] text-teal-600">/ 100</span>
                    </div>
                  </div>

                  <div className="text-left bg-slate-900 text-white px-3.5 py-1.5 rounded-xl">
                    <span className="text-[10px] text-slate-300 block">
                      المعدل النهائي
                    </span>
                    <div className="font-mono-num flex items-baseline gap-1">
                      <span className="text-base font-extrabold text-emerald-400">
                        {rep.finalAverage !== null
                          ? formatScore(rep.finalAverage)
                          : '--'}
                      </span>
                      {rep.finalAverage !== null && (
                        <span className="text-[10px] text-slate-400">/ 100</span>
                      )}
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      onNavigate('student-profile', { studentId: rep.studentId })
                    }
                    className="min-h-[40px] min-w-[40px] p-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl flex items-center justify-center"
                    title="ملف الطالب"
                  >
                    <Eye className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Daily 6-element breakdown */}
              <div className="grid grid-cols-3 sm:grid-cols-6 gap-2 text-xs">
                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  <span className="text-slate-500 block">الحضور</span>
                  <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                    {formatScore(g.attendanceScore)}{' '}
                    <span className="text-[10px] text-slate-400 font-normal">/ 19</span>
                  </span>
                </div>
                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  <span className="text-slate-500 block">المشاركة</span>
                  <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                    {formatScore(g.participationScore)}{' '}
                    <span className="text-[10px] text-slate-400 font-normal">/ 19</span>
                  </span>
                </div>
                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  <span className="text-slate-500 block">الدفتر</span>
                  <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                    {formatScore(g.notebookScore)}{' '}
                    <span className="text-[10px] text-slate-400 font-normal">/ 19</span>
                  </span>
                </div>
                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  <span className="text-slate-500 block">الواجب</span>
                  <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                    {formatScore(g.homeworkScore)}{' '}
                    <span className="text-[10px] text-slate-400 font-normal">/ 19</span>
                  </span>
                </div>
                <div className="bg-slate-50 p-2.5 rounded-xl border border-slate-200/60">
                  <span className="text-slate-500 block">السلوك</span>
                  <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
                    {formatScore(g.behaviorScore)}{' '}
                    <span className="text-[10px] text-slate-400 font-normal">/ 19</span>
                  </span>
                </div>
                <div className="bg-emerald-50/70 p-2.5 rounded-xl border border-emerald-200/70">
                  <span className="text-emerald-800 block">الاختبار</span>
                  <span className="font-mono-num font-extrabold text-emerald-700 mt-0.5 block">
                    {formatScore(g.quizScore)}{' '}
                    <span className="text-[10px] text-emerald-600 font-normal">/ 5</span>
                  </span>
                </div>
              </div>

              {/* StudyPeriods Breakdown */}
              {visibleBreakdowns.length > 0 && (
                <div className="bg-slate-50 rounded-xl p-3 border border-slate-200/70 space-y-2">
                  {visibleBreakdowns.map((bd) => (
                    <div
                      key={bd.period.id}
                      className="flex flex-wrap items-center justify-between gap-2 text-xs border-b last:border-b-0 border-slate-200/60 pb-2 last:pb-0"
                    >
                      <div className="flex items-center gap-2">
                        <span className="font-extrabold text-slate-900">
                          {bd.period.periodName}:
                        </span>
                        <span className="text-slate-700 font-mono-num">
                          اليومي: <strong>{formatScore(bd.dailyGrade)}</strong> | التحريري:{' '}
                          <strong>
                            {bd.writtenExamGrade !== null
                              ? formatScore(bd.writtenExamGrade)
                              : '--'}
                          </strong>{' '}
                          | المعدل:{' '}
                          <strong className="text-emerald-700">
                            {bd.periodAverage !== null
                              ? formatScore(bd.periodAverage)
                              : '--'}
                          </strong>
                        </span>
                      </div>

                      <button
                        type="button"
                        onClick={() => {
                          setEditingStudent(rep);
                          setEditingBreakdown(bd);
                          setWrittenInput(
                            bd.writtenExamGrade !== null
                              ? formatScore(bd.writtenExamGrade)
                              : ''
                          );
                          setWrittenError(null);
                        }}
                        className="px-2.5 py-1 bg-white hover:bg-teal-50 text-teal-700 border border-teal-300 rounded-lg font-bold flex items-center gap-1"
                      >
                        <Edit3 className="w-3 h-3" />
                        <span>
                          {bd.writtenExamGrade !== null
                            ? 'تعديل التحريري'
                            : 'إدخال التحريري'}
                        </span>
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Written Exam Modal */}
      {editingStudent && editingBreakdown && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-5 space-y-4 border border-slate-200 shadow-xl">
            <h3 className="text-base font-extrabold text-slate-900">
              درجة الامتحان التحريري — {editingBreakdown.period.periodName}
            </h3>
            <p className="text-xs font-bold text-slate-700">
              الطالب: {editingStudent.studentName} · الدرجة اليومية المحسوبة للفترة:{' '}
              <span className="text-teal-700 font-mono-num">
                {formatScore(editingBreakdown.dailyGrade)} / 100
              </span>
            </p>

            {writtenError && (
              <div className="bg-rose-50 border border-rose-300 text-rose-700 rounded-xl p-2.5 text-xs font-bold">
                {writtenError}
              </div>
            )}

            <div>
              <label className="block text-xs font-bold text-slate-700 mb-1">
                درجة الامتحان التحريري (من 0 إلى 100)
              </label>
              <input
                type="number"
                min={0}
                max={100}
                step="0.5"
                value={writtenInput}
                onChange={(e) => {
                  setWrittenInput(e.target.value);
                  setWrittenError(null);
                }}
                placeholder="مثال: 70"
                className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-300 rounded-xl text-sm font-bold font-mono-num"
              />
            </div>

            {writtenInput.trim() !== '' &&
              !Number.isNaN(Number(writtenInput)) &&
              Number(writtenInput) >= 0 &&
              Number(writtenInput) <= 100 && (
                <div className="bg-emerald-50 border border-emerald-300 text-emerald-800 rounded-xl p-2.5 text-xs font-extrabold font-mono-num">
                  معدل {editingBreakdown.period.periodName} بعد الحفظ: (
                  {formatScore(editingBreakdown.dailyGrade)} +{' '}
                  {formatScore(Number(writtenInput))}) ÷ 2 ={' '}
                  {formatScore(
                    round2(
                      (editingBreakdown.dailyGrade + Number(writtenInput)) / 2
                    )
                  )}{' '}
                  / 100
                </div>
              )}

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => {
                  setEditingStudent(null);
                  setEditingBreakdown(null);
                }}
                className="min-h-[38px] px-3.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold"
              >
                إلغاء
              </button>
              <button
                type="button"
                onClick={handleSaveWrittenExam}
                className="min-h-[38px] px-4 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-extrabold"
              >
                حفظ الدرجة
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
