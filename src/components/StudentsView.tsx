import React, { useEffect, useState } from 'react';
import {
  ArrowLeftRight,
  Check,
  Edit2,
  Eye,
  FileSpreadsheet,
  Plus,
  Power,
  Search,
  Trash2,
  UserPlus,
  X,
} from 'lucide-react';
import {
  AppScreen,
  ClassEntity,
  SectionEntity,
  StudentCalculatedGrade,
  StudentEntity,
} from '../types';
import {
  GradingService,
  formatDisplayDate,
  formatScore,
} from '../services/gradingService';

interface StudentsViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  students: StudentEntity[];
  selectedClassId: number;
  selectedSectionId: number;
  onSelectClass: (classId: number) => void;
  onSelectSection: (sectionId: number) => void;
  onAddStudent: (
    name: string,
    classId: number,
    sectionId: number
  ) => Promise<{ isDuplicate: boolean }>;
  onUpdateStudentName: (
    studentId: number,
    newName: string
  ) => Promise<{ updated: boolean; isDuplicate: boolean }>;
  onToggleStudentActive: (studentId: number, isActive: boolean) => Promise<void>;
  onTransferStudent: (
    studentId: number,
    targetClassId: number,
    targetSectionId: number
  ) => Promise<{ transferred: boolean; isDuplicate: boolean }>;
  onDeleteStudent: (studentId: number) => Promise<void>;
  onNavigate: (screen: AppScreen, params?: { studentId?: number }) => void;
}

export const StudentsView: React.FC<StudentsViewProps> = ({
  classes,
  sections,
  students,
  selectedClassId,
  selectedSectionId,
  onSelectClass,
  onSelectSection,
  onAddStudent,
  onUpdateStudentName,
  onToggleStudentActive,
  onTransferStudent,
  onDeleteStudent,
  onNavigate,
}) => {
  const [newStudentName, setNewStudentName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [showInactive, setShowInactive] = useState(false);
  const [duplicateAlert, setDuplicateAlert] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);

  // Transfer student state
  const [transferringId, setTransferringId] = useState<number | null>(null);
  const [targetClassId, setTargetClassId] = useState<number>(selectedClassId);
  const [targetSectionId, setTargetSectionId] = useState<number>(selectedSectionId);

  const [gradesMap, setGradesMap] = useState<
    Record<number, StudentCalculatedGrade>
  >({});
  const [behaviorZeroCounts, setBehaviorZeroCounts] = useState<
    Record<number, number>
  >({});

  const currentClass =
    classes.find((c) => c.id === selectedClassId) || classes[0];
  const classSections = sections.filter((s) => s.classId === currentClass?.id);
  const currentSection =
    classSections.find((s) => s.id === selectedSectionId) || classSections[0];

  const sectionStudents = students.filter((s) => {
    const inSection =
      s.classId === currentClass?.id && s.sectionId === currentSection?.id;
    if (!inSection) return false;
    if (!showInactive && !s.isActive) return false;
    if (searchQuery.trim() && !s.studentName.includes(searchQuery.trim())) {
      return false;
    }
    return true;
  });

  useEffect(() => {
    let active = true;
    async function loadGrades() {
      if (!currentClass || !currentSection) return;
      const [list, zeroCounts] = await Promise.all([
        GradingService.calculateGradesForClass(
          currentClass.id,
          currentSection.id
        ),
        GradingService.getBehaviorZeroCounts(),
      ]);
      if (!active) return;
      const map: Record<number, StudentCalculatedGrade> = {};
      for (const g of list) {
        map[g.studentId] = g;
      }
      setGradesMap(map);
      setBehaviorZeroCounts(zeroCounts);
    }
    loadGrades();
    return () => {
      active = false;
    };
  }, [currentClass?.id, currentSection?.id, students]);

  const handleAddSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setDuplicateAlert(null);
    const trimmed = newStudentName.trim();
    if (!trimmed || !currentClass || !currentSection) return;

    const res = await onAddStudent(trimmed, currentClass.id, currentSection.id);
    if (res.isDuplicate) {
      setDuplicateAlert(
        `الطالب "${trimmed}" موجود مسبقاً في ${currentClass.className} (تم منع التكرار على مستوى الصف بالكامل).`
      );
    } else {
      setNewStudentName('');
    }
  };

  const transferAvailableSections = sections.filter(
    (s) => s.classId === targetClassId
  );

  return (
    <div className="space-y-5 pb-10">
      {/* Header & Class/Section Filter */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-extrabold text-slate-900">
              إدارة الطلاب والبحث السريع
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              الصف: {currentClass?.className} · الشعبة: {currentSection?.sectionName} ({sectionStudents.length} طلاب)
            </p>
          </div>

          <button
            type="button"
            onClick={() => onNavigate('excel-center')}
            className="min-h-[40px] px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-800 rounded-xl text-xs font-bold flex items-center gap-1.5 self-start sm:self-auto whitespace-nowrap"
          >
            <FileSpreadsheet className="w-4 h-4 text-emerald-700" />
            <span>قالب واستيراد الطلاب (Excel)</span>
          </button>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 border-t border-slate-100">
          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الصف الدراسي
            </label>
            <select
              value={currentClass?.id || ''}
              onChange={(e) => onSelectClass(Number(e.target.value))}
              className="w-full min-h-[42px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-extrabold"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-600 mb-1">
              الشعبة
            </label>
            <div className="flex items-center gap-1.5 p-1 bg-slate-100 rounded-xl min-h-[42px]">
              {classSections.map((sec) => {
                const active = sec.id === currentSection?.id;
                return (
                  <button
                    key={sec.id}
                    type="button"
                    onClick={() => onSelectSection(sec.id)}
                    className={`flex-1 min-h-[34px] px-3 py-1 rounded-lg text-xs font-extrabold transition-all whitespace-nowrap ${
                      active
                        ? 'bg-white text-slate-900 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    شعبة {sec.sectionName}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Search Input + Show Inactive Toggle */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 pt-2 border-t border-slate-100">
          <div className="relative flex-1">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="ابحث عن طالب بالاسم أو جزء من الاسم..."
              className="w-full min-h-[42px] pr-9 pl-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            />
            <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2" />
          </div>

          <label className="flex items-center gap-2 text-xs font-bold text-slate-600 cursor-pointer shrink-0">
            <input
              type="checkbox"
              checked={showInactive}
              onChange={(e) => setShowInactive(e.target.checked)}
              className="w-4 h-4 accent-emerald-600 rounded"
            />
            <span>إظهار الطلاب غير الفعالين (المعطلين)</span>
          </label>
        </div>
      </div>

      {/* Add Student Form */}
      <form
        onSubmit={handleAddSubmit}
        className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3"
      >
        <label
          htmlFor="new-student-input"
          className="block text-xs font-bold text-slate-700"
        >
          إضافة طالب جديد إلى {currentClass?.className} — شعبة{' '}
          {currentSection?.sectionName}
        </label>
        <div className="flex items-center gap-2">
          <input
            id="new-student-input"
            type="text"
            value={newStudentName}
            onChange={(e) => setNewStudentName(e.target.value)}
            placeholder="اكتب اسم الطالب الثلاثي أو الثنائي..."
            className="flex-1 min-h-[46px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 focus:outline-none focus:border-emerald-600 focus:bg-white"
          />
          <button
            type="submit"
            disabled={!newStudentName.trim()}
            className="min-h-[46px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-200 disabled:text-slate-400 text-white font-bold text-sm rounded-xl flex items-center gap-1.5 shrink-0 whitespace-nowrap"
          >
            <Plus className="w-4 h-4" />
            <span>إضافة طالب</span>
          </button>
        </div>

        {duplicateAlert && (
          <div className="bg-amber-50 border border-amber-200 text-amber-900 rounded-xl p-3 text-xs font-bold">
            {duplicateAlert}
          </div>
        )}
      </form>

      {/* Students List */}
      <div className="space-y-2.5">
        {sectionStudents.length === 0 ? (
          <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
            <UserPlus className="w-8 h-8 text-slate-400 mx-auto mb-2" />
            <p className="text-sm font-bold text-slate-700">
              لا يوجد طلاب مطابقون في هذه الشعبة حالياً
            </p>
          </div>
        ) : (
          sectionStudents.map((student, idx) => {
            const grade = gradesMap[student.id];
            const behaviorZeroCount =
              grade?.behaviorRatings.filter((r) => r.value === 0).length ??
              behaviorZeroCounts[student.id] ??
              0;
            const hasFrequentBehaviorZero = behaviorZeroCount >= 6;
            const isEditing = editingId === student.id;
            const isConfirmingDelete = deletingId === student.id;
            const isTransferring = transferringId === student.id;
            const addedDateFormatted = formatDisplayDate(
              student.createdDate.slice(0, 10)
            );

            return (
              <div
                key={student.id}
                className={`bg-white rounded-2xl p-4 border transition-all ${
                  student.isActive
                    ? 'border-slate-200/80'
                    : 'border-slate-200 bg-slate-50/70 opacity-75'
                }`}
              >
                {isEditing ? (
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      autoFocus
                      className="flex-1 min-h-[42px] px-3 py-1.5 bg-slate-50 border border-emerald-500 rounded-xl text-sm font-bold"
                    />
                    <button
                      type="button"
                      onClick={async () => {
                        if (editingName.trim()) {
                          const res = await onUpdateStudentName(
                            student.id,
                            editingName
                          );
                          if (res.isDuplicate) {
                            setDuplicateAlert(
                              `لا يمكن تعديل الاسم إلى "${editingName.trim()}" لأنه موجود مسبقاً لطالب آخر في نفس الصف.`
                            );
                            return;
                          }
                        }
                        setDuplicateAlert(null);
                        setEditingId(null);
                      }}
                      className="min-h-[42px] px-3.5 bg-emerald-600 text-white rounded-xl text-xs font-bold flex items-center gap-1"
                    >
                      <Check className="w-4 h-4" />
                      <span>حفظ</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingId(null)}
                      className="min-h-[42px] px-2.5 bg-slate-100 text-slate-600 rounded-xl"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ) : isTransferring ? (
                  <div className="bg-slate-50 p-3 rounded-xl border border-slate-200 space-y-3">
                    <p className="text-xs font-extrabold text-slate-900">
                      نقل الطالب "{student.studentName}" إلى صف أو شعبة أخرى:
                    </p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                      <select
                        value={targetClassId}
                        onChange={(e) => {
                          const newCls = Number(e.target.value);
                          setTargetClassId(newCls);
                          const firstSec = sections.find(
                            (s) => s.classId === newCls
                          );
                          if (firstSec) setTargetSectionId(firstSec.id);
                        }}
                        className="min-h-[38px] px-3 bg-white border border-slate-300 rounded-lg text-xs font-bold"
                      >
                        {classes.map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.className}
                          </option>
                        ))}
                      </select>

                      <select
                        value={targetSectionId}
                        onChange={(e) =>
                          setTargetSectionId(Number(e.target.value))
                        }
                        className="min-h-[38px] px-3 bg-white border border-slate-300 rounded-lg text-xs font-bold"
                      >
                        {transferAvailableSections.map((s) => (
                          <option key={s.id} value={s.id}>
                            شعبة {s.sectionName}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="flex items-center justify-end gap-2">
                      <button
                        type="button"
                        onClick={async () => {
                          const res = await onTransferStudent(
                            student.id,
                            targetClassId,
                            targetSectionId
                          );
                          if (res.isDuplicate) {
                            setDuplicateAlert(
                              `لا يمكن نقل الطالب "${student.studentName}" إلى الصف المختار لوجود طالب بنفس الاسم في ذلك الصف.`
                            );
                            return;
                          }
                          setDuplicateAlert(null);
                          setTransferringId(null);
                        }}
                        className="min-h-[36px] px-3.5 bg-emerald-600 text-white rounded-lg text-xs font-bold"
                      >
                        تأكيد النقل
                      </button>
                      <button
                        type="button"
                        onClick={() => setTransferringId(null)}
                        className="min-h-[36px] px-3 bg-white text-slate-700 border border-slate-200 rounded-lg text-xs font-bold"
                      >
                        إلغاء
                      </button>
                    </div>
                  </div>
                ) : isConfirmingDelete ? (
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-rose-50 p-3 rounded-xl border border-rose-200">
                    <p className="text-xs font-bold text-rose-900">
                      حذف الطالب "{student.studentName}" نهائياً أو تعطيله؟
                    </p>
                    <div className="flex flex-wrap items-center gap-2 self-end sm:self-auto">
                      <button
                        type="button"
                        onClick={async () => {
                          await onToggleStudentActive(
                            student.id,
                            !student.isActive
                          );
                          setDeletingId(null);
                        }}
                        className="min-h-[36px] px-3 bg-amber-500 text-white rounded-lg text-xs font-bold"
                      >
                        {student.isActive ? 'تعطيل فقط' : 'تفعيل الطالب'}
                      </button>
                      <button
                        type="button"
                        onClick={async () => {
                          await onDeleteStudent(student.id);
                          setDeletingId(null);
                        }}
                        className="min-h-[36px] px-3 bg-rose-600 text-white rounded-lg text-xs font-bold"
                      >
                        حذف نهائي
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeletingId(null)}
                        className="min-h-[36px] px-3 bg-white text-slate-700 border border-slate-200 rounded-lg text-xs font-bold"
                      >
                        تراجع
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-row items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0 flex-1">
                      <span className="w-7 h-7 rounded-lg bg-slate-100 text-slate-700 font-mono-num text-xs font-bold flex items-center justify-center shrink-0">
                        {idx + 1}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <button
                            type="button"
                            onClick={() =>
                              onNavigate('student-profile', {
                                studentId: student.id,
                              })
                            }
                            className={`text-base font-extrabold transition-colors text-right truncate whitespace-nowrap block min-w-0 flex-1 ${
                              hasFrequentBehaviorZero
                                ? 'text-rose-600 hover:text-rose-700'
                                : 'text-slate-900 hover:text-emerald-700'
                            }`}
                          >
                            {student.studentName}
                          </button>
                          <span
                            className={`text-[11px] font-bold shrink-0 whitespace-nowrap ${
                              student.isActive
                                ? 'text-emerald-700'
                                : 'text-rose-600'
                            }`}
                          >
                            · {student.isActive ? 'فعال' : 'غير فعال'}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 text-xs text-slate-500 mt-0.5 font-mono-num truncate whitespace-nowrap">
                          {grade && (
                            <>
                              <span>
                                الدرجة: {formatScore(grade.totalScore)} / 100
                              </span>
                              <span aria-hidden="true">·</span>
                              <span>
                                الحضور: {grade.presentDays}/
                                {grade.totalWorkingDays}
                              </span>
                              <span aria-hidden="true">·</span>
                            </>
                          )}
                          <span>أضيف: {addedDateFormatted}</span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <button
                        type="button"
                        onClick={() =>
                          onNavigate('student-profile', {
                            studentId: student.id,
                          })
                        }
                        className="min-h-[40px] px-3 py-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 rounded-xl text-xs font-bold flex items-center gap-1.5"
                      >
                        <Eye className="w-4 h-4" />
                        <span>الملف</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          setTransferringId(student.id);
                          setTargetClassId(student.classId);
                          setTargetSectionId(student.sectionId);
                        }}
                        className="min-h-[40px] min-w-[40px] p-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl flex items-center justify-center"
                        title="نقل الطالب بين الشعب"
                      >
                        <ArrowLeftRight className="w-4 h-4" />
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          onToggleStudentActive(student.id, !student.isActive)
                        }
                        className="min-h-[40px] min-w-[40px] p-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl flex items-center justify-center"
                        title={
                          student.isActive ? 'تعطيل الطالب' : 'تفعيل الطالب'
                        }
                      >
                        <Power className="w-4 h-4" />
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          setEditingId(student.id);
                          setEditingName(student.studentName);
                        }}
                        className="min-h-[40px] min-w-[40px] p-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl flex items-center justify-center"
                        title="تعديل الاسم"
                      >
                        <Edit2 className="w-4 h-4" />
                      </button>

                      <button
                        type="button"
                        onClick={() => setDeletingId(student.id)}
                        className="min-h-[40px] min-w-[40px] p-2 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl flex items-center justify-center"
                        title="حذف أو تعطيل الطالب"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
