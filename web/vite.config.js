import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return;
          if (id.includes('/antd/') || id.includes('@ant-design')) return 'antd';
          if (id.includes('/react-dom/') || id.includes('/react/')) return 'react';
          if (id.includes('/axios/')) return 'axios';
        }
      }
    }
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js']
  }
});
