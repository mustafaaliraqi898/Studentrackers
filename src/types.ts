export type RatingValue = 1 | 0.5 | 0 | null;

export type AttendanceStatus = 'present' | 'absent';

export interface ClassEntity {
  id: number;
  className: string;
  createdAt: string;
}

export interface SectionEntity {
  id: number;
  classId: number;
  sectionName: string;
  createdAt: string;
}

export interface StudentEntity {
  id: number;
  studentName: string;
  classId: number;
  className?: string;
  sectionId: number;
  sectionName?: string;
  isActive: boolean;
  createdDate: string;
}

export interface HolidayEntity {
  id: number;
  holidayDate: string; // YYYY-MM-DD
  holidayName: string;
  holidayType: string; // 'رسمية' | 'مناسبة' | 'طارئة'
  notes: string;
}

export interface StudentDailyEntry {
  studentId: number;
  attendance: AttendanceStatus;
  participation: RatingValue;
  notebook: RatingValue;
  homework: RatingValue;
  behavior: RatingValue;
  dailyTestScore: number | null; // 0 to 5 (allows decimals e.g. 3.5)
}

export interface SavedSessionSummary {
  recordDate: string; // YYYY-MM-DD
  classId: number;
  className: string;
  sectionId: number;
  sectionName: string;
  studentCount: number;
  presentCount: number;
  absentCount: number;
  evaluationsCount: number;
  testsCount: number;
  updatedAt: string;
}

export interface StudentCalculatedGrade {
  studentId: number;
  studentName: string;
  classId: number;
  className: string;
  sectionId: number;
  sectionName: string;

  // Attendance (excludes Fridays, Saturdays, and registered Holidays)
  presentDays: number;
  absentDays: number;
  totalWorkingDays: number;
  attendancePercentage: number;
  attendanceScore: number; // out of 19
  hasAttendanceData: boolean;
  attendanceHistory: { date: string; status: AttendanceStatus }[];

  // Participation (19)
  participationRatings: { date: string; value: 1 | 0.5 | 0 }[];
  participationAverage: number | null;
  participationScore: number; // out of 19
  hasParticipationData: boolean;

  // Notebook (19)
  notebookRatings: { date: string; value: 1 | 0.5 | 0 }[];
  notebookAverage: number | null;
  notebookScore: number; // out of 19
  hasNotebookData: boolean;

  // Homework (19)
  homeworkRatings: { date: string; value: 1 | 0.5 | 0 }[];
  homeworkAverage: number | null;
  homeworkScore: number; // out of 19
  hasHomeworkData: boolean;

  // Behavior (19)
  behaviorRatings: { date: string; value: 1 | 0.5 | 0 }[];
  behaviorAverage: number | null;
  behaviorScore: number; // out of 19
  hasBehaviorData: boolean;

  // Daily Tests (5)
  dailyTests: { date: string; score: number }[];
  quizAverage: number | null;
  quizScore: number; // out of 5
  hasQuizData: boolean;

  // Final Daily Grade out of 100
  totalScore: number; // out of 100
}

export interface BackupSnapshotMetadata {
  id: string;
  fileName?: string;
  title: string;
  createdAt: string;
  isAutoPreRestore: boolean;
  isAutoDaily?: boolean;
  sizeBytes: number;
  stats: {
    classesCount: number;
    sectionsCount: number;
    studentsCount: number;
    recordsCount: number;
    holidaysCount: number;
  };
  sqliteBase64: string;
}

export interface ExcelRowError {
  rowNumber: number;
  studentName: string;
  className: string;
  sectionName: string;
  reason: string;
}

export interface ExcelImportResult {
  importedCount: number;
  duplicateCount: number;
  emptyRowCount: number;
  errorCount: number;
  createdClasses: string[];
  createdSections: string[];
  rowErrors: ExcelRowError[];
  details: string[];
}

export type AppScreen =
  | 'dashboard'
  | 'classes-sections'
  | 'students'
  | 'daily-tracking'
  | 'holidays'
  | 'saved-records'
  | 'student-profile'
  | 'reports'
  | 'excel-center'
  | 'backup-restore'
  | 'schema-architecture'
  | 'settings';
