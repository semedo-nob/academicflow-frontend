import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        // Scanned PDF OCR (Docker tesseract) can take 15–30+ minutes for large sheets
        timeout: 1_800_000,
        proxyTimeout: 1_800_000,
      },
    },
  },
});
