import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import {
  googleSignInErrorMessage,
  resolveGoogleClientId,
  withTimeout,
} from "./google-signin";

afterEach(() => {
  vi.useRealTimers();
});

describe("resolveGoogleClientId", () => {
  it("returns null for a missing or blank client id", () => {
    expect(resolveGoogleClientId(undefined)).toBeNull();
    expect(resolveGoogleClientId(null)).toBeNull();
    expect(resolveGoogleClientId("   ")).toBeNull();
  });

  it("trims and returns a configured client id", () => {
    expect(resolveGoogleClientId("  client.apps.googleusercontent.com ")).toBe(
      "client.apps.googleusercontent.com",
    );
  });
});

describe("googleSignInErrorMessage", () => {
  it("explains a disabled account", () => {
    expect(googleSignInErrorMessage(new ApiError(403, "Account is disabled"))).toContain(
      "disabled",
    );
  });

  it("includes the retry window for a rate limit", () => {
    expect(googleSignInErrorMessage(new ApiError(429, "Too many requests", 30))).toContain(
      "30 seconds",
    );
    expect(googleSignInErrorMessage(new ApiError(429, "Too many requests"))).toContain(
      "wait a minute",
    );
  });

  it("explains missing server configuration and timeouts", () => {
    expect(googleSignInErrorMessage(new ApiError(503, "Google sign-in is not configured"))).toContain(
      "not configured",
    );
    expect(googleSignInErrorMessage(new ApiError(504, "Took too long"))).toContain("timed out");
  });

  it("uses a server message for an invalid credential and a network message otherwise", () => {
    expect(googleSignInErrorMessage(new ApiError(401, "Invalid Google ID token"))).toBe(
      "Invalid Google ID token",
    );
    expect(googleSignInErrorMessage(new Error("offline"))).toContain("reach the booking service");
  });
});

describe("withTimeout", () => {
  it("resolves a promise that finishes in time", async () => {
    await expect(withTimeout(Promise.resolve("ok"), 1000)).resolves.toBe("ok");
  });

  it("propagates the original rejection", async () => {
    await expect(withTimeout(Promise.reject(new ApiError(403, "nope")), 1000)).rejects.toMatchObject(
      { status: 403 },
    );
  });

  it("rejects with a 504 when the promise never settles", async () => {
    vi.useFakeTimers();
    const request = withTimeout(new Promise<string>(() => {}), 15_000);
    const assertion = expect(request).rejects.toMatchObject({
      status: 504,
      message: "Google sign-in timed out. Please try again.",
    });

    await vi.advanceTimersByTimeAsync(15_000);
    await assertion;
  });
});
