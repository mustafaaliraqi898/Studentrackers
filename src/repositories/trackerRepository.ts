import {
  AttendanceStatus,
  ClassEntity,
  DailyPlanEntity,
  DailyPlanImageEntity,
  DailyScheduleEntity,
  HolidayEntity,
  PeriodGradeEntity,
  RatingValue,
  SavedSessionSummary,
  SectionEntity,
  StudentDailyEntry,
  StudentEntity,
  StudyPeriodEntity,
  StudyPeriodStatus,
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
    return this.normalizeStudentName(name)
      .replace(/[\u064B-\u065F\u0670]/g, '')
      .replace(/[أإآٱ]/g, 'ا')
      .replace(/ة/g, 'ه')
      .replace(/ى/g, 'ي')
      .replace(/ؤ/g, 'و')
      .replace(/ئ/g, 'ي')
      .toLowerCase();
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
    monthFilter?: string, // e.g. '2026-10', 'ALL', or 'PERIOD:<id>'
    startDateInclusive?: string | null,
    endDateInclusive?: string | null
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

    let effectiveStart = startDateInclusive || null;
    let effectiveEnd = endDateInclusive || null;
    let effectiveMonth = monthFilter;

    if (monthFilter && monthFilter.startsWith('PERIOD:')) {
      const pid = Number(monthFilter.replace('PERIOD:', ''));
      const allPeriods = await this.getStudyPeriods();
      const period = allPeriods.find((p) => p.id === pid);
      if (period) {
        effectiveStart = period.startDate;
        effectiveEnd = period.endDate;
        effectiveMonth = undefined;
      }
    }

    const whereClauses: string[] = [];
    const whereParams: string[] = [];
    if (effectiveStart) {
      whereClauses.push('RecordDate >= ?');
      whereParams.push(effectiveStart);
    }
    if (effectiveEnd) {
      whereClauses.push('RecordDate <= ?');
      whereParams.push(effectiveEnd);
    }
    if (effectiveMonth && effectiveMonth !== 'ALL') {
      whereClauses.push('RecordDate LIKE ?');
      whereParams.push(`${effectiveMonth}%`);
    }
    const dateFilterSql =
      whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

    const attRes = db.exec(
      `SELECT StudentID, RecordDate, Status FROM Attendance ${dateFilterSql} ORDER BY RecordDate ASC;`,
      whereParams
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
      `SELECT StudentID, RecordDate, Participation, Notebook, Homework, Behavior FROM Evaluations ${dateFilterSql} ORDER BY RecordDate ASC;`,
      whereParams
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
      `SELECT StudentID, RecordDate, Score FROM DailyTests ${dateFilterSql} ORDER BY RecordDate ASC;`,
      whereParams
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

  // ---------------- DAILY SCHEDULE ("الجدول اليومي") ----------------
  static async getDailySchedules(): Promise<DailyScheduleEntity[]> {
    const db = await getDatabase();
    const res = db.exec(`
      SELECT
        ds.ID,
        ds.DayOfWeek,
        ds.ClassID,
        c.ClassName,
        ds.SectionID,
        s.SectionName,
        ds.LessonNumber,
        ds.StartTime,
        ds.EndTime,
        ds.CreatedDate
      FROM DailySchedule ds
      JOIN Classes c ON c.ID = ds.ClassID
      JOIN Sections s ON s.ID = ds.SectionID
      ORDER BY
        CASE ds.DayOfWeek
          WHEN 'الأحد' THEN 1
          WHEN 'الاثنين' THEN 2
          WHEN 'الثلاثاء' THEN 3
          WHEN 'الأربعاء' THEN 4
          WHEN 'الخميس' THEN 5
          WHEN 'الجمعة' THEN 6
          WHEN 'السبت' THEN 7
          ELSE 8
        END ASC,
        ds.LessonNumber ASC,
        ds.StartTime ASC,
        ds.ID ASC;
    `);
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      dayOfWeek: String(row[1]),
      classId: Number(row[2]),
      className: String(row[3]),
      sectionId: Number(row[4]),
      sectionName: String(row[5]),
      lessonNumber: Number(row[6]),
      startTime: String(row[7]),
      endTime: String(row[8]),
      createdDate: String(row[9] || ''),
    }));
  }

  static async isDuplicateDailyScheduleSlot(
    dayOfWeek: string,
    classId: number,
    sectionId: number,
    lessonNumber: number,
    excludeId?: number
  ): Promise<boolean> {
    const db = await getDatabase();
    const res =
      excludeId !== undefined
        ? db.exec(
            `SELECT COUNT(*) FROM DailySchedule WHERE DayOfWeek = ? AND ClassID = ? AND SectionID = ? AND LessonNumber = ? AND ID != ?;`,
            [dayOfWeek, classId, sectionId, lessonNumber, excludeId]
          )
        : db.exec(
            `SELECT COUNT(*) FROM DailySchedule WHERE DayOfWeek = ? AND ClassID = ? AND SectionID = ? AND LessonNumber = ?;`,
            [dayOfWeek, classId, sectionId, lessonNumber]
          );
    return res.length > 0 && Number(res[0].values[0][0]) > 0;
  }

  static async addDailyScheduleEntry(
    dayOfWeek: string,
    classId: number,
    sectionId: number,
    lessonNumber: number,
    startTime: string,
    endTime: string
  ): Promise<{ id: number; isDuplicate: boolean }> {
    const isDup = await this.isDuplicateDailyScheduleSlot(
      dayOfWeek,
      classId,
      sectionId,
      lessonNumber
    );
    if (isDup) {
      return { id: -1, isDuplicate: true };
    }
    const db = await getDatabase();
    const now = new Date().toISOString();
    db.run(
      `INSERT INTO DailySchedule (DayOfWeek, ClassID, SectionID, LessonNumber, StartTime, EndTime, CreatedDate)
       VALUES (?, ?, ?, ?, ?, ?, ?);`,
      [dayOfWeek.trim(), classId, sectionId, lessonNumber, startTime.trim(), endTime.trim(), now]
    );
    const idRes = db.exec('SELECT last_insert_rowid();');
    await persistDatabaseToStorage();
    return {
      id: idRes.length > 0 ? Number(idRes[0].values[0][0]) : -1,
      isDuplicate: false,
    };
  }

  static async updateDailyScheduleEntry(
    id: number,
    dayOfWeek: string,
    classId: number,
    sectionId: number,
    lessonNumber: number,
    startTime: string,
    endTime: string
  ): Promise<{ isDuplicate: boolean }> {
    const isDup = await this.isDuplicateDailyScheduleSlot(
      dayOfWeek,
      classId,
      sectionId,
      lessonNumber,
      id
    );
    if (isDup) {
      return { isDuplicate: true };
    }
    const db = await getDatabase();
    db.run(
      `UPDATE DailySchedule
       SET DayOfWeek = ?, ClassID = ?, SectionID = ?, LessonNumber = ?, StartTime = ?, EndTime = ?
       WHERE ID = ?;`,
      [dayOfWeek.trim(), classId, sectionId, lessonNumber, startTime.trim(), endTime.trim(), id]
    );
    await persistDatabaseToStorage();
    return { isDuplicate: false };
  }

  static async deleteDailyScheduleEntry(id: number): Promise<void> {
    const db = await getDatabase();
    db.run('DELETE FROM DailySchedule WHERE ID = ?;', [id]);
    await persistDatabaseToStorage();
  }

  // ---------------- STUDY PERIODS ("الفترات الدراسية") ----------------
  static async getStudyPeriods(): Promise<StudyPeriodEntity[]> {
    const db = await getDatabase();
    const res = db.exec(`
      SELECT ID, PeriodName, StartDate, EndDate, Status, CreatedDate
      FROM StudyPeriods
      ORDER BY StartDate ASC, ID ASC;
    `);
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      periodName: String(row[1] || ''),
      startDate: String(row[2] || ''),
      endDate: row[3] !== null && String(row[3]).trim() !== '' ? String(row[3]) : null,
      status: (String(row[4]) === 'CLOSED' ? 'CLOSED' : 'OPEN') as StudyPeriodStatus,
      createdDate: String(row[5] || ''),
    }));
  }

  static async getOpenStudyPeriod(): Promise<StudyPeriodEntity | null> {
    const list = await this.getStudyPeriods();
    return list.find((p) => p.status === 'OPEN') || null;
  }

  static async getSuggestedNextPeriodName(): Promise<string> {
    const list = await this.getStudyPeriods();
    const nextNum = list.length + 1;
    switch (nextNum) {
      case 1:
        return 'الفترة الأولى';
      case 2:
        return 'الفترة الثانية';
      case 3:
        return 'الفترة الثالثة';
      case 4:
        return 'الفترة الرابعة';
      case 5:
        return 'الفترة الخامسة';
      case 6:
        return 'الفترة السادسة';
      case 7:
        return 'الفترة السابعة';
      case 8:
        return 'الفترة الثامنة';
      case 9:
        return 'الفترة التاسعة';
      case 10:
        return 'الفترة العاشرة';
      default:
        return `الفترة ${nextNum}`;
    }
  }

  private static formatIsoToDisplay(isoDate: string): string {
    const parts = isoDate.trim().split('-');
    if (parts.length !== 3) return isoDate;
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }

  static async validateStudyPeriodDates(
    startDate: string,
    endDate: string | null,
    excludeId?: number
  ): Promise<string | null> {
    if (!startDate.trim()) {
      return 'يرجى تحديد تاريخ بداية الفترة';
    }
    if (endDate !== null) {
      if (!endDate.trim()) {
        return 'يرجى تحديد تاريخ نهاية الفترة';
      }
      if (endDate < startDate) {
        return 'لا يسمح بأن يكون تاريخ النهاية قبل تاريخ البداية';
      }
    }

    const all = await this.getStudyPeriods();
    const others = all.filter((p) => p.id !== excludeId);
    for (const other of others) {
      const otherStart = other.startDate;
      const otherEnd = other.endDate;
      if (otherEnd !== null) {
        if (endDate === null) {
          if (startDate <= otherEnd) {
            return `تاريخ البداية غير منطقي بالنسبة للفترات السابقة؛ يجب أن يكون بعد تاريخ نهاية «${other.periodName}» (${this.formatIsoToDisplay(
              otherEnd
            )})`;
          }
        } else {
          const overlaps = startDate <= otherEnd && endDate >= otherStart;
          if (overlaps) {
            return `التواريخ المحددة تتداخل مع «${other.periodName}» (${this.formatIsoToDisplay(
              otherStart
            )} → ${this.formatIsoToDisplay(otherEnd)})`;
          }
        }
      } else {
        if (endDate !== null && endDate >= otherStart) {
          return `تاريخ النهاية يتداخل مع بداية الفترة المفتوحة «${other.periodName}» (${this.formatIsoToDisplay(
            otherStart
          )})`;
        }
      }
    }
    return null;
  }

  static async startNewStudyPeriod(
    periodName: string,
    startDate: string
  ): Promise<{ id: number; error: string | null }> {
    const openPeriod = await this.getOpenStudyPeriod();
    if (openPeriod) {
      return {
        id: -1,
        error: 'توجد فترة دراسية مفتوحة حالياً. يجب إنهاؤها أولاً قبل بدء فترة جديدة.',
      };
    }

    const suggested = await this.getSuggestedNextPeriodName();
    const cleanName = periodName.trim() || suggested;
    const cleanStart = startDate.trim();
    const dateErr = await this.validateStudyPeriodDates(cleanStart, null);
    if (dateErr) {
      return { id: -1, error: dateErr };
    }

    const db = await getDatabase();
    const now = new Date().toISOString();
    try {
      db.run(
        `INSERT INTO StudyPeriods (PeriodName, StartDate, EndDate, Status, CreatedDate)
         VALUES (?, ?, NULL, 'OPEN', ?);`,
        [cleanName, cleanStart, now]
      );
    } catch {
      return {
        id: -1,
        error: 'توجد فترة دراسية مفتوحة حالياً. يجب إنهاؤها أولاً قبل بدء فترة جديدة.',
      };
    }
    const idRes = db.exec('SELECT last_insert_rowid();');
    await persistDatabaseToStorage();
    return {
      id: idRes.length > 0 ? Number(idRes[0].values[0][0]) : -1,
      error: null,
    };
  }

  static async closeStudyPeriod(
    id: number,
    endDate: string
  ): Promise<{ error: string | null }> {
    const all = await this.getStudyPeriods();
    const target = all.find((p) => p.id === id);
    if (!target) {
      return { error: 'الفترة الدراسية غير موجودة' };
    }

    const cleanEnd = endDate.trim();
    if (!cleanEnd) {
      return { error: 'يرجى تحديد تاريخ نهاية الفترة' };
    }
    if (cleanEnd < target.startDate) {
      return {
        error: `لا يسمح بأن يكون تاريخ النهاية قبل تاريخ البداية (${this.formatIsoToDisplay(
          target.startDate
        )})`,
      };
    }

    const dateErr = await this.validateStudyPeriodDates(
      target.startDate,
      cleanEnd,
      id
    );
    if (dateErr) {
      return { error: dateErr };
    }

    const db = await getDatabase();
    db.run(
      `UPDATE StudyPeriods SET EndDate = ?, Status = 'CLOSED' WHERE ID = ?;`,
      [cleanEnd, id]
    );
    await persistDatabaseToStorage();
    return { error: null };
  }

  static async updateStudyPeriod(
    id: number,
    periodName: string,
    startDate: string,
    endDate: string | null
  ): Promise<{ error: string | null }> {
    const all = await this.getStudyPeriods();
    const target = all.find((p) => p.id === id);
    if (!target) {
      return { error: 'الفترة الدراسية غير موجودة' };
    }

    const cleanName = periodName.trim();
    if (!cleanName) {
      return { error: 'يرجى إدخال اسم الفترة الدراسية' };
    }

    const effectiveStart =
      target.status === 'OPEN' ? startDate.trim() : target.startDate;
    const effectiveEnd =
      target.status === 'CLOSED' ? (endDate?.trim() || null) : null;

    if (target.status === 'CLOSED' && !effectiveEnd) {
      return { error: 'يرجى تحديد تاريخ النهاية للفترة المغلقة' };
    }

    const dateErr = await this.validateStudyPeriodDates(
      effectiveStart,
      effectiveEnd,
      id
    );
    if (dateErr) {
      return { error: dateErr };
    }

    const db = await getDatabase();
    if (target.status === 'OPEN') {
      db.run(
        `UPDATE StudyPeriods SET PeriodName = ?, StartDate = ? WHERE ID = ?;`,
        [cleanName, effectiveStart, id]
      );
    } else {
      db.run(
        `UPDATE StudyPeriods SET PeriodName = ?, EndDate = ? WHERE ID = ?;`,
        [cleanName, effectiveEnd, id]
      );
    }
    await persistDatabaseToStorage();
    return { error: null };
  }

  static async deleteStudyPeriod(id: number): Promise<void> {
    const db = await getDatabase();
    db.run('DELETE FROM StudyPeriods WHERE ID = ?;', [id]);
    await persistDatabaseToStorage();
  }

  // ---------------- PERIOD GRADES ("درجات الفترات والامتحان التحريري والمعدل") ----------------
  static async getPeriodGrades(
    studentId?: number,
    periodId?: number
  ): Promise<PeriodGradeEntity[]> {
    const db = await getDatabase();
    const whereClauses: string[] = [];
    const params: number[] = [];
    if (studentId !== undefined) {
      whereClauses.push('StudentID = ?');
      params.push(studentId);
    }
    if (periodId !== undefined) {
      whereClauses.push('PeriodID = ?');
      params.push(periodId);
    }
    const whereSql =
      whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

    const res = db.exec(
      `SELECT ID, StudentID, PeriodID, DailyGrade, WrittenExamGrade, PeriodAverage, UpdatedDate
       FROM PeriodGrades
       ${whereSql}
       ORDER BY PeriodID ASC, StudentID ASC;`,
      params
    );
    if (res.length === 0) return [];
    return res[0].values.map((row) => ({
      id: Number(row[0]),
      studentId: Number(row[1]),
      periodId: Number(row[2]),
      dailyGrade: row[3] !== null && row[3] !== undefined ? Number(row[3]) : null,
      writtenExamGrade: row[4] !== null && row[4] !== undefined ? Number(row[4]) : null,
      periodAverage: row[5] !== null && row[5] !== undefined ? Number(row[5]) : null,
      updatedDate: String(row[6] || ''),
    }));
  }

  static async upsertPeriodGrade(
    studentId: number,
    periodId: number,
    dailyGrade: number,
    writtenExamGrade: number | null
  ): Promise<{ error: string | null }> {
    if (writtenExamGrade !== null) {
      if (
        Number.isNaN(writtenExamGrade) ||
        writtenExamGrade < 0 ||
        writtenExamGrade > 100
      ) {
        return { error: 'يجب أن تكون درجة الامتحان التحريري بين 0 و 100' };
      }
    }
    const roundedDaily =
      Math.round(Math.min(100, Math.max(0, dailyGrade)) * 100) / 100;
    const roundedWritten =
      writtenExamGrade !== null
        ? Math.round(Math.min(100, Math.max(0, writtenExamGrade)) * 100) / 100
        : null;
    const periodAvg =
      roundedWritten !== null
        ? Math.round(((roundedDaily + roundedWritten) / 2) * 100) / 100
        : null;

    const db = await getDatabase();
    const now = new Date().toISOString();
    db.run(
      `INSERT INTO PeriodGrades (StudentID, PeriodID, DailyGrade, WrittenExamGrade, PeriodAverage, UpdatedDate)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(StudentID, PeriodID) DO UPDATE SET
         DailyGrade = excluded.DailyGrade,
         WrittenExamGrade = excluded.WrittenExamGrade,
         PeriodAverage = excluded.PeriodAverage,
         UpdatedDate = excluded.UpdatedDate;`,
      [studentId, periodId, roundedDaily, roundedWritten, periodAvg, now]
    );
    await persistDatabaseToStorage();
    return { error: null };
  }

  // ---------------- DAILY PLANS ("الخطة اليومية") ----------------
  static async getDailyPlanTopicForClassSectionDate(
    planDate: string,
    classId: number,
    sectionId: number
  ): Promise<string | null> {
    const db = await getDatabase();
    const res = db.exec(
      `SELECT dp.LessonTopic
       FROM DailyPlans dp
       WHERE dp.PlanDate = ?
         AND dp.ClassID = ?
         AND EXISTS (
           SELECT 1 FROM DailyPlanSections dps
           WHERE dps.PlanID = dp.ID AND dps.SectionID = ?
         )
       ORDER BY dp.ID DESC
       LIMIT 1;`,
      [planDate, classId, sectionId]
    );
    if (res.length === 0 || res[0].values.length === 0) return null;
    const topic = String(res[0].values[0][0] || '').trim();
    return topic || null;
  }

  static async getDailyPlans(filters?: {
    planDate?: string;
    classId?: number;
    sectionId?: number;
    searchTopic?: string;
  }): Promise<DailyPlanEntity[]> {
    const db = await getDatabase();
    const whereClauses: string[] = [];
    const params: (string | number)[] = [];

    if (filters?.planDate && filters.planDate.trim()) {
      whereClauses.push('dp.PlanDate = ?');
      params.push(filters.planDate.trim());
    }
    if (filters?.classId !== undefined && filters.classId > 0) {
      whereClauses.push('dp.ClassID = ?');
      params.push(filters.classId);
    }
    if (filters?.sectionId !== undefined && filters.sectionId > 0) {
      whereClauses.push(
        'EXISTS (SELECT 1 FROM DailyPlanSections dps WHERE dps.PlanID = dp.ID AND dps.SectionID = ?)'
      );
      params.push(filters.sectionId);
    }
    if (filters?.searchTopic && filters.searchTopic.trim()) {
      whereClauses.push('dp.LessonTopic LIKE ?');
      params.push(`%${filters.searchTopic.trim()}%`);
    }

    const whereSql =
      whereClauses.length > 0 ? `WHERE ${whereClauses.join(' AND ')}` : '';

    const plansRes = db.exec(
      `SELECT dp.ID, dp.PlanDate, dp.ClassID, COALESCE(c.ClassName, ''), dp.LessonTopic, dp.CreatedDate, dp.UpdatedDate
       FROM DailyPlans dp
       LEFT JOIN Classes c ON c.ID = dp.ClassID
       ${whereSql}
       ORDER BY dp.PlanDate DESC, dp.ID DESC;`,
      params
    );

    if (plansRes.length === 0 || plansRes[0].values.length === 0) {
      return [];
    }

    const secRes = db.exec(`
      SELECT dps.PlanID, dps.SectionID, COALESCE(s.SectionName, '')
      FROM DailyPlanSections dps
      LEFT JOIN Sections s ON s.ID = dps.SectionID
      ORDER BY dps.SectionID ASC;
    `);
    const sectionsByPlan = new Map<
      number,
      { sectionIds: number[]; sectionNames: string[] }
    >();
    if (secRes.length > 0) {
      for (const r of secRes[0].values) {
        const pId = Number(r[0]);
        const sId = Number(r[1]);
        const sName = String(r[2] || '');
        if (!sectionsByPlan.has(pId)) {
          sectionsByPlan.set(pId, { sectionIds: [], sectionNames: [] });
        }
        sectionsByPlan.get(pId)!.sectionIds.push(sId);
        sectionsByPlan.get(pId)!.sectionNames.push(sName);
      }
    }

    const imgRes = db.exec(`
      SELECT ID, PlanID, FileName, ImageBase64, SortOrder, CreatedDate
      FROM DailyPlanImages
      ORDER BY PlanID ASC, SortOrder ASC, ID ASC;
    `);
    const imagesByPlan = new Map<number, DailyPlanImageEntity[]>();
    if (imgRes.length > 0) {
      for (const r of imgRes[0].values) {
        const img: DailyPlanImageEntity = {
          id: Number(r[0]),
          planId: Number(r[1]),
          fileName: String(r[2] || ''),
          imageBase64: String(r[3] || ''),
          sortOrder: Number(r[4] || 0),
          createdDate: String(r[5] || ''),
        };
        if (!imagesByPlan.has(img.planId)) {
          imagesByPlan.set(img.planId, []);
        }
        imagesByPlan.get(img.planId)!.push(img);
      }
    }

    return plansRes[0].values.map((row) => {
      const planId = Number(row[0]);
      const secInfo = sectionsByPlan.get(planId) || {
        sectionIds: [],
        sectionNames: [],
      };
      return {
        id: planId,
        planDate: String(row[1] || ''),
        classId: Number(row[2]),
        className: String(row[3] || ''),
        sectionIds: secInfo.sectionIds,
        sectionNames: secInfo.sectionNames,
        lessonTopic: String(row[4] || ''),
        images: imagesByPlan.get(planId) || [],
        createdDate: String(row[5] || ''),
        updatedDate: String(row[6] || ''),
      };
    });
  }

  static async getDailyPlansForClassAndSection(
    classId: number,
    sectionId: number
  ): Promise<DailyPlanEntity[]> {
    return this.getDailyPlans({ classId, sectionId });
  }

  static async saveDailyPlan(params: {
    existingPlanId?: number | null;
    planDate: string;
    classId: number;
    sectionIds: number[];
    lessonTopic: string;
    images: { fileName: string; imageBase64: string }[];
  }): Promise<{ id: number; error: string | null }> {
    const cleanDate = params.planDate.trim();
    if (!cleanDate) {
      return { id: -1, error: 'يرجى اختيار التاريخ للخطة اليومية' };
    }
    if (!params.classId || params.classId <= 0) {
      return { id: -1, error: 'يرجى اختيار الصف الدراسي' };
    }
    const distinctSecs = Array.from(
      new Set(params.sectionIds.filter((s) => s > 0))
    );
    if (distinctSecs.length === 0) {
      return {
        id: -1,
        error: 'يرجى اختيار شعبة واحدة على الأقل من الصف المحدد',
      };
    }
    const cleanTopic = params.lessonTopic.trim();
    if (!cleanTopic) {
      return { id: -1, error: 'يرجى كتابة عنوان الدرس أو الموضوع' };
    }

    const db = await getDatabase();
    const now = new Date().toISOString();
    let savedPlanId = params.existingPlanId ?? -1;

    db.run('BEGIN TRANSACTION;');
    try {
      if (params.existingPlanId && params.existingPlanId > 0) {
        db.run(
          `UPDATE DailyPlans
           SET PlanDate = ?, ClassID = ?, LessonTopic = ?, UpdatedDate = ?
           WHERE ID = ?;`,
          [cleanDate, params.classId, cleanTopic, now, params.existingPlanId]
        );
        db.run('DELETE FROM DailyPlanSections WHERE PlanID = ?;', [
          params.existingPlanId,
        ]);
        db.run('DELETE FROM DailyPlanImages WHERE PlanID = ?;', [
          params.existingPlanId,
        ]);
        savedPlanId = params.existingPlanId;
      } else {
        db.run(
          `INSERT INTO DailyPlans (PlanDate, ClassID, LessonTopic, CreatedDate, UpdatedDate)
           VALUES (?, ?, ?, ?, ?);`,
          [cleanDate, params.classId, cleanTopic, now, now]
        );
        const idRes = db.exec('SELECT last_insert_rowid();');
        savedPlanId = idRes.length > 0 ? Number(idRes[0].values[0][0]) : -1;
      }

      for (const secId of distinctSecs) {
        db.run(
          `INSERT OR IGNORE INTO DailyPlanSections (PlanID, SectionID) VALUES (?, ?);`,
          [savedPlanId, secId]
        );
      }

      params.images.forEach((img, idx) => {
        const fName =
          img.fileName.trim() ||
          `plan_${savedPlanId}_img_${idx + 1}_${Date.now()}.jpg`;
        db.run(
          `INSERT INTO DailyPlanImages (PlanID, FileName, ImageBase64, SortOrder, CreatedDate)
           VALUES (?, ?, ?, ?, ?);`,
          [savedPlanId, fName, img.imageBase64, idx, now]
        );
      });

      db.run('COMMIT;');
      await persistDatabaseToStorage();
      return { id: savedPlanId, error: null };
    } catch (err) {
      db.run('ROLLBACK;');
      return {
        id: -1,
        error:
          err instanceof Error ? err.message : 'تعذر حفظ الخطة اليومية',
      };
    }
  }

  static async deleteDailyPlan(planId: number): Promise<void> {
    const db = await getDatabase();
    db.run('BEGIN TRANSACTION;');
    try {
      db.run('DELETE FROM DailyPlanImages WHERE PlanID = ?;', [planId]);
      db.run('DELETE FROM DailyPlanSections WHERE PlanID = ?;', [planId]);
      db.run('DELETE FROM DailyPlans WHERE ID = ?;', [planId]);
      db.run('COMMIT;');
      await persistDatabaseToStorage();
    } catch (err) {
      db.run('ROLLBACK;');
      throw err;
    }
  }
}

