const { app, BrowserWindow, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

// 前端构建产物路径
const DIST_DIR = path.join(__dirname, '..', 'frontend', 'dist');
const INDEX_HTML = path.join(DIST_DIR, 'index.html');
const LOGO_PATH = path.join(DIST_DIR, 'logo.jpg');

let mainWindow = null;

function createWindow() {
  // 检查前端构建产物是否存在
  if (!fs.existsSync(INDEX_HTML)) {
    dialog.showErrorBox(
      '前端未构建',
      '请先在 frontend 目录执行 npm run build 构建前端项目。'
    );
    app.quit();
    return;
  }

  mainWindow = new BrowserWindow({
    width: 1366,
    height: 900,
    minWidth: 1024,
    minHeight: 700,
    icon: fs.existsSync(LOGO_PATH) ? LOGO_PATH : undefined,
    title: 'JIN BEST TUTORING',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(INDEX_HTML);

  // 开发环境下可打开 DevTools
  if (process.env.NODE_ENV === 'development') {
    mainWindow.webContents.openDevTools();
  }

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (mainWindow === null) {
    createWindow();
  }
});
