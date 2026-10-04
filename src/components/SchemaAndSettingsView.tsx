import React, { useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Database,
  KeyRound,
  Layers,
  Lock,
  RotateCcw,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  Unlock,
} from 'lucide-react';
import { SCHEMA_VERSION } from '../db/sqliteEngine';
import { BackupService } from '../services/backupService';
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
  const [banner, setBanner] = useState<string | null>(null);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  // Protection state for "أدوات الاختبار والتطوير" (إدارة البيانات التجريبية Demo)
  const [isDevToolsUnlocked, setIsDevToolsUnlocked] = useState(false);
  const [showUnlockWarningModal, setShowUnlockWarningModal] = useState(false);
  const [unlockAcknowledged, setUnlockAcknowledged] = useState(false);

  // Confirmation modal states for Demo Reset and Empty Production Start
  const [showResetDemoModal, setShowResetDemoModal] = useState(false);
  const [demoConfirmText, setDemoConfirmText] = useState('');

  const [showEmptyStartModal, setShowEmptyStartModal] = useState(false);
  const [emptyStartConfirmedCheck, setEmptyStartConfirmedCheck] = useState(false);

  const handleExecuteResetDemo = async () => {
    if (demoConfirmText.trim() !== 'DEMO') return;
    setIsBusy(true);
    setErrorBanner(null);
    setBanner(null);
    try {
      // 1. Mandatory automatic backup before modifying/replacing data
      const backupSnap = await BackupService.createLocalBackup(
        'نسخة أمان تلقائية قبل إعادة بيانات Demo',
        true,
        false
      );

      // 2. Only proceed if backup succeeded
      await onResetDemoData();
      setShowResetDemoModal(false);
      setDemoConfirmText('');
      setBanner(
        `تمت إعادة بيانات Demo بنجاح. تم حفظ نسخة احتياطية تلقائية لبياناتك السابقة في قسم "النسخ الاحتياطي والاستعادة" باسم (${
          backupSnap.fileName || backupSnap.title
        }).`
      );
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error
          ? `تم إلغاء العملية لحماية بياناتك لأن إنشاء النسخة الاحتياطية فشل: ${err.message}`
          : 'تم إلغاء العملية لأن إنشاء النسخة الاحتياطية التلقائية فشل.'
      );
    } finally {
      setIsBusy(false);
    }
  };

  const handleExecuteEmptyStart = async () => {
    if (!emptyStartConfirmedCheck) return;
    setIsBusy(true);
    setErrorBanner(null);
    setBanner(null);
    try {
      // 1. Mandatory automatic backup before clearing data
      const backupSnap = await BackupService.createLocalBackup(
        'نسخة أمان تلقائية قبل البدء الفعلي الفارغ',
        true,
        false
      );

      // 2. Only proceed if backup succeeded
      await onClearAllForProduction();
      setShowEmptyStartModal(false);
      setEmptyStartConfirmedCheck(false);
      setBanner(
        `تمت تهيئة قاعدة البيانات للبدء الفعلي الفارغ بنجاح. تم حفظ نسخة احتياطية تلقائية للبيانات السابقة في قسم "النسخ الاحتياطي والاستعادة" باسم (${
          backupSnap.fileName || backupSnap.title
        }).`
      );
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error
          ? `تم إلغاء العملية لحماية بياناتك لأن إنشاء النسخة الاحتياطية فشل: ${err.message}`
          : 'تم إلغاء العملية لأن إنشاء النسخة الاحتياطية التلقائية فشل.'
      );
    } finally {
      setIsBusy(false);
    }
  };

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
              : 'إعدادات التطبيق وإحصائيات قاعدة البيانات'}
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            إصدار مخطط SQLite الحالي: v{SCHEMA_VERSION} · معمارية الطبقات: UI → Services → Repositories → SQLite Engine
          </p>
        </div>
      </div>

      {banner && (
        <div className="bg-emerald-600 text-white rounded-2xl p-4 text-xs font-extrabold flex items-start gap-2.5 shadow-xs">
          <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
          <span className="leading-relaxed">{banner}</span>
        </div>
      )}

      {errorBanner && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl p-4 text-xs font-extrabold flex items-start gap-2.5">
          <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0 mt-0.5" />
          <span className="leading-relaxed">{errorBanner}</span>
        </div>
      )}

      {/* Install on Android Card */}
      <PWAInstallButton variant="card" />

      {/* Live SQLite Database Engine Status */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <Database className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              إحصائيات محرك SQLite المحلي الحالية
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              البيانات الفعلية المحفوظة حالياً على هذا الجهاز (تعمل بدون إنترنت Offline-first)
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

      {/* Protected Advanced Section: أدوات الاختبار والتطوير (إدارة البيانات التجريبية Demo) */}
      <section className="bg-amber-50/70 rounded-2xl p-4 sm:p-5 border-2 border-amber-300/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <div className="w-11 h-11 rounded-xl bg-amber-100 text-amber-800 border border-amber-300 flex items-center justify-center shrink-0">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-extrabold text-slate-900">
                  أدوات الاختبار والتطوير (إدارة بيانات تجريبية)
                </h3>
                <span className="text-[11px] font-extrabold bg-amber-200 text-amber-950 px-2 py-0.5 rounded-md">
                  قسم متقدم محمي
                </span>
              </div>
              <p className="text-xs text-amber-900 font-bold mt-1">
                هذه الأدوات مخصصة للاختبار فقط، وقد تؤدي إلى تغيير أو حذف البيانات الحالية.
              </p>
            </div>
          </div>

          {!isDevToolsUnlocked ? (
            <button
              type="button"
              onClick={() => {
                setUnlockAcknowledged(false);
                setShowUnlockWarningModal(true);
              }}
              className="min-h-[42px] px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-extrabold flex items-center gap-2 shrink-0 self-start sm:self-auto"
            >
              <Lock className="w-4 h-4 text-amber-400" />
              <span>فك قفل أدوات الاختبار</span>
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setIsDevToolsUnlocked(false)}
              className="min-h-[42px] px-4 py-2 bg-amber-200 hover:bg-amber-300 text-amber-950 rounded-xl text-xs font-extrabold flex items-center gap-2 shrink-0 self-start sm:self-auto"
            >
              <Unlock className="w-4 h-4" />
              <span>إعادة قفل القسم</span>
            </button>
          )}
        </div>

        {isDevToolsUnlocked && (
          <div className="pt-4 border-t border-amber-200/80 space-y-3">
            <div className="p-3 bg-white rounded-xl border border-amber-200 text-xs text-slate-700 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
              <span>
                قبل تنفيذ أي عملية أدناه، يقوم التطبيق تلقائياً بإنشاء نسخة احتياطية كاملة للبيانات الحالية في قسم "النسخ الاحتياطي والاستعادة"، ولن تنفذ العملية إذا فشل إنشاء النسخة الاحتياطية.
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                disabled={isBusy}
                onClick={() => {
                  setDemoConfirmText('');
                  setShowResetDemoModal(true);
                }}
                className="min-h-[44px] px-4 py-2.5 bg-white hover:bg-amber-100 text-amber-950 border border-amber-400 rounded-xl text-xs font-extrabold flex items-center gap-2 transition-colors"
              >
                <RotateCcw className="w-4 h-4 text-amber-700" />
                <span>إعادة بيانات Demo</span>
              </button>

              <button
                type="button"
                disabled={isBusy}
                onClick={() => {
                  setEmptyStartConfirmedCheck(false);
                  setShowEmptyStartModal(true);
                }}
                className="min-h-[44px] px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold flex items-center gap-2 transition-colors"
              >
                <Trash2 className="w-4 h-4" />
                <span>بدء فعلي فارغ</span>
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Step 1 Modal: Unlock Developer & Demo Tools Warning */}
      {showUnlockWarningModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center shrink-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-slate-900">
                  تحذير: فتح أدوات الاختبار والتطوير
                </h3>
                <p className="text-sm text-slate-700 mt-1 leading-relaxed font-bold">
                  هذه الأدوات مخصصة للاختبار فقط، وقد تؤدي إلى تغيير أو حذف البيانات الحالية.
                </p>
              </div>
            </div>

            <label className="flex items-start gap-2.5 p-3 bg-amber-50/70 rounded-xl border border-amber-200 text-xs font-bold text-slate-800 cursor-pointer">
              <input
                type="checkbox"
                checked={unlockAcknowledged}
                onChange={(e) => setUnlockAcknowledged(e.target.checked)}
                className="w-4 h-4 accent-amber-600 rounded mt-0.5"
              />
              <span>
                أدرك أن هذا القسم مخصص للاختبار والتطوير فقط وليس للاستخدام اليومي، وأرغب في إظهار خياراته.
              </span>
            </label>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={!unlockAcknowledged}
                onClick={() => {
                  setIsDevToolsUnlocked(true);
                  setShowUnlockWarningModal(false);
                }}
                className="flex-1 min-h-[44px] bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                تأكيد وفتح القسم
              </button>
              <button
                type="button"
                onClick={() => setShowUnlockWarningModal(false)}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal for "إعادة بيانات Demo" requiring typing DEMO */}
      {showResetDemoModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-amber-50 border border-amber-200 text-amber-600 flex items-center justify-center shrink-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-slate-900">
                  تأكيد إعادة بيانات Demo التجريبية
                </h3>
                <p className="text-xs text-slate-700 mt-1 leading-relaxed font-bold">
                  تحذير: تنفيذ هذه العملية سيؤدي إلى استبدال جميع البيانات الحالية في قاعدة البيانات وإعادة ضبطها إلى بيانات Demo الافتراضية.
                </p>
              </div>
            </div>

            <div className="p-3 bg-emerald-50 rounded-xl border border-emerald-200 text-xs font-bold text-emerald-900">
              سيتم تلقائياً إنشاء نسخة احتياطية كاملة من بياناتك الحالية في قسم "النسخ الاحتياطي والاستعادة" قبل التنفيذ.
            </div>

            <div className="space-y-1.5">
              <label className="block text-xs font-extrabold text-slate-800">
                لتأكيد العملية، اكتب كلمة <span className="font-mono-num text-rose-600">DEMO</span> في الحقل أدناه:
              </label>
              <input
                type="text"
                dir="ltr"
                value={demoConfirmText}
                onChange={(e) => setDemoConfirmText(e.target.value)}
                placeholder="DEMO"
                className="w-full min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-center font-mono-num text-sm font-extrabold tracking-widest uppercase"
              />
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={isBusy || demoConfirmText.trim() !== 'DEMO'}
                onClick={handleExecuteResetDemo}
                className="flex-1 min-h-[44px] bg-amber-600 hover:bg-amber-700 disabled:opacity-40 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                {isBusy ? 'جاري النسخ الاحتياطي والتنفيذ...' : 'تأكيد إعادة بيانات Demo'}
              </button>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => {
                  setShowResetDemoModal(false);
                  setDemoConfirmText('');
                }}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal for "بدء فعلي فارغ" requiring explicit confirmation */}
      {showEmptyStartModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-rose-50 border border-rose-200 text-rose-600 flex items-center justify-center shrink-0">
                <Trash2 className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-slate-900">
                  تأكيد البدء الفعلي الفارغ
                </h3>
                <p className="text-xs text-slate-700 mt-1 leading-relaxed font-bold">
                  تحذير: ستؤدي هذه العملية إلى حذف وإزالة البيانات الحالية أو التجريبية وإعادة تهيئة جداول التطبيق للبدء الفعلي الفارغ.
                </p>
              </div>
            </div>

            <div className="p-3 bg-emerald-50 rounded-xl border border-emerald-200 text-xs font-bold text-emerald-900">
              سيتم تلقائياً إنشاء نسخة احتياطية كاملة من البيانات الحالية في قسم "النسخ الاحتياطي والاستعادة" قبل تنفيذ العملية.
            </div>

            <label className="flex items-start gap-2.5 p-3 bg-rose-50/70 rounded-xl border border-rose-200 text-xs font-bold text-rose-950 cursor-pointer">
              <input
                type="checkbox"
                checked={emptyStartConfirmedCheck}
                onChange={(e) => setEmptyStartConfirmedCheck(e.target.checked)}
                className="w-4 h-4 accent-rose-600 rounded mt-0.5"
              />
              <span>
                أؤكد رغبتي الصريحة في مسح البيانات الحالية والبدء بقاعدة بيانات فارغة (مع حفظ نسخة احتياطية تلقائية قبل المسح).
              </span>
            </label>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={isBusy || !emptyStartConfirmedCheck}
                onClick={handleExecuteEmptyStart}
                className="flex-1 min-h-[44px] bg-rose-600 hover:bg-rose-700 disabled:opacity-40 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                {isBusy ? 'جاري النسخ الاحتياطي والتهيئة...' : 'تأكيد البدء الفعلي الفارغ'}
              </button>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => {
                  setShowEmptyStartModal(false);
                  setEmptyStartConfirmedCheck(false);
                }}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
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
