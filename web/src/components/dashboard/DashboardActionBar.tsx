import React from 'react';
import { Search, CheckCircle, Edit3, Sun, Moon, Settings } from 'lucide-react';
import { Tooltip } from '../ui/Tooltip';

interface DashboardActionBarProps {
    onSearchClick: () => void;
    editMode: boolean;
    setEditMode: (mode: boolean) => void;
    isDark: boolean;
    setIsDark: (dark: boolean) => void;
}

export const DashboardActionBar: React.FC<DashboardActionBarProps> = ({
    onSearchClick,
    editMode,
    setEditMode,
    isDark,
    setIsDark
}) => {
    return (
        <div className="fixed bottom-6 inset-x-4 md:sticky md:top-6 md:bottom-auto md:inset-x-0 z-50 mb-0 md:mb-12">
            <div className={`flex items-center justify-between gap-2 md:gap-4 p-1.5 md:p-2 backdrop-blur-xl border rounded-xl md:rounded-2xl shadow-2xl max-w-4xl mx-auto transition-colors duration-300 ${isDark ? 'bg-slate-900/80 border-white/10' : 'bg-white/60 border-white/60 shadow-slate-200/50'}`}>

                {/* Fake Search Trigger */}
                <button
                    onClick={onSearchClick}
                    className={`flex-1 flex items-center gap-2 md:gap-3 px-3 md:px-4 py-2 md:py-3 border rounded-lg md:rounded-xl transition-all group text-left shadow-sm ${isDark ? 'bg-white/5 hover:bg-white/10 border-white/5 hover:border-white/10' : 'bg-white/40 hover:bg-white/60 border-white/40 hover:border-white/60'}`}
                >
                    <Search className={`${isDark ? 'text-slate-400 group-hover:text-slate-200' : 'text-slate-400 group-hover:text-slate-600'}`} size={18} />
                    <span className={`text-sm md:text-base font-medium ${isDark ? 'text-slate-400 group-hover:text-slate-300' : 'text-slate-400 group-hover:text-slate-600'}`}>Quick launch...</span>
                    <div className="ml-auto flex gap-1">
                        <kbd className={`hidden md:inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-mono border ${isDark ? 'bg-slate-800 text-slate-400 border-white/10' : 'bg-slate-100 text-slate-500 border-slate-200'}`}>
                            ⌘K
                        </kbd>
                    </div>
                </button>

                <div className={`hidden sm:block w-px h-6 md:h-8 mx-0.5 md:mx-1 ${isDark ? 'bg-white/10' : 'bg-slate-200'}`} />

                {/* Tools */}
                <div className="flex items-center gap-1 md:gap-2 pr-1 md:pr-2">
                    <Tooltip content={editMode ? "Save Layout" : "Edit Layout"} isDark={isDark}>
                        <button
                            onClick={() => setEditMode(!editMode)}
                            className={`p-2 md:p-3 rounded-lg md:rounded-xl transition-all shadow-sm ${editMode ? 'bg-indigo-500 text-white shadow-lg shadow-indigo-500/25' : (isDark ? 'hover:bg-white/10 text-slate-400 hover:text-white' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-violet-500')}`}
                        >
                            {editMode ? <CheckCircle size={18} className="md:w-5 md:h-5" /> : <Edit3 size={18} className="md:w-5 md:h-5" />}
                        </button>
                    </Tooltip>

                    <Tooltip content={isDark ? "Light Mode" : "Dark Mode"} isDark={isDark}>
                        <button
                            onClick={() => setIsDark(!isDark)}
                            className={`p-2 md:p-3 rounded-lg md:rounded-xl transition-all shadow-sm ${isDark ? 'hover:bg-white/10 text-slate-400 hover:text-amber-300' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-violet-500'}`}
                        >
                            {isDark ? <Sun size={18} className="md:w-5 md:h-5" /> : <Moon size={18} className="md:w-5 md:h-5" />}
                        </button>
                    </Tooltip>

                    <Tooltip content="System Settings" isDark={isDark}>
                        <button className={`p-2 md:p-3 rounded-lg md:rounded-xl transition-colors shadow-sm ${isDark ? 'hover:bg-white/10 text-slate-400 hover:text-white' : 'bg-white/40 hover:bg-white/80 border border-white/40 hover:border-white/60 text-slate-400 hover:text-slate-600'}`}>
                            <Settings size={18} className="md:w-5 md:h-5" />
                        </button>
                    </Tooltip>
                </div>
            </div>
        </div>
    );
};
