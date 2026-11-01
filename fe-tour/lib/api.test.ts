import { afterEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api";

describe("apiRequest", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
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
