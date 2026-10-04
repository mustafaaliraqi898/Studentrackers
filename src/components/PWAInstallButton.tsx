import React, { useState } from 'react';
import { Download, Smartphone, WifiOff, X } from 'lucide-react';
import { useOnlineStatus, usePWAInstall } from '../hooks/usePWAInstall';

export const PWAInstallButton: React.FC<{ variant?: 'compact' | 'card' }> = ({
  variant = 'compact',
}) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  if (isInstalled) {
    if (variant === 'card') {
      return (
        <div className="bg-emerald-50 border border-emerald-200 rounded-2xl p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Smartphone className="w-5 h-5 text-emerald-700 shrink-0" />
            <div>
              <p className="text-sm font-bold text-emerald-950">التطبيق مثبّت على جهازك</p>
              <p className="text-xs text-emerald-800">
                يعمل التطبيق حالياً بوضع مستقل (Standalone Offline) بقاعدة بيانات SQLite محلية.
              </p>
            </div>
          </div>
        </div>
      );
    }
    return null;
  }

  return (
    <>
      {variant === 'compact' ? (
        <button
          type="button"
          onClick={() => {
            if (isInstallable) {
              install();
            } else {
              setShowInstallGuide(true);
            }
          }}
          className="min-h-[38px] px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-bold flex items-center gap-1.5 transition-colors whitespace-nowrap"
        >
          <Download className="w-3.5 h-3.5" />
          <span>تثبيت التطبيق</span>
        </button>
      ) : (
        <div className="bg-slate-900 text-white rounded-2xl p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-start sm:items-center gap-3.5">
            <div className="w-11 h-11 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0">
              <Smartphone className="w-6 h-6" />
            </div>
            <div>
              <h4 className="text-sm font-extrabold">
                تثبيت النسخة التجريبية على هاتف Android
              </h4>
              <p className="text-xs text-slate-300 mt-0.5 leading-relaxed">
                ثبّت التطبيق على الشاشة الرئيسية ليعمل كتطبيق مستقل بالكامل بدون إنترنت (Offline SQLite).
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              if (isInstallable) {
                install();
              } else {
                setShowInstallGuide(true);
              }
            }}
            className="min-h-[44px] px-5 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-extrabold text-xs rounded-xl flex items-center justify-center gap-2 shrink-0 whitespace-nowrap transition-colors"
          >
            <Download className="w-4 h-4" />
            <span>تثبيت على الجهاز الآن</span>
          </button>
        </div>
      )}

      {showInstallGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-extrabold text-slate-900">
                خطوات تثبيت التطبيق على الهاتف
              </h3>
              <button
                type="button"
                onClick={() => setShowInstallGuide(false)}
                className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {isIOS ? (
              <div className="space-y-2 text-xs text-slate-600 leading-relaxed">
                <p>1. اضغط على زر <strong>المشاركة (Share)</strong> في شريط متصفح Safari.</p>
                <p>2. اختر <strong>إضافة إلى الشاشة الرئيسية (Add to Home Screen)</strong>.</p>
                <p>3. اضغط <strong>إضافة</strong> ليظهر التطبيق على شاشة هاتفك ويعمل بدون إنترنت.</p>
              </div>
            ) : (
              <div className="space-y-2 text-xs text-slate-600 leading-relaxed">
                <p>1. افتح رابط التطبيق في متصفح <strong>Google Chrome</strong> على جهاز Android.</p>
                <p>2. اضغط على قائمة المتصفح العلوية <strong>(⋮)</strong>.</p>
                <p>3. اختر <strong>تثبيت التطبيق (Install App)</strong> أو <strong>الإضافة إلى الشاشة الرئيسية</strong>.</p>
              </div>
            )}

            <button
              type="button"
              onClick={() => setShowInstallGuide(false)}
              className="w-full min-h-[44px] rounded-xl bg-slate-900 text-white text-xs font-bold"
            >
              حسناً، فهمت
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div className="fixed bottom-20 left-4 z-50 flex items-center gap-2 rounded-xl bg-slate-900 border border-slate-700 px-3.5 py-2 text-xs font-bold text-white shadow-lg">
      <WifiOff className="w-4 h-4 text-emerald-400 shrink-0" />
      <span>وضع عدم الاتصال (Offline) — قاعدة بيانات SQLite المحلية نشطة</span>
    </div>
  );
};
