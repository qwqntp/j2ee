import { describe, expect, it } from "vitest";
import { filterInvoices } from "./admin-invoices";
import type { Invoice } from "./types";

describe("filterInvoices", () => {
  const rows = [
    {
      id: 11,
      bookingId: 7,
      createdAt: "2026-11-17T08:00:00",
      status: "PAID",
      discountAmount: 0,
      taxAmount: 0,
      serviceFee: 0,
      totalAmount: 500,
      paymentMethod: "CASH",
      customerName: "An",
      customerPhone: null,
      customerEmail: "AN@EXAMPLE.COM",
      promotionIds: [],
    },
    {
      id: 12,
      bookingId: 8,
      createdAt: "2026-11-17T09:00:00",
      status: "UNPAID",
      discountAmount: 0,
      taxAmount: 0,
      serviceFee: 0,
      totalAmount: 900,
      paymentMethod: "CARD",
      customerName: "Bình",
      customerPhone: null,
      customerEmail: "binh@example.com",
      promotionIds: [],
    },
  ] as Invoice[];

  it("matches status and case-insensitive customer/email/booking id", () => {
    expect(filterInvoices(rows, { status: "PAID", query: "an@" })).toHaveLength(1);
    expect(filterInvoices(rows, { status: "All", query: "8" })).toHaveLength(1);
  });

  it("matches name and payment method case-insensitively", () => {
    expect(filterInvoices(rows, { status: "All", query: "cash" })).toHaveLength(1);
    expect(filterInvoices(rows, { status: "All", query: "BÌNH" })).toHaveLength(1);
  });

  it("returns all rows for an empty filter and does not mutate input", () => {
    const snapshot = JSON.stringify(rows);
    expect(filterInvoices(rows, { status: "All", query: "" })).toHaveLength(2);
    expect(JSON.stringify(rows)).toBe(snapshot);
  });
});
