import React, { useEffect, useState } from 'react';
import {
  ArchiveRestore,
  CalendarCheck,
  CalendarOff,
  ChevronLeft,
  ClipboardList,
  Database,
  FileBarChart2,
  FileSpreadsheet,
  FolderClock,
  Layers,
  Search,
  Settings,
  Users,
} from 'lucide-react';
import {
  AppScreen,
  ClassEntity,
  HolidayEntity,
  SectionEntity,
  StudentCalculatedGrade,
  StudentEntity,
} from '../types';
import { GradingService, formatScore } from '../services/gradingService';
import { PWAInstallButton } from './PWAInstallButton';

interface DashboardViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  students: StudentEntity[];
  holidays: HolidayEntity[];
  stats: {
    classesCount: number;
    sectionsCount: number;
    studentsCount: number;
    recordsCount: number;
    holidaysCount: number;
  };
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
  onNavigate: (screen: AppScreen, params?: { studentId?: number }) => void;
  onOpenSearch: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  classes,
  sections,
  students,
  holidays,
  stats,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
  onNavigate,
  onOpenSearch,
}) => {
  const currentClass = classes.find((c) => c.id === selectedClassId) || classes[0];
  const classSections = sections.filter((s) => s.classId === currentClass?.id);
  const currentSection =
    classSections.find((s) => s.id === selectedSectionId) || classSections[0];

  const [sectionGrades, setSectionGrades] = useState<StudentCalculatedGrade[]>([]);

  useEffect(() => {
    let active = true;
    async function loadSummary() {
      if (!currentClass || !currentSection) return;
      const list = await GradingService.calculateGradesForClass(
        currentClass.id,
        currentSection.id
      );
      if (active) setSectionGrades(list);
    }
    loadSummary();
    return () => {
      active = false;
    };
  }, [currentClass?.id, currentSection?.id, students, stats.recordsCount]);

  const classAverage =
    sectionGrades.length > 0
      ? sectionGrades.reduce((sum, g) => sum + g.totalScore, 0) /
        sectionGrades.length
      : 0;

  return (
    <div className="space-y-5 pb-10">
      {/* Install on Android Banner (if not installed) */}
      <PWAInstallButton variant="card" />

      {/* Current Class & Section Selector Card */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <p className="text-xs text-slate-500 font-medium">
              قاعدة بيانات SQLite المحلية (Offline First)
            </p>
            <h2 className="text-xl font-extrabold text-slate-900 mt-0.5">
              {currentClass?.className} · شعبة {currentSection?.sectionName}
            </h2>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <select
              value={currentClass?.id || ''}
              onChange={(e) => onSelectClass(Number(e.target.value))}
              className="min-h-[40px] px-3 py-1.5 bg-slate-100 rounded-xl text-xs font-extrabold text-slate-900"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>

            <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-xl">
              {classSections.map((sec) => (
                <button
                  key={sec.id}
                  type="button"
                  onClick={() => onSelectSection(sec.id)}
                  className={`min-h-[32px] px-3 py-1 rounded-lg text-xs font-extrabold transition-all ${
                    sec.id === currentSection?.id
                      ? 'bg-white text-slate-900 shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  شعبة {sec.sectionName}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Metrics Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-4 border-t border-slate-100">
          <div>
            <p className="text-xs text-slate-500">طلاب الشعبة</p>
            <p className="text-2xl font-extrabold text-slate-900 font-mono-num mt-0.5">
              {sectionGrades.length}
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">إجمالي سجلات SQLite</p>
            <p className="text-2xl font-extrabold text-slate-900 font-mono-num mt-0.5">
              {stats.recordsCount}
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">العطل المسجلة</p>
            <p className="text-2xl font-extrabold text-slate-900 font-mono-num mt-0.5">
              {holidays.length}
            </p>
          </div>
          <div>
            <p className="text-xs text-slate-500">متوسط اليومي</p>
            <p className="text-2xl font-extrabold text-emerald-700 font-mono-num mt-0.5">
              {formatScore(classAverage)}{' '}
              <span className="text-xs font-normal text-slate-400">/ 100</span>
            </p>
          </div>
        </div>
      </section>

      {/* Primary Hero Action: Daily Tracking */}
      <section className="bg-slate-900 text-white rounded-2xl p-5 sm:p-6 shadow-md">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <p className="text-xs text-emerald-400 font-semibold">
              تسجيل سريع · الحضور (19) والتقييمات اليومية والاختبار (5)
            </p>
            <h3 className="text-xl font-extrabold">المتابعة اليومية للطلاب</h3>
            <p className="text-xs text-slate-300 leading-relaxed max-w-md">
              رصد الحضور والمشاركة والدفتر والواجب والسلوك والاختبار اليومي مع اعتبار الجمعة والسبت عطلة أسبوعية تلقائياً.
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2.5 shrink-0">
            <button
              type="button"
              onClick={onOpenSearch}
              className="min-h-[48px] px-5 py-3 bg-slate-800 hover:bg-slate-700 text-white font-extrabold text-sm rounded-xl transition-all flex items-center justify-center gap-2 active:scale-[0.98] whitespace-nowrap border border-slate-700"
            >
              <Search className="w-4 h-4 text-emerald-400" />
              <span>بحث عن طالب</span>
            </button>

            <button
              type="button"
              onClick={() => onNavigate('daily-tracking')}
              className="min-h-[48px] px-6 py-3 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-sm rounded-xl transition-all flex items-center justify-center gap-2 active:scale-[0.98] whitespace-nowrap shadow-sm"
            >
              <CalendarCheck className="w-5 h-5" />
              <span>فتح المتابعة اليومية</span>
            </button>
          </div>
        </div>
      </section>

      {/* Comprehensive Sections Grid (All 12 Sections from Prompt) */}
      <section className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <button
          type="button"
          onClick={() => onNavigate('classes-sections')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <Layers className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">الصفوف والشعب</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                إنشاء وتعديل الصفوف والشعب ({stats.classesCount} صفوف · {stats.sectionsCount} شعب)
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('students')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <Users className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">الطلاب وملفاتهم</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                إضافة وتعديل وحذف الطلاب ({stats.studentsCount} طلاب مسجلين)
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('daily-tracking')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
              <ClipboardList className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">المتابعة اليومية</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                تسجيل الحضور والمشاركة والدفتر والواجب والسلوك والاختبار
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('holidays')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center shrink-0 group-hover:bg-amber-600 group-hover:text-white transition-colors">
              <CalendarOff className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">العطل والمناسبات</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                الجمعة والسبت تلقائياً + إضافة العطل الرسمية ({holidays.length} عطل)
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('saved-records')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <FolderClock className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">السجلات المحفوظة</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                استعراض ومراجعة وتعديل سجلات الأيام السابقة المستقلة
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('reports')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <FileBarChart2 className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">التقارير والترتيب</h4>
              <p className="text-xs text-slate-500 mt-0.5">
                كشف الدرجات من 100 مرتباً حسب الأعلى مع الفلترة
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('excel-center')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
              <FileSpreadsheet className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">
                استيراد وتصدير Excel
              </h4>
              <p className="text-xs text-slate-500 mt-0.5">
                استيراد الطلاب من Excel وتصدير النتائج بصيغة .xlsx
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('backup-restore')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <ArchiveRestore className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">
                النسخ الاحتياطي والاستعادة
              </h4>
              <p className="text-xs text-slate-500 mt-0.5">
                إنشاء واستعادة نسخ احتياطية كاملة لقاعدة بيانات SQLite
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('schema-architecture')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <Database className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">
                مخطط قاعدة بيانات SQLite
              </h4>
              <p className="text-xs text-slate-500 mt-0.5">
                الجداول، المفاتيح، العلاقات، والفهارس (Schema & ERD)
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>

        <button
          type="button"
          onClick={() => onNavigate('settings')}
          className="bg-white hover:bg-slate-50/80 rounded-2xl p-4 border border-slate-200/80 text-right transition-all flex items-center justify-between group"
        >
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center shrink-0 group-hover:bg-slate-900 group-hover:text-white transition-colors">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold text-slate-900">
                الإعدادات وأدوات الاختبار والتطوير
              </h4>
              <p className="text-xs text-slate-500 mt-0.5">
                إحصائيات قاعدة البيانات وأدوات الاختبار المحمية (Demo)
              </p>
            </div>
          </div>
          <ChevronLeft className="w-4 h-4 text-slate-400" />
        </button>
      </section>

      {/* Quick Student Preview List */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              طلاب {currentClass?.className} (شعبة {currentSection?.sectionName})
            </h3>
            <p className="text-xs text-slate-500">
              اضغط على أي طالب لفتح ملفه الشخصي وتفاصيل درجته من 100
            </p>
          </div>
        </div>

        <div className="divide-y divide-slate-100">
          {sectionGrades.map((grade, idx) => (
            <button
              key={grade.studentId}
              type="button"
              onClick={() =>
                onNavigate('student-profile', { studentId: grade.studentId })
              }
              className="w-full min-h-[54px] py-2.5 px-2 hover:bg-slate-50 transition-colors flex items-center justify-between text-right rounded-lg"
            >
              <div className="flex items-center gap-3 min-w-0">
                <span className="w-6 text-xs font-mono-num text-slate-400 font-semibold">
                  {idx + 1}.
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-slate-900 truncate">
                    {grade.studentName}
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5 font-mono-num">
                    دوام فعلي {grade.presentDays}/{grade.totalWorkingDays} · اختبار{' '}
                    {formatScore(grade.quizScore)}/5
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <div className="text-left">
                  <span className="text-sm font-extrabold text-slate-900 font-mono-num">
                    {formatScore(grade.totalScore)}
                  </span>
                  <span className="text-xs text-slate-400 font-mono-num"> / 100</span>
                </div>
                <ChevronLeft className="w-4 h-4 text-slate-400" />
              </div>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
};
