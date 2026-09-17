const { createHash } = require("node:crypto");
function deviceReference(family, id) {
  return "device-" + createHash("sha256").update(`${family}:${String(id).trim().replace(/\/+$/, "")}`).digest("hex").slice(0, 16);
}
function parseTelemetry(line, family) {
  try {
    const event = JSON.parse(line);
    if (event.schemaVersion !== 1 || event.kind !== "pipeline-event" || event.sourceSystem !== family ||
      !/^device-[0-9a-f]{16}$/.test(event.deviceReference) || !["device-read", "bridge-upload"].includes(event.stageName) ||
      !["SUCCEEDED", "WARNING", "FAILED"].includes(event.status)) return null;
    return { reference: event.deviceReference, state: event.status === "FAILED" ? "error" : event.status === "WARNING" ? "warning" : "online",
      message: `${event.stageName}: ${event.status.toLowerCase()}` };
  } catch { return null; }
}
function lineConsumer(onLine) {
  let pending = "";
  return chunk => {
    pending += String(chunk);
    // Discard oversized/malformed output rather than allowing unbounded worker memory.
    if (pending.length > 65536) { pending = ""; return; }
    const lines = pending.split(/\r?\n/); pending = lines.pop();
    lines.filter(line => line.trim()).forEach(onLine);
  };
}
module.exports = { deviceReference, parseTelemetry, lineConsumer };
