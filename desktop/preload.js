const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("desktop", {
  http: (method, url, body) => ipcRenderer.invoke("http", { method, url, body }),
  loadLegacy: () => ipcRenderer.invoke("legacy:load"),
  focus: () => ipcRenderer.send("win:focus"),
  version: ipcRenderer.sendSync("app:version"),
  onUpdate: (cb) => ipcRenderer.on("update:ready", (_e, info) => cb(info)),
  installUpdate: () => ipcRenderer.send("update:install"),
});
