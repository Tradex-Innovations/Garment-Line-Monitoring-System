import type { Database } from "@/types/database";
import type { AppSupabaseClient } from "./base-repository";

type EmployeeInsert = Database["public"]["Tables"]["employees"]["Insert"];

export async function fetchEmployeesByCodes(
  client: AppSupabaseClient,
  employeeCodes: string[]
) {
  if (!employeeCodes.length) {
    return [];
  }

  const { data, error } = await client
    .from("employees")
    .select("*")
    .in("employee_code", employeeCodes);

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function upsertEmployees(
  client: AppSupabaseClient,
  rows: EmployeeInsert[]
) {
  if (!rows.length) {
    return [];
  }

  const { data, error } = await client
    .from("employees")
    .upsert(rows, { onConflict: "employee_code" })
    .select("*");

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}

export async function upsertDepartments(
  client: AppSupabaseClient,
  departmentNames: string[]
) {
  const rows = [...new Set(departmentNames.filter(Boolean))].map((name) => ({ name }));

  if (!rows.length) {
    return [];
  }

  const { data, error } = await client
    .from("departments")
    .upsert(rows, { onConflict: "name", ignoreDuplicates: false })
    .select("*");

  if (error) {
    throw new Error(error.message);
  }

  return data || [];
}
