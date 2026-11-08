import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest, cancelBooking } from "./api";

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
