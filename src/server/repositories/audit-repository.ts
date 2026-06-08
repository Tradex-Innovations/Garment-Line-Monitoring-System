import type { Json } from "@/types/database";
import type { AppSupabaseClient } from "./base-repository";

export async function logAuditEventRecord(
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
  const { data, error } = await client.rpc("log_audit_event", {
    p_action_type: args.actionType,
    p_entity_type: args.entityType,
    p_entity_id: args.entityId,
    p_old_value: args.oldValue || null,
    p_new_value: args.newValue || null,
    p_metadata: args.metadata || {},
  });

  if (error) {
    throw new Error(error.message);
  }

  return data as string;
}
