import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest, cancelBooking, forgotPassword, resetPassword } from "./api";

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("apiRequest", () => {
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
