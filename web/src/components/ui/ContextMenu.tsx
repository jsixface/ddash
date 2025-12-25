import React, { useState, useEffect, useRef, type ReactNode } from 'react';
import type { MenuItem } from '../../types/dashboard';

interface ContextMenuProps {
    children: ReactNode;
    menuItems: MenuItem[];
    isDark: boolean;
}

export const ContextMenu: React.FC<ContextMenuProps> = ({ children, menuItems, isDark }) => {
    const [visible, setVisible] = useState(false);
    const [position, setPosition] = useState({ x: 0, y: 0 });
    const menuRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                setVisible(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleContextMenu = (e: React.MouseEvent) => {
        e.preventDefault();
        setVisible(true);
        setPosition({ x: e.clientX, y: e.clientY });
    };

    return (
        <div onContextMenu={handleContextMenu}>
            {children}
            {visible && (
                <div
                    ref={menuRef}
                    style={{ top: position.y, left: position.x }}
                    className={`
            fixed z-50 w-48 backdrop-blur-xl border rounded-xl shadow-2xl p-1.5 animate-in fade-in zoom-in-95 duration-100
            ${isDark ? 'bg-slate-900/95 border-white/10 shadow-black/50' : 'bg-white/80 border-white/50 shadow-slate-200/50'}
          `}
                >
                    {menuItems.map((item, idx) => (
                        <button
                            key={idx}
                            onClick={(e) => {
                                e.stopPropagation();
                                item.action();
                                setVisible(false);
                            }}
                            className={`
                w-full flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-left transition-colors
                ${item.variant === 'danger'
                                    ? (isDark ? 'text-red-400 hover:bg-red-500/10' : 'text-rose-500 hover:bg-rose-50')
                                    : (isDark ? 'text-slate-300 hover:bg-white/10 hover:text-white' : 'text-slate-600 hover:bg-violet-50 hover:text-violet-600')}
              `}
                        >
                            <item.icon size={14} />
                            {item.label}
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};
