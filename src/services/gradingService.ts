import {
  AttendanceStatus,
  HolidayEntity,
  RatingValue,
  StudentCalculatedGrade,
  StudentDailyEntry,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';

export function round2(val: number): number {
  return Math.round((val + Number.EPSILON) * 100) / 100;
}

export function formatScore(val: number): string {
  const rounded = round2(val);
  return Number.isInteger(rounded) ? rounded.toString() : rounded.toFixed(2).replace(/\.?0+$/, '');
}

export function getRatingLabel(val: RatingValue): string {
  if (val === 1) return 'كامل';
  if (val === 0.5) return 'نصف';
  if (val === 0) return 'صفر';
  return 'غير مسجل';
}

export function formatDisplayDate(isoDate: string): string {
  // Converts YYYY-MM-DD to DD/MM/YYYY for clear Arabic display
  const parts = isoDate.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  return isoDate;
}

export function getArabicDayName(isoDate: string): string {
  const parts = isoDate.split('-');
  if (parts.length !== 3) return '';
  const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  const days = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
  return days[d.getDay()] || '';
}

/**
 * Checks if a given YYYY-MM-DD date is a Friday (5) or Saturday (6).
 */
export function isWeekendDay(isoDate: string): { isWeekend: boolean; dayName: string } {
  const parts = isoDate.split('-');
  if (parts.length !== 3) return { isWeekend: false, dayName: '' };
  const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  const day = d.getDay();
  const days = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
  return {
    isWeekend: day === 5 || day === 6,
    dayName: days[day] || '',
  };
}

export function checkDateHolidayStatus(
  isoDate: string,
  holidays: HolidayEntity[]
): {
  isNonWorkingDay: boolean;
  reasonType: 'weekend' | 'holiday' | 'working';
  title: string;
  subtitle: string;
  holidayObj?: HolidayEntity;
} {
  const customHoliday = holidays.find((h) => h.holidayDate === isoDate);
  if (customHoliday) {
    return {
      isNonWorkingDay: true,
      reasonType: 'holiday',
      title: 'هذا اليوم عطلة',
      subtitle: `${customHoliday.holidayName} (${customHoliday.holidayType})${
        customHoliday.notes ? ` — ${customHoliday.notes}` : ''
      }`,
      holidayObj: customHoliday,
    };
  }

  const weekend = isWeekendDay(isoDate);
  if (weekend.isWeekend) {
    return {
      isNonWorkingDay: true,
      reasonType: 'weekend',
      title: 'عطلة أسبوعية',
      subtitle: `يوم ${weekend.dayName} عطلة أسبوعية تلقائية — لا يتم تسجيل غياب ولا يحتسب ضمن أيام الدوام`,
    };
  }

  return {
    isNonWorkingDay: false,
    reasonType: 'working',
    title: `يوم دوام اعتيادي (${weekend.dayName})`,
    subtitle: '',
  };
}

export function createDefaultStudentEntry(studentId: number): StudentDailyEntry {
  return {
    studentId,
    attendance: 'present',
    participation: null,
    notebook: null,
    homework: null,
    behavior: null,
    dailyTestScore: null,
  };
}

export class GradingService {
  static async calculateGradesForClass(
    classId?: number,
    sectionId?: number,
    monthFilter?: string,
    liveDraftOverride?: {
      recordDate: string;
      entries: Record<number, StudentDailyEntry>;
    }
  ): Promise<StudentCalculatedGrade[]> {
    const raw = await TrackerRepository.getRawStudentHistory(classId, sectionId, monthFilter);

    // Helper to check if a date is excluded from working days (Friday, Saturday, or registered Holiday)
    const isExcludedDate = (dateStr: string): boolean => {
      if (raw.holidays.has(dateStr)) return true;
      return isWeekendDay(dateStr).isWeekend;
    };

    return raw.students.map((st) => {
      // Build attendance map by date
      const attMap = new Map<string, AttendanceStatus>();
      for (const r of raw.attendanceRows) {
        if (r.studentId === st.id && !isExcludedDate(r.date)) {
          attMap.set(r.date, r.status);
        }
      }

      // Build evaluations map by date
      const evalMap = new Map<
        string,
        {
          participation: RatingValue;
          notebook: RatingValue;
          homework: RatingValue;
          behavior: RatingValue;
        }
      >();
      for (const r of raw.evaluationRows) {
        if (r.studentId === st.id && !isExcludedDate(r.date)) {
          evalMap.set(r.date, {
            participation: r.participation,
            notebook: r.notebook,
            homework: r.homework,
            behavior: r.behavior,
          });
        }
      }

      // Build daily tests map by date
      const testMap = new Map<string, number>();
      for (const r of raw.testRows) {
        if (r.studentId === st.id && !isExcludedDate(r.date)) {
          testMap.set(r.date, r.score);
        }
      }

      // Apply live draft override if the teacher is currently editing a working day
      if (
        liveDraftOverride &&
        liveDraftOverride.entries[st.id] &&
        !isExcludedDate(liveDraftOverride.recordDate)
      ) {
        const d = liveDraftOverride.recordDate;
        const draft = liveDraftOverride.entries[st.id];
        if (!monthFilter || monthFilter === 'ALL' || d.startsWith(monthFilter)) {
          attMap.set(d, draft.attendance);
          evalMap.set(d, {
            participation: draft.participation,
            notebook: draft.notebook,
            homework: draft.homework,
            behavior: draft.behavior,
          });
          if (draft.dailyTestScore !== null && draft.dailyTestScore !== undefined) {
            testMap.set(d, Math.min(5, Math.max(0, Number(draft.dailyTestScore))));
          } else {
            testMap.delete(d);
          }
        }
      }

      // 1. Attendance Calculation (out of 19)
      let presentDays = 0;
      let absentDays = 0;
      const attendanceHistory: { date: string; status: AttendanceStatus }[] = [];

      const sortedAttDates = Array.from(attMap.keys()).sort();
      for (const d of sortedAttDates) {
        const status = attMap.get(d)!;
        attendanceHistory.push({ date: d, status });
        if (status === 'present') presentDays++;
        else absentDays++;
      }

      const totalWorkingDays = presentDays + absentDays;
      const hasAttendanceData = totalWorkingDays > 0;
      const attendancePercentage = hasAttendanceData
        ? (presentDays / totalWorkingDays) * 100
        : 100;
      const attendanceScore = hasAttendanceData
        ? round2((presentDays / totalWorkingDays) * 19)
        : 19;

      // 2. Evaluations Calculation (4 elements × 19 points each)
      const participationRatings: { date: string; value: 1 | 0.5 | 0 }[] = [];
      const notebookRatings: { date: string; value: 1 | 0.5 | 0 }[] = [];
      const homeworkRatings: { date: string; value: 1 | 0.5 | 0 }[] = [];
      const behaviorRatings: { date: string; value: 1 | 0.5 | 0 }[] = [];

      const sortedEvalDates = Array.from(evalMap.keys()).sort();
      for (const d of sortedEvalDates) {
        const ev = evalMap.get(d)!;
        if (ev.participation !== null && ev.participation !== undefined) {
          participationRatings.push({ date: d, value: ev.participation });
        }
        if (ev.notebook !== null && ev.notebook !== undefined) {
          notebookRatings.push({ date: d, value: ev.notebook });
        }
        if (ev.homework !== null && ev.homework !== undefined) {
          homeworkRatings.push({ date: d, value: ev.homework });
        }
        if (ev.behavior !== null && ev.behavior !== undefined) {
          behaviorRatings.push({ date: d, value: ev.behavior });
        }
      }

      const calc19Element = (list: { date: string; value: 1 | 0.5 | 0 }[]) => {
        // Rule: "إذا لم يتم تسجيل أي تقييم لذلك العنصر خلال الشهر، لا يعتبر صفراً."
        if (list.length === 0) {
          return { hasData: false, average: null, score: 19 };
        }
        const sum = list.reduce((acc, item) => acc + item.value, 0);
        const avg = sum / list.length;
        return {
          hasData: true,
          average: round2(avg),
          score: round2(avg * 19),
        };
      };

      const partCalc = calc19Element(participationRatings);
      const noteCalc = calc19Element(notebookRatings);
      const hwCalc = calc19Element(homeworkRatings);
      const behCalc = calc19Element(behaviorRatings);

      // 3. Daily Tests Calculation (out of 5)
      const dailyTests: { date: string; score: number }[] = [];
      const sortedTestDates = Array.from(testMap.keys()).sort();
      for (const d of sortedTestDates) {
        dailyTests.push({ date: d, score: testMap.get(d)! });
      }

      const hasQuizData = dailyTests.length > 0;
      const quizAverage = hasQuizData
        ? round2(dailyTests.reduce((s, t) => s + t.score, 0) / dailyTests.length)
        : null;
      const quizScore = hasQuizData ? quizAverage! : 5;

      // 4. Total Daily Score out of 100
      const totalScore = round2(
        attendanceScore +
          partCalc.score +
          noteCalc.score +
          hwCalc.score +
          behCalc.score +
          quizScore
      );

      return {
        studentId: st.id,
        studentName: st.studentName,
        classId: st.classId,
        className: st.className,
        sectionId: st.sectionId,
        sectionName: st.sectionName,

        presentDays,
        absentDays,
        totalWorkingDays,
        attendancePercentage: round2(attendancePercentage),
        attendanceScore,
        hasAttendanceData,
        attendanceHistory,

        participationRatings,
        participationAverage: partCalc.average,
        participationScore: partCalc.score,
        hasParticipationData: partCalc.hasData,

        notebookRatings,
        notebookAverage: noteCalc.average,
        notebookScore: noteCalc.score,
        hasNotebookData: noteCalc.hasData,

        homeworkRatings,
        homeworkAverage: hwCalc.average,
        homeworkScore: hwCalc.score,
        hasHomeworkData: hwCalc.hasData,

        behaviorRatings,
        behaviorAverage: behCalc.average,
        behaviorScore: behCalc.score,
        hasBehaviorData: behCalc.hasData,

        dailyTests,
        quizAverage,
        quizScore,
        hasQuizData,

        totalScore,
      };
    });
  }
}
