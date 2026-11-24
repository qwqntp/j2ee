import { afterEach, describe, expect, it, vi } from "vitest";
import {
  apiRequest, cancelBooking, createAdminAccount, deleteAdminAccount, forgotPassword, getBooking,
  getInvoiceByBooking, getVnpayStatus, googleSignIn, listAdminBookings, listInvoices,
  getRevenueCsv, reconcileVnpayBooking, requestBookingRefund, resetPassword, startVnpay,
  updateAdminRefund, uploadImage, verifyVnpayReturn,
} from "./api";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("apiRequest", () => {
  it("reads fresh booking status and aborts the fetch when polling stops", async () => {
    const controller = new AbortController();
    const fetchMock = vi.fn((_url: string, init: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
    }));
    vi.stubGlobal("fetch", fetchMock);

    const request = getBooking("synthetic-session", 42, controller.signal);
    const cancelled = expect(request).rejects.toHaveProperty("name", "AbortError");
    controller.abort();
    await cancelled;

    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(expect.stringMatching(/\/api\/bookings\/42$/),
      expect.objectContaining({ method: "GET", cache: "no-store" }));
  });

  it("reports a server timeout as a 504 ApiError, not an AbortError", async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      "fetch",
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener("abort", () =>
            reject(new DOMException("This operation was aborted", "AbortError")),
          );
        }),
    );

    const request = apiRequest("/api/tours/1");
    const assertion = expect(request).rejects.toMatchObject({
      name: "ApiError",
      status: 504,
      message: "The booking service took too long to respond.",
    });

    await vi.advanceTimersByTimeAsync(30_000);
    await assertion;
  });
});

describe("uploadImage", () => {
  it("posts multipart file without overriding the browser Content-Type", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          message: "Success",
          data: { url: "https://res.cloudinary.com/demo/image/upload/tour.png", publicId: "tour-images/tour" },
          timestamp: "2026-11-24T00:00:00",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);
    const file = new File(["bytes"], "tour.png", { type: "image/png" });

    const result = await uploadImage("access-token", file);

    expect(result.publicId).toBe("tour-images/tour");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url.endsWith("/api/admin/uploads")).toBe(true);
    expect(init.headers).toEqual({
      Accept: "application/json",
      Authorization: "Bearer access-token",
    });
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get("file")).toBe(file);
  });
});

describe("getRevenueCsv", () => {
  it("returns CSV text with the date window and access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response("\uFEFFdate,amount\n2026-11-15,50.00\n", {
        status: 200,
        headers: { "Content-Type": "text/csv" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const csv = await getRevenueCsv("access-token", "2026-11-01", "2026-11-30");

    expect(csv).toContain("2026-11-15,50.00");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url.endsWith("/api/admin/reports/revenue.csv?from=2026-11-01&to=2026-11-30")).toBe(true);
    expect(init.headers).toMatchObject({ Authorization: "Bearer access-token" });
  });
});

describe("refund requests", () => {
  it("posts a customer refund request with the access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          message: "Success",
          data: { id: 7, status: "CANCELLED" },
          timestamp: "2026-11-24T00:00:00",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await requestBookingRefund("access-token", 7);

    expect(result.status).toBe("CANCELLED");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url.endsWith("/api/bookings/7/refund-request")).toBe(true);
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ Authorization: "Bearer access-token" });
  });

  it("puts a refund status update with the access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          message: "Success",
          data: { id: 3, status: "DONE" },
          timestamp: "2026-11-24T00:00:00",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await updateAdminRefund("access-token", 3, { status: "DONE" });

    expect(result.status).toBe("DONE");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url.endsWith("/api/refunds/3")).toBe(true);
    expect(init.method).toBe("PUT");
  });
});

describe("cancelBooking", () => {
  it("posts to the cancellation endpoint with the access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          message: "Success",
          data: { id: 7, status: "CANCELLED" },
          timestamp: "2026-11-08T00:00:00",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await cancelBooking("access-token", 7);

    expect(result.status).toBe("CANCELLED");
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url.endsWith("/api/bookings/7/cancel")).toBe(true);
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({
      Accept: "application/json",
      Authorization: "Bearer access-token",
    });
  });
});

describe("admin account mutations", () => {
  it("creates an account through the admin endpoint without signing in as the new user", async () => {
    const input = { username: "fixture-user", password: "fixture-initial", email: null, emailVerified: 0, status: 1, roleId: 2 };
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data: { id: 42, username: input.username } }, { status: 201 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(createAdminAccount("fixture-admin", input)).resolves.toMatchObject({ id: 42 });
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(expect.stringMatching(/\/api\/accounts$/), expect.objectContaining({
      method: "POST",
      body: JSON.stringify(input),
      headers: expect.objectContaining({ Authorization: "Bearer fixture-admin" }),
    }));
  });

  it("accepts a successful deletion with no response data", async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data: null }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(deleteAdminAccount("fixture-admin", 42)).resolves.toBeNull();
    expect(fetchMock).toHaveBeenCalledExactlyOnceWith(expect.stringMatching(/\/api\/accounts\/42$/), expect.objectContaining({ method: "DELETE" }));
  });
});

describe("google sign-in API", () => {
  it("posts only the Google credential and returns the session", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          message: "Success",
          data: {
            accessToken: "access-google",
            account: {
              id: 9,
              username: "google-user",
              email: "google@example.com",
              emailVerified: 1,
              status: 1,
              roleId: 2,
              roleName: "ROLE_USER",
              createdAt: null,
              updatedAt: null,
            },
          },
          timestamp: "2026-11-21T00:00:00",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    const session = await googleSignIn("google-credential");

    expect(session.accessToken).toBe("access-google");
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/auth\/google$/);
    expect(init.method).toBe("POST");
    expect(init.credentials).toBe("include");
    expect(JSON.parse(String(init.body))).toEqual({ idToken: "google-credential" });
  });
});

describe("password recovery API", () => {
  it("posts only the email and accepts the empty success envelope", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true, message: "If the email exists, a password reset link has been sent",
      data: null, timestamp: "2026-11-15T00:00:00",
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(forgotPassword("a@example.com")).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/auth\/forgot-password$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({ email: "a@example.com" });
  });

  it("posts the single-use token and new password and accepts empty success", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: true, message: "Password reset", data: null, timestamp: "2026-11-15T00:00:00",
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(resetPassword("one-use-token", "new-password")).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/auth\/reset-password$/);
    expect(JSON.parse(String(init.body))).toEqual({
      token: "one-use-token", newPassword: "new-password",
    });
  });

  it("preserves Retry-After seconds for a rate-limit response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false, message: "Too many requests", data: null,
      timestamp: "2026-11-15T00:00:00",
    }), { status: 429, headers: { "Retry-After": "27" } })));
    await expect(forgotPassword("a@example.com")).rejects.toMatchObject({
      status: 429, retryAfterSeconds: 27,
    });
  });

  it("ignores non-numeric Retry-After instead of showing an invalid wait", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      success: false, message: "Too many requests", data: null,
      timestamp: "2026-11-15T00:00:00",
    }), { status: 429, headers: { "Retry-After": "n/a" } })));
    await expect(forgotPassword("a@example.com")).rejects.toMatchObject({
      status: 429, retryAfterSeconds: null,
    });
  });
});

describe("VNPay API", () => {
  const reference = `VNP${"A".repeat(32)}`;

  it("starts payment with only the owned booking ID and bearer", async () => {
    const data = { reference, paymentUrl: "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
      expiresAt: "2026-11-21T02:15:00Z" };
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(startVnpay("synthetic-session", 42)).resolves.toEqual(data);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/bookings\/42\/payments\/vnpay$/);
    expect(init).toMatchObject({ method: "POST", headers: { Authorization: "Bearer synthetic-session" } });
    expect(init.body).toBeUndefined();
  });

  it("sends signed return fields in the body and uses only the reference for rechecks", async () => {
    const data = { bookingId: 42, reference, status: "PENDING", expired: false, returnOutcome: null,
      reason: "TRANSACTION_NOT_FOUND", providerCode: "91", providerStatus: null };
    const fetchMock = vi.fn().mockImplementation(() => Response.json({ success: true, data }));
    vi.stubGlobal("fetch", fetchMock);
    const query = `vnp_TxnRef=${reference}&vnp_ResponseCode=00&vnp_SecureHash=synthetic`;

    await expect(verifyVnpayReturn("synthetic-session", query)).resolves.toEqual(data);
    await expect(getVnpayStatus("synthetic-session", reference)).resolves.toEqual(data);
    const [[verifyUrl, verifyInit], [lookupUrl, lookupInit]] = fetchMock.mock.calls as [
      [string, RequestInit], [string, RequestInit]
    ];
    expect(verifyUrl).toMatch(/\/api\/payments\/vnpay\/return$/);
    expect(verifyInit.method).toBe("POST");
    expect(verifyInit.headers).toMatchObject({ Authorization: "Bearer synthetic-session" });
    expect(JSON.parse(String(verifyInit.body))).toEqual({ query });
    expect(lookupUrl).toMatch(new RegExp(`/api/payments/vnpay/status/${reference}$`));
    expect(lookupInit).toMatchObject({ method: "GET", headers: { Authorization: "Bearer synthetic-session" } });
    expect(lookupInit.body).toBeUndefined();
  });

  it("reconciles only the owned booking and returns the verified attempt", async () => {
    const data = { bookingId: 42, reference, status: "FAILED", checked: true,
      reason: "VERIFIED_NONPAYMENT", providerCode: "00", providerStatus: "02" };
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(reconcileVnpayBooking("synthetic-session", 42)).resolves.toEqual(data);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/bookings\/42\/payments\/vnpay\/reconcile$/);
    expect(init).toMatchObject({ method: "POST", headers: { Authorization: "Bearer synthetic-session" } });
    expect(init.body).toBeUndefined();
  });
});

function adminPage<T>(content: T[]) {
  return {
    content, page: 0, size: 10, totalElements: content.length, totalPages: 1,
    last: true, hasNext: false, hasPrevious: false,
  };
}

describe("admin list API", () => {
  it("requests the admin booking projection with paging, filters and sort", async () => {
    const data = adminPage([{ id: 1 }]);
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(listAdminBookings("synthetic-session", 2, 20, {
      status: "PAID", from: "2026-11-01", to: "2026-11-30", q: " Ha Long ", sort: "totalAmount,asc",
    })).resolves.toEqual(data);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(
      /\/api\/admin\/bookings\?page=2&size=20&status=PAID&from=2026-11-01&to=2026-11-30&q=Ha\+Long&sort=totalAmount%2Casc$/,
    );
    expect(init).toMatchObject({
      method: "GET", cache: "no-store", headers: { Authorization: "Bearer synthetic-session" },
    });
  });

  it("requests invoices at page 0 and size 10 by default", async () => {
    const data = adminPage([]);
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ success: true, data }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(listInvoices("synthetic-session")).resolves.toEqual(data);

    const [url] = fetchMock.mock.calls[0] as [string];
    expect(url).toMatch(/\/api\/invoices\?page=0&size=10$/);
  });

  it("encodes full-dataset invoice filters and never sends the UI-only All value", async () => {
    const fetchMock = vi.fn().mockImplementation(() => Response.json({ success: true, data: adminPage([]) }));
    vi.stubGlobal("fetch", fetchMock);

    await listInvoices("synthetic-session", 2, 20, {
      status: "UNPAID", q: " an@example.com ", sort: "createdAt,asc",
    });
    await listInvoices("synthetic-session", 0, 10, { q: "paid" });

    const [firstUrl] = fetchMock.mock.calls[0] as [string];
    const [secondUrl] = fetchMock.mock.calls[1] as [string];
    expect(firstUrl).toMatch(
      /\/api\/invoices\?page=2&size=20&status=UNPAID&q=an%40example.com&sort=createdAt%2Casc$/,
    );
    expect(secondUrl).toMatch(/\/api\/invoices\?page=0&size=10&q=paid$/);
  });

  it("forwards an abort signal to the admin list and invoice detail reads", async () => {
    const fetchMock = vi.fn((_url: string, init: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init.signal?.addEventListener("abort", () => reject(init.signal?.reason));
    }));
    vi.stubGlobal("fetch", fetchMock);

    const listController = new AbortController();
    const listRequest = listAdminBookings("synthetic-session", 0, 10, {}, listController.signal);
    const listAborted = expect(listRequest).rejects.toHaveProperty("name", "AbortError");
    listController.abort();
    await listAborted;

    const detailController = new AbortController();
    const detailRequest = getInvoiceByBooking("synthetic-session", 42, detailController.signal);
    const detailAborted = expect(detailRequest).rejects.toHaveProperty("name", "AbortError");
    detailController.abort();
    await detailAborted;
  });
});
