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
    // 이름으로 들어와도 받는다. vite 는 IP 가 아닌 Host 헤더를 기본으로 막아서,
    // 다른 기기에서 `http://맥이름.local:5173` 으로 열면 화면 대신
    // "Blocked request. This host is not allowed" 만 나온다 (9/11 확인).
    // 사내망에서 쓰는 Bonjour 이름(.local)만 연다 — 아무 이름이나 열지는 않는다.
    allowedHosts: ['.local'],
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
