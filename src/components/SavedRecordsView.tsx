import React, { useEffect, useState } from 'react';
import {
  CalendarCheck2,
  ChevronLeft,
  FolderOpen,
  PlusCircle,
  Trash2,
} from 'lucide-react';
import {
  AppScreen,
  ClassEntity,
  SavedSessionSummary,
  SectionEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';
import { formatDisplayDate, getArabicDayName } from '../services/gradingService';

interface SavedRecordsViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (id: number) => void;
  onSelectSection: (id: number) => void;
  onOpenRecord: (classId: number, sectionId: number, date: string) => void;
  onNavigate: (screen: AppScreen) => void;
}

export const SavedRecordsView: React.FC<SavedRecordsViewProps> = ({
  classes,
  sections,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
  onOpenRecord,
  onNavigate,
}) => {
  const [filterClassId, setFilterClassId] = useState<number | 'ALL'>(selectedClassId);
  const [filterSectionId, setFilterSectionId] = useState<number | 'ALL'>('ALL');
  const [sessions, setSessions] = useState<SavedSessionSummary[]>([]);
  const [lessonTopicsBySession, setLessonTopicsBySession] = useState<Record<string, string | null>>({});

  const loadSessions = async () => {
    const clsParam = filterClassId === 'ALL' ? undefined : filterClassId;
    const secParam = filterSectionId === 'ALL' ? undefined : filterSectionId;
    const list = await TrackerRepository.getSavedSessionsSummary(clsParam, secParam);
    setSessions(list);
    const topics = await Promise.all(
      list.map(async (session) => {
        const key = `${session.recordDate}-${session.classId}-${session.sectionId}`;
        try {
          const topic = await TrackerRepository.getDailyPlanTopicForClassSectionDate(
            session.recordDate,
            session.classId,
            session.sectionId
          );
          return [key, topic] as const;
        } catch {
          return [key, null] as const;
        }
      })
    );
    setLessonTopicsBySession(Object.fromEntries(topics));
  };

  useEffect(() => {
    loadSessions();
  }, [filterClassId, filterSectionId]);

  const availableSections =
    filterClassId === 'ALL'
      ? []
      : sections.filter((s) => s.classId === filterClassId);

  const handleDeleteSession = async (
    e: React.MouseEvent,
    s: SavedSessionSummary
  ) => {
    e.stopPropagation();
    await TrackerRepository.deleteSavedSession(
      s.classId,
      s.sectionId,
      s.recordDate
    );
    await loadSessions();
  };

  return (
    <div className="space-y-5 pb-10">
      {/* Header & Filters */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            السجلات المحفوظة في SQLite
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            كل تاريخ له سجلاته المستقلة داخل جدول DailyRecords؛ اضغط على أي سجل لفتحه ومراجعته أو تعديله
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-slate-100">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              فلترة حسب الصف
            </label>
            <select
              value={filterClassId}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ALL') {
                  setFilterClassId('ALL');
                  setFilterSectionId('ALL');
                } else {
                  const num = Number(v);
                  setFilterClassId(num);
                  setFilterSectionId('ALL');
                  onSelectClass(num);
                }
              }}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            >
              <option value="ALL">جميع الصفوف</option>
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              فلترة حسب الشعبة
            </label>
            <select
              value={filterSectionId}
              disabled={filterClassId === 'ALL'}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ALL') {
                  setFilterSectionId('ALL');
                } else {
                  const num = Number(v);
                  setFilterSectionId(num);
                  onSelectSection(num);
                }
              }}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold disabled:opacity-50"
            >
              <option value="ALL">جميع الشعب</option>
              {availableSections.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  شعبة {sec.sectionName}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Saved Sessions Cards */}
      <div className="space-y-3">
        {sessions.length === 0 ? (
          <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
            <FolderOpen className="w-8 h-8 text-slate-400 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-800">
              لا توجد سجلات محفوظة مطابقة للفلتر الحالي
            </p>
            <button
              type="button"
              onClick={() => onNavigate('daily-tracking')}
              className="mt-4 min-h-[42px] px-5 py-2 bg-emerald-600 text-white rounded-xl text-xs font-bold inline-flex items-center gap-1.5"
            >
              <PlusCircle className="w-4 h-4" />
              <span>تسجيل سجل يومي جديد</span>
            </button>
          </div>
        ) : (
          sessions.map((s) => (
            <div
              key={`${s.recordDate}-${s.classId}-${s.sectionId}`}
              onClick={() => onOpenRecord(s.classId, s.sectionId, s.recordDate)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  onOpenRecord(s.classId, s.sectionId, s.recordDate);
                }
              }}
              className="w-full bg-white hover:bg-slate-50/90 rounded-2xl p-4 sm:p-5 border border-slate-200/80 transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-right group cursor-pointer"
            >
              <div className="flex items-start sm:items-center gap-3.5">
                <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center shrink-0 group-hover:bg-emerald-600 group-hover:text-white transition-colors">
                  <CalendarCheck2 className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-extrabold text-slate-900 font-mono-num">
                    {formatDisplayDate(s.recordDate)} ({getArabicDayName(s.recordDate)}) —{' '}
                    {s.className} — الشعبة {s.sectionName} —{' '}
                    <span className="text-emerald-700">محفوظ</span>
                  </h3>
                  <p className="mt-1.5 text-sm font-bold text-indigo-700">
                    موضوع الدرس: {lessonTopicsBySession[`${s.recordDate}-${s.classId}-${s.sectionId}`] || 'لا يوجد موضوع محفوظ لهذا اليوم'}
                  </p>
                  <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 mt-1 font-mono-num">
                    <span>حاضر: {s.presentCount}</span>
                    <span aria-hidden="true">·</span>
                    <span>غائب: {s.absentCount}</span>
                    <span aria-hidden="true">·</span>
                    <span>التقييمات المرصودة: {s.evaluationsCount}</span>
                    <span aria-hidden="true">·</span>
                    <span>الاختبارات: {s.testsCount}</span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
                <button
                  type="button"
                  onClick={(e) => handleDeleteSession(e, s)}
                  className="min-h-[38px] px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold"
                  title="حذف السجل"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
                <div className="flex items-center gap-1 text-xs font-bold text-emerald-700 group-hover:text-emerald-800 px-2">
                  <span>فتح وتعديل</span>
                  <ChevronLeft className="w-4 h-4" />
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
