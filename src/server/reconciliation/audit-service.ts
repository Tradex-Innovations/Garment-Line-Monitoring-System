import type { Json } from "@/types/database";
import type { AppSupabaseClient } from "../repositories/base-repository";
import { logAuditEventRecord } from "../repositories/audit-repository";

export async function logAuditEvent(
  client: AppSupabaseClient,
  args: {
    actionType: string;
    entityType: string;
    entityId: string;
    oldValue?: Json | null;
    newValue?: Json | null;
    metadata?: Json | null;
  }
) {
  return logAuditEventRecord(client, args);
}
