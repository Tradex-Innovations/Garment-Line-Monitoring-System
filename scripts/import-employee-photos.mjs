#!/usr/bin/env node
// Dry-run by default. Run with --apply only after reviewing the exact-match summary.
import { createClient } from "@supabase/supabase-js";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { mkdtemp, readdir, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, extname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(fileURLToPath(new URL("..", import.meta.url)));
const options = new Set(process.argv.slice(2).filter((arg) => arg.startsWith("--")));
const source = process.argv.slice(2).find((arg) => !arg.startsWith("--"));
if (!source || [...options].some((option) => !["--apply", "--upload-only", "--replace-existing"].includes(option))
    || (options.has("--apply") && options.has("--upload-only"))) {
  console.error("Usage: node scripts/import-employee-photos.mjs <directory-or-rar> [--apply | --upload-only] [--replace-existing]");
  process.exit(2);
}
if (options.has("--replace-existing") && !options.has("--apply")) {
  console.error("--replace-existing requires --apply");
  process.exit(2);
}

function settings(text) {
  return Object.fromEntries(text.split(/\r?\n/).flatMap((line) => {
    const match = /^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/.exec(line.trim());
    return match ? [[match[1], match[2].trim().replace(/^['"]|['"]$/g, "")]] : [];
  }));
}

async function allRows(client, table, columns) {
  const rows = [];
  for (let start = 0; ; start += 1000) {
    const { data, error } = await client.from(table).select(columns).range(start, start + 999);
    if (error) throw new Error(`${table} lookup failed: ${error.message}`);
    rows.push(...data);
    if (data.length < 1000) return rows;
  }
}

async function imageFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return imageFiles(path);
    if (!entry.isFile()) return [];
    return /\.(?:jpe?g|png)$/i.test(entry.name) ? [path] : [];
  }));
  return nested.flat();
}

function employeeNumber(path) {
  const match = /^([^+]+)\+_([0-9]+)\.(?:jpe?g|png)$/i.exec(basename(path));
  if (!match) return null;
  if (/^\d+$/.test(match[1]) && match[1] !== match[2]) return null;
  return match[2];
}

function checkedImage(path, bytes) {
  if (!bytes.length || bytes.length > 5 * 1024 * 1024) return null;
  const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  const png = bytes.length > 8 && [137, 80, 78, 71, 13, 10, 26, 10]
    .every((part, index) => bytes[index] === part);
  const ext = extname(path).toLowerCase();
  if (jpeg && [".jpg", ".jpeg"].includes(ext)) return { extension: "jpg", type: "image/jpeg" };
  if (png && ext === ".png") return { extension: "png", type: "image/png" };
  return null;
}

async function main() {
  let extracted;
  try {
    let directory = resolve(source);
    if (/\.rar$/i.test(directory)) {
      const archivePaths = execFileSync("bsdtar", ["-tf", directory], { encoding: "utf8" }).split(/\r?\n/).filter(Boolean);
      if (archivePaths.some((entry) => entry.startsWith("/") || entry.includes("\\") || entry.split("/").includes("..")))
        throw new Error("Archive contains an unsafe path");
      extracted = await mkdtemp(join(tmpdir(), "unionnorth-employee-photos-"));
      execFileSync("bsdtar", ["-xf", directory, "-C", extracted]);
      directory = extracted;
    }

    const env = settings(await readFile(join(repoRoot, "backend/.env"), "utf8"));
    if (!env.SUPABASE_URL || !env.SUPABASE_SERVICE_ROLE_KEY)
      throw new Error("backend/.env needs SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY");
    const client = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data: bucket, error: bucketError } = await client.storage.getBucket("payroll-employee-photos");
    if (bucketError || !bucket || bucket.public) throw new Error("Private employee-photo bucket is unavailable");

    const [employees, profiles, files] = await Promise.all([
      allRows(client, "employees", "id,employee_code"),
      allRows(client, "employee_profiles", "employee_id,photo_url"),
      imageFiles(directory),
    ]);
    const byNumber = new Map();
    for (const row of employees) {
      const key = String(row.employee_code || "").trim();
      byNumber.set(key, [...(byNumber.get(key) || []), row]);
    }
    const profilesById = new Map(profiles.map((row) => [row.employee_id, row]));
    const filesByNumber = new Map();
    let nonMatchingFilenames = 0;
    for (const path of files) {
      const number = employeeNumber(path);
      if (!number) { nonMatchingFilenames++; continue; }
      filesByNumber.set(number, [...(filesByNumber.get(number) || []), path]);
    }

    const candidates = [];
    const unmatched = [];
    const ambiguous = [];
    let alreadyLinked = 0;
    for (const [number, paths] of filesByNumber) {
      const matches = byNumber.get(number) || [];
      if (paths.length !== 1 || matches.length > 1) { ambiguous.push(number); continue; }
      if (!matches.length) { unmatched.push(number); continue; }
      const employee = matches[0];
      const existing = profilesById.get(employee.id)?.photo_url;
      if (existing && !options.has("--replace-existing")) { alreadyLinked++; continue; }
      candidates.push({ number, employeeId: employee.id, path: paths[0] });
    }
    console.log(JSON.stringify({
      mode: options.has("--apply") ? "apply" : options.has("--upload-only") ? "upload-only" : "dry-run",
      targetHost: new URL(env.SUPABASE_URL).host,
      employeeRecords: employees.length,
      imageFiles: files.length,
      candidates: candidates.length,
      alreadyLinked,
      unmatchedCount: unmatched.length,
      ambiguousCount: ambiguous.length,
      nonMatchingFilenames,
      unmatchedEmployeeNumbers: unmatched,
      ambiguousEmployeeNumbers: ambiguous,
    }, null, 2));
    if (!options.has("--apply") && !options.has("--upload-only")) return;

    let uploaded = 0;
    let linked = 0;
    let invalid = 0;
    const failures = [];
    for (const candidate of candidates) {
      try {
        const bytes = await readFile(candidate.path);
        const image = checkedImage(candidate.path, bytes);
        if (!image) { invalid++; continue; }
        const hash = createHash("sha256").update(bytes).digest("hex").slice(0, 16);
        const objectPath = `employee-photos/${candidate.employeeId}-${hash}.${image.extension}`;
        const { error: uploadError } = await client.storage.from("payroll-employee-photos")
          .upload(objectPath, bytes, { contentType: image.type, upsert: true, cacheControl: "3600" });
        if (uploadError) throw uploadError;
        uploaded++;
        if (options.has("--upload-only")) {
          if (uploaded % 50 === 0) console.log(`Staged ${uploaded}/${candidates.length} employee photographs`);
          continue;
        }
        const { error: linkError } = await client.from("employee_profiles")
          .upsert({ employee_id: candidate.employeeId, photo_url: objectPath }, { onConflict: "employee_id" });
        if (linkError) throw linkError;
        linked++;
        if (linked % 50 === 0) console.log(`Linked ${linked}/${candidates.length} employee photographs`);
      } catch (error) {
        failures.push({ employeeNumber: candidate.number, error: error instanceof Error ? error.message : "Unknown error" });
      }
    }
    console.log(JSON.stringify({ uploaded, linked, invalid, failures }, null, 2));
    if (failures.length || invalid) process.exitCode = 1;
  } finally {
    if (extracted) await rm(extracted, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : "Employee photo import failed");
  process.exitCode = 1;
});
