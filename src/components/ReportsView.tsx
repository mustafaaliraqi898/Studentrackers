import React, { useEffect, useState } from 'react';
import {
  ArrowDownWideNarrow,
  ArrowUpNarrowWide,
  Eye,
  FileDown,
  ListOrdered,
  Trophy,
} from 'lucide-react';
import {
  AppScreen,
  ClassEntity,
  SectionEntity,
  StudentCalculatedGrade,
} from '../types';
import { GradingService, formatScore } from '../services/gradingService';
import { ExcelService } from '../services/excelService';

interface ReportsViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
  onNavigate: (screen: AppScreen, params?: { studentId?: number }) => void;
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
}) => {
  const [filterClassId, setFilterClassId] = useState<number>(selectedClassId);
  const [filterSectionId, setFilterSectionId] = useState<number | 'ALL'>(
    selectedSectionId
  );
  const [filterMonth, setFilterMonth] = useState<string>('2026-10');
  const [sortMode, setSortMode] = useState<SortMode>('score-desc');
  const [grades, setGrades] = useState<StudentCalculatedGrade[]>([]);

  const availableSections = sections.filter((s) => s.classId === filterClassId);

  useEffect(() => {
    let active = true;
    async function loadReport() {
      const secParam = filterSectionId === 'ALL' ? undefined : filterSectionId;
      const list = await GradingService.calculateGradesForClass(
        filterClassId,
        secParam,
        filterMonth
      );
      if (!active) return;
      setGrades(list);
    }
    loadReport();
    return () => {
      active = false;
    };
  }, [filterClassId, filterSectionId, filterMonth]);

  const sortedGrades = [...grades].sort((a, b) => {
    if (sortMode === 'score-desc') return b.totalScore - a.totalScore;
    if (sortMode === 'score-asc') return a.totalScore - b.totalScore;
    return a.studentName.localeCompare(b.studentName, 'ar');
  });

  const handleDirectExcelExport = () => {
    const cls = classes.find((c) => c.id === filterClassId) || classes[0];
    const secLabel =
      filterSectionId === 'ALL'
        ? 'جميع_الشعب'
        : `شعبة_${
            sections.find((s) => s.id === filterSectionId)?.sectionName || ''
          }`;
    const periodLabel = filterMonth === 'ALL' ? 'كامل_الفترة' : filterMonth;
    ExcelService.exportResultsToExcel(
      sortedGrades,
      cls.className,
      secLabel,
      periodLabel
    );
  };

  return (
    <div className="space-y-5 pb-10">
      {/* Header & Filter Controls */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-extrabold text-slate-900">
              تقارير الطلاب وكشف الدرجة النهائية (من 100)
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              فلترة حسب الصف والشعبة والشهر مع استبعاد الجمعة والسبت والعطل الرسمية من أيام الدوام
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

        {/* 3 Filters: Class, Section, Month */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2 border-t border-slate-100">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الصف
            </label>
            <select
              value={filterClassId}
              onChange={(e) => {
                const num = Number(e.target.value);
                setFilterClassId(num);
                setFilterSectionId('ALL');
                onSelectClass(num);
              }}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            >
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
              الشهر / الفترة
            </label>
            <select
              value={filterMonth}
              onChange={(e) => setFilterMonth(e.target.value)}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold font-mono-num"
            >
              <option value="2026-10">تشرين الأول (10 / 2026)</option>
              <option value="2026-11">تشرين الثاني (11 / 2026)</option>
              <option value="ALL">كامل الفترة المسجلة</option>
            </select>
          </div>
        </div>

        {/* Sort Controls */}
        <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-3">
          <span className="text-xs font-bold text-slate-600">
            ترتيب الطلاب حسب الدرجة اليومية:
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
        {sortedGrades.map((g, index) => {
          const isTopThree = sortMode === 'score-desc' && index < 3;

          return (
            <div
              key={g.studentId}
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
                        onNavigate('student-profile', { studentId: g.studentId })
                      }
                      className="text-base font-extrabold text-slate-900 hover:text-emerald-700 transition-colors text-right flex items-center gap-1.5 truncate"
                    >
                      <span className="truncate">{g.studentName}</span>
                      {index === 0 && sortMode === 'score-desc' && (
                        <Trophy className="w-4 h-4 text-amber-500 shrink-0" />
                      )}
                    </button>
                    <p className="text-xs text-slate-500 font-mono-num mt-0.5">
                      الشعبة {g.sectionName} · حضور {g.presentDays}/{g.totalWorkingDays} أيام (
                      {formatScore(g.attendancePercentage)}%)
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2.5 shrink-0">
                  <div className="text-left bg-slate-900 text-white px-3.5 py-2 rounded-xl">
                    <span className="text-[10px] text-slate-300 block">
                      الدرجة النهائية
                    </span>
                    <div className="font-mono-num flex items-baseline gap-1">
                      <span className="text-lg font-extrabold text-emerald-400">
                        {formatScore(g.totalScore)}
                      </span>
                      <span className="text-xs text-slate-400">/ 100</span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      onNavigate('student-profile', { studentId: g.studentId })
                    }
                    className="min-h-[42px] min-w-[42px] p-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl flex items-center justify-center"
                    title="ملف الطالب"
                  >
                    <Eye className="w-4 h-4" />
                  </button>
                </div>
              </div>

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
            </div>
          );
        })}
      </div>
    </div>
  );
};
