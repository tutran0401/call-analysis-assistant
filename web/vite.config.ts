import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Backend Spring Boot chạy profile "web" ở 127.0.0.1:8080; Vite proxy /api để trình duyệt chỉ nói chuyện với một origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    globals: true,
  },
})
