import React, { useEffect, useState } from 'react';
import {
  ArrowRight,
  Calendar,
  ClipboardCheck,
  User,
} from 'lucide-react';
import {
  AppScreen,
  RatingValue,
  StudentCalculatedGrade,
  StudentEntity,
} from '../types';
import {
  GradingService,
  formatDisplayDate,
  formatScore,
  getRatingLabel,
} from '../services/gradingService';

interface StudentProfileViewProps {
  student: StudentEntity | undefined;
  onNavigate: (screen: AppScreen) => void;
}

export const StudentProfileView: React.FC<StudentProfileViewProps> = ({
  student,
  onNavigate,
}) => {
  const [selectedMonth, setSelectedMonth] = useState<string>('ALL');
  const [grade, setGrade] = useState<StudentCalculatedGrade | null>(null);

  useEffect(() => {
    let active = true;
    async function loadProfile() {
      if (!student) return;
      const list = await GradingService.calculateGradesForClass(
        student.classId,
        student.sectionId,
        selectedMonth
      );
      if (!active) return;
      const found = list.find((g) => g.studentId === student.id) || null;
      setGrade(found);
    }
    loadProfile();
    return () => {
      active = false;
    };
  }, [student, selectedMonth]);

  if (!student || !grade) {
    return (
      <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
        <p className="text-sm font-bold text-slate-700">جاري تحميل ملف الطالب...</p>
      </div>
    );
  }

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

  return (
    <div className="space-y-5 pb-10">
      {/* Top Identity & Month Filter */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-center gap-3.5">
          <div className="w-12 h-12 rounded-2xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <User className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs text-slate-500 font-medium">
              الصف: {grade.className} · الشعبة: {grade.sectionName}
            </p>
            <h2 className="text-xl font-extrabold text-slate-900 mt-0.5">
              {grade.studentName}
            </h2>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={selectedMonth}
            onChange={(e) => setSelectedMonth(e.target.value)}
            className="min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold font-mono-num"
          >
            <option value="ALL">جميع الأشهر المسجلة</option>
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

      {/* Prominent Daily Grade Summary Card (100 points) */}
      <section className="bg-slate-900 text-white rounded-2xl p-5 sm:p-6 shadow-md space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-4 border-b border-slate-800">
          <div>
            <p className="text-xs text-emerald-400 font-bold">
              المحصلة اليومية والشهرية التلقائية (مع استثناء الجمعة والسبت والعطل)
            </p>
            <h3 className="text-lg font-extrabold mt-0.5">الدرجة اليومية النهائية</h3>
          </div>
          <div className="font-mono-num flex items-baseline gap-1.5">
            <span className="text-3xl sm:text-4xl font-extrabold text-emerald-400">
              {formatScore(grade.totalScore)}
            </span>
            <span className="text-base text-slate-400 font-bold">/ 100</span>
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
    </div>
  );
};
