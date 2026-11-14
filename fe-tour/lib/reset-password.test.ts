import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import {
  describeForgotError,
  describeResetError,
  MISSING_RESET_TOKEN_FAILURE,
  validateResetPassword,
} from "./reset-password";

describe("validateResetPassword", () => {
  it("requires a new password and exact confirmation", () => {
    expect(validateResetPassword("", "")).toMatch(/required/i);
    expect(validateResetPassword("correct123", "incorrect123")).toMatch(/match/i);
  });

  it("enforces 8-100 characters and the 72 UTF-8 byte bcrypt ceiling", () => {
    expect(validateResetPassword("short12", "short12")).toMatch(/8 and 100/);
    expect(validateResetPassword("a".repeat(101), "a".repeat(101))).toMatch(/8 and 100/);
    expect(validateResetPassword("a".repeat(72), "a".repeat(72))).toBeNull();
    expect(validateResetPassword("a".repeat(73), "a".repeat(73))).toMatch(/72 bytes/);
    expect(validateResetPassword("é".repeat(36), "é".repeat(36))).toBeNull();
    expect(validateResetPassword("é".repeat(37), "é".repeat(37))).toMatch(/72 bytes/);
  });
});

describe("recovery errors", () => {
  it("explains a missing or consumed reset token and points to a new request", () => {
    expect(MISSING_RESET_TOKEN_FAILURE).toMatch(/missing/i);
    expect(describeResetError(new ApiError(400, "Invalid or expired token")))
      .toMatch(/invalid, expired, or already used/i);
  });

  it("shows Retry-After seconds on either form when rate limited", () => {
    const error = new ApiError(429, "Too many requests", 25);
    expect(describeResetError(error)).toMatch(/25 seconds/);
    expect(describeForgotError(error)).toMatch(/25 seconds/);
    expect(describeForgotError(new ApiError(429, "Too many requests"))).toMatch(/wait/i);
  });

  it("gives actionable timeout, server and network messages", () => {
    expect(describeResetError(new ApiError(504, "timeout"))).toMatch(/too long/i);
    expect(describeForgotError(new ApiError(503, "outage"))).toMatch(/try again/i);
    expect(describeResetError(new TypeError("failed to fetch"))).toMatch(/connection/i);
  });

  it("does not reveal account existence in forgot-password errors", () => {
    expect(describeForgotError(new ApiError(400, "No such account")))
      .not.toMatch(/no such account/i);
  });
});
