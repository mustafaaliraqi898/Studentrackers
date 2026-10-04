import React from 'react';
import { RatingValue } from '../types';

interface RatingButtonsProps {
  label: string;
  value: RatingValue;
  onChange: (newVal: RatingValue) => void;
  disabled?: boolean;
  calculatedScoreText?: string;
}

export const RatingButtons: React.FC<RatingButtonsProps> = ({
  label,
  value,
  onChange,
  disabled = false,
  calculatedScoreText,
}) => {
  const handleSelect = (target: 1 | 0.5 | 0) => {
    if (disabled) return;
    // Clicking the already-selected rating toggles it back to null (unrecorded for today)
    if (value === target) {
      onChange(null);
    } else {
      onChange(target);
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between text-xs">
        <span className="font-semibold text-slate-700">{label}</span>
        {calculatedScoreText && (
          <span className="text-slate-500 font-mono-num">{calculatedScoreText}</span>
        )}
      </div>
      <div className="grid grid-cols-3 gap-1.5 p-1 bg-slate-100 rounded-xl border border-slate-200/70">
        <button
          type="button"
          disabled={disabled}
          onClick={() => handleSelect(1)}
          className={`min-h-[42px] px-2 py-1.5 rounded-lg text-xs font-bold transition-all whitespace-nowrap flex items-center justify-center gap-1 ${
            value === 1
              ? 'bg-emerald-600 text-white shadow-xs'
              : 'bg-transparent text-slate-600 hover:bg-white/60 hover:text-slate-900'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : 'active:scale-[0.98]'}`}
        >
          <span>كامل</span>
        </button>

        <button
          type="button"
          disabled={disabled}
          onClick={() => handleSelect(0.5)}
          className={`min-h-[42px] px-2 py-1.5 rounded-lg text-xs font-bold transition-all whitespace-nowrap flex items-center justify-center gap-1 ${
            value === 0.5
              ? 'bg-amber-500 text-white shadow-xs'
              : 'bg-transparent text-slate-600 hover:bg-white/60 hover:text-slate-900'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : 'active:scale-[0.98]'}`}
        >
          <span>نصف</span>
        </button>

        <button
          type="button"
          disabled={disabled}
          onClick={() => handleSelect(0)}
          className={`min-h-[42px] px-2 py-1.5 rounded-lg text-xs font-bold transition-all whitespace-nowrap flex items-center justify-center gap-1 ${
            value === 0
              ? 'bg-rose-600 text-white shadow-xs'
              : 'bg-transparent text-slate-600 hover:bg-white/60 hover:text-slate-900'
          } ${disabled ? 'opacity-60 cursor-not-allowed' : 'active:scale-[0.98]'}`}
        >
          <span>صفر</span>
        </button>
      </div>
    </div>
  );
};
