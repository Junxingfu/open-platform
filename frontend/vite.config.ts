import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发时前端跑 5173，/portal/api 代理到后端 8081，避免跨域。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/portal/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
})
