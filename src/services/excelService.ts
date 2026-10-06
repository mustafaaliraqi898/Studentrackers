import * as XLSX from 'xlsx';
import {
  ExcelImportResult,
  ExcelRowError,
  StudentAbsenceSummary,
  StudentCalculatedGrade,
  StudentComprehensiveGradeReport,
  StudyPeriodEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';
import { formatScore } from './gradingService';

export class ExcelService {
  /**
   * 1. تصدير قالب Excel فارغ جاهز للمدرس (قالب_استيراد_الطلاب.xlsx)
   * يحتوي على الأعمدة الثلاثة المطلوبة:
   * اسم الطالب | الصف | الشعبة
   */
  static downloadEmptyStudentsTemplate(): void {
    const templateRows = [
      { 'اسم الطالب': 'أحمد علي', 'الصف': 'الرابع', 'الشعبة': 'أ' },
      { 'اسم الطالب': 'محمد حسن', 'الصف': 'الرابع', 'الشعبة': 'أ' },
      { 'اسم الطالب': 'علي كريم', 'الصف': 'الرابع', 'الشعبة': 'ب' },
      { 'اسم الطالب': '', 'الصف': '', 'الشعبة': '' },
      { 'اسم الطالب': '', 'الصف': '', 'الشعبة': '' },
      { 'اسم الطالب': '', 'الصف': '', 'الشعبة': '' },
    ];

    const worksheet = XLSX.utils.json_to_sheet(templateRows, {
      header: ['اسم الطالب', 'الصف', 'الشعبة'],
    });
    worksheet['!cols'] = [{ wch: 28 }, { wch: 20 }, { wch: 14 }];

    const workbook = XLSX.utils.book_new();
    if (!workbook.Workbook) workbook.Workbook = {};
    workbook.Workbook.Views = [{ RTL: true }];
    XLSX.utils.book_append_sheet(workbook, worksheet, 'قالب الطلاب');
    XLSX.writeFile(workbook, 'قالب_استيراد_الطلاب.xlsx');
  }

  /**
   * Generates a comprehensive test Excel file containing valid students, duplicates,
   * empty rows, missing Class rows, and missing Section rows so the teacher can test
   * all edge cases immediately on Android.
   */
  static downloadEdgeCaseTestExcel(): void {
    const testRows = [
      { 'اسم الطالب': 'أحمد علي', 'الصف': 'الثالث المتوسط', 'الشعبة': 'أ' }, // مكرر موجود مسبقاً
      { 'اسم الطالب': 'سجاد كاظم الموسوي', 'الصف': 'الرابع العلمي', 'الشعبة': 'أ' }, // طالب جديد صحيح
      { 'اسم الطالب': 'مرتضى حيدر العلي', 'الصف': 'الرابع العلمي', 'الشعبة': 'ب' }, // طالب جديد + شعبة جديدة
      { 'اسم الطالب': '', 'الصف': '', 'الشعبة': '' }, // صف فارغ بالكامل -> يتم تجاهله
      { 'اسم الطالب': 'طالب بدون صف', 'الصف': '', 'الشعبة': 'أ' }, // خطأ: الصف فارغ
      { 'اسم الطالب': 'طالب بدون شعبة', 'الصف': 'الرابع العلمي', 'الشعبة': '' }, // خطأ: الشعبة فارغة
      { 'اسم الطالب': '', 'الصف': 'الرابع العلمي', 'الشعبة': 'أ' }, // خطأ: اسم الطالب فارغ
      { 'اسم الطالب': 'باقر جاسم محمد', 'الصف': 'الخامس العلمي', 'الشعبة': 'أ' }, // طالب جديد + صف جديد
    ];

    const worksheet = XLSX.utils.json_to_sheet(testRows, {
      header: ['اسم الطالب', 'الصف', 'الشعبة'],
    });
    worksheet['!cols'] = [{ wch: 28 }, { wch: 20 }, { wch: 14 }];

    const workbook = XLSX.utils.book_new();
    if (!workbook.Workbook) workbook.Workbook = {};
    workbook.Workbook.Views = [{ RTL: true }];
    XLSX.utils.book_append_sheet(workbook, worksheet, 'اختبار_الاستيراد');
    XLSX.writeFile(workbook, 'ملف_اختبار_حالات_الاستيراد.xlsx');
  }

  /**
   * 2. استيراد الطلاب من Excel بشكل فعلي مع التحقق من كافة الحالات:
   * - يدعم أعمدة: اسم الطالب | الصف | الشعبة
   * - كما يدعم الملفات ذات العمودين (اسم الطالب | الشعبة) باستخدام الصف الافتراضي المحدد
   * - إذا كان الصف فارغاً بالكامل -> يتم تجاهله وحسابه ضمن (صفوف فارغة)
   * - إذا كان اسم الطالب فارغاً أو الصف فارغاً أو الشعبة فارغة -> يسجل كخطأ مفصل مع رقم الصف
   * - يمنع تكرار الطالب الموجود مسبقاً
   */
  static async importStudentsFromExcel(
    file: File,
    fallbackClassId?: number
  ): Promise<ExcelImportResult> {
    let buffer: ArrayBuffer;
    try {
      buffer = await file.arrayBuffer();
    } catch {
      throw new Error('تعذر قراءة الملف المحدد. يرجى التأكد من صلاحية الملف.');
    }

    let workbook: XLSX.WorkBook;
    try {
      workbook = XLSX.read(buffer, { type: 'array' });
    } catch {
      throw new Error('صيغة الملف غير مدعومة. يرجى اختيار ملف Excel صالح (.xlsx).');
    }

    const firstSheetName = workbook.SheetNames[0];
    if (!firstSheetName) {
      return {
        importedCount: 0,
        duplicateCount: 0,
        emptyRowCount: 0,
        errorCount: 0,
        createdClasses: [],
        createdSections: [],
        rowErrors: [],
        details: ['الملف فارغ ولا يحتوي على أوراق عمل.'],
      };
    }

    const worksheet = workbook.Sheets[firstSheetName];
    // Read as 2D array to accurately detect completely empty rows and exact row numbers
    const matrix = XLSX.utils.sheet_to_json< unknown[] >(worksheet, {
      header: 1,
      defval: '',
      blankrows: true,
    });

    if (!matrix || matrix.length === 0) {
      return {
        importedCount: 0,
        duplicateCount: 0,
        emptyRowCount: 0,
        errorCount: 0,
        createdClasses: [],
        createdSections: [],
        rowErrors: [],
        details: ['ورقة العمل في ملف Excel فارغة.'],
      };
    }

    // Determine column indices from header row (row 0)
    const headerRow = (matrix[0] || []).map((cell) => String(cell ?? '').trim());
    let nameColIdx = 0;
    let classColIdx = 1;
    let sectionColIdx = 2;
    let hasExplicitClassColumn = true;
    let startRowIdx = 0;

    const isHeaderRow = headerRow.some(
      (h) =>
        h.includes('اسم') ||
        h.includes('طالب') ||
        h.includes('الصف') ||
        h.includes('الشعبة') ||
        h.toLowerCase().includes('name') ||
        h.toLowerCase().includes('class') ||
        h.toLowerCase().includes('section')
    );

    if (isHeaderRow) {
      startRowIdx = 1;
      const foundNameIdx = headerRow.findIndex(
        (h) => h.includes('اسم') || h.includes('طالب') || h.toLowerCase().includes('name')
      );
      const foundClassIdx = headerRow.findIndex(
        (h) => h === 'الصف' || h.includes('صف') || h.toLowerCase().includes('class')
      );
      const foundSecIdx = headerRow.findIndex(
        (h) => h.includes('شعب') || h.includes('الشعبة') || h.toLowerCase().includes('section')
      );

      if (foundNameIdx >= 0) nameColIdx = foundNameIdx;
      if (foundClassIdx >= 0) {
        classColIdx = foundClassIdx;
        hasExplicitClassColumn = true;
      } else if (headerRow.length === 2 && foundSecIdx >= 0) {
        hasExplicitClassColumn = false;
      }
      if (foundSecIdx >= 0) {
        sectionColIdx = foundSecIdx;
      } else if (!hasExplicitClassColumn) {
        sectionColIdx = 1;
      }
    }

    let importedCount = 0;
    let duplicateCount = 0;
    let emptyRowCount = 0;
    let errorCount = 0;
    const createdClasses: string[] = [];
    const createdSections: string[] = [];
    const rowErrors: ExcelRowError[] = [];
    const details: string[] = [];

    let classes = await TrackerRepository.getClasses();
    let allSections = await TrackerRepository.getSections();

    const fallbackClassObj = fallbackClassId
      ? classes.find((c) => c.id === fallbackClassId)
      : undefined;

    for (let r = startRowIdx; r < matrix.length; r++) {
      const excelRowNumber = r + 1;
      const rowCells = matrix[r] || [];

      const rawName = TrackerRepository.normalizeStudentName(
        String(rowCells[nameColIdx] ?? '')
      );
      const rawClass = hasExplicitClassColumn
        ? TrackerRepository.normalizeStudentName(String(rowCells[classColIdx] ?? ''))
        : fallbackClassObj?.className || '';
      const rawSection = TrackerRepository.normalizeStudentName(
        String(rowCells[sectionColIdx] ?? '')
      );

      // Check if entire row is empty
      const allCellsEmpty = rowCells.every((c) => String(c ?? '').trim() === '');
      if (allCellsEmpty || (!rawName && !rawClass && !rawSection)) {
        emptyRowCount++;
        continue;
      }

      // Skip repeated header row if any
      if (rawName === 'اسم الطالب' && (rawClass === 'الصف' || rawSection === 'الشعبة')) {
        continue;
      }

      // Validation Checks:
      if (!rawName) {
        errorCount++;
        rowErrors.push({
          rowNumber: excelRowNumber,
          studentName: '(فارغ)',
          className: rawClass || '(فارغ)',
          sectionName: rawSection || '(فارغ)',
          reason: 'اسم الطالب فارغ',
        });
        continue;
      }

      if (!rawClass) {
        errorCount++;
        rowErrors.push({
          rowNumber: excelRowNumber,
          studentName: rawName,
          className: '(فارغ)',
          sectionName: rawSection || '(فارغ)',
          reason: 'الصف فارغ',
        });
        continue;
      }

      if (!rawSection) {
        errorCount++;
        rowErrors.push({
          rowNumber: excelRowNumber,
          studentName: rawName,
          className: rawClass,
          sectionName: '(فارغ)',
          reason: 'الشعبة فارغة',
        });
        continue;
      }

      // Resolve or create Class
      let classObj = classes.find((c) => c.className === rawClass);
      if (!classObj) {
        const newClassId = await TrackerRepository.addClass(rawClass);
        classes = await TrackerRepository.getClasses();
        classObj = classes.find((c) => c.id === newClassId);
        if (classObj) {
          createdClasses.push(rawClass);
        }
      }

      if (!classObj) {
        errorCount++;
        rowErrors.push({
          rowNumber: excelRowNumber,
          studentName: rawName,
          className: rawClass,
          sectionName: rawSection,
          reason: 'تعذر إنشاء أو العثور على الصف',
        });
        continue;
      }

      // Resolve or create Section inside this Class
      let sectionObj = allSections.find(
        (s) => s.classId === classObj!.id && s.sectionName === rawSection
      );
      if (!sectionObj) {
        const newSecId = await TrackerRepository.addSection(classObj.id, rawSection);
        allSections = await TrackerRepository.getSections();
        sectionObj = allSections.find((s) => s.id === newSecId);
        if (sectionObj) {
          createdSections.push(`${rawClass} (${rawSection})`);
        }
      }

      if (!sectionObj) {
        errorCount++;
        rowErrors.push({
          rowNumber: excelRowNumber,
          studentName: rawName,
          className: rawClass,
          sectionName: rawSection,
          reason: 'تعذر إنشاء أو العثور على الشعبة',
        });
        continue;
      }

      // Add student to SQLite (with duplicate detection)
      const addRes = await TrackerRepository.addStudent(
        rawName,
        classObj.id,
        sectionObj.id
      );

      if (addRes.isDuplicate) {
        duplicateCount++;
        details.push(`صف ${excelRowNumber}: مكرر (موجود مسبقاً) — ${rawName} (${rawClass} - ${rawSection})`);
      } else {
        importedCount++;
        details.push(`صف ${excelRowNumber}: تمت الإضافة — ${rawName} (${rawClass} - ${rawSection})`);
      }
    }

    return {
      importedCount,
      duplicateCount,
      emptyRowCount,
      errorCount,
      createdClasses,
      createdSections,
      rowErrors,
      details,
    };
  }

  /**
   * 3. تصدير نتائج الطلاب إلى Excel (.xlsx)
   * نتائج_اليومي_اسم_الصف_الشعبة_التاريخ.xlsx
   */
  static exportResultsToExcel(
    grades: StudentCalculatedGrade[],
    className: string,
    sectionLabel: string,
    periodLabel: string
  ): string {
    const exportRows = grades.map((g, idx) => ({
      'ت': idx + 1,
      'اسم الطالب': g.studentName,
      'الصف': g.className,
      'الشعبة': g.sectionName,
      'أيام الحضور': `${g.presentDays}/${g.totalWorkingDays}`,
      'الحضور': Number(formatScore(g.attendanceScore)),
      'المشاركة': Number(formatScore(g.participationScore)),
      'الدفتر': Number(formatScore(g.notebookScore)),
      'الواجب': Number(formatScore(g.homeworkScore)),
      'السلوك': Number(formatScore(g.behaviorScore)),
      'الاختبار': Number(formatScore(g.quizScore)),
      'الدرجة النهائية': Number(formatScore(g.totalScore)),
    }));

    const worksheet = XLSX.utils.json_to_sheet(exportRows);
    worksheet['!cols'] = [
      { wch: 6 },
      { wch: 26 },
      { wch: 18 },
      { wch: 10 },
      { wch: 14 },
      { wch: 12 },
      { wch: 12 },
      { wch: 12 },
      { wch: 12 },
      { wch: 12 },
      { wch: 12 },
      { wch: 16 },
    ];

    const workbook = XLSX.utils.book_new();
    if (!workbook.Workbook) workbook.Workbook = {};
    workbook.Workbook.Views = [{ RTL: true }];

    XLSX.utils.book_append_sheet(workbook, worksheet, 'نتائج اليومي');

    const safeClass = className.replace(/\s+/g, '_');
    const safeSec = sectionLabel.replace(/\s+/g, '_');
    const safeDate = periodLabel.replace(/[^a-zA-Z0-9\u0600-\u06FF_-]/g, '_');
    const fileName = `نتائج_اليومي_${safeClass}_${safeSec}_${safeDate}.xlsx`;

    XLSX.writeFile(workbook, fileName);
    return fileName;
  }

  /**
   * تصدير نتائج ومعدلات الفترات الدراسية والامتحان التحريري والمعدل النهائي إلى Excel (.xlsx)
   */
  static exportComprehensiveResultsToExcel(
    reports: StudentComprehensiveGradeReport[],
    periods: StudyPeriodEntity[],
    className: string,
    sectionLabel: string,
    periodLabel: string
  ): string {
    const header: string[] = ['اسم الطالب', 'الصف', 'الشعبة'];
    if (periods.length > 0) {
      for (const p of periods) {
        header.push(`${p.periodName} اليومي`);
        header.push(`${p.periodName} التحريري`);
        header.push(`معدل ${p.periodName}`);
      }
      header.push('المعدل النهائي');
    } else {
      header.push(
        'الحضور',
        'المشاركة',
        'الدفتر',
        'الواجب',
        'السلوك',
        'الاختبار',
        'الدرجة اليومية',
        'المعدل النهائي'
      );
    }

    const rowsData: Record<string, string | number>[] = reports.map((rep) => {
      const rowObj: Record<string, string | number> = {
        'اسم الطالب': rep.studentName,
        'الصف': rep.className,
        'الشعبة': rep.sectionName,
      };
      if (periods.length > 0) {
        for (const p of periods) {
          const bd = rep.periodBreakdowns.find((b) => b.period.id === p.id);
          rowObj[`${p.periodName} اليومي`] = bd ? Number(formatScore(bd.dailyGrade)) : '--';
          rowObj[`${p.periodName} التحريري`] =
            bd && bd.writtenExamGrade !== null
              ? Number(formatScore(bd.writtenExamGrade))
              : '--';
          rowObj[`معدل ${p.periodName}`] =
            bd && bd.periodAverage !== null
              ? Number(formatScore(bd.periodAverage))
              : '--';
        }
        rowObj['المعدل النهائي'] =
          rep.finalAverage !== null ? Number(formatScore(rep.finalAverage)) : '--';
      } else {
        const g = rep.baseDailyGrade;
        rowObj['الحضور'] = Number(formatScore(g.attendanceScore));
        rowObj['المشاركة'] = Number(formatScore(g.participationScore));
        rowObj['الدفتر'] = Number(formatScore(g.notebookScore));
        rowObj['الواجب'] = Number(formatScore(g.homeworkScore));
        rowObj['السلوك'] = Number(formatScore(g.behaviorScore));
        rowObj['الاختبار'] = Number(formatScore(g.quizScore));
        rowObj['الدرجة اليومية'] = Number(formatScore(g.totalScore));
        rowObj['المعدل النهائي'] = '--';
      }
      return rowObj;
    });

    const worksheet = XLSX.utils.json_to_sheet(rowsData, { header });
    worksheet['!cols'] = header.map((h, i) => ({
      wch: i === 0 ? 26 : Math.max(14, h.length + 4),
    }));

    const workbook = XLSX.utils.book_new();
    if (!workbook.Workbook) workbook.Workbook = {};
    workbook.Workbook.Views = [{ RTL: true }];
    XLSX.utils.book_append_sheet(workbook, worksheet, 'التقارير والنتائج');

    const safeClass = className.replace(/\s+/g, '_');
    const safeSec = sectionLabel.replace(/\s+/g, '_');
    const safeDate = periodLabel.replace(/[^a-zA-Z0-9\u0600-\u06FF_-]/g, '_');
    const fileName = `نتائج_ومعدلات_${safeClass}_${safeSec}_${safeDate}.xlsx`;

    XLSX.writeFile(workbook, fileName);
    return fileName;
  }

  /**
   * 4. تصدير سجل عدد أيام الغياب إلى Excel (.xlsx)
   * يحتوي على الأعمدة الأربعة المطلوبة:
   * اسم الطالب | الصف | الشعبة | عدد أيام الغياب
   */
  static exportAbsenceToExcel(
    summaries: StudentAbsenceSummary[],
    className?: string,
    sectionName?: string
  ): string {
    const exportRows = summaries.map((item) => ({
      'اسم الطالب': item.studentName,
      'الصف': item.className,
      'الشعبة': item.sectionName,
      'عدد أيام الغياب': item.absentDays,
    }));

    const worksheet = XLSX.utils.json_to_sheet(exportRows, {
      header: ['اسم الطالب', 'الصف', 'الشعبة', 'عدد أيام الغياب'],
    });
    worksheet['!cols'] = [{ wch: 28 }, { wch: 20 }, { wch: 14 }, { wch: 18 }];

    const workbook = XLSX.utils.book_new();
    if (!workbook.Workbook) workbook.Workbook = {};
    workbook.Workbook.Views = [{ RTL: true }];

    XLSX.utils.book_append_sheet(workbook, worksheet, 'عدد أيام الغياب');

    const dateIso = new Date().toISOString().slice(0, 10);
    const safeClass = className?.trim().replace(/\s+/g, '_');
    const safeSec = sectionName?.trim().replace(/\s+/g, '_');

    let fileName = `سجل_أيام_الغياب_${dateIso}.xlsx`;
    if (safeClass && safeSec) {
      fileName = `سجل_غياب_${safeClass}_${safeSec}_${dateIso}.xlsx`;
    } else if (safeClass) {
      fileName = `سجل_غياب_${safeClass}_${dateIso}.xlsx`;
    }

    XLSX.writeFile(workbook, fileName);
    return fileName;
  }
}
