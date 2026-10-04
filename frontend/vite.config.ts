import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // 开发期把 /api 代理到后端，避免跨域。
    // 为什么要给请求统一加 /api 前缀：生产环境前端由 nginx 提供静态文件、
    // /api 反代到后端；如果开发期直接用后端根路径，两套环境的路径规则就不一致了。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // 图片由 frontend/public/ 直接提供，不要代理到后端：
      // 代理过去会撞上登录拦截器返回 401，页面上表现为分类图标和封面全是灰块。
    },
  },
})
