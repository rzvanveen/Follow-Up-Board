const { app, BrowserWindow, ipcMain, shell, Menu, net } = require("electron");
const path = require("path");
const fs = require("fs");
let autoUpdater = null;
try { ({ autoUpdater } = require("electron-updater")); } catch {}

app.setName("Follow-Up Board");
app.setAppUserModelId("com.ryanvanveen.followupboard");

const dataDir = () => app.getPath("userData"); // %APPDATA%\Follow-Up Board
const windowFile = () => path.join(dataDir(), "window.json");
const readJSON = (f, fb) => { try { return JSON.parse(fs.readFileSync(f, "utf8")); } catch { return fb; } };

// Requests to the Google Sheet go through here, so the page never hits browser cross-site limits.
ipcMain.handle("http", async (_e, { method, url, body }) => {
  if (!/^https:\/\/script\.google(usercontent)?\.com\//.test(url)) return { status: 0, text: "" };
  try {
    const r = await net.fetch(url, {
      method,
      body: body || undefined,
      headers: body ? { "Content-Type": "text/plain;charset=utf-8" } : undefined,
      redirect: "follow",
    });
    return { status: r.status, text: await r.text() };
  } catch {
    return { status: 0, text: "" };
  }
});

// The board saved by the earlier, offline version of this app (if any), so it can be copied to the sheet.
ipcMain.handle("legacy:load", () => readJSON(path.join(dataDir(), "board.json"), null));

ipcMain.on("app:version", (e) => { e.returnValue = app.getVersion(); });
ipcMain.on("update:install", () => { if (autoUpdater) autoUpdater.quitAndInstall(true, true); });

// Updates: GitHub releases are checked at start and every 4 hours. A new version downloads
// quietly, installs when the app closes, or right away from the "Restart to update" button.
function startUpdates() {
  if (!autoUpdater || !app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on("update-downloaded", (info) => { if (win) win.webContents.send("update:ready", { ready: true, version: info && info.version }); });
  autoUpdater.on("error", () => {});
  const check = () => autoUpdater.checkForUpdates().catch(() => {});
  check();
  setInterval(check, 4 * 60 * 60 * 1000);
}

let win;
ipcMain.on("win:focus", () => { if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); } });

function createWindow() {
  const b = readJSON(windowFile(), { width: 1400, height: 900 });
  win = new BrowserWindow({
    width: b.width, height: b.height, x: b.x, y: b.y,
    minWidth: 420, minHeight: 500,
    title: "Follow-Up Board",
    backgroundColor: "#eef1f4",
    autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, spellcheck: true },
  });
  if (b.maximized) win.maximize();
  win.loadFile(path.join(__dirname, "index.html"));
  win.on("page-title-updated", (e) => e.preventDefault());

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^(mailto:|https?:)/i.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e, url) => {
    if (!url.startsWith("file:")) { e.preventDefault(); if (/^(mailto:|https?:)/i.test(url)) shell.openExternal(url); }
  });
  win.on("close", () => {
    try { fs.mkdirSync(dataDir(), { recursive: true }); fs.writeFileSync(windowFile(), JSON.stringify({ ...win.getNormalBounds(), maximized: win.isMaximized() })); } catch {}
  });
}

Menu.setApplicationMenu(Menu.buildFromTemplate([
  { label: "File", submenu: [
    { label: "Open data folder", click: () => shell.openPath(dataDir()) },
    { type: "separator" },
    { role: "quit", label: "Exit" },
  ]},
  { label: "View", submenu: [
    { role: "reload" }, { type: "separator" },
    { role: "resetZoom" }, { role: "zoomIn" }, { role: "zoomOut" }, { type: "separator" },
    { role: "togglefullscreen" },
  ]},
]));

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
  app.whenReady().then(() => { createWindow(); startUpdates(); });
  app.on("window-all-closed", () => app.quit());
}
