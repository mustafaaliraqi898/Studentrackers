import React, { useEffect, useState } from 'react';
import {
  ClassEntity,
  SectionEntity,
  StudentAbsenceSummary,
} from '../types';
import {
  GradingService,
  formatAbsenceDaysLabel,
} from '../services/gradingService';

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
}) => {
  const [summaries, setSummaries] = useState<StudentAbsenceSummary[]>([]);

  useEffect(() => {
    let active = true;
    async function loadAbsenceSummaries() {
      const list = await GradingService.calculateAbsenceSummaries(
        undefined,
        undefined,
        'ALL'
      );
      if (!active) return;
      setSummaries(list);
    }
    loadAbsenceSummaries();
    return () => {
      active = false;
    };
  }, [recordsVersion, classes.length, sections.length]);

  return (
    <div className="space-y-4 pb-12">
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
        <div className="px-4 py-3.5 bg-slate-900 text-white flex items-center justify-between text-xs sm:text-sm font-extrabold">
          <span>اسم الطالب</span>
          <span>عدد أيام الغياب</span>
        </div>

        {summaries.length === 0 ? (
          <div className="p-6 text-center text-xs font-bold text-slate-500">
            لا يوجد طلاب مسجلون حالياً
          </div>
        ) : (
          <div className="divide-y divide-slate-200/70">
            {summaries.map((item) => (
              <div
                key={item.studentId}
                className="px-4 py-3 flex items-center justify-between gap-3 bg-white hover:bg-slate-50/70 transition-colors"
              >
                <span className="text-sm font-extrabold text-slate-900 truncate min-w-0 flex-1">
                  {item.studentName}
                </span>
                <span
                  className={`text-sm font-extrabold font-mono-num shrink-0 ${
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
