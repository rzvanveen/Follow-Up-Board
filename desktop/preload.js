const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("desktop", {
  http: (method, url, body) => ipcRenderer.invoke("http", { method, url, body }),
  loadLegacy: () => ipcRenderer.invoke("legacy:load"),
  focus: () => ipcRenderer.send("win:focus"),
});
