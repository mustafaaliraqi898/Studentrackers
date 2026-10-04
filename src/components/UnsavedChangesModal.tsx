import React from 'react';
import { AlertTriangle } from 'lucide-react';

interface UnsavedChangesModalProps {
  isOpen: boolean;
  onSaveAndExit: () => void;
  onExitWithoutSaving: () => void;
  onCancel: () => void;
}

export const UnsavedChangesModal: React.FC<UnsavedChangesModalProps> = ({
  isOpen,
  onSaveAndExit,
  onExitWithoutSaving,
  onCancel,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs">
      <div
        role="dialog"
        aria-modal="true"
        className="w-full max-w-md bg-white rounded-2xl p-6 shadow-xl border border-slate-200 animate-in fade-in zoom-in-95 duration-150"
      >
        <div className="flex items-start gap-3.5 mb-4">
          <div className="w-11 h-11 rounded-xl bg-amber-50 border border-amber-200 flex items-center justify-center shrink-0 text-amber-600">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900">تغييرات غير محفوظة</h3>
            <p className="text-sm text-slate-600 mt-1 leading-relaxed">
              لديك تغييرات غير محفوظة، هل تريد حفظها قبل الخروج؟
            </p>
          </div>
        </div>

        <div className="flex flex-col gap-2.5 mt-6">
          <button
            type="button"
            onClick={onSaveAndExit}
            className="w-full min-h-[46px] px-4 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm rounded-xl transition-colors active:scale-[0.99] whitespace-nowrap"
          >
            حفظ والخروج
          </button>

          <button
            type="button"
            onClick={onExitWithoutSaving}
            className="w-full min-h-[46px] px-4 py-2.5 bg-rose-50 hover:bg-rose-100 text-rose-700 font-bold text-sm rounded-xl transition-colors active:scale-[0.99] whitespace-nowrap"
          >
            الخروج بدون حفظ
          </button>

          <button
            type="button"
            onClick={onCancel}
            className="w-full min-h-[46px] px-4 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-sm rounded-xl transition-colors active:scale-[0.99] whitespace-nowrap"
          >
            إلغاء
          </button>
        </div>
      </div>
    </div>
  );
};
