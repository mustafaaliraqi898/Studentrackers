import React, { useEffect, useState } from 'react';
import {
  ClipboardList,
  Eye,
  Search,
  UserCheck,
  UserX,
  X,
} from 'lucide-react';
import {
  AttendanceStatus,
  ClassEntity,
  SectionEntity,
  StudentEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';

interface StudentSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  classes: ClassEntity[];
  sections: SectionEntity[];
  defaultClassId?: number | 'ALL';
  defaultSectionId?: number | 'ALL';
  currentDate: string;
  onOpenStudentProfile: (student: StudentEntity) => void;
  onOpenStudentInDailyTracking: (student: StudentEntity) => void;
  onQuickAttendanceToggle?: (
    student: StudentEntity,
    status: AttendanceStatus
  ) => Promise<void>;
}

export const StudentSearchModal: React.FC<StudentSearchModalProps> = ({
  isOpen,
  onClose,
  classes,
  sections,
  defaultClassId = 'ALL',
  defaultSectionId = 'ALL',
  currentDate,
  onOpenStudentProfile,
  onOpenStudentInDailyTracking,
  onQuickAttendanceToggle,
}) => {
  const [query, setQuery] = useState('');
  const [filterClassId, setFilterClassId] = useState<number | 'ALL'>(
    defaultClassId
  );
  const [filterSectionId, setFilterSectionId] = useState<number | 'ALL'>(
    defaultSectionId
  );
  const [results, setResults] = useState<StudentEntity[]>([]);
  const [recordedStatusMap, setRecordedStatusMap] = useState<
    Record<number, AttendanceStatus | undefined>
  >({});
  const [pendingConfirmAbsentStudent, setPendingConfirmAbsentStudent] =
    useState<StudentEntity | null>(null);

  useEffect(() => {
    if (isOpen) {
      setFilterClassId(defaultClassId);
      setFilterSectionId(defaultSectionId);
    }
  }, [isOpen, defaultClassId, defaultSectionId]);

  useEffect(() => {
    let active = true;
    async function runSearch() {
      if (!isOpen) return;
      const list = await TrackerRepository.searchStudents(
        query,
        filterClassId,
        filterSectionId,
        false
      );
      if (!active) return;
      setResults(list);

      // Load current date saved attendance status for matching sections
      const statusAcc: Record<number, AttendanceStatus | undefined> = {};
      const pairKeys = Array.from(
        new Set(list.map((st) => `${st.classId}:${st.sectionId}`))
      );
      for (const key of pairKeys) {
        const [cId, sId] = key.split(':').map(Number);
        const sessionMap = await TrackerRepository.getSessionEntries(
          cId,
          sId,
          currentDate
        );
        for (const [stIdStr, entry] of Object.entries(sessionMap)) {
          statusAcc[Number(stIdStr)] = entry.attendance;
        }
      }
      if (active) setRecordedStatusMap(statusAcc);
    }
    runSearch();
    return () => {
      active = false;
    };
  }, [isOpen, query, filterClassId, filterSectionId, currentDate]);

  if (!isOpen) return null;

  const availableSections =
    filterClassId === 'ALL'
      ? []
      : sections.filter((s) => s.classId === filterClassId);

  const handleQuickMark = async (
    student: StudentEntity,
    status: AttendanceStatus
  ) => {
    if (!onQuickAttendanceToggle) return;
    await onQuickAttendanceToggle(student, status);
    setRecordedStatusMap((prev) => ({ ...prev, [student.id]: status }));
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start sm:items-center justify-center p-3 sm:p-4 bg-slate-900/60 backdrop-blur-xs overflow-y-auto">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-xl bg-white rounded-2xl shadow-xl border border-slate-200 overflow-hidden my-4"
      >
        {/* Modal Header */}
        <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-900 text-white">
          <div className="flex items-center gap-2.5">
            <Search className="w-5 h-5 text-emerald-400" />
            <div>
              <h3 className="text-base font-extrabold">بحث سريع عن طالب</h3>
              <p className="text-[11px] text-slate-300">
                ابحث بالاسم أو جزء من الاسم وحدد الحضور أو افتح ملف الطالب فوراً
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="min-h-[38px] min-w-[38px] rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 flex items-center justify-center"
            aria-label="إغلاق البحث"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Search Input & Class/Section Filters */}
        <div className="p-4 bg-slate-50 border-b border-slate-200/80 space-y-3">
          <div className="relative">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              autoFocus
              placeholder="اكتب اسم الطالب أو جزء من الاسم (مثال: أحمد، علي)..."
              className="w-full min-h-[46px] pr-10 pl-9 py-2.5 bg-white border border-slate-300 rounded-xl text-sm font-bold text-slate-900 focus:outline-none focus:border-emerald-600"
            />
            <Search className="w-4 h-4 text-slate-400 absolute right-3.5 top-1/2 -translate-y-1/2" />
            {query && (
              <button
                type="button"
                onClick={() => setQuery('')}
                className="p-1 text-slate-400 hover:text-slate-700 absolute left-2.5 top-1/2 -translate-y-1/2"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>

          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">
                تصفية حسب الصف
              </label>
              <select
                value={filterClassId}
                onChange={(e) => {
                  const v = e.target.value;
                  if (v === 'ALL') {
                    setFilterClassId('ALL');
                    setFilterSectionId('ALL');
                  } else {
                    setFilterClassId(Number(v));
                    setFilterSectionId('ALL');
                  }
                }}
                className="w-full min-h-[40px] px-3 py-1.5 bg-white border border-slate-200 rounded-xl text-xs font-bold"
              >
                <option value="ALL">جميع الصفوف</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.className}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">
                تصفية حسب الشعبة
              </label>
              <select
                value={filterSectionId}
                disabled={filterClassId === 'ALL'}
                onChange={(e) => {
                  const v = e.target.value;
                  setFilterSectionId(v === 'ALL' ? 'ALL' : Number(v));
                }}
                className="w-full min-h-[40px] px-3 py-1.5 bg-white border border-slate-200 rounded-xl text-xs font-bold disabled:opacity-50"
              >
                <option value="ALL">جميع الشعب</option>
                {availableSections.map((s) => (
                  <option key={s.id} value={s.id}>
                    شعبة {s.sectionName}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Results List */}
        <div className="max-h-[55vh] overflow-y-auto divide-y divide-slate-100 p-2">
          {results.length === 0 ? (
            <div className="py-10 text-center space-y-1">
              <p className="text-sm font-bold text-slate-700">
                لا توجد نتائج مطابقة للبحث "{query}"
              </p>
              <p className="text-xs text-slate-500">
                جرّب البحث بجزء من الاسم أو اختيار "جميع الصفوف"
              </p>
            </div>
          ) : (
            results.map((st) => {
              const marked = recordedStatusMap[st.id];
              const isAlreadyAbsent = marked === 'absent';
              return (
                <div
                  key={st.id}
                  className="p-3 hover:bg-slate-50 rounded-xl transition-colors flex flex-col sm:flex-row sm:items-center justify-between gap-3"
                >
                  <div>
                    <button
                      type="button"
                      onClick={() => {
                        onOpenStudentProfile(st);
                        onClose();
                      }}
                      className="text-sm font-extrabold text-slate-900 hover:text-emerald-700 text-right block"
                    >
                      {st.studentName}
                    </button>
                    <p className="text-xs text-slate-500 mt-0.5">
                      {st.className} – شعبة {st.sectionName}
                      {isAlreadyAbsent && (
                        <span className="text-rose-700 font-bold mr-1.5">
                          • الحالة اليوم: غائب (0/19)
                        </span>
                      )}
                      {marked === 'present' && (
                        <span className="text-emerald-700 font-bold mr-1.5">
                          • الحالة اليوم: حاضر
                        </span>
                      )}
                    </p>
                  </div>

                  {/* Quick Actions: Present / Absent Toggle / Open in Daily / Profile */}
                  <div className="flex flex-wrap items-center gap-1.5">
                    {onQuickAttendanceToggle && (
                      <>
                        {isAlreadyAbsent ? (
                          <button
                            type="button"
                            onClick={() => handleQuickMark(st, 'present')}
                            className="min-h-[38px] px-3.5 py-1.5 rounded-lg text-xs font-extrabold flex items-center gap-1 transition-all bg-emerald-600 text-white hover:bg-emerald-700"
                            title={`تسجيل حاضر لتاريخ ${currentDate}`}
                          >
                            <UserCheck className="w-3.5 h-3.5" />
                            <span>حاضر</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => handleQuickMark(st, 'absent')}
                            className="min-h-[38px] px-3.5 py-1.5 rounded-lg text-xs font-extrabold flex items-center gap-1 transition-all bg-rose-600 text-white hover:bg-rose-700"
                            title={`تسجيل غائب لتاريخ ${currentDate}`}
                          >
                            <UserX className="w-3.5 h-3.5" />
                            <span>غائب</span>
                          </button>
                        )}
                      </>
                    )}

                    <button
                      type="button"
                      onClick={() => {
                        onOpenStudentInDailyTracking(st);
                        onClose();
                      }}
                      className="min-h-[38px] px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-lg text-xs font-bold flex items-center gap-1"
                    >
                      <ClipboardList className="w-3.5 h-3.5" />
                      <span>فتح في اليومي</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => {
                        onOpenStudentProfile(st);
                        onClose();
                      }}
                      className="min-h-[38px] px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-lg text-xs font-bold flex items-center gap-1"
                    >
                      <Eye className="w-3.5 h-3.5" />
                      <span>الملف</span>
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
