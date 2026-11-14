import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import {
  describeVerifyError,
  isConsumedToken,
  MISSING_TOKEN_FAILURE,
  TIMEOUT_FAILURE,
} from "./verify-email";

describe("isConsumedToken", () => {
  it("treats a 400 as an already-used token", () => {
    expect(isConsumedToken(new ApiError(400, "Invalid or expired token"))).toBe(true);
  });

  it("does not treat other failures as an already-used token", () => {
    expect(isConsumedToken(new ApiError(500, "Server error"))).toBe(false);
    expect(isConsumedToken(new TypeError("fetch failed"))).toBe(false);
  });
});

describe("describeVerifyError", () => {
  it("explains an expired or reused link and points at the profile", () => {
    const failure = describeVerifyError(new ApiError(400, "Invalid or expired token"));

    expect(failure.retryable).toBe(false);
    expect(failure.checkProfile).toBe(true);
    expect(failure.message).toMatch(/invalid, expired, or has already been used/i);
    expect(failure.message).toMatch(/profile/i);
  });

  it("offers a retry for timeouts, rate limits and server errors", () => {
    expect(describeVerifyError(new ApiError(504, "timeout"))).toEqual(TIMEOUT_FAILURE);
    expect(describeVerifyError(new ApiError(429, "slow down")).retryable).toBe(true);
    expect(describeVerifyError(new ApiError(500, "boom")).retryable).toBe(true);
    expect(describeVerifyError(new ApiError(503, "unavailable")).retryable).toBe(true);
  });

  it("offers a retry for network failures", () => {
    const failure = describeVerifyError(new TypeError("fetch failed"));

    expect(failure.retryable).toBe(true);
    expect(failure.checkProfile).toBe(false);
    expect(failure.message).toMatch(/could not reach/i);
  });

  it("falls back to the API message for other client errors", () => {
    const failure = describeVerifyError(new ApiError(403, "Not allowed"));

    expect(failure.message).toBe("Not allowed");
    expect(failure.retryable).toBe(false);
    expect(failure.checkProfile).toBe(true);
  });

  it("uses a clear missing-token message", () => {
    expect(MISSING_TOKEN_FAILURE.checkProfile).toBe(true);
    expect(MISSING_TOKEN_FAILURE.message).toMatch(/missing its token/i);
  });
});
