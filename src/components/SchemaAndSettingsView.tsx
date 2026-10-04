import React from 'react';
import {
  CheckCircle2,
  Database,
  KeyRound,
  Layers,
  RotateCcw,
  Trash2,
} from 'lucide-react';
import { SCHEMA_VERSION } from '../db/sqliteEngine';
import { PWAInstallButton } from './PWAInstallButton';

interface SchemaAndSettingsViewProps {
  mode: 'schema' | 'settings';
  stats: {
    classesCount: number;
    sectionsCount: number;
    studentsCount: number;
    recordsCount: number;
    holidaysCount: number;
  };
  onResetDemoData: () => Promise<void>;
  onClearAllForProduction: () => Promise<void>;
}

export const SchemaAndSettingsView: React.FC<SchemaAndSettingsViewProps> = ({
  mode,
  stats,
  onResetDemoData,
  onClearAllForProduction,
}) => {
  const [confirmClear, setConfirmClear] = React.useState(false);
  const [banner, setBanner] = React.useState<string | null>(null);

  const tablesSpec = [
    {
      name: 'Classes',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: '—',
      unique: 'UNIQUE(ClassName)',
      indexes: 'PRIMARY KEY Index',
      fields: 'ID, ClassName (TEXT), CreatedAt (TEXT)',
      desc: 'جدول الصفوف الدراسية الأساسية.',
    },
    {
      name: 'Sections',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'ClassID → Classes(ID) ON DELETE CASCADE',
      unique: 'UNIQUE(ClassID, SectionName)',
      indexes: 'idx_sections_class (ClassID)',
      fields: 'ID, ClassID (INTEGER), SectionName (TEXT), CreatedAt (TEXT)',
      desc: 'جدول الشعب المرتبطة بكل صف دراسي.',
    },
    {
      name: 'Students',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'ClassID → Classes(ID), SectionID → Sections(ID)',
      unique: 'UNIQUE(StudentName, ClassID, SectionID)',
      indexes: 'idx_students_class_sec (ClassID, SectionID)',
      fields: 'ID, StudentName (TEXT), ClassID, SectionID, IsActive (INTEGER), CreatedDate (TEXT)',
      desc: 'جدول الطلاب مع قيد منع تكرار نفس الطالب داخل نفس الصف والشعبة.',
    },
    {
      name: 'DailyRecords',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'StudentID → Students(ID), ClassID → Classes(ID), SectionID → Sections(ID)',
      unique: 'UNIQUE(StudentID, RecordDate)',
      indexes: 'idx_dailyrecords_date (RecordDate, ClassID, SectionID)',
      fields: 'ID, StudentID (INTEGER), RecordDate (TEXT), ClassID, SectionID, UpdatedAt (TEXT)',
      desc: 'سجل متابعة مستقل لكل طالب في كل تاريخ، يمنع الكتابة فوق الأيام السابقة.',
    },
    {
      name: 'Attendance',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'StudentID → Students(ID) ON DELETE CASCADE',
      unique: 'UNIQUE(StudentID, RecordDate)',
      indexes: 'idx_attendance_student_date (StudentID, RecordDate)',
      fields: "ID, StudentID (INTEGER), RecordDate (TEXT), Status (TEXT CHECK IN ('present','absent'))",
      desc: 'حفظ حالة حضور أو غياب الطالب لكل تاريخ.',
    },
    {
      name: 'Evaluations',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'StudentID → Students(ID) ON DELETE CASCADE',
      unique: 'UNIQUE(StudentID, RecordDate)',
      indexes: 'idx_evaluations_student_date (StudentID, RecordDate)',
      fields: 'ID, StudentID, RecordDate, Participation (REAL NULL), Notebook (REAL NULL), Homework (REAL NULL), Behavior (REAL NULL)',
      desc: 'تخزين تقييمات العناصر الأربعة (1 = كامل، 0.5 = نصف، 0 = صفر، أو NULL اختياري).',
    },
    {
      name: 'DailyTests',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'StudentID → Students(ID) ON DELETE CASCADE',
      unique: 'UNIQUE(StudentID, RecordDate)',
      indexes: 'idx_dailytests_student_date (StudentID, RecordDate)',
      fields: 'ID, StudentID, RecordDate, Score (REAL CHECK Score >= 0 AND Score <= 5)',
      desc: 'حفظ درجات الاختبار اليومي من 0 إلى 5 مع دعم الكسور العشرية مثل 3.5.',
    },
    {
      name: 'Holidays',
      pk: 'ID (INTEGER PRIMARY KEY AUTOINCREMENT)',
      fk: 'مرتبطة بالتاريخ (HolidayDate) وليس بالطالب',
      unique: 'UNIQUE(HolidayDate)',
      indexes: 'idx_holidays_date (HolidayDate)',
      fields: 'ID, HolidayDate (TEXT), HolidayName (TEXT), HolidayType (TEXT), Notes (TEXT)',
      desc: 'حفظ العطل الرسمية والمناسبات لاستثنائها من حساب أيام الدوام الفعلية.',
    },
  ];

  return (
    <div className="space-y-6 pb-10">
      {/* Header */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            {mode === 'schema'
              ? 'مخطط قاعدة بيانات SQLite والعلاقات ومراحل التنفيذ'
              : 'إعدادات التطبيق وإدارة بيانات التطوير (Demo vs Production)'}
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            إصدار مخطط SQLite الحالي: v{SCHEMA_VERSION} · معمارية الطبقات: UI → Services → Repositories → SQLite Engine
          </p>
        </div>
      </div>

      {banner && (
        <div className="bg-emerald-600 text-white rounded-2xl p-4 text-xs font-extrabold flex items-center gap-2">
          <CheckCircle2 className="w-4 h-4 shrink-0" />
          <span>{banner}</span>
        </div>
      )}

      {/* Install on Android Card */}
      <PWAInstallButton variant="card" />

      {/* Live SQLite Database Engine Status & Demo vs Production Controls */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <Database className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              إحصائيات محرك SQLite المحلي وفصل بيانات Demo
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              يمكنك إعادة تحميل بيانات التطوير التجريبية أو تفريغ قاعدة البيانات للبدء الفعلي النظيف
            </p>
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 text-center font-mono-num">
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/70">
            <span className="text-xs text-slate-500 block">الصفوف</span>
            <span className="text-lg font-extrabold text-slate-900">{stats.classesCount}</span>
          </div>
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/70">
            <span className="text-xs text-slate-500 block">الشعب</span>
            <span className="text-lg font-extrabold text-slate-900">{stats.sectionsCount}</span>
          </div>
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/70">
            <span className="text-xs text-slate-500 block">الطلاب</span>
            <span className="text-lg font-extrabold text-slate-900">{stats.studentsCount}</span>
          </div>
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/70">
            <span className="text-xs text-slate-500 block">سجلات الأيام</span>
            <span className="text-lg font-extrabold text-slate-900">{stats.recordsCount}</span>
          </div>
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-200/70">
            <span className="text-xs text-slate-500 block">العطل المسجلة</span>
            <span className="text-lg font-extrabold text-slate-900">{stats.holidaysCount}</span>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2.5 pt-2">
          <button
            type="button"
            onClick={async () => {
              await onResetDemoData();
              setBanner('تمت إعادة تهيئة البيانات التجريبية (Demo Data) في SQLite بنجاح.');
            }}
            className="min-h-[42px] px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-bold flex items-center gap-1.5"
          >
            <RotateCcw className="w-4 h-4" />
            <span>إعادة ضبط البيانات التجريبية (Demo)</span>
          </button>

          {!confirmClear ? (
            <button
              type="button"
              onClick={() => setConfirmClear(true)}
              className="min-h-[42px] px-4 py-2 bg-rose-50 hover:bg-rose-100 text-rose-700 rounded-xl text-xs font-bold flex items-center gap-1.5"
            >
              <Trash2 className="w-4 h-4" />
              <span>مسح بيانات التجربة والبدء بقاعدة بيانات فارغة للمدرس</span>
            </button>
          ) : (
            <div className="flex items-center gap-2 bg-rose-50 p-2 rounded-xl border border-rose-200">
              <span className="text-xs font-bold text-rose-900 px-2">
                تأكيد تفريغ جميع الجداول؟
              </span>
              <button
                type="button"
                onClick={async () => {
                  await onClearAllForProduction();
                  setConfirmClear(false);
                  setBanner('تم تفريغ بيانات التجربة؛ قاعدة البيانات جاهزة الآن للاستخدام الفعلي.');
                }}
                className="min-h-[34px] px-3 bg-rose-600 text-white rounded-lg text-xs font-bold"
              >
                نعم، افرغ الجداول
              </button>
              <button
                type="button"
                onClick={() => setConfirmClear(false)}
                className="min-h-[34px] px-3 bg-white text-slate-700 border border-slate-200 rounded-lg text-xs font-bold"
              >
                تراجع
              </button>
            </div>
          )}
        </div>
      </section>

      {/* Relational Tree Diagram */}
      <section className="bg-slate-900 text-white rounded-2xl p-5 space-y-3">
        <div className="flex items-center gap-2 text-emerald-400 text-xs font-bold">
          <Layers className="w-4 h-4" />
          <span>شجرة العلاقات المرجعية في SQLite (Entity Relationship Diagram)</span>
        </div>
        <pre
          dir="ltr"
          className="text-xs font-mono-num text-slate-200 overflow-x-auto leading-relaxed bg-slate-950/60 p-4 rounded-xl border border-slate-800"
        >
{`Classes (ID, ClassName)
   │
   └── Sections (ID, ClassID, SectionName)  [UNIQUE(ClassID, SectionName)]
          │
          └── Students (ID, StudentName, ClassID, SectionID)  [UNIQUE(StudentName, ClassID, SectionID)]
                 │
                 ├── DailyRecords (ID, StudentID, RecordDate, ClassID, SectionID) [UNIQUE(StudentID, RecordDate)]
                 ├── Attendance   (ID, StudentID, RecordDate, Status)             [UNIQUE(StudentID, RecordDate)]
                 ├── Evaluations  (ID, StudentID, RecordDate, Participation, Notebook, Homework, Behavior)
                 └── DailyTests   (ID, StudentID, RecordDate, Score 0..5)         [UNIQUE(StudentID, RecordDate)]

Holidays (ID, HolidayDate, HolidayName, HolidayType, Notes) [UNIQUE(HolidayDate)] — مرتبطة بالتاريخ`}
        </pre>
      </section>

      {/* Detailed Schema Specification Cards */}
      <section className="space-y-3">
        <h3 className="text-sm font-extrabold text-slate-800 px-1">
          تفاصيل الجداول والقيود والفهارس (Tables, PK, FK, Unique Constraints & Indexes)
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
          {tablesSpec.map((t) => (
            <div
              key={t.name}
              className="bg-white rounded-2xl p-4 border border-slate-200/80 space-y-2"
            >
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <span className="font-mono-num text-sm font-extrabold text-emerald-800">
                  {t.name}
                </span>
                <KeyRound className="w-4 h-4 text-slate-400" />
              </div>
              <p className="text-xs text-slate-700 font-bold">{t.desc}</p>
              <div className="text-[11px] text-slate-600 space-y-1 font-mono-num">
                <p>
                  <strong className="text-slate-900">PK:</strong> {t.pk}
                </p>
                <p>
                  <strong className="text-slate-900">FK:</strong> {t.fk}
                </p>
                <p>
                  <strong className="text-slate-900">Unique:</strong> {t.unique}
                </p>
                <p>
                  <strong className="text-slate-900">Indexes:</strong> {t.indexes}
                </p>
                <p>
                  <strong className="text-slate-900">Fields:</strong> {t.fields}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
};
