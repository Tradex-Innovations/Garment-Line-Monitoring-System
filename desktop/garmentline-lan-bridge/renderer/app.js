const fields = {
  backendUrl: document.querySelector("#backendUrl"),
  bridgeToken: document.querySelector("#bridgeToken"),
  autoStart: document.querySelector("#autoStart"),
  zktecoEnabled: document.querySelector("#zktecoEnabled"),
  zktecoDeviceIps: document.querySelector("#zktecoDeviceIps"),
  zktecoPort: document.querySelector("#zktecoPort"),
  zktecoPassword: document.querySelector("#zktecoPassword"),
  zktecoInterval: document.querySelector("#zktecoInterval"),
  zktecoTimeout: document.querySelector("#zktecoTimeout"),
  zktecoBatchSize: document.querySelector("#zktecoBatchSize"),
  zktecoLookback: document.querySelector("#zktecoLookback"),
  hikvisionEnabled: document.querySelector("#hikvisionEnabled"),
  hikvisionCameraUrls: document.querySelector("#hikvisionCameraUrls"),
  hikvisionUsername: document.querySelector("#hikvisionUsername"),
  hikvisionPassword: document.querySelector("#hikvisionPassword"),
  hikvisionInterval: document.querySelector("#hikvisionInterval"),
  hikvisionTimeout: document.querySelector("#hikvisionTimeout"),
  hikvisionLookback: document.querySelector("#hikvisionLookback"),
  hikvisionMaxResults: document.querySelector("#hikvisionMaxResults")
};

const buttons = {
  saveConfig: document.querySelector("#saveConfig"),
  installDeps: document.querySelector("#installDeps"),
  testBackend: document.querySelector("#testBackend"),
  startAll: document.querySelector("#startAll"),
  stopAll: document.querySelector("#stopAll"),
  startZkteco: document.querySelector("#startZkteco"),
  stopZkteco: document.querySelector("#stopZkteco"),
  startHikvision: document.querySelector("#startHikvision"),
  stopHikvision: document.querySelector("#stopHikvision"),
  clearLog: document.querySelector("#clearLog"),
  openConfigFolder: document.querySelector("#openConfigFolder")
};

const zktecoStatus = document.querySelector("#zktecoStatus");
const hikvisionStatus = document.querySelector("#hikvisionStatus");
const pythonPath = document.querySelector("#pythonPath");
const logOutput = document.querySelector("#logOutput");

let latestStatus = {};

function numberValue(input, fallback) {
  const parsed = Number(input.value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function formConfig() {
  return {
    backendUrl: fields.backendUrl.value.trim(),
    bridgeToken: fields.bridgeToken.value,
    autoStart: fields.autoStart.checked,
    zkteco: {
      enabled: fields.zktecoEnabled.checked,
      deviceIps: fields.zktecoDeviceIps.value.trim(),
      port: numberValue(fields.zktecoPort, 4370),
      password: fields.zktecoPassword.value,
      intervalSeconds: numberValue(fields.zktecoInterval, 30),
      timeoutSeconds: numberValue(fields.zktecoTimeout, 8),
      batchSize: numberValue(fields.zktecoBatchSize, 100),
      lookbackHours: numberValue(fields.zktecoLookback, 24)
    },
    hikvision: {
      enabled: fields.hikvisionEnabled.checked,
      cameraUrls: fields.hikvisionCameraUrls.value.trim(),
      username: fields.hikvisionUsername.value.trim(),
      password: fields.hikvisionPassword.value,
      intervalSeconds: numberValue(fields.hikvisionInterval, 5),
      timeoutSeconds: numberValue(fields.hikvisionTimeout, 10),
      lookbackMinutes: numberValue(fields.hikvisionLookback, 60),
      maxResults: numberValue(fields.hikvisionMaxResults, 30)
    }
  };
}

function fillForm(config) {
  fields.backendUrl.value = config.backendUrl || "";
  fields.bridgeToken.value = config.bridgeToken || "";
  fields.autoStart.checked = Boolean(config.autoStart);
  fields.zktecoEnabled.checked = Boolean(config.zkteco?.enabled);
  fields.zktecoDeviceIps.value = config.zkteco?.deviceIps || "";
  fields.zktecoPort.value = config.zkteco?.port ?? 4370;
  fields.zktecoPassword.value = config.zkteco?.password || "";
  fields.zktecoInterval.value = config.zkteco?.intervalSeconds ?? 30;
  fields.zktecoTimeout.value = config.zkteco?.timeoutSeconds ?? 8;
  fields.zktecoBatchSize.value = config.zkteco?.batchSize ?? 100;
  fields.zktecoLookback.value = config.zkteco?.lookbackHours ?? 24;
  fields.hikvisionEnabled.checked = Boolean(config.hikvision?.enabled);
  fields.hikvisionCameraUrls.value = config.hikvision?.cameraUrls || "";
  fields.hikvisionUsername.value = config.hikvision?.username || "";
  fields.hikvisionPassword.value = config.hikvision?.password || "";
  fields.hikvisionInterval.value = config.hikvision?.intervalSeconds ?? 5;
  fields.hikvisionTimeout.value = config.hikvision?.timeoutSeconds ?? 10;
  fields.hikvisionLookback.value = config.hikvision?.lookbackMinutes ?? 60;
  fields.hikvisionMaxResults.value = config.hikvision?.maxResults ?? 30;
}

function appendLog(line) {
  const at = line.at ? new Date(line.at).toLocaleTimeString() : new Date().toLocaleTimeString();
  logOutput.textContent += `[${at}] ${line.source}: ${line.message}\n`;
  logOutput.scrollTop = logOutput.scrollHeight;
}

function setBusy(button, busy, label) {
  button.disabled = busy;
  if (label) {
    button.textContent = label;
  }
}

function updateStatus(status) {
  latestStatus = status || {};
  zktecoStatus.textContent = latestStatus.zkteco ? "ZKTeco running" : "ZKTeco stopped";
  zktecoStatus.classList.toggle("running", Boolean(latestStatus.zkteco));
  hikvisionStatus.textContent = latestStatus.hikvision ? "Hikvision running" : "Hikvision stopped";
  hikvisionStatus.classList.toggle("running", Boolean(latestStatus.hikvision));
  pythonPath.textContent = latestStatus.python || "Not checked";

  buttons.installDeps.disabled = Boolean(latestStatus.installingDependencies);
  buttons.startZkteco.disabled = Boolean(latestStatus.zkteco);
  buttons.stopZkteco.disabled = !latestStatus.zkteco;
  buttons.startHikvision.disabled = Boolean(latestStatus.hikvision);
  buttons.stopHikvision.disabled = !latestStatus.hikvision;
}

async function saveCurrentConfig() {
  const saved = await window.bridgeApp.saveConfig(formConfig());
  fillForm(saved);
  appendLog({ source: "app", message: "Configuration saved." });
  return saved;
}

async function runAction(button, workingLabel, action) {
  const original = button.textContent;
  try {
    setBusy(button, true, workingLabel);
    const result = await action();
    updateStatus(await window.bridgeApp.status());
    return result;
  } catch (error) {
    appendLog({ source: "app", message: error.message || String(error) });
  } finally {
    setBusy(button, false, original);
  }
}

buttons.saveConfig.addEventListener("click", () => {
  void runAction(buttons.saveConfig, "Saving", saveCurrentConfig);
});

buttons.installDeps.addEventListener("click", () => {
  void runAction(buttons.installDeps, "Installing", async () => {
    await saveCurrentConfig();
    return window.bridgeApp.installDependencies();
  });
});

buttons.testBackend.addEventListener("click", () => {
  void runAction(buttons.testBackend, "Testing", async () => {
    await window.bridgeApp.testBackend(formConfig());
    appendLog({ source: "app", message: "Backend bridge endpoint is reachable." });
  });
});

buttons.startAll.addEventListener("click", () => {
  void runAction(buttons.startAll, "Starting", () => window.bridgeApp.startAll(formConfig()));
});

buttons.stopAll.addEventListener("click", () => {
  void runAction(buttons.stopAll, "Stopping", () => window.bridgeApp.stopAll());
});

buttons.startZkteco.addEventListener("click", () => {
  void runAction(buttons.startZkteco, "Starting", () => window.bridgeApp.start("zkteco", formConfig()));
});

buttons.stopZkteco.addEventListener("click", () => {
  void runAction(buttons.stopZkteco, "Stopping", () => window.bridgeApp.stop("zkteco"));
});

buttons.startHikvision.addEventListener("click", () => {
  void runAction(buttons.startHikvision, "Starting", () => window.bridgeApp.start("hikvision", formConfig()));
});

buttons.stopHikvision.addEventListener("click", () => {
  void runAction(buttons.stopHikvision, "Stopping", () => window.bridgeApp.stop("hikvision"));
});

buttons.clearLog.addEventListener("click", () => {
  logOutput.textContent = "";
});

buttons.openConfigFolder.addEventListener("click", () => {
  void window.bridgeApp.openUserData();
});

fields.autoStart.addEventListener("change", async () => {
  await window.bridgeApp.setAutoStart(fields.autoStart.checked);
  appendLog({
    source: "app",
    message: fields.autoStart.checked ? "Open at login enabled." : "Open at login disabled."
  });
});

window.bridgeApp.onLog(appendLog);
window.bridgeApp.onStatus(updateStatus);

async function init() {
  fillForm(await window.bridgeApp.getConfig());
  updateStatus(await window.bridgeApp.status());
  setInterval(async () => updateStatus(await window.bridgeApp.status()), 2500);
}

void init();
