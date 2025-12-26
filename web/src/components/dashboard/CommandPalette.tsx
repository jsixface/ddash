import React, {useEffect, useMemo, useRef, useState} from 'react';
import {ExternalLink, Search} from 'lucide-react';
import type {AppData} from '../../types/dashboard';

interface CommandPaletteProps {
    isOpen: boolean;
    onClose: () => void;
    apps: AppData[];
    isDark: boolean;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({ isOpen, onClose, apps, isDark }) => {
    const [query, setQuery] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (isOpen) {
            setTimeout(() => inputRef.current?.focus(), 50);
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = 'unset';
            setQuery("");
        }
        return () => { document.body.style.overflow = 'unset'; };
    }, [isOpen]);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && isOpen) onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    const filteredApps = useMemo(() => {
        if (!query) return apps.slice(0, 5);
        return apps.filter(app =>
            app.name.toLowerCase().includes(query.toLowerCase()) ||
            app.category.toLowerCase().includes(query.toLowerCase())
        );
    }, [query, apps]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] px-4">
            <div
                className={`absolute inset-0 backdrop-blur-sm animate-in fade-in duration-200 ${isDark ? 'bg-slate-950/60' : 'bg-slate-200/40'}`}
                onClick={onClose}
            />

            <div className={`
        relative w-full max-w-2xl backdrop-blur-2xl border rounded-2xl shadow-2xl overflow-hidden animate-in zoom-in-95 fade-in slide-in-from-bottom-2 duration-200
        ${isDark ? 'bg-slate-900 border-white/10 shadow-black/50' : 'bg-white/70 border-white/60 shadow-violet-500/10'}
      `}>
                <div className={`flex items-center px-4 py-4 border-b ${isDark ? 'border-white/5' : 'border-slate-100/50'}`}>
                    <Search className={`mr-3 ${isDark ? 'text-slate-500' : 'text-slate-400'}`} size={20} />
                    <input
                        ref={inputRef}
                        type="text"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        placeholder="Search apps..."
                        className={`flex-1 bg-transparent border-none text-lg focus:outline-none ${isDark ? 'text-white placeholder-slate-500' : 'text-slate-700 placeholder-slate-400'}`}
                    />
                    <div className="flex gap-2">
                        <kbd className={`hidden sm:inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-sans border ${isDark ? 'bg-white/5 border-white/10 text-slate-400' : 'bg-slate-100 border-slate-200 text-slate-500'}`}>
                            ESC
                        </kbd>
                    </div>
                </div>

                <div className="max-h-[60vh] overflow-y-auto p-2">
                    {filteredApps.length === 0 ? (
                        <div className={`py-12 text-center ${isDark ? 'text-slate-600' : 'text-slate-500'}`}>
                            <p>No results found.</p>
                        </div>
                    ) : (
                        <>
                            {!query && <div className={`px-3 py-2 text-xs font-medium uppercase tracking-wider ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>Suggested</div>}
                            {filteredApps.map((app) => (
                                <button
                                    key={app.id}
                                    className={`
                    w-full flex items-center gap-3 p-3 rounded-xl group transition-colors text-left
                    ${isDark ? 'hover:bg-white/5' : 'hover:bg-violet-50/50'}
                  `}
                                    onClick={() => {
                                        if (app.url) {
                                            window.open(app.url, '_blank', 'noopener,noreferrer');
                                        }
                                        onClose();
                                    }}
                                >
                                    <div className={`
                    p-2 rounded-lg transition-colors border
                    ${isDark
                                            ? 'bg-slate-800 text-slate-400 group-hover:text-indigo-400 group-hover:bg-indigo-500/10 border-transparent'
                                            : 'bg-white text-slate-400 group-hover:text-violet-500 group-hover:border-violet-200 border-slate-100 shadow-sm'}
                  `}>
                                        <app.icon size={18} />
                                    </div>
                                    <div className="flex-1">
                                        <div className={`font-medium ${isDark ? 'text-slate-200' : 'text-slate-700'}`}>{app.name}</div>
                                        <div className={`text-xs ${isDark ? 'text-slate-500' : 'text-slate-400'}`}>{app.category} • {app.status}</div>
                                    </div>
                                    <ExternalLink size={14} className={`opacity-0 group-hover:opacity-100 transition-all ${isDark ? 'text-slate-600 group-hover:text-slate-400' : 'text-slate-400 group-hover:text-violet-400'}`} />
                                </button>
                            ))}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};
