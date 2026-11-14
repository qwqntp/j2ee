import { ApiError } from "./api";

/**
 * How long the verification screen waits before it gives up on a hung request.
 * `lib/api.ts` already aborts browser fetches after 10s and turns that into a
 * 504 ApiError; this is a last-resort guard so the page can never sit on
 * "Verifying…" forever if that layer changes or a promise never settles.
 */
export const VERIFY_TIMEOUT_MS = 15_000;

export type VerifyFailure = {
  message: string;
  /** A network/server problem may succeed if the user tries again. */
  retryable: boolean;
  /** The copy should point the user at /account/profile to check verification. */
  checkProfile: boolean;
};

export const MISSING_TOKEN_FAILURE: VerifyFailure = {
  message:
    "This verification link is missing its token. Open the link from your email again.",
  retryable: false,
  checkProfile: true,
};

export const TIMEOUT_FAILURE: VerifyFailure = {
  message:
    "The verification request took too long. Check your connection and try again.",
  retryable: true,
  checkProfile: false,
};

const INVALID_TOKEN_FAILURE: VerifyFailure = {
  message:
    "This verification link is invalid, expired, or has already been used. " +
    "If you already confirmed this email, open your profile to check whether it shows as verified.",
  retryable: false,
  checkProfile: true,
};

/** True when a 400 means the single-use token was already consumed. */
export function isConsumedToken(error: unknown): boolean {
  return error instanceof ApiError && error.status === 400;
}

/**
 * Maps an unknown thrown value to the state the page should render. Kept pure
 * so the mapping can be unit-tested without React.
 */
export function describeVerifyError(error: unknown): VerifyFailure {
  if (error instanceof ApiError) {
    if (error.status === 400) return INVALID_TOKEN_FAILURE;
    if (error.status === 504) return TIMEOUT_FAILURE;
    if (error.status === 429) {
      return {
        message: "Too many verification attempts. Wait a moment and try again.",
        retryable: true,
        checkProfile: false,
      };
    }
    if (error.status >= 500) {
      return {
        message:
          "The booking service is having trouble right now. Try again in a moment.",
        retryable: true,
        checkProfile: false,
      };
    }
    return {
      message: error.message || "We could not verify this link.",
      retryable: false,
      checkProfile: true,
    };
  }

  return {
    message:
      "Could not reach the booking service. Check your connection and try again.",
    retryable: true,
    checkProfile: false,
  };
}
