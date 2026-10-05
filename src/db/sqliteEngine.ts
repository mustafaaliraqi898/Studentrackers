import initSqlJs, { Database, SqlJsStatic } from 'sql.js';

const IDB_NAME = 'StudentTrackerSQLiteDB';
const IDB_STORE = 'sqlite_store';
const IDB_KEY = 'primary_sqlite_binary';

let sqlPromise: Promise<SqlJsStatic> | null = null;
let dbInstance: Database | null = null;
let lastMigrationWarning: string | null = null;

export const SCHEMA_VERSION = 5;

export function getLastMigrationWarning(): string | null {
  return lastMigrationWarning;
}

export function clearLastMigrationWarning(): void {
  lastMigrationWarning = null;
}

export const SQL_SCHEMA_DDL = `
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS SchemaMigrations (
  Version INTEGER PRIMARY KEY,
  AppliedAt TEXT NOT NULL,
  Description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS Classes (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  ClassName TEXT NOT NULL UNIQUE,
  CreatedAt TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS Sections (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  ClassID INTEGER NOT NULL,
  SectionName TEXT NOT NULL,
  CreatedAt TEXT NOT NULL,
  FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
  UNIQUE(ClassID, SectionName)
);

CREATE TABLE IF NOT EXISTS Students (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  StudentName TEXT NOT NULL,
  ClassID INTEGER NOT NULL,
  SectionID INTEGER NOT NULL,
  IsActive INTEGER NOT NULL DEFAULT 1,
  CreatedDate TEXT NOT NULL,
  FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
  FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
  UNIQUE(StudentName, ClassID)
);

CREATE TABLE IF NOT EXISTS DailyRecords (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  StudentID INTEGER NOT NULL,
  RecordDate TEXT NOT NULL,
  ClassID INTEGER NOT NULL,
  SectionID INTEGER NOT NULL,
  IsSaved INTEGER NOT NULL DEFAULT 1,
  CreatedDate TEXT NOT NULL DEFAULT '',
  ModifiedDate TEXT NOT NULL DEFAULT '',
  UpdatedAt TEXT NOT NULL,
  FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
  FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
  FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
  UNIQUE(StudentID, ClassID, SectionID, RecordDate),
  UNIQUE(StudentID, RecordDate)
);

CREATE TABLE IF NOT EXISTS Attendance (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  StudentID INTEGER NOT NULL,
  RecordDate TEXT NOT NULL,
  Status TEXT NOT NULL CHECK(Status IN ('present', 'absent')),
  FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
  UNIQUE(StudentID, RecordDate)
);

CREATE TABLE IF NOT EXISTS Evaluations (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  StudentID INTEGER NOT NULL,
  RecordDate TEXT NOT NULL,
  Participation REAL NULL CHECK(Participation IN (1, 0.5, 0) OR Participation IS NULL),
  Notebook REAL NULL CHECK(Notebook IN (1, 0.5, 0) OR Notebook IS NULL),
  Homework REAL NULL CHECK(Homework IN (1, 0.5, 0) OR Homework IS NULL),
  Behavior REAL NULL CHECK(Behavior IN (1, 0.5, 0) OR Behavior IS NULL),
  FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
  UNIQUE(StudentID, RecordDate)
);

CREATE TABLE IF NOT EXISTS DailyTests (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  StudentID INTEGER NOT NULL,
  RecordDate TEXT NOT NULL,
  Score REAL NOT NULL CHECK(Score >= 0 AND Score <= 5),
  TestValue REAL NOT NULL DEFAULT 0 CHECK(TestValue >= 0 AND TestValue <= 5),
  FOREIGN KEY (StudentID) REFERENCES Students(ID) ON DELETE CASCADE,
  UNIQUE(StudentID, RecordDate)
);

CREATE TABLE IF NOT EXISTS Holidays (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  HolidayDate TEXT NOT NULL UNIQUE,
  HolidayName TEXT NOT NULL,
  HolidayType TEXT NOT NULL,
  Notes TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS DailySchedule (
  ID INTEGER PRIMARY KEY AUTOINCREMENT,
  DayOfWeek TEXT NOT NULL,
  ClassID INTEGER NOT NULL,
  SectionID INTEGER NOT NULL,
  LessonNumber INTEGER NOT NULL CHECK(LessonNumber >= 1),
  StartTime TEXT NOT NULL,
  EndTime TEXT NOT NULL,
  CreatedDate TEXT NOT NULL,
  FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
  FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
  UNIQUE(DayOfWeek, ClassID, SectionID, LessonNumber)
);

CREATE INDEX IF NOT EXISTS idx_sections_class ON Sections(ClassID);
CREATE INDEX IF NOT EXISTS idx_students_class_sec ON Students(ClassID, SectionID);
CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);
CREATE INDEX IF NOT EXISTS idx_dailyrecords_date ON DailyRecords(RecordDate, ClassID, SectionID);
CREATE INDEX IF NOT EXISTS idx_attendance_student_date ON Attendance(StudentID, RecordDate);
CREATE INDEX IF NOT EXISTS idx_evaluations_student_date ON Evaluations(StudentID, RecordDate);
CREATE INDEX IF NOT EXISTS idx_dailytests_student_date ON DailyTests(StudentID, RecordDate);
CREATE INDEX IF NOT EXISTS idx_holidays_date ON Holidays(HolidayDate);
CREATE INDEX IF NOT EXISTS idx_schedule_day_lesson ON DailySchedule(DayOfWeek, LessonNumber, StartTime);
CREATE INDEX IF NOT EXISTS idx_schedule_class_sec ON DailySchedule(ClassID, SectionID);
CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_unique_slot ON DailySchedule(DayOfWeek, ClassID, SectionID, LessonNumber);
`;

function openIndexedDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(IDB_STORE)) {
        db.createObjectStore(IDB_STORE);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function loadBinaryFromIndexedDB(): Promise<Uint8Array | null> {
  try {
    const idb = await openIndexedDB();
    return new Promise((resolve, reject) => {
      const tx = idb.transaction(IDB_STORE, 'readonly');
      const store = tx.objectStore(IDB_STORE);
      const req = store.get(IDB_KEY);
      req.onsuccess = () => {
        if (req.result instanceof Uint8Array) {
          resolve(req.result);
        } else if (req.result instanceof ArrayBuffer) {
          resolve(new Uint8Array(req.result));
        } else {
          resolve(null);
        }
      };
      req.onerror = () => reject(req.error);
    });
  } catch (err) {
    console.warn('IndexedDB read fallback:', err);
    return null;
  }
}

export async function persistDatabaseToStorage(): Promise<void> {
  if (!dbInstance) return;
  const binary = dbInstance.export();
  try {
    const idb = await openIndexedDB();
    await new Promise<void>((resolve, reject) => {
      const tx = idb.transaction(IDB_STORE, 'readwrite');
      const store = tx.objectStore(IDB_STORE);
      store.put(binary, IDB_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  } catch (err) {
    console.error('Failed to persist SQLite binary to IndexedDB:', err);
  }
}

function runMigrations(db: Database): void {
  db.run(SQL_SCHEMA_DDL);

  const res = db.exec('SELECT MAX(Version) as maxVer FROM SchemaMigrations;');
  const currentVer =
    res.length > 0 && res[0].values.length > 0 && res[0].values[0][0] !== null
      ? Number(res[0].values[0][0])
      : 0;

  if (currentVer < 1) {
    db.run(
      `INSERT INTO SchemaMigrations (Version, AppliedAt, Description) VALUES (1, ?, ?);`,
      [
        new Date().toISOString(),
        'Initial relational schema: Classes, Sections, Students, DailyRecords, Attendance, Evaluations, DailyTests, Holidays',
      ]
    );
  }

  if (currentVer < 2) {
    db.run(
      `INSERT INTO SchemaMigrations (Version, AppliedAt, Description) VALUES (2, ?, ?);`,
      [
        new Date().toISOString(),
        'Added performance indexes and strict CHECK constraints on Attendance, Evaluations, and DailyTests',
      ]
    );
  }

  if (currentVer < 3) {
    const safeAlter = (sql: string) => {
      try {
        db.run(sql);
      } catch {
        // column already exists
      }
    };
    safeAlter(`ALTER TABLE DailyRecords ADD COLUMN IsSaved INTEGER NOT NULL DEFAULT 1;`);
    safeAlter(`ALTER TABLE DailyRecords ADD COLUMN CreatedDate TEXT NOT NULL DEFAULT '';`);
    safeAlter(`ALTER TABLE DailyRecords ADD COLUMN ModifiedDate TEXT NOT NULL DEFAULT '';`);
    safeAlter(`ALTER TABLE DailyTests ADD COLUMN TestValue REAL NOT NULL DEFAULT 0;`);
    safeAlter(`UPDATE DailyTests SET TestValue = Score WHERE TestValue = 0 AND Score > 0;`);
    safeAlter(`CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);`);

    db.run(
      `INSERT INTO SchemaMigrations (Version, AppliedAt, Description) VALUES (3, ?, ?);`,
      [
        new Date().toISOString(),
        'Added IsSaved, CreatedDate, ModifiedDate to DailyRecords, TestValue to DailyTests, and idx_students_name index',
      ]
    );
  }

  if (currentVer < 4) {
    try {
      // Check if existing data has duplicate student names in the same ClassID across different sections
      const dupCheck = db.exec(`
        SELECT StudentName, ClassID, COUNT(*) as cnt
        FROM Students
        GROUP BY StudentName, ClassID
        HAVING cnt > 1;
      `);
      if (dupCheck.length > 0 && dupCheck[0].values.length > 0) {
        const dupNames = dupCheck[0].values.map((r) => String(r[0])).join('، ');
        lastMigrationWarning = `تنبيه Migration: وُجدت أسماء مكررة سابقة في نفس الصف (${dupNames})؛ تم الحفاظ على جميع الطلاب الحاليين بأمان وتطبيق منع التكرار على الإضافات والتعديلات.`;
      } else {
        db.run('PRAGMA foreign_keys = OFF;');
        db.run('BEGIN TRANSACTION;');
        try {
          db.run(`
            CREATE TABLE IF NOT EXISTS Students_v4 (
              ID INTEGER PRIMARY KEY AUTOINCREMENT,
              StudentName TEXT NOT NULL,
              ClassID INTEGER NOT NULL,
              SectionID INTEGER NOT NULL,
              IsActive INTEGER NOT NULL DEFAULT 1,
              CreatedDate TEXT NOT NULL,
              FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
              FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
              UNIQUE(StudentName, ClassID)
            );
          `);
          db.run(`
            INSERT INTO Students_v4 (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate)
            SELECT ID, StudentName, ClassID, SectionID, IsActive, CreatedDate FROM Students;
          `);
          db.run('DROP TABLE Students;');
          db.run('ALTER TABLE Students_v4 RENAME TO Students;');
          db.run('CREATE INDEX IF NOT EXISTS idx_students_class_sec ON Students(ClassID, SectionID);');
          db.run('CREATE INDEX IF NOT EXISTS idx_students_name ON Students(StudentName);');
          db.run('CREATE UNIQUE INDEX IF NOT EXISTS idx_students_unique_name_class ON Students(StudentName, ClassID);');
          db.run('COMMIT;');
        } catch (err) {
          db.run('ROLLBACK;');
          throw err;
        } finally {
          db.run('PRAGMA foreign_keys = ON;');
        }
      }
      db.run(
        `INSERT INTO SchemaMigrations (Version, AppliedAt, Description) VALUES (4, ?, ?);`,
        [
          new Date().toISOString(),
          'Migrated student uniqueness rule from (StudentName, ClassID, SectionID) to class-level (StudentName, ClassID)',
        ]
      );
    } catch (err) {
      console.warn('Migration v4 warning:', err);
    }
  }

  if (currentVer < 5) {
    try {
      db.run(`
        CREATE TABLE IF NOT EXISTS DailySchedule (
          ID INTEGER PRIMARY KEY AUTOINCREMENT,
          DayOfWeek TEXT NOT NULL,
          ClassID INTEGER NOT NULL,
          SectionID INTEGER NOT NULL,
          LessonNumber INTEGER NOT NULL CHECK(LessonNumber >= 1),
          StartTime TEXT NOT NULL,
          EndTime TEXT NOT NULL,
          CreatedDate TEXT NOT NULL,
          FOREIGN KEY (ClassID) REFERENCES Classes(ID) ON DELETE CASCADE,
          FOREIGN KEY (SectionID) REFERENCES Sections(ID) ON DELETE CASCADE,
          UNIQUE(DayOfWeek, ClassID, SectionID, LessonNumber)
        );
      `);
      db.run(
        'CREATE INDEX IF NOT EXISTS idx_schedule_day_lesson ON DailySchedule(DayOfWeek, LessonNumber, StartTime);'
      );
      db.run(
        'CREATE INDEX IF NOT EXISTS idx_schedule_class_sec ON DailySchedule(ClassID, SectionID);'
      );
      db.run(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_unique_slot ON DailySchedule(DayOfWeek, ClassID, SectionID, LessonNumber);'
      );
      db.run(
        `INSERT INTO SchemaMigrations (Version, AppliedAt, Description) VALUES (5, ?, ?);`,
        [
          new Date().toISOString(),
          'Added DailySchedule table with foreign keys to Classes and Sections, indexes, and unique slot constraint',
        ]
      );
    } catch (err) {
      console.warn('Migration v5 warning:', err);
    }
  }
}

export function seedDevelopmentDemoData(db: Database): void {
  const now = new Date().toISOString();

  db.run('BEGIN TRANSACTION;');
  try {
    // 1. Classes
    db.run(`INSERT OR IGNORE INTO Classes (ID, ClassName, CreatedAt) VALUES (1, 'الثالث المتوسط', ?);`, [now]);
    db.run(`INSERT OR IGNORE INTO Classes (ID, ClassName, CreatedAt) VALUES (2, 'الرابع العلمي', ?);`, [now]);

    // 2. Sections
    db.run(`INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (1, 1, 'أ', ?);`, [now]);
    db.run(`INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (2, 1, 'ب', ?);`, [now]);
    db.run(`INSERT OR IGNORE INTO Sections (ID, ClassID, SectionName, CreatedAt) VALUES (3, 2, 'أ', ?);`, [now]);

    // 3. Students for الثالث المتوسط - أ
    const studentsSec1 = [
      'أحمد علي',
      'محمد حسن',
      'علي كريم',
      'مصطفى حسين',
      'حسن قاسم',
      'حسين محمد',
      'عباس علي',
      'عمر خالد',
    ];
    studentsSec1.forEach((name, idx) => {
      db.run(
        `INSERT OR IGNORE INTO Students (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate) VALUES (?, ?, 1, 1, 1, ?);`,
        [idx + 1, name, now]
      );
    });

    // Students for الثالث المتوسط - ب
    const studentsSec2 = ['يوسف عبد الله', 'زيد محمود', 'إبراهيم طارق'];
    studentsSec2.forEach((name, idx) => {
      db.run(
        `INSERT OR IGNORE INTO Students (ID, StudentName, ClassID, SectionID, IsActive, CreatedDate) VALUES (?, ?, 1, 2, 1, ?);`,
        [idx + 9, name, now]
      );
    });

    // 4. Sample Holidays (10/10/2026 as requested in prompt: "10/10 عيد أو عطلة رسمية")
    // Note: 2026-10-10 is Saturday, let's also add 2026-10-08 (Thursday) and 2026-10-10 so teacher can test both!
    db.run(
      `INSERT OR IGNORE INTO Holidays (HolidayDate, HolidayName, HolidayType, Notes) VALUES ('2026-10-08', 'عطلة رسمية محلية', 'رسمية', 'عطلة رسمية لا تحتسب ضمن أيام الدوام');`
    );
    db.run(
      `INSERT OR IGNORE INTO Holidays (HolidayDate, HolidayName, HolidayType, Notes) VALUES ('2026-10-10', 'مناسبة وطنية / عطلة رسمية', 'مناسبة', 'مثال عطلة 10/10');`
    );

    // 5. Sample DailyRecords, Attendance, Evaluations, DailyTests on working days:
    // Note: In October 2026:
    // 2026-10-01 is Thursday (Working day)
    // 2026-10-02 is Friday (Weekend)
    // 2026-10-03 is Saturday (Weekend)
    // 2026-10-04 is Sunday (Working day)
    // 2026-10-05 is Monday (Working day)
    // 2026-10-06 is Tuesday (Working day)
    const sampleDays: {
      date: string;
      rows: {
        studentId: number;
        att: 'present' | 'absent';
        part: number | null;
        note: number | null;
        hw: number | null;
        beh: number | null;
        test: number | null;
      }[];
    }[] = [
      {
        date: '2026-10-01',
        rows: [
          { studentId: 1, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: 4 },
          { studentId: 2, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: 5 },
          { studentId: 3, att: 'present', part: 0.5, note: 1, hw: 0.5, beh: 1, test: 4 },
          { studentId: 4, att: 'absent', part: null, note: null, hw: null, beh: null, test: null },
          { studentId: 5, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: 5 },
          { studentId: 6, att: 'present', part: 0.5, note: 0.5, hw: 1, beh: 1, test: 4.5 },
          { studentId: 7, att: 'present', part: 0, note: 0.5, hw: 0.5, beh: 0.5, test: 3 },
          { studentId: 8, att: 'present', part: 1, note: 1, hw: 0.5, beh: 1, test: 4 },
        ],
      },
      {
        date: '2026-10-04',
        rows: [
          { studentId: 1, att: 'present', part: 1, note: null, hw: 0.5, beh: 1, test: null },
          { studentId: 2, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: null },
          { studentId: 3, att: 'present', part: 1, note: 0.5, hw: 1, beh: 1, test: null },
          { studentId: 4, att: 'present', part: 0.5, note: 1, hw: 0.5, beh: 1, test: 3.5 },
          { studentId: 5, att: 'present', part: 1, note: null, hw: 1, beh: 1, test: null },
          { studentId: 6, att: 'absent', part: null, note: null, hw: null, beh: null, test: null },
          { studentId: 7, att: 'present', part: 0.5, note: 0.5, hw: 0, beh: 1, test: null },
          { studentId: 8, att: 'present', part: 0.5, note: 1, hw: 1, beh: 1, test: null },
        ],
      },
      {
        date: '2026-10-05',
        rows: [
          { studentId: 1, att: 'present', part: 0.5, note: 1, hw: 1, beh: 1, test: null },
          { studentId: 2, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: null },
          { studentId: 3, att: 'absent', part: null, note: null, hw: null, beh: null, test: null },
          { studentId: 4, att: 'present', part: 1, note: 0.5, hw: 1, beh: 0.5, test: null },
          { studentId: 5, att: 'present', part: 1, note: 1, hw: 1, beh: 1, test: null },
          { studentId: 6, att: 'present', part: 1, note: 1, hw: 0.5, beh: 1, test: null },
          { studentId: 7, att: 'present', part: 0.5, note: 1, hw: 0.5, beh: 0.5, test: null },
          { studentId: 8, att: 'present', part: 1, note: null, hw: 1, beh: 1, test: null },
        ],
      },
    ];

    for (const day of sampleDays) {
      for (const r of day.rows) {
        db.run(
          `INSERT OR IGNORE INTO DailyRecords (StudentID, RecordDate, ClassID, SectionID, UpdatedAt) VALUES (?, ?, 1, 1, ?);`,
          [r.studentId, day.date, now]
        );
        db.run(
          `INSERT OR IGNORE INTO Attendance (StudentID, RecordDate, Status) VALUES (?, ?, ?);`,
          [r.studentId, day.date, r.att]
        );
        db.run(
          `INSERT OR IGNORE INTO Evaluations (StudentID, RecordDate, Participation, Notebook, Homework, Behavior) VALUES (?, ?, ?, ?, ?, ?);`,
          [r.studentId, day.date, r.part, r.note, r.hw, r.beh]
        );
        if (r.test !== null) {
          db.run(
            `INSERT OR IGNORE INTO DailyTests (StudentID, RecordDate, Score) VALUES (?, ?, ?);`,
            [r.studentId, day.date, r.test]
          );
        }
      }
    }

    db.run('COMMIT;');
  } catch (err) {
    db.run('ROLLBACK;');
    throw err;
  }
}

export async function getDatabase(): Promise<Database> {
  if (dbInstance) return dbInstance;

  if (!sqlPromise) {
    sqlPromise = initSqlJs({
      locateFile: () => '/sql-wasm.wasm',
    });
  }

  const SQL = await sqlPromise;
  const savedBytes = await loadBinaryFromIndexedDB();

  if (savedBytes && savedBytes.length > 0) {
    dbInstance = new SQL.Database(savedBytes);
    runMigrations(dbInstance);
    await persistDatabaseToStorage();
  } else {
    dbInstance = new SQL.Database();
    runMigrations(dbInstance);
    seedDevelopmentDemoData(dbInstance);
    await persistDatabaseToStorage();
  }

  return dbInstance;
}

export async function inspectBinaryCounts(bytes: Uint8Array): Promise<{
  classesCount: number;
  sectionsCount: number;
  studentsCount: number;
  recordsCount: number;
  holidaysCount: number;
}> {
  if (!sqlPromise) {
    sqlPromise = initSqlJs({
      locateFile: () => '/sql-wasm.wasm',
    });
  }
  const SQL = await sqlPromise;
  const tempDb = new SQL.Database(bytes);
  try {
    const countTable = (table: string): number => {
      const res = tempDb.exec(`SELECT COUNT(*) FROM ${table};`);
      return res.length > 0 && res[0].values.length > 0 ? Number(res[0].values[0][0]) : 0;
    };
    return {
      classesCount: countTable('Classes'),
      sectionsCount: countTable('Sections'),
      studentsCount: countTable('Students'),
      recordsCount: countTable('DailyRecords'),
      holidaysCount: countTable('Holidays'),
    };
  } finally {
    tempDb.close();
  }
}

export async function replaceDatabaseFromBinary(bytes: Uint8Array): Promise<void> {
  if (!sqlPromise) {
    sqlPromise = initSqlJs({
      locateFile: () => '/sql-wasm.wasm',
    });
  }
  const SQL = await sqlPromise;
  const newDb = new SQL.Database(bytes);
  // Verify it has the Classes & Students tables
  const check = newDb.exec("SELECT name FROM sqlite_master WHERE type='table' AND name='Students';");
  if (check.length === 0) {
    newDb.close();
    throw new Error('ملف النسخة الاحتياطية غير صالح أو لا يحتوي على جداول قاعدة البيانات المطلوبة.');
  }
  const expectedStudentsRes = newDb.exec('SELECT COUNT(*) FROM Students;');
  const expectedStudents =
    expectedStudentsRes.length > 0 && expectedStudentsRes[0].values.length > 0
      ? Number(expectedStudentsRes[0].values[0][0])
      : 0;

  runMigrations(newDb);
  if (dbInstance) {
    try {
      dbInstance.close();
    } catch {
      // ignore close error
    }
  }
  dbInstance = newDb;
  await persistDatabaseToStorage();

  const verifyRes = dbInstance.exec('SELECT COUNT(*) FROM Students;');
  const actualStudents =
    verifyRes.length > 0 && verifyRes[0].values.length > 0
      ? Number(verifyRes[0].values[0][0])
      : 0;
  if (actualStudents !== expectedStudents) {
    throw new Error(
      `فشل التحقق بعد الاستعادة: النسخة تحتوي (${expectedStudents} طالب) والمستعاد (${actualStudents} طالب).`
    );
  }
}

export async function clearAllAndResetDatabase(includeDemoData: boolean): Promise<void> {
  if (!sqlPromise) {
    sqlPromise = initSqlJs({
      locateFile: () => '/sql-wasm.wasm',
    });
  }
  const SQL = await sqlPromise;
  if (dbInstance) {
    try {
      dbInstance.close();
    } catch {
      // ignore
    }
  }
  dbInstance = new SQL.Database();
  runMigrations(dbInstance);
  if (includeDemoData) {
    seedDevelopmentDemoData(dbInstance);
  }
  await persistDatabaseToStorage();
}

export function exportDatabaseBinary(): Uint8Array {
  if (!dbInstance) {
    throw new Error('قاعدة البيانات غير مهيأة بعد.');
  }
  return dbInstance.export();
}
