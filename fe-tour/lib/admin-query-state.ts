export type AdminQueryField =
  | { kind: "page" }
  | { kind: "number"; allowed: readonly number[]; defaultValue: number }
  | { kind: "enum"; allowed: readonly string[]; defaultValue?: string }
  | { kind: "date" }
  | { kind: "text"; maxLength: number };

export type AdminQuerySchema = Record<string, AdminQueryField>;

function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

export function normalizeAdminQuery(input: URLSearchParams, schema: AdminQuerySchema): URLSearchParams {
  const result = new URLSearchParams(input);
  for (const [key, field] of Object.entries(schema)) {
    const values = input.getAll(key);
    const value = values[0] ?? "";
    let valid = values.length === 1 && value.length > 0;
    if (field.kind === "page") {
      valid &&= /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) <= 1000000;
      if (value === "1") valid = false;
    } else if (field.kind === "number") {
      valid &&= /^(0|[1-9]\d*)$/.test(value) && field.allowed.includes(Number(value));
      if (Number(value) === field.defaultValue) valid = false;
    } else if (field.kind === "enum") {
      valid &&= field.allowed.includes(value) && value !== field.defaultValue;
    } else if (field.kind === "date") {
      valid &&= validDate(value);
    } else {
      valid &&= value.trim().length > 0 && value.length <= field.maxLength;
    }
    if (!valid) result.delete(key);
  }
  if (schema.from?.kind === "date" && schema.to?.kind === "date") {
    const from = result.get("from");
    const to = result.get("to");
    if (from && to && from > to) {
      result.delete("from");
      result.delete("to");
    }
  }
  return result;
}

export function updateAdminQuery(
  input: URLSearchParams,
  schema: AdminQuerySchema,
  patch: Record<string, string | null>,
): URLSearchParams {
  const next = new URLSearchParams(input);
  for (const [key, value] of Object.entries(patch)) {
    if (!(key in schema)) continue;
    if (value === null || value === "") next.delete(key);
    else next.set(key, value);
  }
  return normalizeAdminQuery(next, schema);
}
