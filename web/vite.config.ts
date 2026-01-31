import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// [https://vitejs.dev/config/](https://vitejs.dev/config/)
export default defineConfig(({ mode }) => ({
    plugins: [
        react(),
        tailwindcss(),
    ],
    server: {
        proxy: mode === 'localdev' ? {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            }
        } : undefined
    },
    build: {
        chunkSizeWarningLimit: 1000,
        rollupOptions: {
            output: {
                manualChunks(id) {
                    if (id.includes('node_modules')) {
                        if (id.includes('react') || id.includes('react-dom')) {
                            return 'vendor-react';
                        }
                        if (id.includes('lucide-react')) {
                            return 'vendor-lucide';
                        }
                        return 'vendor';
                    }
                },
            },
        },
    },

}))

