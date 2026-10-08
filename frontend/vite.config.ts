import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

function normalizeBasePath(value = '/') {
  if (value === './') return './';
  const withLeadingSlash = value.startsWith('/') ? value : `/${value}`;
  return withLeadingSlash.endsWith('/') ? withLeadingSlash : `${withLeadingSlash}/`;
}

export default defineConfig({
  plugins: [vue()],
  base: normalizeBasePath(process.env.VITE_BASE_PATH ?? '/'),
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Split core dependencies manually so the admin shell does not overgrow the first-load bundle.
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          elementPlus: ['element-plus', '@element-plus/icons-vue'],
          network: ['axios'],
        },
      },
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    host: '0.0.0.0',
    // 让 Vite 根据实际启动端口推导 HMR 端口，避免切换到 5175 时仍连接 5173。
    watch: {
      usePolling: true,
      interval: 200,
    },
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8085',
        changeOrigin: true,
        configure(proxy) {
          proxy.on('proxyReq', (proxyRequest, request) => {
            const origin = request.headers.origin;
            const host = request.headers.host;
            if (!origin || !host) return;
            try {
              const browserOrigin = new URL(origin);
              // 仅转换浏览器对当前 Vite 站点发出的同源请求，供本地后端的 CORS 白名单识别。
              if (browserOrigin.protocol === 'http:' && browserOrigin.host === host) {
                proxyRequest.setHeader('Origin', 'http://localhost:5173');
              }
            } catch {
              // 非法或外站 Origin 保持原样，让后端按既有 CORS 规则拒绝。
            }
          });
        },
      },
    },
  },
});
