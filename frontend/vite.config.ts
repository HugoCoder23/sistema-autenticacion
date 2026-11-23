import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo, /api se redirige al backend Spring Boot. Al ser el mismo origen, la cookie
// HttpOnly del refresh token (SameSite=Strict) funciona sin configuración CORS adicional.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175,
    strictPort: true,
    proxy: {
      '/api': { target: process.env.VITE_API_PROXY ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
});
