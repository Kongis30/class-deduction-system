import { defineStore } from 'pinia';

export type AppFontSize = 'small' | 'medium' | 'large';

const FONT_SIZE_VALUES: Record<AppFontSize, string> = {
  small: '15px',
  medium: '16px',
  large: '18px',
};

const FONT_SIZE_STORAGE_KEY = 'jin-app-font-size';

function readFontSize(): AppFontSize {
  if (typeof window === 'undefined') {
    return 'medium';
  }
  const saved = window.localStorage.getItem(FONT_SIZE_STORAGE_KEY);
  return saved === 'small' || saved === 'large' ? saved : 'medium';
}

function applyFontSize(size: AppFontSize) {
  if (typeof document === 'undefined') {
    return;
  }
  const value = FONT_SIZE_VALUES[size];
  document.documentElement.style.setProperty('--app-font-size', value);
  document.documentElement.style.setProperty('--el-font-size-base', value);
  document.documentElement.style.setProperty('--el-font-size-small', `calc(${value} * 0.86)`);
  document.documentElement.style.setProperty('--el-font-size-extra-small', `calc(${value} * 0.79)`);
  document.documentElement.style.setProperty('--el-font-size-large', `calc(${value} * 1.14)`);
}

export const useAppStore = defineStore('app', {
  state: () => ({
    sidebarCollapsed: false,
    fontSize: readFontSize() as AppFontSize,
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    },
    initializePreferences() {
      applyFontSize(this.fontSize);
    },
    setFontSize(size: AppFontSize) {
      this.fontSize = size;
      applyFontSize(size);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(FONT_SIZE_STORAGE_KEY, size);
      }
    },
  },
});
