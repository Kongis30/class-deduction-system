import { defineStore } from 'pinia';

export type AppFontSize = 'small' | 'medium' | 'large';
export type LayoutPreference = 'auto' | 'mobile' | 'desktop';

const MOBILE_NAV_QUERY = '(max-width: 920px)';
const LAYOUT_PREFERENCE_STORAGE_KEY = 'jin-layout-preference';

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

function readLayoutPreference(): LayoutPreference {
  if (typeof window === 'undefined') return 'auto';
  const saved = window.localStorage.getItem(LAYOUT_PREFERENCE_STORAGE_KEY);
  return saved === 'mobile' || saved === 'desktop' ? saved : 'auto';
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
    detectedMobileViewport: typeof window !== 'undefined' && window.matchMedia(MOBILE_NAV_QUERY).matches,
    isMobileViewport: typeof window !== 'undefined' && window.matchMedia(MOBILE_NAV_QUERY).matches,
    layoutPreference: readLayoutPreference() as LayoutPreference,
    sidebarCollapsed: typeof window !== 'undefined' && window.matchMedia(MOBILE_NAV_QUERY).matches,
    desktopSidebarCollapsed: false,
    fontSize: readFontSize() as AppFontSize,
  }),
  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed;
      if (!this.isMobileViewport) this.desktopSidebarCollapsed = this.sidebarCollapsed;
    },
    // 进入窄屏时暂存桌面导航状态，恢复宽屏后还原，避免旋转设备后侧栏意外消失。
    setMobileViewport(isMobile: boolean) {
      this.detectedMobileViewport = isMobile;
      this.applyLayoutMode(this.layoutPreference === 'mobile'
        || (this.layoutPreference === 'auto' && isMobile));
    },
    setLayoutPreference(preference: LayoutPreference) {
      this.layoutPreference = preference;
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(LAYOUT_PREFERENCE_STORAGE_KEY, preference);
      }
      this.applyLayoutMode(preference === 'mobile'
        || (preference === 'auto' && this.detectedMobileViewport));
    },
    // 把布局模式同步到 CSS：平板等宽屏设备强制 Mobile 时，@media (max-width: 920px)
    // 不会命中，导致移动端 DOM 配桌面端样式的杂交布局。html.mobile-layout 类让
    // 移动端样式跟随 JS 状态（含 Settings 手动切换）而非屏宽；手机端两者本就同时成立。
    applyLayoutMode(isMobile: boolean) {
      const wasMobile = this.isMobileViewport;
      const enteringMobile = isMobile && !this.isMobileViewport;
      this.isMobileViewport = isMobile;
      if (typeof document !== 'undefined') {
        document.documentElement.classList.toggle('mobile-layout', isMobile);
      }
      if (enteringMobile) {
        this.desktopSidebarCollapsed = this.sidebarCollapsed;
        this.sidebarCollapsed = true;
      } else if (!isMobile && wasMobile) {
        this.sidebarCollapsed = this.desktopSidebarCollapsed;
      }
    },
    initializePreferences() {
      applyFontSize(this.fontSize);
      this.applyLayoutMode(this.layoutPreference === 'mobile'
        || (this.layoutPreference === 'auto' && this.detectedMobileViewport));
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
