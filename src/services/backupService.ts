import { BackupSnapshotMetadata } from '../types';
import {
  exportDatabaseBinary,
  replaceDatabaseFromBinary,
  SCHEMA_VERSION,
} from '../db/sqliteEngine';
import { TrackerRepository } from '../repositories/trackerRepository';

const BACKUP_IDB_NAME = 'StudentTrackerBackupsVault';
const BACKUP_STORE = 'snapshots';
const MAX_LOCAL_BACKUPS = 7;
const LAST_AUTO_BACKUP_DATE_KEY = 'student_tracker_last_auto_backup_date';
const BACKUP_ERROR_LOGS_KEY = 'student_tracker_backup_diagnostics';

export interface BackupDiagnosticLog {
  timestamp: string;
  operation: string;
  message: string;
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = '';
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToUint8(base64: string): Uint8Array {
  const binary = atob(base64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function formatBackupFileName(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  const yyyy = date.getFullYear();
  const mm = pad(date.getMonth() + 1);
  const dd = pad(date.getDate());
  const hh = pad(date.getHours());
  const min = pad(date.getMinutes());
  const ss = pad(date.getSeconds());
  return `StudentTracker_Backup_${yyyy}-${mm}-${dd}_${hh}-${min}-${ss}`;
}

export function getLocalTodayDateString(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function openBackupVaultDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(BACKUP_IDB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(BACKUP_STORE)) {
        db.createObjectStore(BACKUP_STORE, { keyPath: 'id' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

export class BackupService {
  static logDiagnosticError(operation: string, err: unknown): string {
    const message =
      err instanceof Error ? err.message : 'حدث خطأ غير متوقع أثناء عملية النسخ الاحتياطي.';
    console.error(`[BackupService:${operation}]`, err);
    try {
      const raw = localStorage.getItem(BACKUP_ERROR_LOGS_KEY);
      const list: BackupDiagnosticLog[] = raw ? JSON.parse(raw) : [];
      list.unshift({
        timestamp: new Date().toISOString(),
        operation,
        message,
      });
      localStorage.setItem(BACKUP_ERROR_LOGS_KEY, JSON.stringify(list.slice(0, 20)));
    } catch {
      // ignore storage quota errors
    }
    return message;
  }

  static getDiagnosticLogs(): BackupDiagnosticLog[] {
    try {
      const raw = localStorage.getItem(BACKUP_ERROR_LOGS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  static clearDiagnosticLogs(): void {
    try {
      localStorage.removeItem(BACKUP_ERROR_LOGS_KEY);
    } catch {
      // ignore
    }
  }

  static async listLocalBackups(): Promise<BackupSnapshotMetadata[]> {
    try {
      const idb = await openBackupVaultDB();
      return new Promise((resolve, reject) => {
        const tx = idb.transaction(BACKUP_STORE, 'readonly');
        const store = tx.objectStore(BACKUP_STORE);
        const req = store.getAll();
        req.onsuccess = () => {
          const items = (req.result as BackupSnapshotMetadata[]) || [];
          items.sort(
            (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          );
          resolve(items);
        };
        req.onerror = () => reject(req.error);
      });
    } catch (err) {
      this.logDiagnosticError('listLocalBackups', err);
      return [];
    }
  }

  /**
   * Enforces keeping only the latest 7 local backups and automatically deletes older ones.
   */
  static async pruneOldBackups(maxCount: number = MAX_LOCAL_BACKUPS): Promise<number> {
    try {
      const all = await this.listLocalBackups();
      if (all.length <= maxCount) return 0;

      const toDelete = all.slice(maxCount);
      const idb = await openBackupVaultDB();
      await new Promise<void>((resolve, reject) => {
        const tx = idb.transaction(BACKUP_STORE, 'readwrite');
        const store = tx.objectStore(BACKUP_STORE);
        for (const item of toDelete) {
          store.delete(item.id);
        }
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
      return toDelete.length;
    } catch (err) {
      this.logDiagnosticError('pruneOldBackups', err);
      return 0;
    }
  }

  static async createLocalBackup(
    customTitle?: string,
    isAutoPreRestore = false,
    isAutoDaily = false
  ): Promise<BackupSnapshotMetadata> {
    try {
      const binary = exportDatabaseBinary();
      if (!binary || binary.byteLength === 0) {
        throw new Error('بيانات قاعدة البيانات فارغة أو غير جاهزة للنسخ الاحتياطي.');
      }
      const stats = await TrackerRepository.getDatabaseStats();
      const now = new Date();
      const fileBaseName = formatBackupFileName(now);
      const formattedTime = now.toLocaleString('ar-IQ', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });

      const defaultTitle = isAutoPreRestore
        ? `نسخة أمان تلقائية قبل التعديل/الاستعادة (${formattedTime})`
        : isAutoDaily
        ? `نسخة احتياطية يومية تلقائية (${formattedTime})`
        : `${fileBaseName} (${formattedTime})`;

      const snapshot: BackupSnapshotMetadata = {
        id: `bkp-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        fileName: `${fileBaseName}.db`,
        title: customTitle || defaultTitle,
        createdAt: now.toISOString(),
        isAutoPreRestore,
        isAutoDaily,
        sizeBytes: binary.byteLength,
        stats,
        sqliteBase64: uint8ToBase64(binary),
      };

      const idb = await openBackupVaultDB();
      await new Promise<void>((resolve, reject) => {
        const tx = idb.transaction(BACKUP_STORE, 'readwrite');
        const store = tx.objectStore(BACKUP_STORE);
        store.put(snapshot);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });

      // Enforce keeping only the latest 7 local backups
      await this.pruneOldBackups(MAX_LOCAL_BACKUPS);

      // Mark today's date as having a successful backup
      try {
        localStorage.setItem(LAST_AUTO_BACKUP_DATE_KEY, getLocalTodayDateString(now));
      } catch {
        // ignore
      }

      return snapshot;
    } catch (err) {
      const msg = this.logDiagnosticError('createLocalBackup', err);
      throw new Error(`فشل إنشاء النسخة الاحتياطية: ${msg}`);
    }
  }

  /**
   * 1. عند أول تشغيل للتطبيق كل يوم:
   * - تحقق هل تم إنشاء Backup ناجح في نفس اليوم.
   * - إذا لم يوجد، أنشئ Backup تلقائياً.
   * - لا تنشئ أكثر من Backup تلقائي واحد في اليوم.
   */
  static async ensureDailyAutoBackup(): Promise<{
    created: boolean;
    snapshot?: BackupSnapshotMetadata;
    error?: string;
  }> {
    try {
      const todayStr = getLocalTodayDateString(new Date());
      const lastRecordedDate = localStorage.getItem(LAST_AUTO_BACKUP_DATE_KEY);

      const existingBackups = await this.listLocalBackups();
      const hasBackupToday = existingBackups.some((b) => {
        const createdDate = getLocalTodayDateString(new Date(b.createdAt));
        return createdDate === todayStr;
      });

      if (lastRecordedDate === todayStr && hasBackupToday) {
        return { created: false, snapshot: existingBackups[0] };
      }

      if (hasBackupToday) {
        try {
          localStorage.setItem(LAST_AUTO_BACKUP_DATE_KEY, todayStr);
        } catch {
          // ignore
        }
        return { created: false, snapshot: existingBackups[0] };
      }

      const snap = await this.createLocalBackup(undefined, false, true);
      return { created: true, snapshot: snap };
    } catch (err) {
      const msg = this.logDiagnosticError('ensureDailyAutoBackup', err);
      return { created: false, error: msg };
    }
  }

  static async deleteLocalBackup(id: string): Promise<void> {
    try {
      const idb = await openBackupVaultDB();
      await new Promise<void>((resolve, reject) => {
        const tx = idb.transaction(BACKUP_STORE, 'readwrite');
        const store = tx.objectStore(BACKUP_STORE);
        store.delete(id);
        tx.oncomplete = () => resolve();
        tx.onerror = () => reject(tx.error);
      });
    } catch (err) {
      const msg = this.logDiagnosticError('deleteLocalBackup', err);
      throw new Error(`تعذر حذف النسخة الاحتياطية: ${msg}`);
    }
  }

  /**
   * Restores a local backup snapshot after automatically creating a mandatory safety snapshot of current data.
   * Will abort restore if creating the safety backup fails.
   */
  static async restoreFromSnapshot(
    snapshot: BackupSnapshotMetadata
  ): Promise<BackupSnapshotMetadata> {
    // Mandatory pre-restore safety backup
    const safetyBackup = await this.createLocalBackup(
      `نسخة أمان تلقائية قبل استعادة (${snapshot.fileName || snapshot.title})`,
      true,
      false
    );
    try {
      const bytes = base64ToUint8(snapshot.sqliteBase64);
      await replaceDatabaseFromBinary(bytes);
      return safetyBackup;
    } catch (err) {
      const msg = this.logDiagnosticError('restoreFromSnapshot', err);
      throw new Error(`فشلت استعادة النسخة الاحتياطية (بياناتك الحالية محفوظة بأمان): ${msg}`);
    }
  }

  /**
   * Downloads a backup snapshot as an external file (.stbackup.json) to device storage
   * using clear naming: StudentTracker_Backup_YYYY-MM-DD_HH-mm-ss.stbackup.json
   */
  static downloadBackupToFile(snapshot: BackupSnapshotMetadata): void {
    const payload = {
      app: 'سجل متابعة الطلاب',
      schemaVersion: SCHEMA_VERSION,
      exportedAt: new Date().toISOString(),
      snapshot,
    };

    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const baseName = (snapshot.fileName || formatBackupFileName(new Date(snapshot.createdAt))).replace(/\.db$/, '');
    a.href = url;
    a.download = `${baseName}.stbackup.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Downloads the raw .sqlite/.db binary file directly with clear naming:
   * StudentTracker_Backup_YYYY-MM-DD_HH-mm-ss.db
   */
  static downloadRawSQLiteFile(): void {
    const binary = exportDatabaseBinary();
    const blob = new Blob([binary], { type: 'application/x-sqlite3' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const baseName = formatBackupFileName(new Date());
    a.href = url;
    a.download = `${baseName}.db`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Parses and restores an uploaded backup file (.stbackup.json or raw .sqlite/.db).
   * Always creates a mandatory safety backup of current data before restoring.
   */
  static async restoreFromUploadedFile(
    file: File
  ): Promise<{ message: string; safetyBackup: BackupSnapshotMetadata }> {
    const safetyBackup = await this.createLocalBackup(
      `نسخة أمان تلقائية قبل استعادة ملف خارجي (${file.name})`,
      true,
      false
    );

    try {
      if (file.name.endsWith('.sqlite') || file.name.endsWith('.db')) {
        const buf = await file.arrayBuffer();
        await replaceDatabaseFromBinary(new Uint8Array(buf));
        return {
          message: `تمت استعادة ملف قاعدة البيانات (${file.name}) بنجاح. تم حفظ نسخة أمان للبيانات السابقة باسم (${safetyBackup.fileName || safetyBackup.title}).`,
          safetyBackup,
        };
      }

      const text = await file.text();
      const parsed = JSON.parse(text);
      const base64 = parsed?.snapshot?.sqliteBase64 || parsed?.sqliteBase64;
      if (!base64 || typeof base64 !== 'string') {
        throw new Error('ملف النسخة الاحتياطية غير صالح أو تالف.');
      }

      const bytes = base64ToUint8(base64);
      await replaceDatabaseFromBinary(bytes);
      return {
        message: `تمت استعادة النسخة الاحتياطية "${parsed?.snapshot?.title || file.name}" بنجاح. تم حفظ نسخة أمان للبيانات السابقة.`,
        safetyBackup,
      };
    } catch (err) {
      const msg = this.logDiagnosticError('restoreFromUploadedFile', err);
      throw new Error(`فشل استعادة الملف الخارجي (بياناتك الحالية محفوظة بأمان): ${msg}`);
    }
  }
}
