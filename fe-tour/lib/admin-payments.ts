export type PaymentFilters = { page: number; status: string; method: string; from: string; to: string; q: string };
export type AdminPayment = {
  id: number; bookingId: number; bookingCode: string; createdAt: string;
  amount: number; method: string; status: string; providerTransactionId: string | null;
};

export function buildPaymentQuery(filters: PaymentFilters): string {
  if (filters.from && filters.to && filters.from > filters.to) throw new Error("Start date must be on or before end date.");
  const query = new URLSearchParams({ page: String(filters.page), size: "10" });
  for (const key of ["status", "method", "from", "to", "q"] as const) {
    const value = filters[key].trim();
    if (value) query.set(key, value);
  }
  return query.toString();
}
