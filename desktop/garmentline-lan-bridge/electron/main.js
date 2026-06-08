const { app, BrowserWindow, ipcMain, shell } = require("electron");
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const bridgeProcesses = new Map();
let mainWindow = null;
let installingDependencies = false;
let quitting = false;

const singleInstanceLock = app.requestSingleInstanceLock();
if (!singleInstanceLock) {
  app.quit();
}

const defaultConfig = {
  backendUrl: "http://localhost:8080",
  bridgeToken: "",
  autoStart: false,
  zkteco: {
    enabled: true,
    deviceIps: "10.10.4.40,10.10.4.41,10.10.4.42,10.10.4.43,10.10.4.46",
    port: 4370,
    password: "0",
    intervalSeconds: 30,
    timeoutSeconds: 8,
    batchSize: 100,
    lookbackHours: 24
  },
  hikvision: {
    enabled: true,
    cameraUrls: "http://10.10.4.101,http://10.10.4.102,http://10.10.4.103,http://10.10.4.104,http://10.10.4.105,http://10.10.4.106,http://10.10.4.107",
    username: "admin",
    password: "",
    intervalSeconds: 5,
    lookbackMinutes: 60,
    maxResults: 30,
    timeoutSeconds: 10
  }
};

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1120,
    height: 780,
    minWidth: 960,
    minHeight: 680,
    title: "Integration Hub LAN Bridge",
    backgroundColor: "#f6f4f3",
    icon: appIconPath(),
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, "..", "renderer", "index.html"));

  mainWindow.on("close", (event) => {
    if (!quitting && isAnyBridgeRunning()) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
}

if (singleInstanceLock) {
  app.on("second-instance", () => {
    mainWindow?.show();
    mainWindow?.focus();
  });

  app.whenReady().then(() => {
    createWindow();
    applyAutoStart(loadConfig().autoStart);

    app.on("activate", () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
        return;
      }
      mainWindow?.show();
    });
  });
}

app.on("before-quit", () => {
  quitting = true;
  stopBridge("zkteco");
  stopBridge("hikvision");
});

function appRoot() {
  return app.isPackaged ? process.resourcesPath : path.join(__dirname, "..");
}

function appIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, "app.asar", "public", "INTEGRATION-HUb.png")
    : path.join(__dirname, "..", "public", "INTEGRATION-HUb.png");
}

function workersDir() {
  return app.isPackaged
    ? path.join(process.resourcesPath, "workers")
    : path.join(__dirname, "..", "workers");
}

function userDataPath(...segments) {
  return path.join(app.getPath("userData"), ...segments);
}

function configPath() {
  return userDataPath("bridge-config.json");
}

function stateDir() {
  const dir = userDataPath("state");
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

function venvDir() {
  return userDataPath(".venv-bridges");
}

function venvPythonPath() {
  return process.platform === "win32"
    ? path.join(venvDir(), "Scripts", "python.exe")
    : path.join(venvDir(), "bin", "python");
}

function pythonExecutable() {
  const venvPython = venvPythonPath();
  if (fs.existsSync(venvPython)) {
    return venvPython;
  }
  return process.env.PYTHON_PATH || (process.platform === "win32" ? "python" : "python3");
}

function loadConfig() {
  try {
    if (!fs.existsSync(configPath())) {
      return structuredClone(defaultConfig);
    }
    const raw = JSON.parse(fs.readFileSync(configPath(), "utf8"));
    return mergeConfig(defaultConfig, raw);
  } catch (error) {
    emitLog("app", `Could not read config: ${error.message}`);
    return structuredClone(defaultConfig);
  }
}

function saveConfig(config) {
  const nextConfig = mergeConfig(defaultConfig, config || {});
  fs.mkdirSync(path.dirname(configPath()), { recursive: true });
  fs.writeFileSync(configPath(), JSON.stringify(nextConfig, null, 2));
  applyAutoStart(nextConfig.autoStart);
  return nextConfig;
}

function mergeConfig(base, override) {
  const result = structuredClone(base);
  for (const [key, value] of Object.entries(override || {})) {
    if (
      value &&
      typeof value === "object" &&
      !Array.isArray(value) &&
      result[key] &&
      typeof result[key] === "object"
    ) {
      result[key] = { ...result[key], ...value };
    } else {
      result[key] = value;
    }
  }
  return result;
}

function applyAutoStart(enabled) {
  try {
    app.setLoginItemSettings({ openAtLogin: Boolean(enabled) });
  } catch (error) {
    emitLog("app", `Could not update login item: ${error.message}`);
  }
}

function isAnyBridgeRunning() {
  return [...bridgeProcesses.values()].some((entry) => entry.process && !entry.process.killed);
}

function statusPayload() {
  return {
    zkteco: Boolean(bridgeProcesses.get("zkteco")?.process),
    hikvision: Boolean(bridgeProcesses.get("hikvision")?.process),
    installingDependencies,
    python: pythonExecutable(),
    appRoot: appRoot(),
    configPath: configPath()
  };
}

function emitStatus() {
  mainWindow?.webContents.send("bridge:status", statusPayload());
}

function emitLog(source, message) {
  const line = {
    source,
    message: String(message || "").trimEnd(),
    at: new Date().toISOString()
  };
  if (line.message) {
    mainWindow?.webContents.send("bridge:log", line);
  }
}

function startBridge(kind, configInput) {
  if (bridgeProcesses.get(kind)?.process) {
    return statusPayload();
  }

  const config = saveConfig(configInput || loadConfig());
  const script =
    kind === "zkteco"
      ? path.join(workersDir(), "zkteco_bridge.py")
      : path.join(workersDir(), "hikvision_bridge.py");

  const child = spawn(pythonExecutable(), [script], {
    cwd: appRoot(),
    env: workerEnv(kind, config),
    stdio: ["ignore", "pipe", "pipe"]
  });

  bridgeProcesses.set(kind, { process: child, startedAt: Date.now() });
  emitLog("app", `Started ${kind} bridge with ${pythonExecutable()}.`);
  emitStatus();

  child.stdout.on("data", (data) => {
    splitLines(data).forEach((line) => emitLog(kind, line));
  });
  child.stderr.on("data", (data) => {
    splitLines(data).forEach((line) => emitLog(kind, line));
  });
  child.on("exit", (code, signal) => {
    bridgeProcesses.delete(kind);
    emitLog("app", `${kind} bridge stopped${signal ? ` by ${signal}` : ` with code ${code}`}.`);
    emitStatus();
  });

  return statusPayload();
}

function stopBridge(kind) {
  const entry = bridgeProcesses.get(kind);
  if (!entry?.process) {
    return statusPayload();
  }
  entry.process.kill();
  bridgeProcesses.delete(kind);
  emitLog("app", `Stopping ${kind} bridge.`);
  emitStatus();
  return statusPayload();
}

function workerEnv(kind, config) {
  const env = {
    ...process.env,
    PYTHONUNBUFFERED: "1",
    BRIDGE_STATE_DIR: stateDir(),
    BRIDGE_SHARED_TOKEN: config.bridgeToken || "",
    BRIDGE_TIME_ZONE: "Asia/Colombo"
  };

  if (kind === "zkteco") {
    Object.assign(env, {
      ZKTECO_BRIDGE_BACKEND_URL: config.backendUrl || "",
      ZKTECO_DEVICE_IPS: config.zkteco.deviceIps || "",
      ZKTECO_PORT: String(config.zkteco.port || 4370),
      ZKTECO_PASSWORD: String(config.zkteco.password || "0"),
      ZKTECO_INTERVAL_SECONDS: String(config.zkteco.intervalSeconds || 30),
      ZKTECO_TIMEOUT_SECONDS: String(config.zkteco.timeoutSeconds || 8),
      ZKTECO_BATCH_SIZE: String(config.zkteco.batchSize || 100),
      ZKTECO_LOOKBACK_HOURS: String(config.zkteco.lookbackHours || 24)
    });
  } else {
    Object.assign(env, {
      HIKVISION_BRIDGE_BACKEND_URL: config.backendUrl || "",
      HIKVISION_CAMERA_URLS: config.hikvision.cameraUrls || "",
      HIKVISION_USERNAME: config.hikvision.username || "",
      HIKVISION_PASSWORD: config.hikvision.password || "",
      HIKVISION_INTERVAL_SECONDS: String(config.hikvision.intervalSeconds || 5),
      HIKVISION_LOOKBACK_MINUTES: String(config.hikvision.lookbackMinutes || 60),
      HIKVISION_MAX_RESULTS: String(config.hikvision.maxResults || 30),
      HIKVISION_TIMEOUT_SECONDS: String(config.hikvision.timeoutSeconds || 10)
    });
  }

  return env;
}

function splitLines(data) {
  return String(data).split(/\r?\n/).filter((line) => line.trim().length > 0);
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, options);
    child.stdout?.on("data", (data) => splitLines(data).forEach((line) => emitLog("deps", line)));
    child.stderr?.on("data", (data) => splitLines(data).forEach((line) => emitLog("deps", line)));
    child.on("error", reject);
    child.on("exit", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${command} ${args.join(" ")} exited with code ${code}`));
    });
  });
}

async function installDependencies() {
  if (installingDependencies) {
    return statusPayload();
  }
  installingDependencies = true;
  emitStatus();
  try {
    fs.mkdirSync(venvDir(), { recursive: true });
    const basePython = process.env.PYTHON_PATH || (process.platform === "win32" ? "python" : "python3");
    emitLog("deps", `Creating Python environment at ${venvDir()}.`);
    await runCommand(basePython, ["-m", "venv", venvDir()], { cwd: appRoot() });
    emitLog("deps", "Installing worker packages.");
    await runCommand(
      venvPythonPath(),
      ["-m", "pip", "install", "--upgrade", "pip", "-r", path.join(workersDir(), "requirements.txt")],
      { cwd: appRoot() }
    );
    emitLog("deps", "Python bridge dependencies are ready.");
  } finally {
    installingDependencies = false;
    emitStatus();
  }
  return statusPayload();
}

function bridgeHealthUrl(backendUrl) {
  return new URL("/api/bridge/health", backendUrl.endsWith("/") ? backendUrl : `${backendUrl}/`).toString();
}

ipcMain.handle("config:get", () => loadConfig());
ipcMain.handle("config:save", (_event, config) => saveConfig(config));
ipcMain.handle("bridge:status", () => statusPayload());
ipcMain.handle("bridge:start", (_event, kind, config) => startBridge(kind, config));
ipcMain.handle("bridge:stop", (_event, kind) => stopBridge(kind));
ipcMain.handle("bridge:startAll", (_event, config) => {
  const saved = saveConfig(config);
  if (saved.zkteco.enabled) {
    startBridge("zkteco", saved);
  }
  if (saved.hikvision.enabled) {
    startBridge("hikvision", saved);
  }
  return statusPayload();
});
ipcMain.handle("bridge:stopAll", () => {
  stopBridge("zkteco");
  stopBridge("hikvision");
  return statusPayload();
});
ipcMain.handle("deps:install", () => installDependencies());
ipcMain.handle("app:setAutoStart", (_event, enabled) => {
  const config = loadConfig();
  config.autoStart = Boolean(enabled);
  return saveConfig(config);
});
ipcMain.handle("app:openUserData", () => shell.openPath(app.getPath("userData")));
ipcMain.handle("bridge:testHealth", async (_event, configInput) => {
  const config = saveConfig(configInput || loadConfig());
  const response = await fetch(bridgeHealthUrl(config.backendUrl), {
    headers: {
      "X-Bridge-Token": config.bridgeToken || ""
    }
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Backend returned ${response.status}: ${text}`);
  }
  emitLog("app", "Backend bridge health check passed.");
  return text;
});
