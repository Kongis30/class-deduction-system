import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';

import App from './App.vue';
import router from './router';
import { useAuthStore } from './stores/auth';
import './assets/styles/index.css';

const app = createApp(App);
const pinia = createPinia();

app.use(pinia);
const authStore = useAuthStore(pinia);
await authStore.bootstrap();
authStore.startAutoRefresh();
app.use(router);
app.use(ElementPlus);

app.mount('#app');
