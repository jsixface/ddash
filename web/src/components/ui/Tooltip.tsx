import React, { useState, type ReactNode } from 'react';

interface TooltipProps {
    children: ReactNode;
    content: string;
    isDark: boolean;
}

export const Tooltip: React.FC<TooltipProps> = ({ children, content, isDark }) => {
    const [isVisible, setIsVisible] = useState(false);

    return (
        <div
            className="relative flex items-center"
            onMouseEnter={() => setIsVisible(true)}
            onMouseLeave={() => setIsVisible(false)}
        >
            {children}
            {isVisible && (
                <div className={`
          absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-3 py-1.5 
          ${isDark ? 'bg-slate-800 border-white/10 text-slate-200' : 'bg-white/90 border-white/50 text-slate-600 shadow-rose-900/5'}
          border text-xs rounded-lg shadow-xl whitespace-nowrap z-50 animate-in fade-in zoom-in-95 duration-200 backdrop-blur-md font-medium
        `}>
                    {content}
                    <div className={`absolute bottom-[-4px] left-1/2 -translate-x-1/2 w-2 h-2 border-r border-b rotate-45 ${isDark ? 'bg-slate-800 border-white/10' : 'bg-white/90 border-white/50'}`} />
                </div>
            )}
        </div>
    );
};
