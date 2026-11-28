import { apiRequest } from "./api";
import type { PageData } from "./types";

export type AdminDeparture = {
  id: number; tourId: number; tourName: string; startDate: string; endDate: string;
  status: string; capacity: number; booked: number; remaining: number;
};

export const DEPARTURE_QUERY_SCHEMA = {
  page: { kind: "page" },
  status: { kind: "enum", allowed: ["OPEN", "FULL", "CLOSED", "CANCELLED"] },
  upcomingOnly: { kind: "enum", allowed: ["true", "false"], defaultValue: "true" },
  q: { kind: "text", maxLength: 100 },
} as const;

export function buildDepartureQuery(params: URLSearchParams): string {
  const query = new URLSearchParams({
    page: String(Number(params.get("page") ?? "1") - 1), size: "10",
    upcomingOnly: params.get("upcomingOnly") === "false" ? "false" : "true",
  });
  const status = params.get("status");
  const q = params.get("q")?.trim();
  if (status) query.set("status", status);
  if (q) query.set("q", q);
  return query.toString();
}

export function listAdminDepartures(token: string, query: string): Promise<PageData<AdminDeparture>> {
  return apiRequest(`/api/admin/departures?${query}`, { token });
}
