import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

import App from './App.vue';
import router from './router';
import { useAuthStore } from './stores/auth';
import './assets/styles/index.css';
import './assets/styles/workbook.css';

const preloadReloadKey = 'jbt-vite-preload-reload';
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault();
  if (sessionStorage.getItem(preloadReloadKey)) {
    console.error('[app] A page asset failed to load after refreshing.');
    return;
  }
  sessionStorage.setItem(preloadReloadKey, '1');
  window.location.reload();
});
router.afterEach(() => sessionStorage.removeItem(preloadReloadKey));

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
const authStore = useAuthStore(pinia);
await authStore.bootstrap();
authStore.startAutoRefresh();
app.use(router);
app.use(ElementPlus);

app.mount('#app');
