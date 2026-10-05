import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  ArchiveRestore,
  CheckCircle2,
  Clock,
  Database,
  Download,
  FileCode2,
  FolderCheck,
  FolderOpen,
  HardDriveDownload,
  Plus,
  ShieldCheck,
  Trash2,
  Upload,
} from 'lucide-react';
import { BackupSnapshotMetadata } from '../types';
import {
  BackupDiagnosticLog,
  BackupService,
  getLocalTodayDateString,
} from '../services/backupService';

interface BackupRestoreViewProps {
  onDatabaseRestored: () => Promise<void>;
}

export const BackupRestoreView: React.FC<BackupRestoreViewProps> = ({
  onDatabaseRestored,
}) => {
  const [snapshots, setSnapshots] = useState<BackupSnapshotMetadata[]>([]);
  const [diagnostics, setDiagnostics] = useState<BackupDiagnosticLog[]>([]);
  const [customTitle, setCustomTitle] = useState('');
  const [statusBanner, setStatusBanner] = useState<string | null>(null);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [backupFolderPath, setBackupFolderPath] = useState<string | null>(() =>
    BackupService.getSavedBackupFolderPath()
  );
  const [showFolderPickerModal, setShowFolderPickerModal] = useState(false);
  const [folderInputPath, setFolderInputPath] = useState(
    BackupService.getSavedBackupFolderPath() || 'Download/سجل متابعة الطلاب/النسخ الاحتياطية'
  );
  const [createBackupAfterFolderConfirm, setCreateBackupAfterFolderConfirm] = useState(false);

  // Confirmation modal state for restoring a snapshot or uploaded file
  const [pendingRestoreSnapshot, setPendingRestoreSnapshot] =
    useState<BackupSnapshotMetadata | null>(null);
  const [pendingRestoreFile, setPendingRestoreFile] = useState<File | null>(null);
  const [pendingDeleteSnapshot, setPendingDeleteSnapshot] =
    useState<BackupSnapshotMetadata | null>(null);

  const loadSnapshots = async () => {
    const list = await BackupService.listLocalBackups();
    setSnapshots(list);
    setDiagnostics(BackupService.getDiagnosticLogs());
  };

  useEffect(() => {
    loadSnapshots();
  }, []);

  const executeCreateBackup = async (targetFolder?: string) => {
    setIsBusy(true);
    setErrorBanner(null);
    setStatusBanner(null);
    try {
      const snap = await BackupService.createLocalBackup(
        customTitle.trim() || undefined,
        false,
        false
      );
      setCustomTitle('');
      await loadSnapshots();
      const folderUsed = targetFolder || backupFolderPath;
      setStatusBanner(
        `تم إنشاء النسخة الاحتياطية "${snap.fileName || snap.title}" بنجاح في المجلد (${folderUsed}). (يتم الاحتفاظ بآخر 7 نسخ محلية تلقائياً)`
      );
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error ? err.message : 'تعذر إنشاء النسخة الاحتياطية.'
      );
      setDiagnostics(BackupService.getDiagnosticLogs());
    } finally {
      setIsBusy(false);
    }
  };

  const handleCreateBackupNow = async () => {
    const currentFolder = BackupService.getSavedBackupFolderPath();
    if (!currentFolder) {
      setCreateBackupAfterFolderConfirm(true);
      setShowFolderPickerModal(true);
      return;
    }
    await executeCreateBackup(currentFolder);
  };

  const handleSaveFolderSelection = async () => {
    const cleaned = folderInputPath.trim() || 'Download/سجل متابعة الطلاب/النسخ الاحتياطية';
    BackupService.setSavedBackupFolderPath(cleaned);
    setBackupFolderPath(cleaned);
    setShowFolderPickerModal(false);
    if (createBackupAfterFolderConfirm) {
      setCreateBackupAfterFolderConfirm(false);
      await executeCreateBackup(cleaned);
    } else {
      setStatusBanner(`تم حفظ مجلد النسخ الاحتياطي: ${cleaned}`);
    }
  };

  const confirmAndExecuteDelete = async () => {
    if (!pendingDeleteSnapshot) return;
    setIsBusy(true);
    setErrorBanner(null);
    setStatusBanner(null);
    try {
      await BackupService.deleteLocalBackup(pendingDeleteSnapshot.id);
      await loadSnapshots();
      setStatusBanner(
        `تم حذف النسخة الاحتياطية "${pendingDeleteSnapshot.fileName || pendingDeleteSnapshot.title}" بنجاح.`
      );
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error ? err.message : 'تعذر حذف النسخة الاحتياطية.'
      );
    } finally {
      setIsBusy(false);
      setPendingDeleteSnapshot(null);
    }
  };

  const confirmAndExecuteRestore = async () => {
    setIsBusy(true);
    setErrorBanner(null);
    setStatusBanner(null);
    try {
      if (pendingRestoreSnapshot) {
        const safetySnap = await BackupService.restoreFromSnapshot(
          pendingRestoreSnapshot
        );
        await onDatabaseRestored();
        await loadSnapshots();
        setStatusBanner(
          `تمت استعادة النسخة الاحتياطية "${
            pendingRestoreSnapshot.fileName || pendingRestoreSnapshot.title
          }" بنجاح. تم حفظ نسخة احتياطية تلقائية لبياناتك السابقة باسم "${
            safetySnap.fileName || safetySnap.title
          }".`
        );
      } else if (pendingRestoreFile) {
        const res = await BackupService.restoreFromUploadedFile(pendingRestoreFile);
        await onDatabaseRestored();
        await loadSnapshots();
        setStatusBanner(res.message);
      }
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error ? err.message : 'فشلت عملية استعادة النسخة الاحتياطية.'
      );
      setDiagnostics(BackupService.getDiagnosticLogs());
    } finally {
      setIsBusy(false);
      setPendingRestoreSnapshot(null);
      setPendingRestoreFile(null);
    }
  };

  const latestBackup = snapshots[0];
  const todayStr = getLocalTodayDateString(new Date());
  const hasTodayBackup = snapshots.some(
    (s) => getLocalTodayDateString(new Date(s.createdAt)) === todayStr
  );

  return (
    <div className="space-y-6 pb-10">
      {/* Header & Last Backup Summary */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-extrabold text-slate-900">
              النسخ الاحتياطي والاستعادة
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              نسخ احتياطي تلقائي يومي لقاعدة بيانات SQLite (الصفوف، الشعب، الطلاب، الحضور، التقييمات، الاختبارات، والعطل) مع الاحتفاظ بآخر 7 نسخ محلية تلقائياً
            </p>
          </div>

          <button
            type="button"
            disabled={isBusy}
            onClick={handleCreateBackupNow}
            className="min-h-[44px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 whitespace-nowrap transition-colors shadow-xs shrink-0"
          >
            <Plus className="w-4 h-4" />
            <span>نسخ احتياطي الآن</span>
          </button>
        </div>

        {/* Selected Backup Folder Status & Change Folder Button */}
        <div
          className={`p-3.5 rounded-xl border flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${
            backupFolderPath
              ? 'bg-emerald-50/70 border-emerald-200'
              : 'bg-amber-50/80 border-amber-200'
          }`}
        >
          <div className="flex items-start gap-2.5 min-w-0">
            <div
              className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${
                backupFolderPath
                  ? 'bg-emerald-100 text-emerald-700'
                  : 'bg-amber-100 text-amber-700'
              }`}
            >
              {backupFolderPath ? (
                <FolderCheck className="w-5 h-5" />
              ) : (
                <FolderOpen className="w-5 h-5" />
              )}
            </div>
            <div className="min-w-0">
              <p
                className={`text-xs font-extrabold ${
                  backupFolderPath ? 'text-emerald-900' : 'text-amber-900'
                }`}
              >
                {backupFolderPath
                  ? `مجلد النسخ الاحتياطي الحالي: ${backupFolderPath}`
                  : 'لم يتم تحديد مكان حفظ النسخ الاحتياطية بعد'}
              </p>
              <p className="text-[11px] text-slate-600 mt-0.5">
                {backupFolderPath
                  ? 'تُحفظ النسخ اليدوية والتلقائية عند إغلاق التطبيق داخل هذا المجلد (مع الاحتفاظ بآخر 7 نسخ).'
                  : 'يرجى تحديد مجلد في ذاكرة الهاتف (مثل Download/سجل متابعة الطلاب/النسخ الاحتياطية) لحفظ النسخ واستعادتها منه.'}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => {
              setCreateBackupAfterFolderConfirm(false);
              setFolderInputPath(
                backupFolderPath || 'Download/سجل متابعة الطلاب/النسخ الاحتياطية'
              );
              setShowFolderPickerModal(true);
            }}
            className="min-h-[38px] px-4 py-2 bg-white hover:bg-slate-50 text-slate-800 border border-slate-300 rounded-xl text-xs font-extrabold flex items-center justify-center gap-1.5 whitespace-nowrap shrink-0 shadow-2xs"
          >
            <FolderOpen className="w-4 h-4 text-teal-700" />
            <span>
              {backupFolderPath
                ? 'تغيير مجلد الحفظ'
                : 'تحديد مكان حفظ النسخ الاحتياطية'}
            </span>
          </button>
        </div>

        {/* Last Backup Details Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-3 border-t border-slate-100">
          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200/80">
            <span className="text-xs text-slate-500 block">آخر نسخة احتياطية:</span>
            <span className="font-mono-num text-xs font-extrabold text-slate-900 mt-1 block truncate">
              {latestBackup
                ? latestBackup.fileName || latestBackup.title
                : 'لا توجد نسخة بعد'}
            </span>
          </div>

          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200/80">
            <span className="text-xs text-slate-500 block">تاريخ ووقت آخر Backup:</span>
            <span className="font-mono-num text-xs font-extrabold text-slate-900 mt-1 block">
              {latestBackup
                ? new Date(latestBackup.createdAt).toLocaleString('ar-IQ', {
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                  })
                : '—'}
            </span>
          </div>

          <div className="bg-slate-50 p-3.5 rounded-xl border border-slate-200/80">
            <span className="text-xs text-slate-500 block">النسخ التلقائي اليومي (حد أقصى 7 نسخ):</span>
            <span
              className={`text-xs font-extrabold mt-1 flex items-center gap-1.5 ${
                hasTodayBackup ? 'text-emerald-700' : 'text-amber-700'
              }`}
            >
              <Clock className="w-3.5 h-3.5 shrink-0" />
              <span>
                {hasTodayBackup
                  ? `تم إنشاء نسخة اليوم (${snapshots.length} / 7 محفوظة)`
                  : 'لم يتم إنشاء نسخة اليوم بعد'}
              </span>
            </span>
          </div>
        </div>
      </section>

      {statusBanner && (
        <div
          role="status"
          className="bg-emerald-600 text-white rounded-2xl p-4 shadow-sm flex items-center gap-3"
        >
          <CheckCircle2 className="w-5 h-5 shrink-0" />
          <span className="text-xs sm:text-sm font-extrabold">{statusBanner}</span>
        </div>
      )}

      {errorBanner && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl p-4 text-xs font-bold flex items-start gap-2.5">
          <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <p className="font-extrabold">تعذر إتمام العملية:</p>
            <p className="mt-0.5">{errorBanner}</p>
          </div>
        </div>
      )}

      {/* Create Local Backup with Custom Label & Export Actions */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              إنشاء نسخة الآن وتصدير أو استيراد ملف خارجي
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              يحتفظ التطبيق بآخر 7 نسخ احتياطية محلية تلقائياً ويحذف النسخ الأقدم لمنع امتلاء الذاكرة
            </p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
          <input
            type="text"
            value={customTitle}
            onChange={(e) => setCustomTitle(e.target.value)}
            placeholder="ملاحظة أو عنوان اختياري للنسخة (مثال: قبل رصد درجات نهاية الشهر)..."
            className="flex-1 min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs"
          />
          <button
            type="button"
            disabled={isBusy}
            onClick={handleCreateBackupNow}
            className="min-h-[44px] px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 whitespace-nowrap transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>إنشاء نسخة الآن</span>
          </button>
        </div>

        {/* File Export / Import Bar */}
        <div className="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2.5">
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => BackupService.downloadRawSQLiteFile()}
              className="min-h-[40px] px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-bold flex items-center gap-1.5"
            >
              <Database className="w-3.5 h-3.5" />
              <span>تنزيل ملف قاعدة البيانات (StudentTracker_Backup.db)</span>
            </button>

            <label className="min-h-[40px] px-3.5 py-2 bg-emerald-700 hover:bg-emerald-800 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 cursor-pointer">
              <Upload className="w-3.5 h-3.5" />
              <span>استعادة نسخة من ملف خارجي (.db / .sqlite / .stbackup)</span>
              <input
                type="file"
                accept=".json,.sqlite,.db"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) {
                    setPendingRestoreFile(f);
                  }
                  e.target.value = '';
                }}
                className="hidden"
              />
            </label>
          </div>
        </div>
      </section>

      {/* Saved Local Backups Vault List */}
      <section className="space-y-3">
        <div className="flex items-center justify-between px-1">
          <h3 className="text-sm font-extrabold text-slate-800">
            قائمة النسخ المحلية الموجودة ({snapshots.length} / 7)
          </h3>
          <span className="text-xs text-slate-500">
            يتم الاحتفاظ بآخر 7 نسخ فقط وحذف الأقدم تلقائياً
          </span>
        </div>

        {snapshots.length === 0 ? (
          <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
            <HardDriveDownload className="w-8 h-8 text-slate-400 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-700">
              لا توجد نسخ احتياطية محفوظة حتى الآن
            </p>
            <p className="text-xs text-slate-500 mt-1">
              اضغط على "نسخ احتياطي الآن" أعلاه لحفظ نسخة كاملة من بياناتك.
            </p>
          </div>
        ) : (
          snapshots.map((snap) => (
            <div
              key={snap.id}
              className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
            >
              <div className="space-y-1 min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h4 className="text-sm font-extrabold text-slate-900 truncate">
                    {snap.title}
                  </h4>
                  {snap.isAutoDaily && (
                    <span className="text-[11px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-md">
                      نسخة يومية تلقائية
                    </span>
                  )}
                  {snap.isAutoPreRestore && (
                    <span className="text-[11px] font-bold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-md">
                      نسخة أمان قبل الاستعادة/التعديل
                    </span>
                  )}
                </div>

                {snap.fileName && (
                  <p className="text-xs font-mono-num text-slate-500 truncate">
                    اسم الملف: {snap.fileName} ({Math.max(1, Math.round(snap.sizeBytes / 1024))} KB)
                  </p>
                )}

                <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 font-mono-num">
                  <span>{new Date(snap.createdAt).toLocaleString('ar-IQ')}</span>
                  <span aria-hidden="true">·</span>
                  <span>الصفوف: {snap.stats.classesCount}</span>
                  <span aria-hidden="true">·</span>
                  <span>الشعب: {snap.stats.sectionsCount}</span>
                  <span aria-hidden="true">·</span>
                  <span>الطلاب: {snap.stats.studentsCount}</span>
                  <span aria-hidden="true">·</span>
                  <span>سجلات الأيام: {snap.stats.recordsCount}</span>
                  <span aria-hidden="true">·</span>
                  <span>العطل: {snap.stats.holidaysCount}</span>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
                <button
                  type="button"
                  onClick={() => setPendingRestoreSnapshot(snap)}
                  className="min-h-[40px] px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 whitespace-nowrap"
                >
                  <ArchiveRestore className="w-4 h-4" />
                  <span>استعادة نسخة</span>
                </button>

                <button
                  type="button"
                  onClick={() => BackupService.downloadBackupToFile(snap)}
                  className="min-h-[40px] px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1"
                  title="تنزيل كملف"
                >
                  <Download className="w-4 h-4" />
                  <span>تنزيل</span>
                </button>

                <button
                  type="button"
                  onClick={() => setPendingDeleteSnapshot(snap)}
                  className="min-h-[40px] px-3 py-2 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold flex items-center gap-1"
                  title="حذف نسخة"
                >
                  <Trash2 className="w-4 h-4" />
                  <span>حذف نسخة</span>
                </button>
              </div>
            </div>
          ))
        )}
      </section>

      {/* Diagnostic Error Log Section (Only shown if any backup errors were recorded) */}
      {diagnostics.length > 0 && (
        <section className="bg-slate-900 text-slate-200 rounded-2xl p-4 sm:p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-amber-400 text-xs font-extrabold">
              <FileCode2 className="w-4 h-4" />
              <span>سجل تشخيص أخطاء النسخ الاحتياطي ({diagnostics.length})</span>
            </div>
            <button
              type="button"
              onClick={() => {
                BackupService.clearDiagnosticLogs();
                setDiagnostics([]);
              }}
              className="text-xs text-slate-400 hover:text-white underline"
            >
              مسح السجل
            </button>
          </div>
          <div className="space-y-1.5 max-h-40 overflow-y-auto text-xs font-mono-num">
            {diagnostics.map((log, i) => (
              <div key={i} className="bg-slate-950/60 p-2 rounded-lg border border-slate-800">
                <span className="text-slate-400">
                  [{new Date(log.timestamp).toLocaleString('ar-IQ')}] ({log.operation}):
                </span>{' '}
                <span className="text-rose-300">{log.message}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Restore Warning Confirmation Modal (Mandatory safety backup before restore) */}
      {(pendingRestoreSnapshot || pendingRestoreFile) && (
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
                  تأكيد استعادة النسخة الاحتياطية
                </h3>
                <p className="text-sm text-slate-700 mt-1 leading-relaxed font-medium">
                  ستقوم هذه العملية باستبدال البيانات الحالية بالنسخة المختارة (
                  {pendingRestoreSnapshot
                    ? pendingRestoreSnapshot.fileName || pendingRestoreSnapshot.title
                    : pendingRestoreFile?.name}
                  ). هل تريد المتابعة؟
                </p>
              </div>
            </div>

            <div className="p-3.5 bg-emerald-50 rounded-xl border border-emerald-200 text-xs font-bold text-emerald-900 flex items-start gap-2.5">
              <ShieldCheck className="w-4 h-4 text-emerald-700 shrink-0 mt-0.5" />
              <span>
                حماية تلقائية: سيقوم التطبيق تلقائياً بإنشاء نسخة احتياطية كاملة للبيانات الحالية وحفظها في الجهاز قبل تنفيذ الاستعادة لضمان عدم فقدان أي بيانات.
              </span>
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={isBusy}
                onClick={confirmAndExecuteRestore}
                className="flex-1 min-h-[44px] bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                {isBusy ? 'جاري الاستعادة بأمان...' : 'تأكيد واستعادة النسخة'}
              </button>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => {
                  setPendingRestoreSnapshot(null);
                  setPendingRestoreFile(null);
                }}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Backup Confirmation Modal */}
      {pendingDeleteSnapshot && (
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
                  تأكيد حذف النسخة الاحتياطية
                </h3>
                <p className="text-sm text-slate-700 mt-1 leading-relaxed font-medium">
                  هل أنت متأكد من حذف النسخة الاحتياطية ({pendingDeleteSnapshot.fileName || pendingDeleteSnapshot.title}) من الجهاز؟
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={isBusy}
                onClick={confirmAndExecuteDelete}
                className="flex-1 min-h-[44px] bg-rose-600 hover:bg-rose-700 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                تأكيد الحذف
              </button>
              <button
                type="button"
                disabled={isBusy}
                onClick={() => setPendingDeleteSnapshot(null)}
                className="flex-1 min-h-[44px] bg-slate-100 hover:bg-slate-200 text-slate-700 font-extrabold text-xs rounded-xl transition-colors"
              >
                إلغاء
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Folder Selection Modal */}
      {showFolderPickerModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
          <div
            role="dialog"
            aria-modal="true"
            className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 space-y-4"
          >
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-teal-50 border border-teal-200 text-teal-700 flex items-center justify-center shrink-0">
                <FolderOpen className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-extrabold text-slate-900">
                  تحديد مكان حفظ النسخ الاحتياطية
                </h3>
                <p className="text-xs text-slate-600 mt-1 leading-relaxed">
                  اختر مجلد حفظ النسخ الاحتياطية في ذاكرة الهاتف (Storage Access Framework). سيتم حفظ الصلاحية واستخدام هذا المجلد تلقائياً دون الحاجة لاختياره في كل مرة.
                </p>
              </div>
            </div>

            <div className="space-y-2">
              <label className="block text-xs font-extrabold text-slate-800">
                مسار المجلد المختار:
              </label>
              <input
                type="text"
                dir="ltr"
                value={folderInputPath}
                onChange={(e) => setFolderInputPath(e.target.value)}
                placeholder="Download/سجل متابعة الطلاب/النسخ الاحتياطية"
                className="w-full min-h-[42px] px-3.5 py-2 bg-slate-50 border border-slate-300 rounded-xl text-xs font-mono-num text-slate-900"
              />
              <div className="flex flex-wrap gap-1.5 pt-1">
                {[
                  'Download/سجل متابعة الطلاب/النسخ الاحتياطية',
                  'Documents/سجل متابعة الطلاب/النسخ الاحتياطية',
                  'Download/Backups',
                ].map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    onClick={() => setFolderInputPath(preset)}
                    className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-lg text-[11px] font-mono-num"
                  >
                    {preset}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                onClick={handleSaveFolderSelection}
                className="flex-1 min-h-[44px] bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                {createBackupAfterFolderConfirm
                  ? 'حفظ المجلد وإنشاء النسخة الآن'
                  : 'اعتماد وحفظ المجلد'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowFolderPickerModal(false);
                  setCreateBackupAfterFolderConfirm(false);
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
