import {
  AttendanceStatus,
  ClassEntity,
  HolidayEntity,
  RatingValue,
  SavedSessionSummary,
  SectionEntity,
  StudentDailyEntry,
  StudentEntity,
} from '../types';
import { getDatabase, persistDatabaseToStorage } from '../db/sqliteEngine';

export class TrackerRepository {
  static normalizeStudentName(name: string): string {
    return name
      .replace(/[\u200B-\u200D\uFEFF]/g, '')
      .replace(/ـ/g, '')
      .trim()
      .replace(/\s+/g, ' ');
  }

  static canonicalArabicComparisonKey(name: string): string {
    return this.normalizeStudentName(name).replace(/[\u064B-\u065F\u0670]/g, '');
  }

  // ---------------- CLASSES ----------------
  static async getClasses(): Promise<ClassEntity[]> {
    const db = await getDatabase();
    const res = db.exec('SELECT ID, ClassName, CreatedAt FROM Classes ORDER BY ID ASC;');
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      className: String(row[1]),
      createdAt: String(row[2]),
    }));
  }

  static async addClass(className: string): Promise<number> {
    const trimmed = className.trim();
    if (!trimmed) throw new Error('اسم الصف مطلوب');
    const db = await getDatabase();
    const now = new Date().toISOString();
    db.run('INSERT INTO Classes (ClassName, CreatedAt) VALUES (?, ?);', [trimmed, now]);
    const idRes = db.exec('SELECT last_insert_rowid();');
    const newId = Number(idRes[0].values[0][0]);
    await persistDatabaseToStorage();
    return newId;
  }

  static async updateClass(id: number, className: string): Promise<void> {
    const trimmed = className.trim();
    if (!trimmed) throw new Error('اسم الصف مطلوب');
    const db = await getDatabase();
    db.run('UPDATE Classes SET ClassName = ? WHERE ID = ?;', [trimmed, id]);
    await persistDatabaseToStorage();
  }

  static async deleteClass(id: number): Promise<void> {
    const db = await getDatabase();
    db.run('PRAGMA foreign_keys = ON;');
    db.run('DELETE FROM Classes WHERE ID = ?;', [id]);
    await persistDatabaseToStorage();
  }

  // ---------------- SECTIONS ----------------
  static async getSections(classId?: number): Promise<SectionEntity[]> {
    const db = await getDatabase();
    const res = classId
      ? db.exec(
          'SELECT ID, ClassID, SectionName, CreatedAt FROM Sections WHERE ClassID = ? ORDER BY ID ASC;',
          [classId]
        )
      : db.exec('SELECT ID, ClassID, SectionName, CreatedAt FROM Sections ORDER BY ClassID ASC, ID ASC;');
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      classId: Number(row[1]),
      sectionName: String(row[2]),
      createdAt: String(row[3]),
    }));
  }

  static async addSection(classId: number, sectionName: string): Promise<number> {
    const trimmed = sectionName.trim();
    if (!trimmed) throw new Error('اسم الشعبة مطلوب');
    const db = await getDatabase();
    const now = new Date().toISOString();
    db.run('INSERT INTO Sections (ClassID, SectionName, CreatedAt) VALUES (?, ?, ?);', [
      classId,
      trimmed,
      now,
    ]);
    const idRes = db.exec('SELECT last_insert_rowid();');
    const newId = Number(idRes[0].values[0][0]);
    await persistDatabaseToStorage();
    return newId;
  }

  static async updateSection(id: number, sectionName: string): Promise<void> {
    const trimmed = sectionName.trim();
    if (!trimmed) throw new Error('اسم الشعبة مطلوب');
    const db = await getDatabase();
    db.run('UPDATE Sections SET SectionName = ? WHERE ID = ?;', [trimmed, id]);
    await persistDatabaseToStorage();
  }

  static async deleteSection(id: number): Promise<void> {
    const db = await getDatabase();
    db.run('PRAGMA foreign_keys = ON;');
    db.run('DELETE FROM Sections WHERE ID = ?;', [id]);
    await persistDatabaseToStorage();
  }

  // ---------------- STUDENTS ----------------
  static async getStudents(
    classId?: number,
    sectionId?: number,
    includeInactive = false
  ): Promise<StudentEntity[]> {
    const db = await getDatabase();
    let query = `
      SELECT
        st.ID,
        st.StudentName,
        st.ClassID,
        c.ClassName,
        st.SectionID,
        s.SectionName,
        st.IsActive,
        st.CreatedDate
      FROM Students st
      JOIN Classes c ON c.ID = st.ClassID
      JOIN Sections s ON s.ID = st.SectionID
      WHERE 1=1
    `;
    if (!includeInactive) {
      query += ' AND st.IsActive = 1';
    }
    const params: (number | string)[] = [];

    if (classId !== undefined) {
      query += ' AND st.ClassID = ?';
      params.push(classId);
    }
    if (sectionId !== undefined) {
      query += ' AND st.SectionID = ?';
      params.push(sectionId);
    }
    query += ' ORDER BY st.ID ASC;';

    const res = db.exec(query, params);
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      studentName: String(row[1]),
      classId: Number(row[2]),
      className: String(row[3]),
      sectionId: Number(row[4]),
      sectionName: String(row[5]),
      isActive: Number(row[6]) === 1,
      createdDate: String(row[7]),
    }));
  }

  static async searchStudents(
    searchText: string,
    classId?: number | 'ALL',
    sectionId?: number | 'ALL',
    includeInactive = false
  ): Promise<StudentEntity[]> {
    const db = await getDatabase();
    const cleaned = searchText.trim();
    let query = `
      SELECT
        st.ID,
        st.StudentName,
        st.ClassID,
        c.ClassName,
        st.SectionID,
        s.SectionName,
        st.IsActive,
        st.CreatedDate
      FROM Students st
      JOIN Classes c ON c.ID = st.ClassID
      JOIN Sections s ON s.ID = st.SectionID
      WHERE 1=1
    `;
    const params: (number | string)[] = [];

    if (!includeInactive) {
      query += ' AND st.IsActive = 1';
    }
    if (cleaned) {
      query += ' AND st.StudentName LIKE ?';
      params.push(`%${cleaned}%`);
    }
    if (classId !== undefined && classId !== 'ALL') {
      query += ' AND st.ClassID = ?';
      params.push(classId);
    }
    if (sectionId !== undefined && sectionId !== 'ALL') {
      query += ' AND st.SectionID = ?';
      params.push(sectionId);
    }
    query += ' ORDER BY st.StudentName ASC LIMIT 100;';

    const res = db.exec(query, params);
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      studentName: String(row[1]),
      classId: Number(row[2]),
      className: String(row[3]),
      sectionId: Number(row[4]),
      sectionName: String(row[5]),
      isActive: Number(row[6]) === 1,
      createdDate: String(row[7]),
    }));
  }

  static async addStudent(
    studentName: string,
    classId: number,
    sectionId: number
  ): Promise<{ id: number; isDuplicate: boolean }> {
    const trimmed = this.normalizeStudentName(studentName);
    if (!trimmed) throw new Error('اسم الطالب مطلوب');
    const targetKey = this.canonicalArabicComparisonKey(trimmed);

    const db = await getDatabase();
    // Check duplicate across the ENTIRE ClassID (regardless of SectionID)
    const existing = db.exec(
      'SELECT ID, StudentName, SectionID, IsActive FROM Students WHERE ClassID = ?;',
      [classId]
    );
    if (existing.length > 0 && existing[0].values.length > 0) {
      for (const row of existing[0].values) {
        const existingId = Number(row[0]);
        const existingName = String(row[1] || '');
        const existingSecId = Number(row[2]);
        const isAct = Number(row[3]) === 1;
        if (this.canonicalArabicComparisonKey(existingName) === targetKey) {
          if (!isAct && existingSecId === sectionId) {
            db.run('UPDATE Students SET IsActive = 1, StudentName = ? WHERE ID = ?;', [
              trimmed,
              existingId,
            ]);
            await persistDatabaseToStorage();
            return { id: existingId, isDuplicate: false };
          }
          return { id: existingId, isDuplicate: true };
        }
      }
    }

    const now = new Date().toISOString();
    db.run(
      'INSERT INTO Students (StudentName, ClassID, SectionID, IsActive, CreatedDate) VALUES (?, ?, ?, 1, ?);',
      [trimmed, classId, sectionId, now]
    );
    const idRes = db.exec('SELECT last_insert_rowid();');
    const newId = Number(idRes[0].values[0][0]);
    await persistDatabaseToStorage();
    return { id: newId, isDuplicate: false };
  }

  static async updateStudentName(
    studentId: number,
    newName: string
  ): Promise<{ updated: boolean; isDuplicate: boolean }> {
    const trimmed = this.normalizeStudentName(newName);
    if (!trimmed) throw new Error('اسم الطالب مطلوب');
    const targetKey = this.canonicalArabicComparisonKey(trimmed);

    const db = await getDatabase();
    const stuRes = db.exec('SELECT ClassID FROM Students WHERE ID = ?;', [studentId]);
    if (stuRes.length === 0 || stuRes[0].values.length === 0) {
      return { updated: false, isDuplicate: false };
    }
    const classId = Number(stuRes[0].values[0][0]);

    const others = db.exec(
      'SELECT ID, StudentName FROM Students WHERE ClassID = ? AND ID != ?;',
      [classId, studentId]
    );
    if (others.length > 0 && others[0].values.length > 0) {
      for (const row of others[0].values) {
        const otherName = String(row[1] || '');
        if (this.canonicalArabicComparisonKey(otherName) === targetKey) {
          return { updated: false, isDuplicate: true };
        }
      }
    }

    db.run('UPDATE Students SET StudentName = ? WHERE ID = ?;', [trimmed, studentId]);
    await persistDatabaseToStorage();
    return { updated: true, isDuplicate: false };
  }

  static async toggleStudentActiveStatus(
    studentId: number,
    isActive: boolean
  ): Promise<void> {
    const db = await getDatabase();
    db.run('UPDATE Students SET IsActive = ? WHERE ID = ?;', [
      isActive ? 1 : 0,
      studentId,
    ]);
    await persistDatabaseToStorage();
  }

  static async transferStudentSection(
    studentId: number,
    targetClassId: number,
    targetSectionId: number
  ): Promise<{ transferred: boolean; isDuplicate: boolean }> {
    const db = await getDatabase();
    const stuRes = db.exec('SELECT StudentName FROM Students WHERE ID = ?;', [studentId]);
    if (stuRes.length === 0 || stuRes[0].values.length === 0) {
      return { transferred: false, isDuplicate: false };
    }
    const currentName = String(stuRes[0].values[0][0] || '');
    const targetKey = this.canonicalArabicComparisonKey(currentName);

    const others = db.exec(
      'SELECT ID, StudentName FROM Students WHERE ClassID = ? AND ID != ?;',
      [targetClassId, studentId]
    );
    if (others.length > 0 && others[0].values.length > 0) {
      for (const row of others[0].values) {
        const otherName = String(row[1] || '');
        if (this.canonicalArabicComparisonKey(otherName) === targetKey) {
          return { transferred: false, isDuplicate: true };
        }
      }
    }

    db.run(
      'UPDATE Students SET ClassID = ?, SectionID = ? WHERE ID = ?;',
      [targetClassId, targetSectionId, studentId]
    );
    db.run(
      'UPDATE DailyRecords SET ClassID = ?, SectionID = ? WHERE StudentID = ?;',
      [targetClassId, targetSectionId, studentId]
    );
    await persistDatabaseToStorage();
    return { transferred: true, isDuplicate: false };
  }

  static async deleteStudent(studentId: number): Promise<void> {
    const db = await getDatabase();
    db.run('PRAGMA foreign_keys = ON;');
    db.run('DELETE FROM Attendance WHERE StudentID = ?;', [studentId]);
    db.run('DELETE FROM Evaluations WHERE StudentID = ?;', [studentId]);
    db.run('DELETE FROM DailyTests WHERE StudentID = ?;', [studentId]);
    db.run('DELETE FROM DailyRecords WHERE StudentID = ?;', [studentId]);
    db.run('DELETE FROM Students WHERE ID = ?;', [studentId]);
    await persistDatabaseToStorage();
  }

  // ---------------- HOLIDAYS ----------------
  static async getHolidays(): Promise<HolidayEntity[]> {
    const db = await getDatabase();
    const res = db.exec(
      'SELECT ID, HolidayDate, HolidayName, HolidayType, Notes FROM Holidays ORDER BY HolidayDate ASC;'
    );
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      holidayDate: String(row[1]),
      holidayName: String(row[2]),
      holidayType: String(row[3]),
      notes: String(row[4] || ''),
    }));
  }

  static async addHoliday(
    holidayDate: string,
    holidayName: string,
    holidayType: string,
    notes: string
  ): Promise<void> {
    const db = await getDatabase();
    db.run(
      `INSERT OR REPLACE INTO Holidays (HolidayDate, HolidayName, HolidayType, Notes) VALUES (?, ?, ?, ?);`,
      [holidayDate.trim(), holidayName.trim(), holidayType.trim(), notes.trim()]
    );
    await persistDatabaseToStorage();
  }

  static async updateHoliday(
    id: number,
    holidayDate: string,
    holidayName: string,
    holidayType: string,
    notes: string
  ): Promise<void> {
    const db = await getDatabase();
    db.run(
      `UPDATE Holidays SET HolidayDate = ?, HolidayName = ?, HolidayType = ?, Notes = ? WHERE ID = ?;`,
      [holidayDate.trim(), holidayName.trim(), holidayType.trim(), notes.trim(), id]
    );
    await persistDatabaseToStorage();
  }

  static async deleteHoliday(id: number): Promise<void> {
    const db = await getDatabase();
    db.run('DELETE FROM Holidays WHERE ID = ?;', [id]);
    await persistDatabaseToStorage();
  }

  // ---------------- DAILY RECORDS, ATTENDANCE, EVALUATIONS, DAILY TESTS ----------------
  static async hasSavedSession(
    classId: number,
    sectionId: number,
    recordDate: string
  ): Promise<boolean> {
    const db = await getDatabase();
    const res = db.exec(
      'SELECT COUNT(*) FROM DailyRecords WHERE ClassID = ? AND SectionID = ? AND RecordDate = ?;',
      [classId, sectionId, recordDate]
    );
    return res.length > 0 && Number(res[0].values[0][0]) > 0;
  }

  static async getSessionEntries(
    classId: number,
    sectionId: number,
    recordDate: string
  ): Promise<Record<number, StudentDailyEntry>> {
    const db = await getDatabase();
    const res = db.exec(
      `
      SELECT
        dr.StudentID,
        a.Status,
        e.Participation,
        e.Notebook,
        e.Homework,
        e.Behavior,
        dt.Score
      FROM DailyRecords dr
      LEFT JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
      LEFT JOIN Evaluations e ON e.StudentID = dr.StudentID AND e.RecordDate = dr.RecordDate
      LEFT JOIN DailyTests dt ON dt.StudentID = dr.StudentID AND dt.RecordDate = dr.RecordDate
      WHERE dr.ClassID = ? AND dr.SectionID = ? AND dr.RecordDate = ?;
      `,
      [classId, sectionId, recordDate]
    );

    const map: Record<number, StudentDailyEntry> = {};
    if (res.length === 0) return map;

    for (const row of res[0].values) {
      const studentId = Number(row[0]);
      const status = (row[1] as AttendanceStatus) || 'present';
      const toRating = (v: unknown): RatingValue =>
        v === 1 || v === 0.5 || v === 0 ? (v as RatingValue) : null;

      map[studentId] = {
        studentId,
        attendance: status,
        participation: toRating(row[2]),
        notebook: toRating(row[3]),
        homework: toRating(row[4]),
        behavior: toRating(row[5]),
        dailyTestScore: row[6] !== null && row[6] !== undefined ? Number(row[6]) : null,
      };
    }

    return map;
  }

  /**
   * Saves a daily session into DailyRecords, Attendance, Evaluations, and DailyTests.
   * Uses UNIQUE(StudentID, RecordDate) so each student+date is independent and never overwrites previous dates.
   */
  static async saveDailySession(
    classId: number,
    sectionId: number,
    recordDate: string,
    entries: Record<number, StudentDailyEntry>
  ): Promise<void> {
    const db = await getDatabase();
    const now = new Date().toISOString();

    db.run('BEGIN TRANSACTION;');
    try {
      for (const studentIdStr of Object.keys(entries)) {
        const studentId = Number(studentIdStr);
        const entry = entries[studentId];
        if (!entry) continue;

        // 1. Upsert DailyRecords
        db.run(
          `INSERT INTO DailyRecords (StudentID, RecordDate, ClassID, SectionID, IsSaved, CreatedDate, ModifiedDate, UpdatedAt)
           VALUES (?, ?, ?, ?, 1, ?, ?, ?)
           ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
             ClassID = excluded.ClassID,
             SectionID = excluded.SectionID,
             IsSaved = 1,
             ModifiedDate = excluded.ModifiedDate,
             UpdatedAt = excluded.UpdatedAt;`,
          [studentId, recordDate, classId, sectionId, now, now, now]
        );

        // 2. Upsert Attendance
        db.run(
          `INSERT INTO Attendance (StudentID, RecordDate, Status)
           VALUES (?, ?, ?)
           ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
             Status = excluded.Status;`,
          [studentId, recordDate, entry.attendance]
        );

        // 3. Upsert Evaluations
        db.run(
          `INSERT INTO Evaluations (StudentID, RecordDate, Participation, Notebook, Homework, Behavior)
           VALUES (?, ?, ?, ?, ?, ?)
           ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
             Participation = excluded.Participation,
             Notebook = excluded.Notebook,
             Homework = excluded.Homework,
             Behavior = excluded.Behavior;`,
          [
            studentId,
            recordDate,
            entry.participation,
            entry.notebook,
            entry.homework,
            entry.behavior,
          ]
        );

        // 4. Upsert or delete DailyTests
        if (entry.dailyTestScore !== null && entry.dailyTestScore !== undefined) {
          const clamped = Math.min(5, Math.max(0, Number(entry.dailyTestScore)));
          db.run(
            `INSERT INTO DailyTests (StudentID, RecordDate, Score, TestValue)
             VALUES (?, ?, ?, ?)
             ON CONFLICT(StudentID, RecordDate) DO UPDATE SET
               Score = excluded.Score,
               TestValue = excluded.TestValue;`,
            [studentId, recordDate, clamped, clamped]
          );
        } else {
          db.run(`DELETE FROM DailyTests WHERE StudentID = ? AND RecordDate = ?;`, [
            studentId,
            recordDate,
          ]);
        }
      }

      db.run('COMMIT;');
      await persistDatabaseToStorage();
    } catch (err) {
      db.run('ROLLBACK;');
      throw err;
    }
  }

  static async getSavedSessionsSummary(
    classId?: number,
    sectionId?: number
  ): Promise<SavedSessionSummary[]> {
    const db = await getDatabase();
    let whereClause = 'WHERE 1=1';
    const params: number[] = [];
    if (classId !== undefined) {
      whereClause += ' AND dr.ClassID = ?';
      params.push(classId);
    }
    if (sectionId !== undefined) {
      whereClause += ' AND dr.SectionID = ?';
      params.push(sectionId);
    }

    const res = db.exec(
      `
      SELECT
        dr.RecordDate,
        dr.ClassID,
        c.ClassName,
        dr.SectionID,
        s.SectionName,
        COUNT(DISTINCT dr.StudentID) as StudentCount,
        SUM(CASE WHEN a.Status = 'present' THEN 1 ELSE 0 END) as PresentCount,
        SUM(CASE WHEN a.Status = 'absent' THEN 1 ELSE 0 END) as AbsentCount,
        SUM(
          (CASE WHEN e.Participation IS NOT NULL THEN 1 ELSE 0 END) +
          (CASE WHEN e.Notebook IS NOT NULL THEN 1 ELSE 0 END) +
          (CASE WHEN e.Homework IS NOT NULL THEN 1 ELSE 0 END) +
          (CASE WHEN e.Behavior IS NOT NULL THEN 1 ELSE 0 END)
        ) as EvalCount,
        COUNT(dt.Score) as TestsCount,
        MAX(dr.UpdatedAt) as LastUpdated
      FROM DailyRecords dr
      JOIN Classes c ON c.ID = dr.ClassID
      JOIN Sections s ON s.ID = dr.SectionID
      LEFT JOIN Attendance a ON a.StudentID = dr.StudentID AND a.RecordDate = dr.RecordDate
      LEFT JOIN Evaluations e ON e.StudentID = dr.StudentID AND e.RecordDate = dr.RecordDate
      LEFT JOIN DailyTests dt ON dt.StudentID = dr.StudentID AND dt.RecordDate = dr.RecordDate
      ${whereClause}
      GROUP BY dr.RecordDate, dr.ClassID, dr.SectionID
      ORDER BY dr.RecordDate DESC, dr.ClassID ASC, dr.SectionID ASC;
      `,
      params
    );

    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      recordDate: String(row[0]),
      classId: Number(row[1]),
      className: String(row[2]),
      sectionId: Number(row[3]),
      sectionName: String(row[4]),
      studentCount: Number(row[5]),
      presentCount: Number(row[6]),
      absentCount: Number(row[7]),
      evaluationsCount: Number(row[8]),
      testsCount: Number(row[9]),
      updatedAt: String(row[10]),
    }));
  }

  static async deleteSavedSession(
    classId: number,
    sectionId: number,
    recordDate: string
  ): Promise<void> {
    const db = await getDatabase();
    db.run('BEGIN TRANSACTION;');
    try {
      const studentsRes = db.exec(
        'SELECT StudentID FROM DailyRecords WHERE ClassID = ? AND SectionID = ? AND RecordDate = ?;',
        [classId, sectionId, recordDate]
      );
      if (studentsRes.length > 0) {
        for (const r of studentsRes[0].values) {
          const stId = Number(r[0]);
          db.run('DELETE FROM Attendance WHERE StudentID = ? AND RecordDate = ?;', [
            stId,
            recordDate,
          ]);
          db.run('DELETE FROM Evaluations WHERE StudentID = ? AND RecordDate = ?;', [
            stId,
            recordDate,
          ]);
          db.run('DELETE FROM DailyTests WHERE StudentID = ? AND RecordDate = ?;', [
            stId,
            recordDate,
          ]);
        }
      }
      db.run(
        'DELETE FROM DailyRecords WHERE ClassID = ? AND SectionID = ? AND RecordDate = ?;',
        [classId, sectionId, recordDate]
      );
      db.run('COMMIT;');
      await persistDatabaseToStorage();
    } catch (e) {
      db.run('ROLLBACK;');
      throw e;
    }
  }

  /**
   * Fetches all raw historical records for a set of students to feed into GradingService.
   */
  static async getRawStudentHistory(
    classId?: number,
    sectionId?: number,
    monthFilter?: string // e.g. '2026-10' or 'ALL'
  ): Promise<{
    students: {
      id: number;
      studentName: string;
      classId: number;
      className: string;
      sectionId: number;
      sectionName: string;
    }[];
    attendanceRows: { studentId: number; date: string; status: AttendanceStatus }[];
    evaluationRows: {
      studentId: number;
      date: string;
      participation: RatingValue;
      notebook: RatingValue;
      homework: RatingValue;
      behavior: RatingValue;
    }[];
    testRows: { studentId: number; date: string; score: number }[];
    holidays: Set<string>;
  }> {
    const db = await getDatabase();

    let stQuery = `
      SELECT st.ID, st.StudentName, st.ClassID, c.ClassName, st.SectionID, s.SectionName
      FROM Students st
      JOIN Classes c ON c.ID = st.ClassID
      JOIN Sections s ON s.ID = st.SectionID
      WHERE st.IsActive = 1
    `;
    const stParams: number[] = [];
    if (classId !== undefined) {
      stQuery += ' AND st.ClassID = ?';
      stParams.push(classId);
    }
    if (sectionId !== undefined) {
      stQuery += ' AND st.SectionID = ?';
      stParams.push(sectionId);
    }
    stQuery += ' ORDER BY st.ID ASC;';

    const stRes = db.exec(stQuery, stParams);
    const students =
      stRes.length > 0
        ? stRes[0].values.map((r) => ({
            id: Number(r[0]),
            studentName: String(r[1]),
            classId: Number(r[2]),
            className: String(r[3]),
            sectionId: Number(r[4]),
            sectionName: String(r[5]),
          }))
        : [];

    const dateFilterSql =
      monthFilter && monthFilter !== 'ALL' ? `WHERE RecordDate LIKE '${monthFilter}%'` : '';

    const attRes = db.exec(
      `SELECT StudentID, RecordDate, Status FROM Attendance ${dateFilterSql} ORDER BY RecordDate ASC;`
    );
    const attendanceRows =
      attRes.length > 0
        ? attRes[0].values.map((r) => ({
            studentId: Number(r[0]),
            date: String(r[1]),
            status: (r[2] as AttendanceStatus) || 'present',
          }))
        : [];

    const toRating = (v: unknown): RatingValue =>
      v === 1 || v === 0.5 || v === 0 ? (v as RatingValue) : null;

    const evalRes = db.exec(
      `SELECT StudentID, RecordDate, Participation, Notebook, Homework, Behavior FROM Evaluations ${dateFilterSql} ORDER BY RecordDate ASC;`
    );
    const evaluationRows =
      evalRes.length > 0
        ? evalRes[0].values.map((r) => ({
            studentId: Number(r[0]),
            date: String(r[1]),
            participation: toRating(r[2]),
            notebook: toRating(r[3]),
            homework: toRating(r[4]),
            behavior: toRating(r[5]),
          }))
        : [];

    const testRes = db.exec(
      `SELECT StudentID, RecordDate, Score FROM DailyTests ${dateFilterSql} ORDER BY RecordDate ASC;`
    );
    const testRows =
      testRes.length > 0
        ? testRes[0].values.map((r) => ({
            studentId: Number(r[0]),
            date: String(r[1]),
            score: Number(r[2]),
          }))
        : [];

    const holRes = db.exec('SELECT HolidayDate FROM Holidays;');
    const holidays = new Set<string>();
    if (holRes.length > 0) {
      for (const r of holRes[0].values) {
        holidays.add(String(r[0]));
      }
    }

    return {
      students,
      attendanceRows,
      evaluationRows,
      testRows,
      holidays,
    };
  }

  static async getDatabaseStats(): Promise<{
    classesCount: number;
    sectionsCount: number;
    studentsCount: number;
    recordsCount: number;
    holidaysCount: number;
  }> {
    const db = await getDatabase();
    const countTable = (tbl: string) => {
      const r = db.exec(`SELECT COUNT(*) FROM ${tbl};`);
      return r.length > 0 ? Number(r[0].values[0][0]) : 0;
    };

    return {
      classesCount: countTable('Classes'),
      sectionsCount: countTable('Sections'),
      studentsCount: countTable('Students'),
      recordsCount: countTable('DailyRecords'),
      holidaysCount: countTable('Holidays'),
    };
  }
}
