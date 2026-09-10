import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': new URL('./src', import.meta.url).pathname,
    },
  },
  server: {
    // 0.0.0.0 바인딩. 같은 내부망의 다른 기기에서 http://<내부IP>:5173 으로 붙는다.
    host: true,
    port: 5173,
    // VITE_USE_MOCK=false 로 실서버를 붙일 때 CORS 설정 없이 바로 쓰기 위한 프록시.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
