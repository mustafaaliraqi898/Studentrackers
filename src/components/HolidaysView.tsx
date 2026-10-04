import React, { useState } from 'react';
import {
  CalendarOff,
  Check,
  Edit2,
  Plus,
  Trash2,
  X,
} from 'lucide-react';
import { HolidayEntity } from '../types';
import { formatDisplayDate, getArabicDayName } from '../services/gradingService';

interface HolidaysViewProps {
  holidays: HolidayEntity[];
  onAddHoliday: (
    date: string,
    name: string,
    type: string,
    notes: string
  ) => Promise<void>;
  onUpdateHoliday: (
    id: number,
    date: string,
    name: string,
    type: string,
    notes: string
  ) => Promise<void>;
  onDeleteHoliday: (id: number) => Promise<void>;
}

export const HolidaysView: React.FC<HolidaysViewProps> = ({
  holidays,
  onAddHoliday,
  onUpdateHoliday,
  onDeleteHoliday,
}) => {
  const [holidayDate, setHolidayDate] = useState('2026-10-10');
  const [holidayName, setHolidayName] = useState('');
  const [holidayType, setHolidayType] = useState('رسمية');
  const [notes, setNotes] = useState('');

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDate, setEditDate] = useState('');
  const [editName, setEditName] = useState('');
  const [editType, setEditType] = useState('رسمية');
  const [editNotes, setEditNotes] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!holidayDate || !holidayName.trim()) return;
    await onAddHoliday(holidayDate, holidayName, holidayType, notes);
    setHolidayName('');
    setNotes('');
  };

  const startEdit = (h: HolidayEntity) => {
    setEditingId(h.id);
    setEditDate(h.holidayDate);
    setEditName(h.holidayName);
    setEditType(h.holidayType);
    setEditNotes(h.notes);
  };

  const saveEdit = async (id: number) => {
    if (!editDate || !editName.trim()) return;
    await onUpdateHoliday(id, editDate, editName, editType, editNotes);
    setEditingId(null);
  };

  return (
    <div className="space-y-5 pb-10">
      {/* Header & Automatic Weekend Rule Card */}
      <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-3">
        <div>
          <h2 className="text-lg font-extrabold text-slate-900">
            العطل الأسبوعية والمناسبات الرسمية
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            أيام العطل لا يُسجل فيها غياب ولا تُحتسب ضمن أيام الدوام الفعلية عند حساب درجة الحضور من 19
          </p>
        </div>

        <div className="bg-amber-50/80 border border-amber-200/80 rounded-xl p-3.5 flex items-start gap-3">
          <CalendarOff className="w-5 h-5 text-amber-700 shrink-0 mt-0.5" />
          <div className="text-xs text-amber-950 leading-relaxed">
            <span className="font-extrabold block">
              قاعدة العطلة الأسبوعية التلقائية (الجمعة والسبت):
            </span>
            يعتبر التطبيق يومي <strong>الجمعة والسبت</strong> عطلة أسبوعية تلقائياً دون الحاجة لإدخالهما يدوياً، ويظهر فيهما تنبيه "عطلة أسبوعية".
          </div>
        </div>
      </div>

      {/* Add Holiday Form */}
      <form
        onSubmit={handleSubmit}
        className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 space-y-4"
      >
        <h3 className="text-sm font-extrabold text-slate-900">
          إضافة عطلة أو مناسبة جديدة
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              تاريخ العطلة
            </label>
            <input
              type="date"
              value={holidayDate}
              onChange={(e) => setHolidayDate(e.target.value)}
              required
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono-num"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              اسم العطلة أو المناسبة
            </label>
            <input
              type="text"
              value={holidayName}
              onChange={(e) => setHolidayName(e.target.value)}
              placeholder="مثال: عيد وطني، عطلة رسمية..."
              required
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs"
            />
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 mb-1">
              نوع العطلة
            </label>
            <select
              value={holidayType}
              onChange={(e) => setHolidayType(e.target.value)}
              className="w-full min-h-[44px] px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold"
            >
              <option value="عطلة رسمية">عطلة رسمية</option>
              <option value="مناسبة">مناسبة</option>
              <option value="عطلة مدرسية">عطلة مدرسية</option>
              <option value="أخرى">أخرى</option>
            </select>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
          <input
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder="ملاحظات إضافية (اختياري)..."
            className="flex-1 min-h-[44px] px-3.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs"
          />
          <button
            type="submit"
            disabled={!holidayName.trim() || !holidayDate}
            className="min-h-[44px] px-5 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:bg-slate-200 disabled:text-slate-400 text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 whitespace-nowrap transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>حفظ العطلة في SQLite</span>
          </button>
        </div>
      </form>

      {/* Registered Holidays List */}
      <div className="space-y-3">
        <h3 className="text-sm font-extrabold text-slate-800 px-1">
          قائمة العطل والمناسبات المسجلة ({holidays.length})
        </h3>

        {holidays.length === 0 ? (
          <div className="bg-white rounded-2xl p-8 text-center border border-slate-200/80">
            <p className="text-sm font-bold text-slate-700">لا توجد عطل مضافة يدوياً</p>
            <p className="text-xs text-slate-500 mt-1">
              الجمعة والسبت مفعّلان تلقائياً كعطلة أسبوعية.
            </p>
          </div>
        ) : (
          holidays.map((h) => {
            const isEditing = editingId === h.id;
            return (
              <div
                key={h.id}
                className="bg-white rounded-2xl p-4 border border-slate-200/80"
              >
                {isEditing ? (
                  <div className="space-y-3">
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                      <input
                        type="date"
                        value={editDate}
                        onChange={(e) => setEditDate(e.target.value)}
                        className="min-h-[40px] px-3 bg-slate-50 border border-slate-300 rounded-xl text-xs font-mono-num"
                      />
                      <input
                        type="text"
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        className="min-h-[40px] px-3 bg-slate-50 border border-slate-300 rounded-xl text-xs font-bold"
                      />
                      <select
                        value={editType}
                        onChange={(e) => setEditType(e.target.value)}
                        className="min-h-[40px] px-3 bg-slate-50 border border-slate-300 rounded-xl text-xs font-bold"
                      >
                        <option value="عطلة رسمية">عطلة رسمية</option>
                        <option value="مناسبة">مناسبة</option>
                        <option value="عطلة مدرسية">عطلة مدرسية</option>
                        <option value="أخرى">أخرى</option>
                      </select>
                    </div>
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={editNotes}
                        onChange={(e) => setEditNotes(e.target.value)}
                        placeholder="ملاحظات..."
                        className="flex-1 min-h-[40px] px-3 bg-slate-50 border border-slate-300 rounded-xl text-xs"
                      />
                      <button
                        type="button"
                        onClick={() => saveEdit(h.id)}
                        className="min-h-[40px] px-4 bg-emerald-600 text-white rounded-xl text-xs font-bold flex items-center gap-1"
                      >
                        <Check className="w-4 h-4" />
                        <span>حفظ</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingId(null)}
                        className="min-h-[40px] px-3 bg-slate-100 text-slate-600 rounded-xl text-xs"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2 text-xs text-slate-500 font-mono-num">
                        <span>{formatDisplayDate(h.holidayDate)}</span>
                        <span aria-hidden="true">·</span>
                        <span>يوم {getArabicDayName(h.holidayDate)}</span>
                        <span aria-hidden="true">·</span>
                        <span className="text-amber-700 font-bold">{h.holidayType}</span>
                      </div>
                      <h4 className="text-base font-extrabold text-slate-900 mt-1">
                        {h.holidayName}
                      </h4>
                      {h.notes && (
                        <p className="text-xs text-slate-500 mt-0.5">{h.notes}</p>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5 self-end sm:self-auto shrink-0">
                      <button
                        type="button"
                        onClick={() => startEdit(h)}
                        className="min-h-[38px] px-3 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-bold flex items-center gap-1"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                        <span>تعديل</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => onDeleteHoliday(h.id)}
                        className="min-h-[38px] px-2.5 py-1.5 bg-rose-50 hover:bg-rose-100 text-rose-600 rounded-xl text-xs font-bold"
                        title="حذف العطلة"
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
