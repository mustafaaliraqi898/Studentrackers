import React, { useState } from 'react';
import {
  Check,
  Edit2,
  Layers,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import { ClassEntity, SectionEntity, StudentEntity } from '../types';

interface ClassesSectionsViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  students: StudentEntity[];
  onAddClass: (className: string) => Promise<void>;
  onUpdateClass: (id: number, className: string) => Promise<void>;
  onDeleteClass: (id: number) => Promise<void>;
  onAddSection: (classId: number, sectionName: string) => Promise<void>;
  onUpdateSection: (id: number, sectionName: string) => Promise<void>;
  onDeleteSection: (id: number) => Promise<void>;
}

export const ClassesSectionsView: React.FC<ClassesSectionsViewProps> = ({
  classes,
  sections,
  students,
  onAddClass,
  onUpdateClass,
  onDeleteClass,
  onAddSection,
  onUpdateSection,
  onDeleteSection,
}) => {
  const [newClassName, setNewClassName] = useState('');
  const [editingClassId, setEditingClassId] = useState<number | null>(null);
  const [editingClassName, setEditingClassName] = useState('');
  const [deletingClassId, setDeletingClassId] = useState<number | null>(null);

  const [newSectionInputs, setNewSectionInputs] = useState<Record<number, string>>({});
  const [editingSectionId, setEditingSectionId] = useState<number | null>(null);
  const [editingSectionName, setEditingSectionName] = useState('');
  const [deletingSectionId, setDeletingSectionId] = useState<number | null>(null);

  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleCreateClass = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    const trimmed = newClassName.trim();
    if (!trimmed) return;
    try {
      await onAddClass(trimmed);
      setNewClassName('');
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'تعذر إضافة الصف، قد يكون موجوداً مسبقاً.');
    }
  };

  const handleCreateSection = async (classId: number) => {
    setErrorMsg(null);
    const val = (newSectionInputs[classId] || '').trim();
    if (!val) return;
    try {
      await onAddSection(classId, val);
      setNewSectionInputs((prev) => ({ ...prev, [classId]: '' }));
    } catch {
      setErrorMsg('تعذر إضافة الشعبة، تأكد من عدم تكرار اسم الشعبة داخل نفس الصف.');
    }
  };

  return (
    <div className="space-y-5 pb-10">
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80">
        <h2 className="text-lg font-extrabold text-slate-900">إدارة الصفوف والشعب</h2>
        <p className="text-xs text-slate-500 mt-0.5">
          أنشئ الصفوف الدراسية وأضف الشعب التابعة لكل صف في قاعدة بيانات SQLite المحلية
        </p>
      </div>

      {errorMsg && (
        <div className="bg-rose-50 border border-rose-200 text-rose-800 rounded-xl p-3.5 text-xs font-bold">
          {errorMsg}
        </div>
      )}

      {/* Add New Class Form */}
      <form
        onSubmit={handleCreateClass}
        className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80"
      >
        <label
          htmlFor="new-class-input"
          className="block text-xs font-bold text-slate-700 mb-2"
        >
          إضافة صف دراسي جديد (مثال: الأول المتوسط، الخامس العلمي...)
        </label>
        <div className="flex items-center gap-2">
          <input
            id="new-class-input"
            type="text"
            value={newClassName}
            onChange={(e) => setNewClassName(e.target.value)}
            placeholder="اسم الصف الدراسي..."
            className="flex-1 min-h-[46px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 focus:outline-none focus:border-emerald-600 focus:bg-white"
          />
          <button
            type="submit"
            disabled={!newClassName.trim()}
            className="min-h-[46px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-200 disabled:text-slate-400 text-white font-bold text-sm rounded-xl flex items-center gap-1.5 shrink-0 whitespace-nowrap transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>إضافة صف</span>
          </button>
        </div>
      </form>

      {/* Classes & Their Sections List */}
      <div className="space-y-4">
        {classes.map((cls) => {
          const clsSections = sections.filter((s) => s.classId === cls.id);
          const clsStudentsCount = students.filter((st) => st.classId === cls.id).length;
          const isEditingCls = editingClassId === cls.id;
          const isDeletingCls = deletingClassId === cls.id;

          return (
            <div
              key={cls.id}
              className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4"
            >
              {/* Class Header */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-100">
                {isEditingCls ? (
                  <div className="flex items-center gap-2 w-full">
                    <input
                      type="text"
                      value={editingClassName}
                      onChange={(e) => setEditingClassName(e.target.value)}
                      autoFocus
                      className="flex-1 min-h-[42px] px-3 py-1.5 bg-slate-50 border border-emerald-500 rounded-xl text-sm font-bold"
                    />
                    <button
                      type="button"
                      onClick={async () => {
                        if (editingClassName.trim()) {
                          await onUpdateClass(cls.id, editingClassName);
                        }
                        setEditingClassId(null);
                      }}
                      className="min-h-[42px] px-3.5 bg-emerald-600 text-white rounded-xl text-xs font-bold flex items-center gap-1"
                    >
                      <Check className="w-4 h-4" />
                      <span>حفظ</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingClassId(null)}
                      className="min-h-[42px] px-2.5 bg-slate-100 text-slate-600 rounded-xl"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ) : isDeletingCls ? (
                  <div className="w-full flex flex-col sm:flex-row sm:items-center justify-between gap-2 bg-rose-50 p-3 rounded-xl border border-rose-200">
                    <span className="text-xs font-bold text-rose-900">
                      حذف الصف "{cls.className}" سيحذف جميع شعبه وطلابه. هل أنت متأكد؟
                    </span>
                    <div className="flex items-center gap-2 self-end sm:self-auto">
                      <button
                        type="button"
                        onClick={async () => {
                          await onDeleteClass(cls.id);
                          setDeletingClassId(null);
                        }}
                        className="min-h-[36px] px-3 bg-rose-600 text-white rounded-lg text-xs font-bold"
                      >
                        نعم، حذف الصف
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeletingClassId(null)}
                        className="min-h-[36px] px-3 bg-white text-slate-700 border border-slate-200 rounded-lg text-xs font-bold"
                      >
                        إلغاء
                      </button>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shrink-0">
                        <Layers className="w-5 h-5" />
                      </div>
                      <div>
                        <h3 className="text-base font-extrabold text-slate-900">
                          {cls.className}
                        </h3>
                        <p className="text-xs text-slate-500 font-mono-num mt-0.5">
                          {clsSections.length} شعب · {clsStudentsCount} طلاب
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 self-end sm:self-auto">
                      <button
                        type="button"
                        onClick={() => {
                          setEditingClassId(cls.id);
                          setEditingClassName(cls.className);
                        }}
                        className="min-h-[38px] px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                        <span>تعديل الصف</span>
                      </button>
                      {classes.length > 1 && (
                        <button
                          type="button"
                          onClick={() => setDeletingClassId(cls.id)}
                          className="min-h-[38px] px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold"
                          title="حذف الصف"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>

              {/* Sections inside this Class */}
              <div className="space-y-2.5">
                <p className="text-xs font-bold text-slate-600">الشعب المرتبطة بالصف:</p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {clsSections.map((sec) => {
                    const secStudentsCount = students.filter(
                      (st) => st.sectionId === sec.id
                    ).length;
                    const isEditingSec = editingSectionId === sec.id;
                    const isDeletingSec = deletingSectionId === sec.id;

                    return (
                      <div
                        key={sec.id}
                        className="bg-slate-50 rounded-xl p-3 border border-slate-200/70 flex items-center justify-between gap-2"
                      >
                        {isEditingSec ? (
                          <div className="flex items-center gap-1.5 w-full">
                            <input
                              type="text"
                              value={editingSectionName}
                              onChange={(e) => setEditingSectionName(e.target.value)}
                              className="flex-1 min-h-[36px] px-2.5 bg-white border border-emerald-500 rounded-lg text-xs font-bold"
                            />
                            <button
                              type="button"
                              onClick={async () => {
                                if (editingSectionName.trim()) {
                                  await onUpdateSection(sec.id, editingSectionName);
                                }
                                setEditingSectionId(null);
                              }}
                              className="min-h-[36px] px-2.5 bg-emerald-600 text-white rounded-lg text-xs font-bold"
                            >
                              حفظ
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditingSectionId(null)}
                              className="min-h-[36px] px-2 bg-slate-200 text-slate-700 rounded-lg text-xs"
                            >
                              إلغاء
                            </button>
                          </div>
                        ) : isDeletingSec ? (
                          <div className="flex items-center justify-between w-full gap-2">
                            <span className="text-xs font-bold text-rose-800">
                              حذف شعبة {sec.sectionName}؟
                            </span>
                            <div className="flex items-center gap-1">
                              <button
                                type="button"
                                onClick={async () => {
                                  await onDeleteSection(sec.id);
                                  setDeletingSectionId(null);
                                }}
                                className="min-h-[32px] px-2.5 bg-rose-600 text-white rounded-lg text-xs font-bold"
                              >
                                حذف
                              </button>
                              <button
                                type="button"
                                onClick={() => setDeletingSectionId(null)}
                                className="min-h-[32px] px-2.5 bg-white text-slate-700 border border-slate-200 rounded-lg text-xs font-bold"
                              >
                                لا
                              </button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div>
                              <span className="text-sm font-extrabold text-slate-900">
                                شعبة {sec.sectionName}
                              </span>
                              <span className="text-xs text-slate-500 mr-2 font-mono-num">
                                ({secStudentsCount} طلاب)
                              </span>
                            </div>

                            <div className="flex items-center gap-1">
                              <button
                                type="button"
                                onClick={() => {
                                  setEditingSectionId(sec.id);
                                  setEditingSectionName(sec.sectionName);
                                }}
                                className="min-h-[34px] min-w-[34px] p-1.5 bg-white hover:bg-slate-200/70 text-slate-600 rounded-lg border border-slate-200/80 flex items-center justify-center"
                                title="تعديل اسم الشعبة"
                              >
                                <Edit2 className="w-3.5 h-3.5" />
                              </button>
                              {clsSections.length > 1 && (
                                <button
                                  type="button"
                                  onClick={() => setDeletingSectionId(sec.id)}
                                  className="min-h-[34px] min-w-[34px] p-1.5 bg-white hover:bg-rose-50 text-rose-600 rounded-lg border border-slate-200/80 flex items-center justify-center"
                                  title="حذف الشعبة"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              )}
                            </div>
                          </>
                        )}
                      </div>
                    );
                  })}
                </div>

                {/* Add Section to this Class */}
                <div className="flex items-center gap-2 pt-2">
                  <input
                    type="text"
                    value={newSectionInputs[cls.id] || ''}
                    onChange={(e) =>
                      setNewSectionInputs((prev) => ({
                        ...prev,
                        [cls.id]: e.target.value,
                      }))
                    }
                    placeholder="إضافة شعبة جديدة (مثال: ج، د)..."
                    className="flex-1 min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-emerald-600 focus:bg-white"
                  />
                  <button
                    type="button"
                    onClick={() => handleCreateSection(cls.id)}
                    disabled={!(newSectionInputs[cls.id] || '').trim()}
                    className="min-h-[40px] px-4 py-1.5 bg-slate-900 hover:bg-slate-800 disabled:bg-slate-200 disabled:text-slate-400 text-white rounded-xl text-xs font-bold flex items-center gap-1 whitespace-nowrap"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>إضافة شعبة</span>
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
