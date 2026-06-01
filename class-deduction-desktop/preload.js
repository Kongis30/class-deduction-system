const { contextBridge } = require('electron');

// 暴露给渲染进程的 API
contextBridge.exposeInMainWorld('electronAPI', {
  platform: process.platform,
});
