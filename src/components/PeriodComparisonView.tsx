import React, { useEffect, useMemo, useState } from 'react';
import {
  ClassEntity,
  SectionEntity,
  StudentComprehensiveGradeReport,
  StudyPeriodEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';
import { GradingService, formatScore } from '../services/gradingService';

interface PeriodComparisonViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
}

interface StudentComparisonRow {
  sequence: number;
  studentId: number;
  studentName: string;
  firstScore: number | null;
  secondScore: number | null;
  difference: number | null;
  status: 'improved' | 'declined' | 'unchanged' | 'incomplete';
}

export const PeriodComparisonView: React.FC<PeriodComparisonViewProps> = ({
  classes,
  sections,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
}) => {
  const currentClass =
    classes.find((c) => c.id === selectedClassId) || classes[0];
  const classSections = useMemo(
    () => sections.filter((s) => s.classId === currentClass?.id),
    [sections, currentClass?.id]
  );
  const currentSection =
    classSections.find((s) => s.id === selectedSectionId) || classSections[0];

  useEffect(() => {
    if (
      classSections.length > 0 &&
      !classSections.some((s) => s.id === selectedSectionId)
    ) {
      onSelectSection(classSections[0].id);
    }
  }, [classSections, selectedSectionId, onSelectSection]);

  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodEntity[]>([]);
  const [firstPeriodId, setFirstPeriodId] = useState<number>(0);
  const [secondPeriodId, setSecondPeriodId] = useState<number>(0);
  const [reports, setReports] = useState<StudentComprehensiveGradeReport[]>([]);

  useEffect(() => {
    let active = true;
    async function loadPeriods() {
      const periods = await TrackerRepository.getStudyPeriods();
      if (!active) return;
      setStudyPeriods(periods);
      if (periods.length > 0) {
        setFirstPeriodId((prev) =>
          periods.some((p) => p.id === prev) ? prev : periods[0].id
        );
        setSecondPeriodId((prev) =>
          periods.some((p) => p.id === prev)
            ? prev
            : (periods[1] ?? periods[0]).id
        );
      }
    }
    loadPeriods();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    async function loadReports() {
      if (!currentClass || !currentSection) {
        if (active) setReports([]);
        return;
      }
      const list = await GradingService.calculateComprehensiveStudentReports(
        currentClass.id,
        currentSection.id,
        'ALL'
      );
      if (active) setReports(list);
    }
    loadReports();
    return () => {
      active = false;
    };
  }, [currentClass?.id, currentSection?.id]);

  const firstPeriod =
    studyPeriods.find((p) => p.id === firstPeriodId) || studyPeriods[0];
  const secondPeriod =
    studyPeriods.find((p) => p.id === secondPeriodId) ||
    studyPeriods[1] ||
    studyPeriods[0];

  const isSamePeriodSelected =
    Boolean(firstPeriod && secondPeriod) && firstPeriod.id === secondPeriod.id;

  const comparisonRows = useMemo<StudentComparisonRow[]>(() => {
    if (!firstPeriod || !secondPeriod || isSamePeriodSelected) return [];
    return reports.map((rep, idx) => {
      const b1 = rep.periodBreakdowns.find(
        (b) => b.period.id === firstPeriod.id
      );
      const b2 = rep.periodBreakdowns.find(
        (b) => b.period.id === secondPeriod.id
      );

      const s1 = b1?.periodAverage ?? null;
      const s2 = b2?.periodAverage ?? null;

      const diff =
        s1 !== null && s2 !== null
          ? Math.round((s2 - s1) * 100) / 100
          : null;

      let status: StudentComparisonRow['status'] = 'incomplete';
      if (diff !== null) {
        if (diff > 0.0001) status = 'improved';
        else if (diff < -0.0001) status = 'declined';
        else status = 'unchanged';
      }

      return {
        sequence: idx + 1,
        studentId: rep.studentId,
        studentName: rep.studentName,
        firstScore: s1,
        secondScore: s2,
        difference: diff,
        status,
      };
    });
  }, [reports, firstPeriod, secondPeriod, isSamePeriodSelected]);

  const improvedCount = useMemo(
    () => comparisonRows.filter((r) => r.status === 'improved').length,
    [comparisonRows]
  );
  const declinedCount = useMemo(
    () => comparisonRows.filter((r) => r.status === 'declined').length,
    [comparisonRows]
  );
  const unchangedCount = useMemo(
    () => comparisonRows.filter((r) => r.status === 'unchanged').length,
    [comparisonRows]
  );

  const formatDiff = (diff: number | null): string => {
    if (diff === null) return '—';
    const absVal = formatScore(Math.abs(diff));
    if (diff > 0.0001) return `+${absVal}`;
    if (diff < -0.0001) return `-${absVal}`;
    return '0';
  };

  return (
    <div className="space-y-4 pb-10">
      {/* 1. Filters Card (الصف، الشعبة، الفترة الأولى، الفترة الثانية) */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs space-y-3.5">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            مقارنة نتائج الفترات
          </h2>
          <p className="text-xs font-bold text-slate-500 mt-0.5">
            {currentClass?.className || '—'} · شعبة{' '}
            {currentSection?.sectionName || '—'} · عدد الطلاب: {reports.length}
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 pt-1">
          {/* 1. الصف */}
          <div>
            <label className="block text-xs font-extrabold text-slate-700 mb-1">
              الصف
            </label>
            <select
              value={currentClass?.id || ''}
              onChange={(e) => onSelectClass(Number(e.target.value))}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          {/* 2. الشعبة */}
          <div>
            <label className="block text-xs font-extrabold text-slate-700 mb-1">
              الشعبة
            </label>
            <select
              value={currentSection?.id || ''}
              onChange={(e) => onSelectSection(Number(e.target.value))}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {classSections.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  شعبة {sec.sectionName}
                </option>
              ))}
            </select>
          </div>

          {/* 3. الفترة الأولى */}
          <div>
            <label className="block text-xs font-extrabold text-slate-700 mb-1">
              الفترة الأولى
            </label>
            <select
              value={firstPeriod?.id || ''}
              onChange={(e) => setFirstPeriodId(Number(e.target.value))}
              disabled={studyPeriods.length === 0}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {studyPeriods.length === 0 ? (
                <option value="">لا توجد فترات دراسية</option>
              ) : (
                studyPeriods.map((sp) => (
                  <option key={sp.id} value={sp.id}>
                    {sp.periodName}
                  </option>
                ))
              )}
            </select>
          </div>

          {/* 4. الفترة الثانية */}
          <div>
            <label className="block text-xs font-extrabold text-slate-700 mb-1">
              الفترة الثانية
            </label>
            <select
              value={secondPeriod?.id || ''}
              onChange={(e) => setSecondPeriodId(Number(e.target.value))}
              disabled={studyPeriods.length === 0}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {studyPeriods.length === 0 ? (
                <option value="">لا توجد فترات دراسية</option>
              ) : (
                studyPeriods.map((sp) => (
                  <option key={sp.id} value={sp.id}>
                    {sp.periodName}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>
      </section>

      {studyPeriods.length === 0 ? (
        <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80 text-xs font-bold text-slate-500">
          لا توجد فترات دراسية مضافة حالياً للمقارنة
        </div>
      ) : isSamePeriodSelected ? (
        <div className="bg-amber-50 rounded-2xl p-6 text-center border border-amber-300 text-xs font-extrabold text-amber-800">
          يرجى اختيار فترتين مختلفتين للمقارنة
        </div>
      ) : (
        <>
          {/* 2. Summary Strip (3 cards on a single row: Improved, Declined, Unchanged) */}
          <div className="grid grid-cols-3 gap-2.5 sm:gap-3">
            <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-3 text-center">
              <p className="text-lg sm:text-xl font-extrabold text-emerald-700 font-mono-num">
                {improvedCount}
              </p>
              <p className="text-xs font-extrabold text-emerald-700 mt-0.5">
                تحسن (↑)
              </p>
            </div>

            <div className="bg-rose-50 border border-rose-200 rounded-xl p-3 text-center">
              <p className="text-lg sm:text-xl font-extrabold text-rose-700 font-mono-num">
                {declinedCount}
              </p>
              <p className="text-xs font-extrabold text-rose-700 mt-0.5">
                تراجع (↓)
              </p>
            </div>

            <div className="bg-slate-100 border border-slate-200 rounded-xl p-3 text-center">
              <p className="text-lg sm:text-xl font-extrabold text-slate-700 font-mono-num">
                {unchangedCount}
              </p>
              <p className="text-xs font-extrabold text-slate-700 mt-0.5">
                ثابت (=)
              </p>
            </div>
          </div>

          {/* 3. Students Comparison Table */}
          <section className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
            {comparisonRows.length === 0 ? (
              <div className="p-8 text-center text-xs font-bold text-slate-500">
                لا يوجد طلاب مسجلين في هذا الصف والشعبة
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-right border-collapse">
                  <thead>
                    <tr className="bg-slate-900 text-white text-xs font-extrabold">
                      <th className="py-3 px-2.5 text-center w-10">ت</th>
                      <th className="py-3 px-3 text-right">اسم الطالب</th>
                      <th className="py-3 px-2.5 text-center">
                        {firstPeriod?.periodName || 'درجة الفترة الأولى'}
                      </th>
                      <th className="py-3 px-2.5 text-center">
                        {secondPeriod?.periodName || 'درجة الفترة الثانية'}
                      </th>
                      <th className="py-3 px-2.5 text-center">الفرق</th>
                      <th className="py-3 px-2.5 text-center">الحالة</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-xs">
                    {comparisonRows.map((row, index) => (
                      <tr
                        key={row.studentId}
                        className={
                          index % 2 === 0 ? 'bg-white' : 'bg-slate-50/70'
                        }
                      >
                        <td className="py-3 px-2.5 text-center font-bold text-slate-500 font-mono-num">
                          {row.sequence}
                        </td>
                        <td className="py-3 px-3 font-extrabold text-slate-900">
                          {row.studentName}
                        </td>
                        <td className="py-3 px-2.5 text-center font-bold text-slate-800 font-mono-num">
                          {row.firstScore !== null
                            ? formatScore(row.firstScore)
                            : '—'}
                        </td>
                        <td className="py-3 px-2.5 text-center font-bold text-slate-800 font-mono-num">
                          {row.secondScore !== null
                            ? formatScore(row.secondScore)
                            : '—'}
                        </td>
                        <td
                          className={`py-3 px-2.5 text-center font-extrabold font-mono-num ${
                            row.status === 'improved'
                              ? 'text-emerald-700'
                              : row.status === 'declined'
                              ? 'text-rose-700'
                              : 'text-slate-600'
                          }`}
                        >
                          {formatDiff(row.difference)}
                        </td>
                        <td className="py-3 px-2.5 text-center">
                          {row.status === 'improved' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700 font-extrabold text-[11px]">
                              ↑ تحسن
                            </span>
                          ) : row.status === 'declined' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-rose-50 border border-rose-200 text-rose-700 font-extrabold text-[11px]">
                              ↓ تراجع
                            </span>
                          ) : row.status === 'unchanged' ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-700 font-extrabold text-[11px]">
                              = ثابت
                            </span>
                          ) : (
                            <span className="text-slate-400 font-bold">—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
};
