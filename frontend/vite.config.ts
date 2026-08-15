import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// docker/.env can remap the gateway's host port (HOST_PORT_GATEWAY) to avoid clashing
// with other local stacks, so let the dev proxy follow it instead of assuming 8080.
const gatewayPort = process.env.HOST_PORT_GATEWAY ?? '8080'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'favicon.svg', 'apple-touch-icon.png'],
      // The MUI chunk (icons included) is ~4.2 MB, over Workbox's 2 MiB default, which fails the
      // build at the service-worker step even though the bundle itself is fine.
      workbox: {
        maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
      },
      manifest: {
        name: 'Civil Engineering Marketplace',
        short_name: 'CivEngMarket',
        description: 'Book civil engineering professionals instantly',
        theme_color: '#667eea',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable'
          }
        ]
      }
    })
  ],
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api': {
        target: `http://localhost:${gatewayPort}`,
        changeOrigin: true,
        secure: false
      },
      '/ws': {
        target: `ws://localhost:${gatewayPort}`,
        ws: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    minify: 'terser',
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
          state: ['@reduxjs/toolkit', 'react-redux'],
          query: ['@tanstack/react-query']
        }
      }
    }
  }
})
