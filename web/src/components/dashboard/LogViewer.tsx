import React, { useEffect, useRef, useState } from 'react';
import { X, Terminal, Clock, ArrowDown, Trash2 } from 'lucide-react';
import type { AppData } from '../../types/dashboard';

interface LogViewerProps {
    app: AppData | null;
    isOpen: boolean;
    onClose: () => void;
    isDark: boolean;
}

export const LogViewer: React.FC<LogViewerProps> = ({ app, isOpen, onClose, isDark }) => {
    const [logs, setLogs] = useState<string[]>([]);
    const [showTimestamps, setShowTimestamps] = useState(false);
    const [autoScroll, setAutoScroll] = useState(true);
    const scrollRef = useRef<HTMLDivElement>(null);
    const abortControllerRef = useRef<AbortController | null>(null);

    useEffect(() => {
        if (isOpen && app) {
            startStreaming();
        } else {
            stopStreaming();
            setLogs([]);
        }
        return () => stopStreaming();
    }, [isOpen, app, showTimestamps]);

    useEffect(() => {
        if (autoScroll && scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [logs, autoScroll]);

    const stopStreaming = () => {
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
            abortControllerRef.current = null;
        }
    };

    const startStreaming = async () => {
        if (!app) return;
        stopStreaming();
        // Clear logs when starting new stream or changing timestamp toggle
        setLogs([]);

        const controller = new AbortController();
        abortControllerRef.current = controller;

        try {
            const response = await fetch(`/api/app/${app.id}/logs?timestamps=${showTimestamps}`, {
                signal: controller.signal
            });

            if (!response.body) return;

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let partialLine = "";

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                const chunk = decoder.decode(value, { stream: true });
                const lines = (partialLine + chunk).split('\n');
                partialLine = lines.pop() || "";

                if (lines.length > 0) {
                    setLogs(prev => [...prev, ...lines]);
                }
            }
            if (partialLine) {
                setLogs(prev => [...prev, partialLine]);
            }
        } catch (error: any) {
            if (error.name !== 'AbortError') {
                console.error('Error streaming logs:', error);
                setLogs(prev => [...prev, `[ERROR] Failed to connect to log stream: ${error.message}`]);
            }
        }
    };

    if (!isOpen || !app) return null;

    return (
        <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 md:p-8">
            <div
                className={`absolute inset-0 backdrop-blur-md animate-in fade-in duration-300 ${isDark ? 'bg-slate-950/70' : 'bg-slate-200/50'}`}
                onClick={onClose}
            />

            <div className={`
                relative w-full max-w-5xl h-[80vh] flex flex-col backdrop-blur-2xl border rounded-2xl shadow-3xl overflow-hidden animate-in zoom-in-95 fade-in duration-300
                ${isDark ? 'bg-slate-900/90 border-white/10 shadow-black/50' : 'bg-white/80 border-white/60 shadow-indigo-500/10'}
            `}>
                {/* Header */}
                <div className={`flex items-center justify-between px-4 py-3 border-b ${isDark ? 'border-white/5' : 'border-slate-100'}`}>
                    <div className="flex items-center gap-3">
                        <div className={`p-2 rounded-lg ${isDark ? 'bg-indigo-500/10 text-indigo-400' : 'bg-violet-100 text-violet-600'}`}>
                            <Terminal size={20} />
                        </div>
                        <div>
                            <h2 className={`font-bold text-lg leading-none ${isDark ? 'text-white' : 'text-slate-800'}`}>
                                {app.name} Logs
                            </h2>
                            <p className={`text-xs mt-1 ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>
                                {app.category} • Streaming from container
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className={`hidden sm:flex items-center gap-1.5 px-2 py-1 rounded-md text-[10px] font-mono border ${isDark ? 'bg-white/5 border-white/10 text-slate-400' : 'bg-slate-100 border-slate-200 text-slate-500'}`}>
                            <div className={`w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse`} />
                            LIVE
                        </div>
                        <button
                            onClick={onClose}
                            className={`p-2 rounded-xl transition-colors ${isDark ? 'hover:bg-white/5 text-slate-400 hover:text-white' : 'hover:bg-slate-100 text-slate-500 hover:text-slate-800'}`}
                        >
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Logs Area */}
                <div
                    ref={scrollRef}
                    className={`
                        flex-1 overflow-y-auto p-4 font-mono text-[13px] leading-relaxed scroll-smooth
                        ${isDark ? 'selection:bg-indigo-500/30 selection:text-indigo-200' : 'selection:bg-violet-200 selection:text-violet-900'}
                    `}
                >
                    {logs.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-full opacity-50 space-y-4">
                            <div className={`animate-spin rounded-full h-10 w-10 border-b-2 ${isDark ? 'border-indigo-500' : 'border-violet-500'}`} />
                            <p className={`text-sm tracking-wide ${isDark ? 'text-slate-400' : 'text-slate-500'}`}>CONNECTING TO LOG STREAM...</p>
                        </div>
                    ) : (
                        <div className="space-y-px">
                            {logs.map((line, i) => (
                                <div
                                    key={i}
                                    className={`
                                        px-3 py-1 rounded-sm transition-colors break-words
                                        ${i % 2 === 0 ? (isDark ? 'bg-white/[0.05]' : 'bg-black/[0.04]') : 'bg-transparent'}
                                        ${isDark ? 'text-slate-300 hover:text-white hover:bg-white/[0.08]' : 'text-slate-600 hover:text-slate-900 hover:bg-black/[0.08]'}
                                    `}
                                >
                                    {line}
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Footer / Toggles */}
                <div className={`px-4 py-3 border-t flex flex-wrap gap-4 items-center justify-between ${isDark ? 'border-white/5 bg-black/20' : 'border-slate-100 bg-slate-50/50'}`}>
                    <div className="flex gap-6">
                        <button
                            onClick={() => setShowTimestamps(!showTimestamps)}
                            className="flex items-center gap-2 cursor-pointer group outline-none"
                        >
                            <div className={`
                                relative w-9 h-5 rounded-full transition-colors duration-200
                                ${showTimestamps ? (isDark ? 'bg-indigo-500' : 'bg-violet-500') : (isDark ? 'bg-slate-700' : 'bg-slate-300')}
                            `}>
                                <div className={`
                                    absolute top-1 left-1 w-3 h-3 bg-white rounded-full transition-transform duration-200
                                    ${showTimestamps ? 'translate-x-4' : 'translate-x-0'}
                                `} />
                            </div>
                            <span className={`text-xs font-semibold flex items-center gap-1.5 transition-colors ${isDark ? 'text-slate-400 group-hover:text-slate-200' : 'text-slate-500 group-hover:text-slate-700'}`}>
                                <Clock size={14} className={showTimestamps ? (isDark ? 'text-indigo-400' : 'text-violet-500') : ''} /> Timestamps
                            </span>
                        </button>

                        <button
                            onClick={() => setAutoScroll(!autoScroll)}
                            className="flex items-center gap-2 cursor-pointer group outline-none"
                        >
                            <div className={`
                                relative w-9 h-5 rounded-full transition-colors duration-200
                                ${autoScroll ? (isDark ? 'bg-indigo-500' : 'bg-violet-500') : (isDark ? 'bg-slate-700' : 'bg-slate-300')}
                            `}>
                                <div className={`
                                    absolute top-1 left-1 w-3 h-3 bg-white rounded-full transition-transform duration-200
                                    ${autoScroll ? 'translate-x-4' : 'translate-x-0'}
                                `} />
                            </div>
                            <span className={`text-xs font-semibold flex items-center gap-1.5 transition-colors ${isDark ? 'text-slate-400 group-hover:text-slate-200' : 'text-slate-500 group-hover:text-slate-700'}`}>
                                <ArrowDown size={14} className={autoScroll ? (isDark ? 'text-indigo-400' : 'text-violet-500') : ''} /> Auto-scroll
                            </span>
                        </button>
                    </div>

                    <button
                        onClick={() => setLogs([])}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all
                            ${isDark
                                ? 'bg-white/5 text-slate-400 hover:text-white hover:bg-white/10 active:scale-95'
                                : 'bg-slate-200/50 text-slate-500 hover:text-slate-800 hover:bg-slate-200 active:scale-95'}
                        `}
                    >
                        <Trash2 size={14} /> Clear View
                    </button>
                </div>
            </div>
        </div>
    );
};
