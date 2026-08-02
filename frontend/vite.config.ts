import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  // Dev-only proxy: forwards /api/* to the real API Gateway with the /api
  // prefix stripped. This sidesteps CORS locally — the backend only allows the
  // CloudFront origin, not localhost. Production builds call the API directly.
  server: {
    proxy: {
      '/api': {
        target: 'https://zhirkpafz2.execute-api.us-east-1.amazonaws.com',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
        // changeOrigin only rewrites Host, not Origin. Spring's @CrossOrigin
        // rejects the actual request (not just preflight) when Origin doesn't
        // match the allowed value, so overwrite it with the real frontend origin.
        headers: {
          Origin: 'https://d2cxbcvjwx9lw7.cloudfront.net',
        },
      },
    },
  },
})
