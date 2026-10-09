import { useEffect, useState } from "react";
import type { AppSupabaseClient } from "@/server/repositories/base-repository";
import { getSupabaseBrowserClient } from "@/lib/supabase/client";

export const EMPLOYEE_PHOTO_BUCKET = "payroll-employee-photos";
const SIGNED_URL_SECONDS = 60 * 60;
const managedPath = /^employee-photos\/[A-Za-z0-9._-]+\.(?:jpg|jpeg|png)$/i;
const signedUrls = new Map<string, { url: string; expiresAt: number }>();

export function isManagedEmployeePhoto(reference: string | null | undefined) {
  return Boolean(reference && managedPath.test(reference) && !reference.includes(".."));
}

function externalPhotoUrl(reference: string) {
  try {
    const url = new URL(reference);
    return ["https:", "http:"].includes(url.protocol) && !url.username && !url.password
      ? url.href
      : "";
  } catch {
    return "";
  }
}

export function useEmployeePhotoUrl(reference: string | null | undefined) {
  const [resolved, setResolved] = useState({ reference, url: "" });
  const [renewal, setRenewal] = useState(0);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    if (!reference) {
      setResolved({ reference, url: "" });
      return;
    }
    if (!isManagedEmployeePhoto(reference)) {
      setResolved({ reference, url: externalPhotoUrl(reference) });
      return;
    }
    const cached = signedUrls.get(reference);
    if (cached && cached.expiresAt > Date.now()) {
      setResolved({ reference, url: cached.url });
      timer = setTimeout(() => {
        signedUrls.delete(reference);
        setRenewal((value) => value + 1);
      }, cached.expiresAt - Date.now());
      return () => { active = false; clearTimeout(timer); };
    }
    setResolved({ reference, url: "" });
    const client = getSupabaseBrowserClient();
    if (!client) return;
    void client.storage.from(EMPLOYEE_PHOTO_BUCKET)
      .createSignedUrl(reference, SIGNED_URL_SECONDS)
      .then(({ data, error }) => {
        if (error || !data?.signedUrl) return;
        signedUrls.set(reference, {
          url: data.signedUrl,
          expiresAt: Date.now() + (SIGNED_URL_SECONDS - 60) * 1000,
        });
        if (active) {
          setResolved({ reference, url: data.signedUrl });
          timer = setTimeout(() => {
            signedUrls.delete(reference);
            setRenewal((value) => value + 1);
          }, (SIGNED_URL_SECONDS - 60) * 1000);
        }
      });
    return () => { active = false; clearTimeout(timer); };
  }, [reference, renewal]);

  return resolved.reference === reference ? resolved.url : "";
}

export async function uploadSharedEmployeePhoto(
  client: AppSupabaseClient,
  employeeId: string,
  file: File,
) {
  if (!/^[0-9a-f-]{36}$/i.test(employeeId)) throw new Error("Invalid employee ID.");
  if (!["image/jpeg", "image/png"].includes(file.type))
    throw new Error("Choose a JPEG or PNG employee photograph.");
  if (!file.size || file.size > 5 * 1024 * 1024)
    throw new Error("Employee photograph must be 5 MB or smaller.");

  const bytes = new Uint8Array(await file.arrayBuffer());
  const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  const png = bytes.length > 8 && [137, 80, 78, 71, 13, 10, 26, 10]
    .every((part, index) => bytes[index] === part);
  if ((file.type === "image/jpeg" && !jpeg) || (file.type === "image/png" && !png))
    throw new Error("The selected file is not a valid JPEG or PNG image.");

  const extension = jpeg ? "jpg" : "png";
  const path = `employee-photos/${employeeId}-${crypto.randomUUID()}.${extension}`;
  const bucket = client.storage.from(EMPLOYEE_PHOTO_BUCKET);
  const { error: uploadError } = await bucket.upload(path, bytes, {
    contentType: file.type,
    cacheControl: "3600",
    upsert: false,
  });
  if (uploadError) throw new Error(`Employee photograph upload failed: ${uploadError.message}`);

  const { data, error: profileError } = await client.from("employee_profiles")
    .update({ photo_url: path })
    .eq("employee_id", employeeId)
    .select("employee_id")
    .maybeSingle();
  if (profileError || !data) {
    await bucket.remove([path]);
    throw new Error(profileError?.message || "Employee profile was not found; the photo was not linked.");
  }
  signedUrls.delete(path);
  return path;
}
