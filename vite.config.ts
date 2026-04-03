import fs from 'fs'
import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'

function githubPagesSpaFallback() {
  return {
    name: 'github-pages-spa-fallback',
    closeBundle() {
      const outDir = path.resolve(__dirname, 'dist')
      const indexFile = path.join(outDir, 'index.html')
      const fallbackFile = path.join(outDir, '404.html')

      if (fs.existsSync(indexFile)) {
        fs.copyFileSync(indexFile, fallbackFile)
      }
    },
  }
}

export default defineConfig({
  base: process.env.VITE_BASE_PATH || '/',
  plugins: [
    react(),
    tailwindcss(),
    githubPagesSpaFallback(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  assetsInclude: ['**/*.svg', '**/*.csv'],
})
