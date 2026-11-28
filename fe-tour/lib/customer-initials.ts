export function customerInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return Array.from(parts[0])[0].toLocaleUpperCase() +
    (parts.length > 1 ? Array.from(parts[parts.length - 1])[0].toLocaleUpperCase() : "");
}
