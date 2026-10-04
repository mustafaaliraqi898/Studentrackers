import React, { useEffect, useState } from 'react';
import {
  AlertTriangle,
  ArchiveRestore,
  CheckCircle2,
  Cloud,
  Database,
  Download,
  HardDriveDownload,
  Plus,
  ShieldCheck,
  Trash2,
  Upload,
} from 'lucide-react';
import { BackupSnapshotMetadata } from '../types';
import { BackupService } from '../services/backupService';

interface BackupRestoreViewProps {
  onDatabaseRestored: () => Promise<void>;
}

export const BackupRestoreView: React.FC<BackupRestoreViewProps> = ({
  onDatabaseRestored,
}) => {
  const [snapshots, setSnapshots] = useState<BackupSnapshotMetadata[]>([]);
  const [customTitle, setCustomTitle] = useState('');
  const [statusBanner, setStatusBanner] = useState<string | null>(null);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  // Confirmation modal state for restoring a snapshot or uploaded file
  const [pendingRestoreSnapshot, setPendingRestoreSnapshot] =
    useState<BackupSnapshotMetadata | null>(null);
  const [pendingRestoreFile, setPendingRestoreFile] = useState<File | null>(null);
  const [createSafetyBackupBeforeRestore, setCreateSafetyBackupBeforeRestore] =
    useState(true);

  const loadSnapshots = async () => {
    const list = await BackupService.listLocalBackups();
    setSnapshots(list);
  };

  useEffect(() => {
    loadSnapshots();
  }, []);

  const handleCreateBackupNow = async () => {
    setIsBusy(true);
    setErrorBanner(null);
    setStatusBanner(null);
    try {
      const snap = await BackupService.createLocalBackup(
        customTitle.trim() || undefined,
        false
      );
      setCustomTitle('');
      await loadSnapshots();
      setStatusBanner(`تم إنشاء النسخة الاحتياطية "${snap.title}" بنجاح.`);
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error ? err.message : 'تعذر إنشاء النسخة الاحتياطية.'
      );
    } finally {
      setIsBusy(false);
    }
  };

  const handleDeleteSnapshot = async (id: string) => {
    await BackupService.deleteLocalBackup(id);
    await loadSnapshots();
  };

  const confirmAndExecuteRestore = async () => {
    setIsBusy(true);
    setErrorBanner(null);
    setStatusBanner(null);
    try {
      if (pendingRestoreSnapshot) {
        await BackupService.restoreFromSnapshot(
          pendingRestoreSnapshot,
          createSafetyBackupBeforeRestore
        );
        await onDatabaseRestored();
        await loadSnapshots();
        setStatusBanner(
          `تمت استعادة النسخة الاحتياطية "${pendingRestoreSnapshot.title}" بنجاح ورجوع جميع البيانات والسجلات كما كانت.`
        );
      } else if (pendingRestoreFile) {
        const msg = await BackupService.restoreFromUploadedFile(
          pendingRestoreFile,
          createSafetyBackupBeforeRestore
        );
        await onDatabaseRestored();
        await loadSnapshots();
        setStatusBanner(msg);
      }
    } catch (err: unknown) {
      setErrorBanner(
        err instanceof Error ? err.message : 'فشلت عملية استعادة النسخة الاحتياطية.'
      );
    } finally {
      setIsBusy(false);
      setPendingRestoreSnapshot(null);
      setPendingRestoreFile(null);
    }
  };

  const latestBackup = snapshots[0];

  return (
    <div className="space-y-6 pb-10">
      {/* Header & Last Backup Info */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            النسخ الاحتياطي والاستعادة (SQLite Backup)
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            احفظ نسخة احتياطية كاملة من قاعدة بيانات SQLite (الصفوف، الشعب، الطلاب، الحضور، التقييمات، الاختبارات، والعطل)
          </p>
        </div>

        <div className="bg-slate-50 px-3.5 py-2.5 rounded-xl border border-slate-200/80 text-xs shrink-0">
          <span className="text-slate-500 block">آخر نسخة احتياطية محلية:</span>
          <span className="font-mono-num font-extrabold text-slate-900 mt-0.5 block">
            {latestBackup
              ? new Date(latestBackup.createdAt).toLocaleString('ar-IQ')
              : 'لم يتم إنشاء نسخة بعد'}
          </span>
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
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-2xl p-4 text-xs font-bold">
          {errorBanner}
        </div>
      )}

      {/* Create Local Backup & Export Actions */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              إنشاء نسخة احتياطية محلية الآن
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              تُحفظ النسخة في خزنة الجهاز المحلية مع إمكانية تنزيلها كملف لمشاركته أو الاحتفاظ به
            </p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2.5">
          <input
            type="text"
            value={customTitle}
            onChange={(e) => setCustomTitle(e.target.value)}
            placeholder="عنوان اختياري للنسخة (مثال: قبل تعديل درجات شهر تشرين الأول)..."
            className="flex-1 min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs"
          />
          <button
            type="button"
            disabled={isBusy}
            onClick={handleCreateBackupNow}
            className="min-h-[44px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 whitespace-nowrap transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>إنشاء نسخة احتياطية الآن</span>
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
              <span>تنزيل ملف قاعدة البيانات (.sqlite)</span>
            </button>

            <label className="min-h-[40px] px-3.5 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 cursor-pointer">
              <Upload className="w-3.5 h-3.5" />
              <span>استعادة من ملف خارجي (.stbackup / .sqlite)</span>
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
        <h3 className="text-sm font-extrabold text-slate-800 px-1">
          النسخ الاحتياطية المتوفرة داخل الجهاز ({snapshots.length})
        </h3>

        {snapshots.length === 0 ? (
          <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
            <HardDriveDownload className="w-8 h-8 text-slate-400 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-700">
              لا توجد نسخ احتياطية محفوظة حتى الآن
            </p>
            <p className="text-xs text-slate-500 mt-1">
              اضغط على "إنشاء نسخة احتياطية الآن" أعلاه لتجربة حفظ واستعادة البيانات عملياً.
            </p>
          </div>
        ) : (
          snapshots.map((snap) => (
            <div
              key={snap.id}
              className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4"
            >
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h4 className="text-sm font-extrabold text-slate-900">
                    {snap.title}
                  </h4>
                  {snap.isAutoPreRestore && (
                    <span className="text-[11px] font-bold text-amber-700">
                      · نسخة أمان تلقائية
                    </span>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 font-mono-num">
                  <span>{new Date(snap.createdAt).toLocaleString('ar-IQ')}</span>
                  <span aria-hidden="true">·</span>
                  <span>الصفوف: {snap.stats.classesCount}</span>
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
                  <span>استعادة</span>
                </button>

                <button
                  type="button"
                  onClick={() => BackupService.downloadBackupToFile(snap)}
                  className="min-h-[40px] px-3 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1"
                  title="تنزيل كملف"
                >
                  <Download className="w-4 h-4" />
                  <span>ملف</span>
                </button>

                <button
                  type="button"
                  onClick={() => handleDeleteSnapshot(snap.id)}
                  className="min-h-[40px] px-2.5 py-2 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold"
                  title="حذف النسخة"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))
        )}
      </section>

      {/* Phase 10 Preview Card: Google Drive Backup (Scheduled after local MVP verification) */}
      <section className="bg-slate-100 rounded-2xl p-4 sm:p-5 border border-slate-200/80 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3">
          <div className="w-10 h-10 rounded-xl bg-white text-slate-700 border border-slate-200 flex items-center justify-center shrink-0">
            <Cloud className="w-5 h-5" />
          </div>
          <div>
            <h4 className="text-sm font-extrabold text-slate-900">
              النسخ الاحتياطي السحابي إلى Google Drive (المرحلة العاشرة)
            </h4>
            <p className="text-xs text-slate-600 mt-0.5 leading-relaxed">
              وفق خطة التنفيذ، يتم أولاً اعتماد واختبار النسخ الاحتياطي المحلي لقاعدة بيانات SQLite على أجهزة Android، ثم تفعيل ربط Google Drive المشفر في المرحلة العاشرة.
            </p>
          </div>
        </div>
      </section>

      {/* Restore Warning Confirmation Modal (Exact prompt text) */}
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
                  استعادة النسخة الاحتياطية قد تستبدل البيانات الحالية. هل تريد المتابعة؟
                </p>
              </div>
            </div>

            <label className="flex items-center gap-2.5 p-3 bg-slate-50 rounded-xl border border-slate-200/80 text-xs font-bold text-slate-700 cursor-pointer">
              <input
                type="checkbox"
                checked={createSafetyBackupBeforeRestore}
                onChange={(e) => setCreateSafetyBackupBeforeRestore(e.target.checked)}
                className="w-4 h-4 accent-emerald-600 rounded"
              />
              <span>إنشاء نسخة احتياطية تلقائية من البيانات الحالية قبل تنفيذ الاستعادة</span>
            </label>

            <div className="flex items-center gap-2.5 pt-2">
              <button
                type="button"
                disabled={isBusy}
                onClick={confirmAndExecuteRestore}
                className="flex-1 min-h-[44px] bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl transition-colors"
              >
                استعادة
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
    </div>
  );
};
