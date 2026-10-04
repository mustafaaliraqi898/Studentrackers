import { BackupSnapshotMetadata } from '../types';
import {
  exportDatabaseBinary,
  replaceDatabaseFromBinary,
  SCHEMA_VERSION,
} from '../db/sqliteEngine';
import { TrackerRepository } from '../repositories/trackerRepository';

const BACKUP_IDB_NAME = 'StudentTrackerBackupsVault';
const BACKUP_STORE = 'snapshots';

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
      console.error('Failed to list backups:', err);
      return [];
    }
  }

  static async createLocalBackup(
    customTitle?: string,
    isAutoPreRestore = false
  ): Promise<BackupSnapshotMetadata> {
    const binary = exportDatabaseBinary();
    const stats = await TrackerRepository.getDatabaseStats();
    const now = new Date();
    const formattedTime = now.toLocaleString('ar-IQ', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });

    const snapshot: BackupSnapshotMetadata = {
      id: `bkp-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      title:
        customTitle ||
        (isAutoPreRestore
          ? `نسخة تلقائية قبل الاستعادة (${formattedTime})`
          : `نسخة احتياطية كاملة (${formattedTime})`),
      createdAt: now.toISOString(),
      isAutoPreRestore,
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

    return snapshot;
  }

  static async deleteLocalBackup(id: string): Promise<void> {
    const idb = await openBackupVaultDB();
    await new Promise<void>((resolve, reject) => {
      const tx = idb.transaction(BACKUP_STORE, 'readwrite');
      const store = tx.objectStore(BACKUP_STORE);
      store.delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  /**
   * Restores a local backup snapshot after automatically creating a safety snapshot of current data.
   */
  static async restoreFromSnapshot(
    snapshot: BackupSnapshotMetadata,
    createAutoSafetyBackup = true
  ): Promise<void> {
    if (createAutoSafetyBackup) {
      await this.createLocalBackup(undefined, true);
    }
    const bytes = base64ToUint8(snapshot.sqliteBase64);
    await replaceDatabaseFromBinary(bytes);
  }

  /**
   * Downloads a backup snapshot as an external file (.stbackup.json) to device storage.
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
    const datePart = snapshot.createdAt.slice(0, 10);
    a.href = url;
    a.download = `نسخة_احتياطية_سجل_الطلاب_${datePart}.stbackup.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Downloads the raw .sqlite binary file directly if the teacher wants to inspect it in a SQLite viewer.
   */
  static downloadRawSQLiteFile(): void {
    const binary = exportDatabaseBinary();
    const blob = new Blob([binary], { type: 'application/x-sqlite3' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    const datePart = new Date().toISOString().slice(0, 10);
    a.href = url;
    a.download = `student_tracker_${datePart}.sqlite`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  /**
   * Parses and restores an uploaded backup file (.stbackup.json or raw .sqlite).
   */
  static async restoreFromUploadedFile(
    file: File,
    createAutoSafetyBackup = true
  ): Promise<string> {
    if (createAutoSafetyBackup) {
      await this.createLocalBackup(undefined, true);
    }

    if (file.name.endsWith('.sqlite') || file.name.endsWith('.db')) {
      const buf = await file.arrayBuffer();
      await replaceDatabaseFromBinary(new Uint8Array(buf));
      return `تمت استعادة ملف قاعدة البيانات الخام (${file.name}) بنجاح.`;
    }

    const text = await file.text();
    const parsed = JSON.parse(text);
    const base64 = parsed?.snapshot?.sqliteBase64 || parsed?.sqliteBase64;
    if (!base64 || typeof base64 !== 'string') {
      throw new Error('ملف النسخة الاحتياطية غير صالح أو تالف.');
    }

    const bytes = base64ToUint8(base64);
    await replaceDatabaseFromBinary(bytes);
    return `تمت استعادة النسخة الاحتياطية "${parsed?.snapshot?.title || file.name}" بنجاح.`;
  }
}
