import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Backend Spring Boot chạy profile "web" ở 127.0.0.1:8080; Vite proxy /api để trình duyệt chỉ nói chuyện với một origin.
// Cổng 8080 bận thì chạy backend với --server.port=8090 và đặt BACKEND_URL=http://127.0.0.1:8090 khi `npm run dev`.
const backend = process.env.BACKEND_URL ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    globals: true,
  },
})
