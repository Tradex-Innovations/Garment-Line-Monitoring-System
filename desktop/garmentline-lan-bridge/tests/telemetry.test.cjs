const { test } = require("node:test");
const assert = require("node:assert/strict");
const { deviceReference, parseTelemetry, lineConsumer } = require("../electron/telemetry");
test("fragmented JSON lines are assembled and status is validated", () => {
  const events=[];const consume=lineConsumer(line=>events.push(parseTelemetry(line,"zkteco")));
  const line=JSON.stringify({schemaVersion:1,kind:"pipeline-event",sourceSystem:"zkteco",stageName:"device-read",status:"FAILED",deviceReference:deviceReference("zkteco","192.0.2.1")});
  consume(line.slice(0,40));assert.equal(events.length,0);consume(line.slice(40)+"\n");
  assert.equal(events[0].state,"error");assert.ok(!events[0].message.includes("192.0.2.1"));
});
test("does not accept arbitrary JSON or another worker's events", () => {
  assert.equal(parseTelemetry('{"password":"private"}',"hikvision"),null);
  assert.equal(parseTelemetry('not-json',"hikvision"),null);
});
