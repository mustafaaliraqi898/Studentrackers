import React, { useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Download,
  FileDown,
  FileSpreadsheet,
  FileUp,
  FlaskConical,
} from 'lucide-react';
import {
  ClassEntity,
  ExcelImportResult,
  SectionEntity,
  StudentCalculatedGrade,
} from '../types';
import { ExcelService } from '../services/excelService';
import { GradingService } from '../services/gradingService';

interface ExcelCenterViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (classId: number) => void;
  onSelectSection: (sectionId: number) => void;
  onRefreshData: () => Promise<void>;
}

export const ExcelCenterView: React.FC<ExcelCenterViewProps> = ({
  classes,
  sections,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
  onRefreshData,
}) => {
  const [fallbackClassId, setFallbackClassId] = useState<number>(selectedClassId);
  const [importResult, setImportResult] = useState<ExcelImportResult | null>(
    null
  );
  const [importError, setImportError] = useState<string | null>(null);
  const [isImporting, setIsImporting] = useState(false);

  const [exportClassId, setExportClassId] = useState<number>(selectedClassId);
  const [exportSectionId, setExportSectionId] = useState<number | 'ALL'>(
    selectedSectionId
  );
  const [exportMonth, setExportMonth] = useState<string>('2026-10');
  const [exportedFileName, setExportedFileName] = useState<string | null>(null);

  const exportSections = sections.filter((s) => s.classId === exportClassId);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsImporting(true);
    setImportError(null);
    setImportResult(null);

    try {
      const res = await ExcelService.importStudentsFromExcel(
        file,
        fallbackClassId
      );
      setImportResult(res);
      await onRefreshData();
    } catch (err: unknown) {
      setImportError(
        err instanceof Error ? err.message : 'حدث خطأ أثناء قراءة ملف Excel.'
      );
    } finally {
      setIsImporting(false);
      e.target.value = '';
    }
  };

  const handleExportResults = async () => {
    const cls = classes.find((c) => c.id === exportClassId) || classes[0];
    const secIdParam = exportSectionId === 'ALL' ? undefined : exportSectionId;
    const secLabel =
      exportSectionId === 'ALL'
        ? 'جميع_الشعب'
        : `شعبة_${
            sections.find((s) => s.id === exportSectionId)?.sectionName || ''
          }`;

    const grades: StudentCalculatedGrade[] =
      await GradingService.calculateGradesForClass(
        exportClassId,
        secIdParam,
        exportMonth
      );

    const sorted = [...grades].sort((a, b) => b.totalScore - a.totalScore);
    const periodLabel = exportMonth === 'ALL' ? 'كامل_الفترة' : exportMonth;

    const fname = ExcelService.exportResultsToExcel(
      sorted,
      cls.className,
      secLabel,
      periodLabel
    );
    setExportedFileName(fname);
  };

  return (
    <div className="space-y-6 pb-10">
      {/* Header */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80">
        <h2 className="text-lg font-extrabold text-slate-900">
          مركز ملفات Excel (تصدير القالب · استيراد الطلاب · تصدير النتائج)
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          دعم كامل للغة العربية (RTL) والتحقق الصارم من البيانات مع حفظ فوري في قاعدة بيانات SQLite
        </p>
      </div>

      {/* Step 1: Export Empty Student Template (تصدير قالب الطلاب) */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <div className="w-11 h-11 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-extrabold text-slate-900">
                أولاً: تصدير قالب Excel فارغ للطلاب
              </h3>
              <p className="text-xs text-slate-500 mt-0.5 leading-relaxed">
                ينشئ ملف <strong>قالب_استيراد_الطلاب.xlsx</strong> جاهزاً بالأعمدة الثلاثة:{' '}
                <strong>اسم الطالب | الصف | الشعبة</strong> لتعبئته ثم إعادة استيراده.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => ExcelService.downloadEmptyStudentsTemplate()}
              className="min-h-[44px] px-5 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-extrabold flex items-center gap-2 whitespace-nowrap transition-colors"
            >
              <Download className="w-4 h-4" />
              <span>تصدير قالب الطلاب</span>
            </button>

            <button
              type="button"
              onClick={() => ExcelService.downloadEdgeCaseTestExcel()}
              className="min-h-[44px] px-3.5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1.5 whitespace-nowrap transition-colors"
              title="ملف اختبار يحتوي على طلاب جدد، مكررين، صفوف فارغة، وصفوف ناقصة لاختبار التحقق"
            >
              <FlaskConical className="w-4 h-4 text-emerald-700" />
              <span>تنزيل ملف اختبار الحالات والأخطاء</span>
            </button>
          </div>
        </div>
      </section>

      {/* Step 2: Import Students from Excel */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0">
            <FileUp className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              ثانياً: استيراد الطلاب من Excel إلى SQLite
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              يقرأ الأعمدة (اسم الطالب | الصف | الشعبة)، يتجاهل الصفوف الفارغة، يمنع التكرار، ويكتشف الصفوف التي تحتوي أخطاء
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1.5">
              الصف الافتراضي (في حال كان الملف يحتوي على عمودي الاسم والشعبة فقط):
            </label>
            <select
              value={fallbackClassId}
              onChange={(e) => setFallbackClassId(Number(e.target.value))}
              className="w-full min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="w-full min-h-[44px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 cursor-pointer transition-colors">
              <FileSpreadsheet className="w-4 h-4" />
              <span>
                {isImporting
                  ? 'جاري قراءة الملف والتحقق...'
                  : 'استيراد الطلاب من Excel'}
              </span>
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                onChange={handleFileChange}
                className="hidden"
              />
            </label>
          </div>
        </div>

        {importError && (
          <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-3.5 text-xs font-bold">
            {importError}
          </div>
        )}

        {/* Detailed Import Report matching Section 19 */}
        {importResult && (
          <div className="bg-slate-50 border border-slate-200 rounded-2xl p-4 space-y-4">
            <div className="flex items-center gap-2 text-emerald-800 font-extrabold text-sm">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
              <span>تم استيراد الملف بنجاح</span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 text-center font-mono-num">
              <div className="bg-emerald-50 border border-emerald-200 p-3 rounded-xl">
                <span className="text-xs text-emerald-800 block font-bold">
                  الطلاب الجدد
                </span>
                <span className="text-xl font-extrabold text-emerald-700 mt-0.5 block">
                  {importResult.importedCount}
                </span>
              </div>

              <div className="bg-amber-50 border border-amber-200 p-3 rounded-xl">
                <span className="text-xs text-amber-900 block font-bold">
                  طلاب موجودون مسبقاً
                </span>
                <span className="text-xl font-extrabold text-amber-700 mt-0.5 block">
                  {importResult.duplicateCount}
                </span>
              </div>

              <div className="bg-white border border-slate-200 p-3 rounded-xl">
                <span className="text-xs text-slate-600 block font-bold">
                  صفوف فارغة
                </span>
                <span className="text-xl font-extrabold text-slate-800 mt-0.5 block">
                  {importResult.emptyRowCount}
                </span>
              </div>

              <div className="bg-rose-50 border border-rose-200 p-3 rounded-xl">
                <span className="text-xs text-rose-800 block font-bold">
                  صفوف تحتوي أخطاء
                </span>
                <span className="text-xl font-extrabold text-rose-700 mt-0.5 block">
                  {importResult.errorCount}
                </span>
              </div>
            </div>

            {/* Detailed Row Errors List if any */}
            {importResult.rowErrors.length > 0 && (
              <div className="bg-rose-50/80 border border-rose-200 rounded-xl p-3.5 space-y-2">
                <div className="flex items-center gap-1.5 text-xs font-extrabold text-rose-900">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>تفاصيل الصفوف التي تحتوي أخطاء في ملف Excel:</span>
                </div>
                <div className="divide-y divide-rose-200/60 text-xs font-mono-num">
                  {importResult.rowErrors.map((err, i) => (
                    <div
                      key={`${err.rowNumber}-${i}`}
                      className="py-1.5 flex flex-wrap items-center justify-between gap-2 text-rose-950"
                    >
                      <span className="font-bold">
                        صف رقم {err.rowNumber}: {err.reason}
                      </span>
                      <span className="text-rose-700">
                        [الاسم: {err.studentName} | الصف: {err.className} | الشعبة:{' '}
                        {err.sectionName}]
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </section>

      {/* Step 3: Export Results to Excel */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-100">
          <div className="w-11 h-11 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
            <FileDown className="w-5 h-5" />
          </div>
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              ثالثاً: تصدير نتائج الطلاب إلى Excel
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              يصدّر: اسم الطالب، الصف، الشعبة، الحضور، المشاركة، الدفتر، الواجب، السلوك، الاختبار، والدرجة النهائية
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              الصف الدراسي
            </label>
            <select
              value={exportClassId}
              onChange={(e) => {
                const newCls = Number(e.target.value);
                setExportClassId(newCls);
                setExportSectionId('ALL');
                onSelectClass(newCls);
              }}
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              الشعبة
            </label>
            <select
              value={exportSectionId}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ALL') {
                  setExportSectionId('ALL');
                } else {
                  const num = Number(v);
                  setExportSectionId(num);
                  onSelectSection(num);
                }
              }}
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            >
              <option value="ALL">جميع الشعب</option>
              {exportSections.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  شعبة {sec.sectionName}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              الشهر / الفترة
            </label>
            <select
              value={exportMonth}
              onChange={(e) => setExportMonth(e.target.value)}
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold font-mono-num"
            >
              <option value="2026-10">تشرين الأول (10 / 2026)</option>
              <option value="2026-11">تشرين الثاني (11 / 2026)</option>
              <option value="2026-12">كانون الأول (12 / 2026)</option>
              <option value="ALL">جميع الأشهر المسجلة</option>
            </select>
          </div>
        </div>

        <div className="pt-2 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <p className="text-xs text-slate-500 font-mono-num">
            الاسم المقترح: نتائج_اليومي_اسم_الصف_الشعبة_التاريخ.xlsx
          </p>

          <button
            type="button"
            onClick={handleExportResults}
            className="min-h-[46px] px-6 py-2.5 bg-slate-900 hover:bg-slate-800 text-white font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 whitespace-nowrap transition-colors"
          >
            <FileDown className="w-4 h-4" />
            <span>تصدير النتائج إلى Excel</span>
          </button>
        </div>

        {exportedFileName && (
          <div className="bg-emerald-50 border border-emerald-200 text-emerald-900 rounded-xl p-3 text-xs font-bold flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>تم تصدير وتنزيل الملف بنجاح: {exportedFileName}</span>
          </div>
        )}
      </section>
    </div>
  );
};
