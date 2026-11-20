import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
  root: './',
  base: './',
  resolve: {
    alias: {
      'vs': resolve(__dirname, 'src/core/vs'),
      '@mythic': resolve(__dirname, '../../Mythic-Editor/src'),
      'mythic-editor': resolve(__dirname, '../../Mythic-Editor/src')
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    cors: true
  },
  build: {
    outDir: resolve(__dirname, '../app/src/main/assets/editor'),
    emptyOutDir: true,
    rollupOptions: {
      input: resolve(__dirname, 'index.html'),
      output: {
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]'
      }
    }
  }
});
