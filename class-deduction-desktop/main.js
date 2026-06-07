const { app, BrowserWindow, dialog } = require('electron');
const path = require('path');
const fs = require('fs');

function findDistDir() {
  // 开发环境：直接从 frontend 目录加载
  const devDir = path.join(__dirname, '..', 'frontend', 'dist');
  if (fs.existsSync(path.join(devDir, 'index.html'))) {
    return devDir;
  }
  // 打包环境：从 extraResources 目录加载
  const prodDir = path.join(process.resourcesPath, 'frontend-dist');
  if (fs.existsSync(path.join(prodDir, 'index.html'))) {
    return prodDir;
  }
  return null;
}

let mainWindow = null;

function createWindow() {
  const distDir = findDistDir();

  if (!distDir) {
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
    show: false, // 先隐藏窗口，等页面渲染就绪后再显示，避免白屏
    backgroundColor: '#f5f6fa', // 背景色与应用主题一致，防止闪烁
    icon: fs.existsSync(path.join(distDir, 'logo.jpg')) ? path.join(distDir, 'logo.jpg') : undefined,
    title: 'JIN BEST TUTORING',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(path.join(distDir, 'index.html'));

  // 等待页面加载完成后显示窗口，消除白屏
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    if (process.env.NODE_ENV === 'development') {
      mainWindow.webContents.openDevTools();
    }
  });

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
