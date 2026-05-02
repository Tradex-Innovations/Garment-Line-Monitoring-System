export const supabaseEnv = {
  url: import.meta.env.VITE_SUPABASE_URL?.trim() || "",
  anonKey: import.meta.env.VITE_SUPABASE_ANON_KEY?.trim() || "",
  importsBucket: import.meta.env.VITE_SUPABASE_IMPORTS_BUCKET?.trim() || "imports",
};

export function isSupabaseConfigured() {
  return Boolean(supabaseEnv.url && supabaseEnv.anonKey);
}
