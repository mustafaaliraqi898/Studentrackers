import React, { useEffect, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  Calendar,
  ClipboardCheck,
  Edit3,
  User,
  X,
  ZoomIn,
} from 'lucide-react';
import {
  AppScreen,
  DailyPlanEntity,
  DailyPlanImageEntity,
  RatingValue,
  StudentCalculatedGrade,
  StudentComprehensiveGradeReport,
  StudentEntity,
  StudentPeriodGradeBreakdown,
  StudyPeriodEntity,
} from '../types';
import {
  GradingService,
  formatDisplayDate,
  formatScore,
  getArabicDayName,
  getRatingLabel,
  round2,
} from '../services/gradingService';
import { TrackerRepository } from '../repositories/trackerRepository';
import { getPlanImageDataSrc } from './DailyPlanView';

interface StudentProfileViewProps {
  student: StudentEntity | undefined;
  onNavigate: (screen: AppScreen) => void;
  onDataChanged?: () => Promise<void>;
}

export const StudentProfileView: React.FC<StudentProfileViewProps> = ({
  student,
  onNavigate,
  onDataChanged,
}) => {
  const [selectedFilter, setSelectedFilter] = useState<string>('ALL');
  const [grade, setGrade] = useState<StudentCalculatedGrade | null>(null);
  const [comprehensiveReport, setComprehensiveReport] =
    useState<StudentComprehensiveGradeReport | null>(null);
  const [studyPeriods, setStudyPeriods] = useState<StudyPeriodEntity[]>([]);
  const [studentDailyPlans, setStudentDailyPlans] = useState<DailyPlanEntity[]>(
    []
  );
  const [previewPlanImage, setPreviewPlanImage] =
    useState<DailyPlanImageEntity | null>(null);
  const [reloadTick, setReloadTick] = useState<number>(0);

  // Written exam modal state
  const [editingBreakdown, setEditingBreakdown] =
    useState<StudentPeriodGradeBreakdown | null>(null);
  const [writtenInput, setWrittenInput] = useState<string>('');
  const [writtenError, setWrittenError] = useState<string | null>(null);
  const [statusBanner, setStatusBanner] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    async function loadProfile() {
      if (!student) return;
      const [periods, list, compList, plans] = await Promise.all([
        TrackerRepository.getStudyPeriods(),
        GradingService.calculateGradesForClass(
          student.classId,
          student.sectionId,
          selectedFilter
        ),
        GradingService.calculateComprehensiveStudentReports(
          student.classId,
          student.sectionId,
          selectedFilter
        ),
        TrackerRepository.getDailyPlansForClassAndSection(
          student.classId,
          student.sectionId
        ),
      ]);
      if (!active) return;
      setStudyPeriods(periods);
      setGrade(list.find((g) => g.studentId === student.id) || null);
      setComprehensiveReport(
        compList.find((r) => r.studentId === student.id) || null
      );
      setStudentDailyPlans(plans);
    }
    loadProfile();
    return () => {
      active = false;
    };
  }, [student, selectedFilter, reloadTick]);

  if (!student || !grade) {
    return (
      <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
        <p className="text-sm font-bold text-slate-700">جاري تحميل ملف الطالب...</p>
      </div>
    );
  }

  const handleSaveWrittenExam = async () => {
    if (!editingBreakdown) return;
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
      student.id,
      editingBreakdown.period.id,
      editingBreakdown.dailyGrade,
      numericVal
    );
    if (res.error) {
      setWrittenError(res.error);
      return;
    }

    const periodName = editingBreakdown.period.periodName;
    setEditingBreakdown(null);
    setReloadTick((t) => t + 1);
    setStatusBanner(
      numericVal !== null
        ? `تم حفظ درجة الامتحان التحريري وحساب معدل ${periodName} في قاعدة البيانات`
        : `تم مسح درجة الامتحان التحريري لـ ${periodName}`
    );
  };

  const renderRatingsList = (
    title: string,
    score: number,
    average: number | null,
    ratings: { date: string; value: 1 | 0.5 | 0 }[]
  ) => {
    const getValueColor = (val: RatingValue) => {
      if (val === 1) return 'text-emerald-700 font-bold';
      if (val === 0.5) return 'text-amber-600 font-bold';
      return 'text-rose-600 font-bold';
    };

    return (
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">{title}</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              {ratings.length > 0
                ? `متوسط التقييمات (${ratings.length} تقييم): ${formatScore(average || 0)} × 19`
                : 'لا توجد تقييمات مسجلة (لا تحتسب صفراً تلقائياً)'}
            </p>
          </div>
          <div className="text-left font-mono-num">
            <span className="text-lg font-extrabold text-slate-900">
              {formatScore(score)}
            </span>
            <span className="text-xs text-slate-400"> / 19</span>
          </div>
        </div>

        {ratings.length === 0 ? (
          <p className="text-xs text-slate-500 py-1">
            لم يتم رصد تقييمات لهذا العنصر خلال هذه الفترة.
          </p>
        ) : (
          <div className="divide-y divide-slate-100">
            {ratings.map((item, idx) => (
              <div
                key={`${item.date}-${idx}`}
                className="py-2 flex items-center justify-between text-xs"
              >
                <span className="text-slate-600 font-mono-num flex items-center gap-1.5">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  <span>{formatDisplayDate(item.date)}</span>
                </span>
                <div className="flex items-center gap-2">
                  <span className={getValueColor(item.value)}>
                    {getRatingLabel(item.value)}
                  </span>
                  <span className="text-slate-400 font-mono-num">({item.value})</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  const breakdowns = comprehensiveReport?.periodBreakdowns || [];
  const finalAvg = comprehensiveReport?.finalAverage ?? null;
  const completedCount = comprehensiveReport?.completedPeriodsCount ?? 0;

  return (
    <div className="space-y-5 pb-10">
      {statusBanner && (
        <div className="bg-emerald-50 border border-emerald-500 text-emerald-800 rounded-xl px-4 py-2.5 text-xs font-extrabold flex items-center justify-between">
          <span>{statusBanner}</span>
          <button
            type="button"
            onClick={() => setStatusBanner(null)}
            className="text-slate-600 hover:text-slate-900 font-bold"
          >
            إغلاق
          </button>
        </div>
      )}

      {/* Top Identity & Period Filter */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <User className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-medium">
              الصف: {grade.className} · الشعبة: {grade.sectionName}
            </p>
            <h2
              className={`text-xl font-extrabold mt-0.5 ${
                grade.behaviorRatings.filter((r) => r.value === 0).length >= 6
                  ? 'text-rose-600'
                  : 'text-slate-900'
              }`}
            >
              {grade.studentName}
            </h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={selectedFilter}
            onChange={(e) => setSelectedFilter(e.target.value)}
            className="min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold font-mono-num"
          >
            <option value="ALL">جميع السجلات المسجلة</option>
            {studyPeriods.map((sp) => (
              <option key={sp.id} value={`PERIOD:${sp.id}`}>
                {sp.periodName} ({sp.status === 'OPEN' ? 'مفتوحة' : 'مغلقة'})
              </option>
            ))}
            <option value="2026-10">تشرين الأول (10 / 2026)</option>
            <option value="2026-11">تشرين الثاني (11 / 2026)</option>
          </select>

          <button
            type="button"
            onClick={() => onNavigate('students')}
            className="min-h-[40px] px-3.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1.5"
          >
            <ArrowRight className="w-4 h-4" />
            <span>قائمة الطلاب</span>
          </button>
        </div>
      </div>

      {/* Prominent Daily Grade Summary Card (100 points) + Final Average */}
      <section className="bg-slate-900 text-white rounded-2xl p-5 sm:p-6 shadow-md space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-4 border-b border-slate-800">
          <div>
            <p className="text-xs text-emerald-400 font-bold">
              المحصلة اليومية ومعدل الفترات الدراسية (مع استثناء الجمعة والسبت والعطل)
            </p>
            <h3 className="text-lg font-extrabold mt-0.5">
              الدرجة اليومية والمعدل النهائي
            </h3>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <div className="bg-slate-800 px-3.5 py-2 rounded-xl border border-slate-700 font-mono-num">
              <span className="text-[10px] text-slate-300 block">الدرجة اليومية</span>
              <span className="text-2xl font-extrabold text-teal-400">
                {formatScore(grade.totalScore)}
              </span>
              <span className="text-xs text-slate-400"> / 100</span>
            </div>

            <div className="bg-emerald-900/60 px-3.5 py-2 rounded-xl border border-emerald-600/60 font-mono-num">
              <span className="text-[10px] text-emerald-200 block">المعدل النهائي</span>
              <span className="text-2xl font-extrabold text-emerald-400">
                {finalAvg !== null ? `${formatScore(finalAvg)} / 100` : '--'}
              </span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">الحضور</span>
            <span className="text-base font-extrabold text-white font-mono-num mt-1 block">
              {formatScore(grade.attendanceScore)} / 19
            </span>
          </div>
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">المشاركة</span>
            <span className="text-base font-extrabold text-white font-mono-num mt-1 block">
              {formatScore(grade.participationScore)} / 19
            </span>
          </div>
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">الدفتر</span>
            <span className="text-base font-extrabold text-white font-mono-num mt-1 block">
              {formatScore(grade.notebookScore)} / 19
            </span>
          </div>
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">الواجب</span>
            <span className="text-base font-extrabold text-white font-mono-num mt-1 block">
              {formatScore(grade.homeworkScore)} / 19
            </span>
          </div>
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">السلوك</span>
            <span className="text-base font-extrabold text-white font-mono-num mt-1 block">
              {formatScore(grade.behaviorScore)} / 19
            </span>
          </div>
          <div className="bg-slate-800/90 p-3 rounded-xl border border-slate-700/70">
            <span className="text-xs text-slate-300 block">الاختبار</span>
            <span className="text-base font-extrabold text-emerald-400 font-mono-num mt-1 block">
              {formatScore(grade.quizScore)} / 5
            </span>
          </div>
        </div>
      </section>

      {/* Study Periods Grades & Written Exam & Period Average Card */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              درجات الفترات الدراسية والامتحان التحريري والمعدل
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              معدل الفترة = (الدرجة اليومية للفترة + درجة الامتحان التحريري) ÷ 2
            </p>
          </div>
        </div>

        {breakdowns.length === 0 ? (
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 text-xs text-slate-600 font-bold">
            لم يتم إنشاء فترات دراسية بعد. يمكنك إضافة فترة دراسية من قسم «الفترات الدراسية» لحساب الدرجة اليومية لكل فترة وإدخال درجة الامتحان التحريري وحساب المعدل النهائي.
          </div>
        ) : (
          <div className="space-y-3">
            {breakdowns.map((bd) => {
              const p = bd.period;
              const rangeStr = `${formatDisplayDate(p.startDate)} → ${
                p.endDate ? formatDisplayDate(p.endDate) : 'مفتوحة'
              }`;
              return (
                <div
                  key={p.id}
                  className="bg-slate-50 border border-slate-200/80 rounded-xl p-3.5 space-y-3"
                >
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-extrabold text-slate-900">
                          {p.periodName}
                        </span>
                        <span
                          className={`px-2 py-0.5 rounded-md text-[10px] font-extrabold ${
                            p.status === 'OPEN'
                              ? 'bg-emerald-100 text-emerald-700'
                              : 'bg-slate-200 text-slate-700'
                          }`}
                        >
                          {p.status === 'OPEN' ? 'مفتوحة' : 'مغلقة'}
                        </span>
                      </div>
                      <p className="text-xs text-slate-500 font-mono-num mt-0.5">
                        {rangeStr}
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() => {
                        setEditingBreakdown(bd);
                        setWrittenInput(
                          bd.writtenExamGrade !== null
                            ? formatScore(bd.writtenExamGrade)
                            : ''
                        );
                        setWrittenError(null);
                      }}
                      className="min-h-[36px] px-3 py-1.5 bg-teal-700 hover:bg-teal-800 text-white rounded-lg text-xs font-extrabold flex items-center gap-1.5"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>
                        {bd.writtenExamGrade !== null
                          ? 'تعديل التحريري'
                          : 'إدخال التحريري'}
                      </span>
                    </button>
                  </div>

                  <div className="grid grid-cols-3 gap-2.5 text-center">
                    <div className="bg-white p-2.5 rounded-lg border border-slate-200">
                      <span className="text-[11px] text-slate-500 font-bold block">
                        الدرجة اليومية
                      </span>
                      <span className="text-sm font-extrabold text-slate-900 font-mono-num mt-0.5 block">
                        {formatScore(bd.dailyGrade)} / 100
                      </span>
                    </div>

                    <div className="bg-white p-2.5 rounded-lg border border-slate-200">
                      <span className="text-[11px] text-slate-500 font-bold block">
                        الامتحان التحريري
                      </span>
                      <span className="text-sm font-extrabold text-teal-700 font-mono-num mt-0.5 block">
                        {bd.writtenExamGrade !== null
                          ? `${formatScore(bd.writtenExamGrade)} / 100`
                          : '--'}
                      </span>
                    </div>

                    <div
                      className={`p-2.5 rounded-lg border ${
                        bd.periodAverage !== null
                          ? 'bg-emerald-50 border-emerald-300'
                          : 'bg-white border-slate-200'
                      }`}
                    >
                      <span className="text-[11px] text-slate-600 font-bold block">
                        معدل الفترة
                      </span>
                      <span className="text-sm font-extrabold text-emerald-700 font-mono-num mt-0.5 block">
                        {bd.periodAverage !== null
                          ? `${formatScore(bd.periodAverage)} / 100`
                          : '--'}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}

            <div className="bg-slate-900 text-white rounded-xl p-3.5 flex items-center justify-between">
              <div>
                <span className="text-sm font-extrabold block">
                  المعدل النهائي لجميع الفترات المكتملة
                </span>
                <span className="text-xs text-slate-300">
                  {completedCount > 0
                    ? `محسوب من ${completedCount} فترة مكتملة (مجموع معدلات الفترات ÷ ${completedCount})`
                    : 'أدخل درجة الامتحان التحريري لفترة واحدة على الأقل لحساب المعدل النهائي'}
                </span>
              </div>
              <span className="text-lg font-extrabold text-emerald-400 font-mono-num">
                {finalAvg !== null ? `${formatScore(finalAvg)} / 100` : '--'}
              </span>
            </div>
          </div>
        )}
      </section>

      {/* Attendance History Card */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              سجل الحضور والغياب الفعلي
            </h3>
            <p className="text-xs text-slate-500 mt-0.5 font-mono-num">
              {grade.presentDays}÷{grade.totalWorkingDays} ={' '}
              {formatScore(grade.attendancePercentage)}% × 19 ={' '}
              {formatScore(grade.attendanceScore)}
            </p>
          </div>
          <div className="font-mono-num text-left">
            <span className="text-lg font-extrabold text-slate-900">
              {formatScore(grade.attendanceScore)}
            </span>
            <span className="text-xs text-slate-400"> / 19</span>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/60">
            <span className="text-xs text-slate-500 block">أيام الدوام الفعلية</span>
            <span className="text-xl font-extrabold text-slate-900 font-mono-num mt-0.5 block">
              {grade.totalWorkingDays}
            </span>
          </div>
          <div className="bg-emerald-50/70 p-3 rounded-xl border border-emerald-200/60">
            <span className="text-xs text-emerald-800 block">أيام الحضور</span>
            <span className="text-xl font-extrabold text-emerald-700 font-mono-num mt-0.5 block">
              {grade.presentDays}
            </span>
          </div>
          <div className="bg-rose-50/70 p-3 rounded-xl border border-rose-200/60">
            <span className="text-xs text-rose-800 block">أيام الغياب</span>
            <span className="text-xl font-extrabold text-rose-700 font-mono-num mt-0.5 block">
              {grade.absentDays}
            </span>
          </div>
        </div>

        {grade.attendanceHistory.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 pt-2">
            {grade.attendanceHistory.map((a) => (
              <div
                key={a.date}
                className="text-xs font-mono-num px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-200 flex items-center gap-2"
              >
                <span>{formatDisplayDate(a.date)}</span>
                <span
                  className={
                    a.status === 'present'
                      ? 'text-emerald-700 font-extrabold'
                      : 'text-rose-600 font-extrabold'
                  }
                >
                  {a.status === 'present' ? 'حاضر (19/19)' : 'غائب (0/19)'}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* 4 Evaluation Categories */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {renderRatingsList(
          'المشاركة اليومية',
          grade.participationScore,
          grade.participationAverage,
          grade.participationRatings
        )}
        {renderRatingsList(
          'متابعة الدفتر',
          grade.notebookScore,
          grade.notebookAverage,
          grade.notebookRatings
        )}
        {renderRatingsList(
          'الواجب البيتي',
          grade.homeworkScore,
          grade.homeworkAverage,
          grade.homeworkRatings
        )}
        {renderRatingsList(
          'السلوك والانضباط',
          grade.behaviorScore,
          grade.behaviorAverage,
          grade.behaviorRatings
        )}
      </div>

      {/* Daily Tests History */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2.5">
            <ClipboardCheck className="w-5 h-5 text-emerald-700" />
            <div>
              <h3 className="text-base font-extrabold text-slate-900">
                سجل الاختبارات اليومية (من 5)
              </h3>
              <p className="text-xs text-slate-500">
                جميع درجات الاختبار المرصودة للطالب في جدول DailyTests
              </p>
            </div>
          </div>
          <div className="font-mono-num text-left">
            <span className="text-lg font-extrabold text-emerald-700">
              {formatScore(grade.quizScore)}
            </span>
            <span className="text-xs text-slate-400"> / 5</span>
          </div>
        </div>

        {grade.dailyTests.length === 0 ? (
          <p className="text-xs text-slate-500">لم يتم تسجيل اختبارات يومية بعد.</p>
        ) : (
          <div className="divide-y divide-slate-100">
            {grade.dailyTests.map((t, i) => (
              <div
                key={`${t.date}-${i}`}
                className="py-2 flex items-center justify-between text-xs font-mono-num"
              >
                <span className="text-slate-600">بتاريخ {formatDisplayDate(t.date)}</span>
                <span className="font-extrabold text-slate-900">
                  {formatScore(t.score)} / 5
                </span>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Daily Plans Linked to Student's Class & Section */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2.5">
            <BookOpen className="w-5 h-5 text-teal-700" />
            <div>
              <h3 className="text-base font-extrabold text-slate-900">
                الخطط اليومية المرتبطة بالصف والشعبة ({grade.className} — شعبة{' '}
                {grade.sectionName})
              </h3>
              <p className="text-xs text-slate-500">
                جميع الدروس والخطط اليومية والصور المسجلة لصف وشعبة الطالب
              </p>
            </div>
          </div>
          <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-teal-50 text-teal-800 border border-teal-200 text-xs font-extrabold font-mono-num">
            {studentDailyPlans.length} خطة
          </span>
        </div>

        {studentDailyPlans.length === 0 ? (
          <p className="text-xs text-slate-500">
            لا توجد خطط يومية مسجلة لصف وشعبة هذا الطالب حالياً.
          </p>
        ) : (
          <div className="space-y-2.5">
            {studentDailyPlans.map((plan) => (
              <div
                key={plan.id}
                className="rounded-xl p-3 bg-slate-50 border border-slate-200/80 space-y-2"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-xs font-extrabold text-teal-800 font-mono-num">
                    {getArabicDayName(plan.planDate)} —{' '}
                    {formatDisplayDate(plan.planDate)}
                  </span>
                  <span className="text-[11px] font-bold text-slate-500">
                    الشعب المشمولة: {plan.sectionNames.join('، ')}
                  </span>
                </div>
                <p className="text-xs sm:text-sm font-bold text-slate-900 whitespace-pre-wrap">
                  {plan.lessonTopic}
                </p>
                {plan.images.length > 0 && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {plan.images.map((img) => (
                      <button
                        key={img.id || img.fileName}
                        type="button"
                        onClick={() => setPreviewPlanImage(img)}
                        className="relative w-16 h-16 rounded-lg border border-slate-200 overflow-hidden bg-white group"
                      >
                        <img
                          src={getPlanImageDataSrc(img.imageBase64)}
                          alt="صورة الدرس"
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                        />
                        <span className="absolute bottom-1 left-1 w-4 h-4 rounded-full bg-slate-900/75 text-white flex items-center justify-center">
                          <ZoomIn className="w-2.5 h-2.5" />
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Fullscreen Plan Image Modal */}
      {previewPlanImage && (
        <div
          className="fixed inset-0 z-50 bg-slate-950/90 flex flex-col justify-between p-4"
          onClick={() => setPreviewPlanImage(null)}
        >
          <div
            className="flex items-center justify-between max-w-5xl w-full mx-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <h4 className="text-sm font-extrabold text-white">
              معاينة صورة الدرس بالحجم الكامل
            </h4>
            <button
              type="button"
              onClick={() => setPreviewPlanImage(null)}
              className="px-3.5 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-extrabold flex items-center gap-1.5"
            >
              <X className="w-4 h-4" />
              إغلاق
            </button>
          </div>
          <div
            className="flex-1 flex items-center justify-center overflow-auto my-4"
            onClick={(e) => e.stopPropagation()}
          >
            <img
              src={getPlanImageDataSrc(previewPlanImage.imageBase64)}
              alt="صورة الدرس"
              className="max-w-full max-h-[82vh] object-contain rounded-xl shadow-2xl"
            />
          </div>
        </div>
      )}

      {/* Written Exam Modal */}
      {editingBreakdown && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-5 space-y-4 border border-slate-200 shadow-xl">
            <h3 className="text-base font-extrabold text-slate-900">
              درجة الامتحان التحريري — {editingBreakdown.period.periodName}
            </h3>
            <p className="text-xs font-bold text-slate-700">
              الطالب: {grade.studentName} · الدرجة اليومية المحسوبة للفترة:{' '}
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
                onClick={() => setEditingBreakdown(null)}
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
