import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  BookOpen,
  Calendar,
  CheckCircle2,
  Edit3,
  Filter,
  ImagePlus,
  Plus,
  RotateCcw,
  Save,
  Search,
  Trash2,
  X,
  ZoomIn,
} from 'lucide-react';
import {
  ClassEntity,
  DailyPlanEntity,
  DailyPlanImageEntity,
  SectionEntity,
} from '../types';
import { TrackerRepository } from '../repositories/trackerRepository';
import {
  formatDisplayDate,
  getArabicDayName,
} from '../services/gradingService';
import { getLocalTodayDateString } from '../services/backupService';

interface DailyPlanViewProps {
  classes: ClassEntity[];
  sections: SectionEntity[];
  onDataChanged: () => Promise<void>;
  onShowToast: (message: string) => void;
}

interface DraftPlanImage {
  fileName: string;
  imageBase64: string;
}

function compressImageFileToBase64(file: File): Promise<DraftPlanImage> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('تعذر قراءة ملف الصورة'));
    reader.onload = () => {
      const dataUrl = String(reader.result || '');
      const img = new Image();
      img.onerror = () => reject(new Error('ملف الصورة غير صالح'));
      img.onload = () => {
        try {
          const maxDim = 1280;
          let width = img.width;
          let height = img.height;
          if (width > maxDim || height > maxDim) {
            const ratio = Math.min(maxDim / width, maxDim / height);
            width = Math.max(1, Math.round(width * ratio));
            height = Math.max(1, Math.round(height * ratio));
          }
          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) {
            const rawB64 = dataUrl.includes(',') ? dataUrl.split(',')[1] : dataUrl;
            resolve({
              fileName: `plan_img_${Date.now()}_${Math.floor(Math.random() * 9000 + 1000)}.jpg`,
              imageBase64: rawB64,
            });
            return;
          }
          ctx.drawImage(img, 0, 0, width, height);
          const compressedUrl = canvas.toDataURL('image/jpeg', 0.82);
          const b64 = compressedUrl.includes(',')
            ? compressedUrl.split(',')[1]
            : compressedUrl;
          resolve({
            fileName: `plan_img_${Date.now()}_${Math.floor(Math.random() * 9000 + 1000)}.jpg`,
            imageBase64: b64,
          });
        } catch (err) {
          reject(err);
        }
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  });
}

export function getPlanImageDataSrc(imageBase64: string): string {
  if (!imageBase64) return '';
  if (imageBase64.startsWith('data:image/')) return imageBase64;
  return `data:image/jpeg;base64,${imageBase64}`;
}

export const DailyPlanView: React.FC<DailyPlanViewProps> = ({
  classes,
  sections,
  onDataChanged,
  onShowToast,
}) => {
  // Create / Edit Form State
  const [editingPlanId, setEditingPlanId] = useState<number | null>(null);
  const [formDate, setFormDate] = useState<string>(() => getLocalTodayDateString());
  const [formClassId, setFormClassId] = useState<number>(
    () => classes[0]?.id ?? 0
  );
  const [formSelectedSectionIds, setFormSelectedSectionIds] = useState<number[]>(
    () => {
      const firstClsId = classes[0]?.id ?? 0;
      const firstSec = sections.find((s) => s.classId === firstClsId);
      return firstSec ? [firstSec.id] : [];
    }
  );
  const [formLessonTopic, setFormLessonTopic] = useState<string>('');
  const [formImages, setFormImages] = useState<DraftPlanImage[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState<boolean>(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Saved Plans & Filter State
  const [filterDate, setFilterDate] = useState<string>('');
  const [filterClassId, setFilterClassId] = useState<number | null>(null);
  const [filterSectionId, setFilterSectionId] = useState<number | null>(null);
  const [searchTopicQuery, setSearchTopicQuery] = useState<string>('');
  const [savedPlans, setSavedPlans] = useState<DailyPlanEntity[]>([]);
  const [loadingPlans, setLoadingPlans] = useState<boolean>(true);
  const [localRefreshTick, setLocalRefreshTick] = useState<number>(0);

  // Fullscreen image preview & delete confirmation
  const [previewImage, setPreviewImage] = useState<DailyPlanImageEntity | null>(
    null
  );
  const [pendingDeletePlan, setPendingDeletePlan] =
    useState<DailyPlanEntity | null>(null);

  useEffect(() => {
    if (classes.length > 0 && !classes.some((c) => c.id === formClassId)) {
      const nextClsId = classes[0].id;
      setFormClassId(nextClsId);
      const firstSec = sections.find((s) => s.classId === nextClsId);
      setFormSelectedSectionIds(firstSec ? [firstSec.id] : []);
    }
  }, [classes, sections, formClassId]);

  const formClassSections = useMemo(
    () => sections.filter((s) => s.classId === formClassId),
    [sections, formClassId]
  );

  useEffect(() => {
    const validIds = new Set(formClassSections.map((s) => s.id));
    setFormSelectedSectionIds((prev) => {
      const filtered = prev.filter((id) => validIds.has(id));
      if (filtered.length > 0) return filtered;
      if (editingPlanId === null && formClassSections.length > 0) {
        return [formClassSections[0].id];
      }
      return [];
    });
  }, [formClassId, formClassSections, editingPlanId]);

  const filterAvailableSections = useMemo(
    () =>
      filterClassId === null
        ? []
        : sections.filter((s) => s.classId === filterClassId),
    [sections, filterClassId]
  );

  useEffect(() => {
    if (filterClassId === null) {
      setFilterSectionId(null);
    } else if (
      filterSectionId !== null &&
      !filterAvailableSections.some((s) => s.id === filterSectionId)
    ) {
      setFilterSectionId(null);
    }
  }, [filterClassId, filterAvailableSections, filterSectionId]);

  useEffect(() => {
    let active = true;
    setLoadingPlans(true);
    TrackerRepository.getDailyPlans({
      planDate: filterDate.trim() || undefined,
      classId: filterClassId ?? undefined,
      sectionId: filterSectionId ?? undefined,
      searchTopic: searchTopicQuery.trim() || undefined,
    })
      .then((rows) => {
        if (active) {
          setSavedPlans(rows);
          setLoadingPlans(false);
        }
      })
      .catch(() => {
        if (active) setLoadingPlans(false);
      });
    return () => {
      active = false;
    };
  }, [
    filterDate,
    filterClassId,
    filterSectionId,
    searchTopicQuery,
    localRefreshTick,
    classes,
    sections,
  ]);

  const resetFormToNew = () => {
    setEditingPlanId(null);
    setFormDate(getLocalTodayDateString());
    const firstClsId = classes[0]?.id ?? 0;
    setFormClassId(firstClsId);
    const firstSec = sections.find((s) => s.classId === firstClsId);
    setFormSelectedSectionIds(firstSec ? [firstSec.id] : []);
    setFormLessonTopic('');
    setFormImages([]);
    setFormError(null);
  };

  const handleToggleSection = (secId: number) => {
    setFormSelectedSectionIds((prev) =>
      prev.includes(secId) ? prev.filter((id) => id !== secId) : [...prev, secId]
    );
    setFormError(null);
  };

  const handleSelectFiles = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList || fileList.length === 0) return;
    try {
      const filesArray = Array.from(fileList);
      const compressed = await Promise.all(
        filesArray.map((file) => compressImageFileToBase64(file))
      );
      setFormImages((prev) => [...prev, ...compressed]);
      setFormError(null);
    } catch {
      onShowToast('تعذر قراءة بعض الصور المختارة.');
    } finally {
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleSavePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      const orderedSecIds = formClassSections
        .filter((s) => formSelectedSectionIds.includes(s.id))
        .map((s) => s.id);
      const result = await TrackerRepository.saveDailyPlan({
        existingPlanId: editingPlanId ?? undefined,
        planDate: formDate,
        classId: formClassId,
        sectionIds: orderedSecIds,
        lessonTopic: formLessonTopic,
        images: formImages,
      });
      if (result.error) {
        setFormError(result.error);
      } else {
        const wasEditing = editingPlanId !== null;
        resetFormToNew();
        setLocalRefreshTick((t) => t + 1);
        await onDataChanged();
        onShowToast(
          wasEditing
            ? 'تم حفظ تعديلات الخطة اليومية بنجاح'
            : 'تم حفظ الخطة اليومية في قاعدة البيانات بنجاح'
        );
      }
    } finally {
      setSaving(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!pendingDeletePlan) return;
    const planToDelete = pendingDeletePlan;
    setPendingDeletePlan(null);
    await TrackerRepository.deleteDailyPlan(planToDelete.id);
    if (editingPlanId === planToDelete.id) {
      resetFormToNew();
    }
    setLocalRefreshTick((t) => t + 1);
    await onDataChanged();
    onShowToast('تم حذف الخطة اليومية بنجاح');
  };

  const hasActiveFilters =
    filterDate.trim() !== '' ||
    filterClassId !== null ||
    filterSectionId !== null ||
    searchTopicQuery.trim() !== '';

  return (
    <div className="space-y-5">
      {/* 1. Header Banner */}
      <section className="bg-slate-900 text-white rounded-2xl p-4 sm:p-5 border border-slate-800 shadow-sm flex flex-wrap items-center justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-teal-400" />
            <h2 className="text-base sm:text-lg font-extrabold">
              الخطة اليومية للدروس
            </h2>
          </div>
          <p className="text-xs text-teal-100/90">
            قسم مستقل يدعم جميع الصفوف والشعب مع إمكانية اختيار شعبة أو عدة شعب وإرفاق عدد غير محدود من الصور
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-700 text-white text-xs font-extrabold font-mono-num">
          {savedPlans.length} خطة محفوظة
        </span>
      </section>

      {/* 2. Create / Edit Daily Plan Form */}
      <form
        onSubmit={handleSavePlan}
        className={`bg-white rounded-2xl p-4 sm:p-5 border space-y-4 ${
          editingPlanId !== null
            ? 'border-amber-500 ring-2 ring-amber-500/15'
            : 'border-slate-200/80'
        }`}
      >
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h3
            className={`text-base font-extrabold ${
              editingPlanId !== null ? 'text-amber-700' : 'text-slate-900'
            }`}
          >
            {editingPlanId !== null
              ? 'تعديل الخطة اليومية'
              : 'إضافة خطة يومية جديدة'}
          </h3>
          {editingPlanId !== null && (
            <button
              type="button"
              onClick={resetFormToNew}
              className="text-xs font-bold text-rose-600 hover:text-rose-700 flex items-center gap-1"
            >
              <X className="w-3.5 h-3.5" />
              إلغاء التعديل
            </button>
          )}
        </div>

        {formError && (
          <div className="bg-rose-50 border border-rose-300 text-rose-700 rounded-xl p-3 text-xs font-bold">
            {formError}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Date */}
          <div>
            <label className="block text-xs font-extrabold text-slate-800 mb-1.5">
              1. التاريخ ({getArabicDayName(formDate)} —{' '}
              {formatDisplayDate(formDate)})
            </label>
            <div className="relative">
              <Calendar className="w-4 h-4 text-teal-700 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type="date"
                value={formDate}
                onChange={(e) => {
                  setFormDate(e.target.value);
                  setFormError(null);
                }}
                className="w-full min-h-[42px] pr-9 pl-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-teal-600"
              />
            </div>
          </div>

          {/* Class */}
          <div>
            <label className="block text-xs font-extrabold text-slate-800 mb-1.5">
              2. الصف الدراسي
            </label>
            <select
              value={formClassId}
              onChange={(e) => {
                const nextCls = Number(e.target.value);
                setFormClassId(nextCls);
                const secs = sections.filter((s) => s.classId === nextCls);
                setFormSelectedSectionIds(secs.length > 0 ? [secs[0].id] : []);
                setFormError(null);
              }}
              className="w-full min-h-[42px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-teal-600"
            >
              {classes.map((cls) => (
                <option key={cls.id} value={cls.id}>
                  {cls.className}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Multi-Section Checkboxes */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-xs font-extrabold text-slate-800">
              3. الشعبة أو الشعب المشمولة بالخطة (يمكن اختيار شعبة واحدة أو عدة شعب من نفس الصف)
            </label>
            {formClassSections.length > 1 && (
              <button
                type="button"
                onClick={() => {
                  const allSelected =
                    formSelectedSectionIds.length === formClassSections.length;
                  setFormSelectedSectionIds(
                    allSelected ? [] : formClassSections.map((s) => s.id)
                  );
                  setFormError(null);
                }}
                className="text-xs font-extrabold text-teal-700 hover:text-teal-800"
              >
                {formSelectedSectionIds.length === formClassSections.length
                  ? 'إلغاء تحديد الكل'
                  : 'تحديد كل الشعب'}
              </button>
            )}
          </div>

          {formClassSections.length === 0 ? (
            <p className="text-xs text-slate-500">
              لا توجد شعب مضافة لهذا الصف حالياً.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {formClassSections.map((sec) => {
                const isSelected = formSelectedSectionIds.includes(sec.id);
                return (
                  <button
                    key={sec.id}
                    type="button"
                    onClick={() => handleToggleSection(sec.id)}
                    className={`min-h-[38px] px-3.5 py-1.5 rounded-xl text-xs font-extrabold border transition-all flex items-center gap-1.5 ${
                      isSelected
                        ? 'bg-teal-700 text-white border-teal-700 shadow-xs'
                        : 'bg-slate-100 text-slate-800 border-slate-200 hover:bg-slate-200/70'
                    }`}
                  >
                    {isSelected ? (
                      <CheckCircle2 className="w-3.5 h-3.5" />
                    ) : (
                      <Plus className="w-3.5 h-3.5" />
                    )}
                    شعبة {sec.sectionName}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* Lesson Topic */}
        <div>
          <label className="block text-xs font-extrabold text-slate-800 mb-1.5">
            4. عنوان الدرس أو الموضوع
          </label>
          <textarea
            rows={3}
            value={formLessonTopic}
            onChange={(e) => {
              setFormLessonTopic(e.target.value);
              setFormError(null);
            }}
            placeholder="اكتب عنوان الدرس، الموضوع، أو تفاصيل الخطة اليومية..."
            className="w-full p-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-teal-600"
          />
        </div>

        {/* Unlimited Images Attachment */}
        <div className="space-y-2.5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <label className="block text-xs font-extrabold text-slate-800">
                5. صور الدرس المرفقة ({formImages.length} صورة)
              </label>
              <p className="text-[11px] text-slate-500">
                يمكنك إضافة عدة صور للدرس الواحد من الجهاز ومعاينتها بالحجم الكامل أو حذف أي صورة
              </p>
            </div>
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                multiple
                onChange={handleSelectFiles}
                className="hidden"
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="min-h-[38px] px-3.5 py-1.5 rounded-xl border border-teal-700 text-teal-700 hover:bg-teal-50 text-xs font-extrabold flex items-center gap-1.5"
              >
                <ImagePlus className="w-4 h-4" />
                إضافة صور
              </button>
            </div>
          </div>

          {formImages.length > 0 && (
            <div className="flex flex-wrap gap-2.5 pt-1">
              {formImages.map((img, idx) => {
                const tempEntity: DailyPlanImageEntity = {
                  id: idx,
                  planId: editingPlanId ?? 0,
                  fileName: img.fileName,
                  imageBase64: img.imageBase64,
                  sortOrder: idx,
                  createdDate: '',
                };
                return (
                  <div
                    key={`${img.fileName}-${idx}`}
                    className="relative w-20 h-20 rounded-xl border border-slate-200 overflow-hidden bg-slate-100 group"
                  >
                    <button
                      type="button"
                      onClick={() => setPreviewImage(tempEntity)}
                      className="w-full h-full block"
                      title="تكبير الصورة"
                    >
                      <img
                        src={getPlanImageDataSrc(img.imageBase64)}
                        alt="صورة مرفقة"
                        className="w-full h-full object-cover"
                      />
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        setFormImages((prev) =>
                          prev.filter((_, itemIdx) => itemIdx !== idx)
                        )
                      }
                      className="absolute top-1 left-1 w-5 h-5 rounded-full bg-rose-600 text-white flex items-center justify-center shadow-xs hover:bg-rose-700"
                      title="حذف الصورة"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <button
          type="submit"
          disabled={saving}
          className={`w-full min-h-[46px] rounded-xl text-white font-extrabold text-sm flex items-center justify-center gap-2 transition-colors ${
            editingPlanId !== null
              ? 'bg-amber-600 hover:bg-amber-700'
              : 'bg-emerald-600 hover:bg-emerald-700'
          }`}
        >
          <Save className="w-4 h-4" />
          {editingPlanId !== null
            ? 'حفظ تعديلات الخطة اليومية'
            : 'حفظ الخطة اليومية'}
        </button>
      </form>

      {/* 3. Search & Filters Section */}
      <section className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3.5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Filter className="w-4 h-4 text-teal-700" />
            <h3 className="text-sm font-extrabold text-slate-900">
              بحث وفلترة الخطط اليومية المحفوظة
            </h3>
          </div>
          {hasActiveFilters && (
            <button
              type="button"
              onClick={() => {
                setFilterDate('');
                setFilterClassId(null);
                setFilterSectionId(null);
                setSearchTopicQuery('');
              }}
              className="text-xs font-extrabold text-teal-700 hover:text-teal-800 flex items-center gap-1"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              إعادة ضبط الفلاتر
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {/* Search by topic */}
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none" />
            <input
              type="text"
              value={searchTopicQuery}
              onChange={(e) => setSearchTopicQuery(e.target.value)}
              placeholder="بحث بعنوان الدرس أو الموضوع..."
              className="w-full min-h-[40px] pr-9 pl-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900"
            />
          </div>

          {/* Filter by Date */}
          <div className="flex items-center gap-1.5">
            <input
              type="date"
              value={filterDate}
              onChange={(e) => setFilterDate(e.target.value)}
              className="w-full min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900"
            />
            {filterDate && (
              <button
                type="button"
                onClick={() => setFilterDate('')}
                className="min-h-[40px] px-2.5 rounded-xl bg-slate-100 text-slate-600 hover:bg-slate-200 text-xs font-bold"
                title="كل التواريخ"
              >
                الكل
              </button>
            )}
          </div>

          {/* Filter by Class */}
          <select
            value={filterClassId ?? ''}
            onChange={(e) => {
              const val = e.target.value;
              setFilterClassId(val === '' ? null : Number(val));
            }}
            className="w-full min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900"
          >
            <option value="">جميع الصفوف</option>
            {classes.map((cls) => (
              <option key={cls.id} value={cls.id}>
                {cls.className}
              </option>
            ))}
          </select>

          {/* Filter by Section */}
          <select
            value={filterSectionId ?? ''}
            disabled={filterClassId === null}
            onChange={(e) => {
              const val = e.target.value;
              setFilterSectionId(val === '' ? null : Number(val));
            }}
            className="w-full min-h-[40px] px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold text-slate-900 disabled:opacity-50"
          >
            <option value="">جميع الشعب</option>
            {filterAvailableSections.map((sec) => (
              <option key={sec.id} value={sec.id}>
                شعبة {sec.sectionName}
              </option>
            ))}
          </select>
        </div>
      </section>

      {/* 4. Saved Daily Plans List */}
      <section className="space-y-3">
        {loadingPlans ? (
          <div className="bg-white rounded-2xl p-6 border border-slate-200/80 text-center text-xs font-bold text-slate-500">
            جاري تحميل الخطط اليومية...
          </div>
        ) : savedPlans.length === 0 ? (
          <div className="bg-white rounded-2xl p-6 border border-slate-200/80 text-center space-y-1">
            <p className="text-sm font-extrabold text-slate-800">
              لا توجد خطط يومية محفوظة تطابق الفلتر الحالي
            </p>
            <p className="text-xs text-slate-500">
              يمكنك إضافة خطة يومية جديدة من النموذج أعلاه مع تحديد الصف والشعب وإرفاق الصور.
            </p>
          </div>
        ) : (
          savedPlans.map((plan) => (
            <div
              key={plan.id}
              className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3 shadow-2xs"
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-teal-50 text-teal-800 border border-teal-200 text-xs font-extrabold font-mono-num">
                      {getArabicDayName(plan.planDate)} —{' '}
                      {formatDisplayDate(plan.planDate)}
                    </span>
                    <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-slate-900 text-white text-xs font-extrabold">
                      {plan.className}
                    </span>
                    {plan.sectionNames.map((secName, i) => (
                      <span
                        key={`${plan.id}-sec-${i}`}
                        className="inline-flex items-center px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-200 text-xs font-extrabold"
                      >
                        شعبة {secName}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={() => {
                      setEditingPlanId(plan.id);
                      setFormDate(plan.planDate);
                      setFormClassId(plan.classId);
                      setFormSelectedSectionIds(plan.sectionIds);
                      setFormLessonTopic(plan.lessonTopic);
                      setFormImages(
                        plan.images.map((img) => ({
                          fileName: img.fileName,
                          imageBase64: img.imageBase64,
                        }))
                      );
                      setFormError(null);
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                    className="min-h-[34px] px-3 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 text-xs font-extrabold flex items-center gap-1"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                    تعديل
                  </button>
                  <button
                    type="button"
                    onClick={() => setPendingDeletePlan(plan)}
                    className="min-h-[34px] px-3 py-1 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-extrabold flex items-center gap-1"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    حذف
                  </button>
                </div>
              </div>

              <div className="bg-slate-50 rounded-xl p-3 border border-slate-200/80">
                <p className="text-[11px] font-bold text-slate-500 mb-1">
                  عنوان الدرس أو الموضوع:
                </p>
                <p className="text-sm font-bold text-slate-900 whitespace-pre-wrap leading-relaxed">
                  {plan.lessonTopic}
                </p>
              </div>

              {plan.images.length > 0 && (
                <div className="space-y-1.5">
                  <p className="text-xs font-extrabold text-slate-700">
                    صور الدرس المرفقة ({plan.images.length} صورة — اضغط على أي صورة للتكبير):
                  </p>
                  <div className="flex flex-wrap gap-2.5">
                    {plan.images.map((img) => (
                      <button
                        key={img.id || img.fileName}
                        type="button"
                        onClick={() => setPreviewImage(img)}
                        className="relative w-20 h-20 rounded-xl border border-slate-200 overflow-hidden bg-slate-100 group"
                      >
                        <img
                          src={getPlanImageDataSrc(img.imageBase64)}
                          alt="صورة الدرس"
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                        />
                        <span className="absolute bottom-1 left-1 w-5 h-5 rounded-full bg-slate-900/75 text-white flex items-center justify-center">
                          <ZoomIn className="w-3 h-3" />
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </section>

      {/* Fullscreen Image Lightbox Modal */}
      {previewImage && (
        <ZoomablePlanImageModal
          image={previewImage}
          onClose={() => setPreviewImage(null)}
        />
      )}

      {/* Delete Plan Confirmation Modal */}
      {pendingDeletePlan && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-5 space-y-4 border border-slate-200 shadow-xl">
            <h3 className="text-base font-extrabold text-slate-900">
              تأكيد حذف الخطة اليومية
            </h3>
            <p className="text-xs font-semibold text-slate-600 leading-relaxed">
              هل أنت متأكد من حذف الخطة اليومية بتاريخ{' '}
              <span className="font-extrabold text-slate-900">
                {formatDisplayDate(pendingDeletePlan.planDate)}
              </span>{' '}
              للصف{' '}
              <span className="font-extrabold text-slate-900">
                {pendingDeletePlan.className} (شعبة{' '}
                {pendingDeletePlan.sectionNames.join('، ')})
              </span>
              ؟ سيتم حذف الخطة وجميع الصور المرتبطة بها نهائياً.
            </p>
            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setPendingDeletePlan(null)}
                className="min-h-[38px] px-3.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold"
              >
                إلغاء
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="min-h-[38px] px-4 py-1.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-extrabold"
              >
                تأكيد الحذف
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export const ZoomablePlanImageModal: React.FC<{
  image: DailyPlanImageEntity;
  onClose: () => void;
}> = ({ image, onClose }) => {
  const [scale, setScale] = useState<number>(1);
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const containerRef = useRef<HTMLDivElement | null>(null);

  const gestureRef = useRef<{
    isPinching: boolean;
    initialPinchDistance: number;
    initialScale: number;
    isPanning: boolean;
    panStartX: number;
    panStartY: number;
    initialOffsetX: number;
    initialOffsetY: number;
    lastTapTime: number;
  }>({
    isPinching: false,
    initialPinchDistance: 0,
    initialScale: 1,
    isPanning: false,
    panStartX: 0,
    panStartY: 0,
    initialOffsetX: 0,
    initialOffsetY: 0,
    lastTapTime: 0,
  });

  useEffect(() => {
    setScale(1);
    setOffset({ x: 0, y: 0 });
  }, [image.id, image.fileName, image.imageBase64]);

  const clampOffset = (
    nextX: number,
    nextY: number,
    targetScale: number
  ): { x: number; y: number } => {
    if (targetScale <= 1) return { x: 0, y: 0 };
    const el = containerRef.current;
    const width = el ? el.clientWidth : window.innerWidth;
    const height = el ? el.clientHeight : window.innerHeight;
    const maxX = (width * (targetScale - 1)) / 2;
    const maxY = (height * (targetScale - 1)) / 2;
    return {
      x: Math.max(-maxX, Math.min(maxX, nextX)),
      y: Math.max(-maxY, Math.min(maxY, nextY)),
    };
  };

  const resetZoom = () => {
    setScale(1);
    setOffset({ x: 0, y: 0 });
  };

  const getTouchDistance = (touches: React.TouchList): number => {
    if (touches.length < 2) return 0;
    const dx = touches[0].clientX - touches[1].clientX;
    const dy = touches[0].clientY - touches[1].clientY;
    return Math.hypot(dx, dy);
  };

  const handleTouchStart = (e: React.TouchEvent<HTMLDivElement>) => {
    e.stopPropagation();
    if (e.touches.length === 2) {
      const dist = getTouchDistance(e.touches);
      gestureRef.current.isPinching = true;
      gestureRef.current.isPanning = false;
      gestureRef.current.initialPinchDistance = dist > 0 ? dist : 1;
      gestureRef.current.initialScale = scale;
    } else if (e.touches.length === 1) {
      const now = Date.now();
      if (now - gestureRef.current.lastTapTime < 300) {
        e.preventDefault();
        resetZoom();
        gestureRef.current.lastTapTime = 0;
        gestureRef.current.isPanning = false;
        return;
      }
      gestureRef.current.lastTapTime = now;

      if (scale > 1) {
        gestureRef.current.isPanning = true;
        gestureRef.current.panStartX = e.touches[0].clientX;
        gestureRef.current.panStartY = e.touches[0].clientY;
        gestureRef.current.initialOffsetX = offset.x;
        gestureRef.current.initialOffsetY = offset.y;
      }
    }
  };

  const handleTouchMove = (e: React.TouchEvent<HTMLDivElement>) => {
    e.stopPropagation();
    if (e.touches.length === 2 && gestureRef.current.isPinching) {
      e.preventDefault();
      const dist = getTouchDistance(e.touches);
      if (dist > 0 && gestureRef.current.initialPinchDistance > 0) {
        const ratio = dist / gestureRef.current.initialPinchDistance;
        const nextScale = Math.max(
          1,
          Math.min(5, gestureRef.current.initialScale * ratio)
        );
        if (nextScale <= 1.01) {
          setScale(1);
          setOffset({ x: 0, y: 0 });
        } else {
          setScale(nextScale);
          setOffset((prev) => clampOffset(prev.x, prev.y, nextScale));
        }
      }
    } else if (
      e.touches.length === 1 &&
      gestureRef.current.isPanning &&
      scale > 1
    ) {
      e.preventDefault();
      const dx = e.touches[0].clientX - gestureRef.current.panStartX;
      const dy = e.touches[0].clientY - gestureRef.current.panStartY;
      setOffset(
        clampOffset(
          gestureRef.current.initialOffsetX + dx,
          gestureRef.current.initialOffsetY + dy,
          scale
        )
      );
    }
  };

  const handleTouchEnd = (e: React.TouchEvent<HTMLDivElement>) => {
    e.stopPropagation();
    if (e.touches.length < 2) {
      gestureRef.current.isPinching = false;
    }
    if (e.touches.length === 1 && scale > 1) {
      gestureRef.current.isPanning = true;
      gestureRef.current.panStartX = e.touches[0].clientX;
      gestureRef.current.panStartY = e.touches[0].clientY;
      gestureRef.current.initialOffsetX = offset.x;
      gestureRef.current.initialOffsetY = offset.y;
    } else if (e.touches.length === 0) {
      gestureRef.current.isPanning = false;
      if (scale <= 1.01) {
        resetZoom();
      }
    }
  };

  const handleMouseDown = (e: React.MouseEvent<HTMLDivElement>) => {
    e.stopPropagation();
    if (scale <= 1) return;
    gestureRef.current.isPanning = true;
    gestureRef.current.panStartX = e.clientX;
    gestureRef.current.panStartY = e.clientY;
    gestureRef.current.initialOffsetX = offset.x;
    gestureRef.current.initialOffsetY = offset.y;
  };

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!gestureRef.current.isPanning || scale <= 1) return;
    e.preventDefault();
    const dx = e.clientX - gestureRef.current.panStartX;
    const dy = e.clientY - gestureRef.current.panStartY;
    setOffset(
      clampOffset(
        gestureRef.current.initialOffsetX + dx,
        gestureRef.current.initialOffsetY + dy,
        scale
      )
    );
  };

  const handleMouseUp = () => {
    gestureRef.current.isPanning = false;
  };

  const handleWheel = (e: React.WheelEvent<HTMLDivElement>) => {
    e.stopPropagation();
    const factor = e.deltaY < 0 ? 1.15 : 0.87;
    const nextScale = Math.max(1, Math.min(5, scale * factor));
    if (nextScale <= 1.01) {
      resetZoom();
    } else {
      setScale(nextScale);
      setOffset((prev) => clampOffset(prev.x, prev.y, nextScale));
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-slate-950/90 flex flex-col justify-between p-4 select-none"
      onClick={() => {
        if (scale <= 1) onClose();
      }}
    >
      <div
        className="flex items-center justify-between max-w-5xl w-full mx-auto z-10"
        onClick={(e) => e.stopPropagation()}
      >
        <h4 className="text-sm font-extrabold text-white">
          معاينة صورة الدرس بالحجم الكامل
        </h4>
        <button
          type="button"
          onClick={onClose}
          className="px-3.5 py-1.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white text-xs font-extrabold flex items-center gap-1.5"
        >
          <X className="w-4 h-4" />
          إغلاق
        </button>
      </div>

      <div
        ref={containerRef}
        className="flex-1 flex items-center justify-center overflow-hidden my-4 touch-none"
        onClick={(e) => e.stopPropagation()}
        onDoubleClick={(e) => {
          e.stopPropagation();
          resetZoom();
        }}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchEnd}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
      >
        <img
          src={getPlanImageDataSrc(image.imageBase64)}
          alt="معاينة صورة الدرس"
          draggable={false}
          style={{
            transform: `translate3d(${offset.x}px, ${offset.y}px, 0) scale(${scale})`,
            transformOrigin: 'center center',
            cursor: scale > 1 ? 'grab' : 'default',
          }}
          className="max-w-full max-h-[82vh] object-contain rounded-xl shadow-2xl pointer-events-none select-none"
        />
      </div>
    </div>
  );
};

