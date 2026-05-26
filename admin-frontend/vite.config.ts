import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8081',
      '/admin': 'http://localhost:8081',
      '/files': 'http://localhost:8081',
    },
  },
})
