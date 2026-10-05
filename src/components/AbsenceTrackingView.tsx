import React, { useEffect, useMemo, useState } from 'react';
import { FileSpreadsheet } from 'lucide-react';
import {
  ClassEntity,
  SectionEntity,
  StudentAbsenceSummary,
} from '../types';
import {
  GradingService,
  formatAbsenceDaysLabel,
} from '../services/gradingService';
import { ExcelService } from '../services/excelService';

interface AbsenceTrackingViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  recordsVersion: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
}

export const AbsenceTrackingView: React.FC<AbsenceTrackingViewProps> = ({
  recordsVersion,
  classes,
  sections,
  onSelectClass,
  onSelectSection,
}) => {
  const [filterClassId, setFilterClassId] = useState<number | null>(null);
  const [filterSectionId, setFilterSectionId] = useState<number | null>(null);
  const [summaries, setSummaries] = useState<StudentAbsenceSummary[]>([]);
  const [exportMessage, setExportMessage] = useState<string | null>(null);

  const availableSections = useMemo(() => {
    if (filterClassId === null) return [];
    return sections.filter((s) => s.classId === filterClassId);
  }, [sections, filterClassId]);

  useEffect(() => {
    if (filterClassId === null) {
      setFilterSectionId(null);
    } else if (
      filterSectionId !== null &&
      !availableSections.some((s) => s.id === filterSectionId)
    ) {
      setFilterSectionId(null);
    }
  }, [filterClassId, availableSections, filterSectionId]);

  useEffect(() => {
    let active = true;
    async function loadAbsenceSummaries() {
      const list = await GradingService.calculateAbsenceSummaries(
        filterClassId ?? undefined,
        filterClassId === null ? undefined : (filterSectionId ?? undefined),
        'ALL'
      );
      if (!active) return;
      setSummaries(list);
    }
    loadAbsenceSummaries();
    return () => {
      active = false;
    };
  }, [
    recordsVersion,
    classes.length,
    sections.length,
    filterClassId,
    filterSectionId,
  ]);

  const handleExportExcel = () => {
    const selectedClassObj = classes.find((c) => c.id === filterClassId);
    const selectedSecObj = availableSections.find(
      (s) => s.id === filterSectionId
    );
    const fileName = ExcelService.exportAbsenceToExcel(
      summaries,
      selectedClassObj?.className,
      selectedSecObj?.sectionName
    );
    setExportMessage(`تم تصدير الملف بنجاح: ${fileName}`);
    setTimeout(() => setExportMessage(null), 4000);
  };

  return (
    <div className="space-y-4 pb-12">
      {/* Filters & Export to Excel Card */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-4 space-y-3">
        {/* 1. Class Filter */}
        <div className="space-y-1.5">
          <div className="text-xs font-extrabold text-slate-800">الصف:</div>
          <div className="flex items-center gap-2 overflow-x-auto pb-1">
            <button
              type="button"
              onClick={() => {
                setFilterClassId(null);
                setFilterSectionId(null);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                filterClassId === null
                  ? 'bg-slate-900 text-white'
                  : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
              }`}
            >
              الكل
            </button>
            {classes.map((cls) => {
              const isSelected = filterClassId === cls.id;
              return (
                <button
                  key={cls.id}
                  type="button"
                  onClick={() => {
                    if (filterClassId !== cls.id) {
                      setFilterClassId(cls.id);
                      setFilterSectionId(null);
                      onSelectClass(cls.id);
                    }
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                    isSelected
                      ? 'bg-slate-900 text-white'
                      : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                  }`}
                >
                  {cls.className}
                </button>
              );
            })}
          </div>
        </div>

        {/* 2. Section Filter */}
        <div className="space-y-1.5">
          <div className="text-xs font-extrabold text-slate-800">الشعبة:</div>
          <div className="flex items-center gap-2 overflow-x-auto pb-1">
            <button
              type="button"
              onClick={() => setFilterSectionId(null)}
              className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                filterSectionId === null
                  ? 'bg-teal-700 text-white'
                  : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
              }`}
            >
              الكل
            </button>
            {availableSections.map((sec) => {
              const isSelected = filterSectionId === sec.id;
              return (
                <button
                  key={sec.id}
                  type="button"
                  onClick={() => {
                    setFilterSectionId(sec.id);
                    onSelectSection(sec.id);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold shrink-0 transition-colors ${
                    isSelected
                      ? 'bg-teal-700 text-white'
                      : 'bg-slate-100 text-slate-800 hover:bg-slate-200'
                  }`}
                >
                  {sec.sectionName}
                </button>
              );
            })}
          </div>
        </div>

        {/* 3. Export to Excel Button */}
        <button
          type="button"
          onClick={handleExportExcel}
          className="w-full py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs sm:text-sm font-extrabold flex items-center justify-center gap-2 transition-colors shadow-2xs"
        >
          <FileSpreadsheet className="w-4 h-4" />
          <span>تصدير إلى Excel</span>
        </button>

        {exportMessage && (
          <div className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-xl px-3 py-2 text-center">
            {exportMessage}
          </div>
        )}
      </div>

      {/* Table: اسم الطالب | الصف والشعبة | عدد أيام الغياب */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
        <div className="px-4 py-3.5 bg-slate-900 text-white grid grid-cols-12 items-center gap-2 text-xs sm:text-sm font-extrabold">
          <span className="col-span-5">اسم الطالب</span>
          <span className="col-span-4">الصف والشعبة</span>
          <span className="col-span-3 text-left">عدد أيام الغياب</span>
        </div>

        {summaries.length === 0 ? (
          <div className="p-6 text-center text-xs font-bold text-slate-500">
            لا يوجد طلاب ضمن الفلتر المحدد حالياً
          </div>
        ) : (
          <div className="divide-y divide-slate-200/70">
            {summaries.map((item) => (
              <div
                key={item.studentId}
                className="px-4 py-3 grid grid-cols-12 items-center gap-2 bg-white hover:bg-slate-50/70 transition-colors"
              >
                <span className="col-span-5 text-sm font-extrabold text-slate-900 truncate">
                  {item.studentName}
                </span>
                <span className="col-span-4 text-xs sm:text-sm font-bold text-slate-700 truncate">
                  {item.className} / {item.sectionName}
                </span>
                <span
                  className={`col-span-3 text-left text-sm font-extrabold font-mono-num ${
                    item.absentDays > 0 ? 'text-rose-700' : 'text-slate-700'
                  }`}
                >
                  {formatAbsenceDaysLabel(item.absentDays)}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

