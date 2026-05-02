import type { SupabaseClient } from "@supabase/supabase-js";
import type { Database } from "@/types/database";
import { chunkArray } from "../parsers/shared";

export type AppSupabaseClient = SupabaseClient<Database>;

export function toErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

export async function insertInChunks<T extends Record<string, unknown>>(args: {
  client: AppSupabaseClient;
  table: keyof Database["public"]["Tables"];
  rows: T[];
  chunkSize?: number;
}) {
  for (const chunk of chunkArray(args.rows, args.chunkSize || 500)) {
    const { error } = await args.client.from(args.table).insert(chunk as never);
    if (error) {
      throw new Error(error.message);
    }
  }
}
