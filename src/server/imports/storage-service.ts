import { supabaseEnv } from "@/lib/supabase/env";
import type { SourceType } from "@/types/pipeline";
import type { AppSupabaseClient } from "../repositories/base-repository";
import { buildImportsStoragePath } from "../parsers/shared";

export async function uploadImportFileToSupabaseStorage(
  client: AppSupabaseClient,
  args: {
    batchId: string;
    sourceType: SourceType;
    file: File;
  }
) {
  const storagePath = buildImportsStoragePath({
    sourceType: args.sourceType,
    batchId: args.batchId,
    originalFilename: args.file.name,
  });

  // Upload raw bytes instead of the browser File/FormData object because
  // Safari's stream implementation can break the multipart path used by
  // `@supabase/storage-js`, causing imports to fail before parsing starts.
  const fileBytes = new Uint8Array(await args.file.arrayBuffer());

  const { error } = await client.storage
    .from(supabaseEnv.importsBucket)
    .upload(storagePath, fileBytes, {
      cacheControl: "3600",
      upsert: false,
      contentType: args.file.type || undefined,
    });

  if (error) {
    throw new Error(error.message);
  }

  return storagePath;
}

export async function getSignedImportUrl(
  client: AppSupabaseClient,
  storagePath: string,
  expiresInSeconds = 60 * 15
) {
  const { data, error } = await client.storage
    .from(supabaseEnv.importsBucket)
    .createSignedUrl(storagePath, expiresInSeconds);

  if (error) {
    throw new Error(error.message);
  }

  return data.signedUrl;
}
