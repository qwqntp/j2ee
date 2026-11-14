import { ApiError } from "./api";

export const RESET_TIMEOUT_MS = 15_000;
export const MISSING_RESET_TOKEN_FAILURE =
  "This reset link is missing its token. Request a new password reset link.";
export const NEUTRAL_RESET_MESSAGE =
  "If this email exists, we sent a password reset link. Check your inbox.";

export function validateResetPassword(newPassword: string, confirmation: string): string | null {
  if (!newPassword.trim()) return "New password is required.";
  if (newPassword.length < 8 || newPassword.length > 100) {
    return "Password must be between 8 and 100 characters.";
  }
  if (new TextEncoder().encode(newPassword).length > 72) {
    return "Password must be at most 72 bytes.";
  }
  if (newPassword !== confirmation) return "New passwords do not match.";
  return null;
}

function rateLimitMessage(error: ApiError): string {
  return error.retryAfterSeconds !== null
    ? `Too many attempts. Wait ${error.retryAfterSeconds} seconds before trying again.`
    : "Too many attempts. Wait a moment before trying again.";
}

export function describeResetError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 400) {
      return "This reset link is invalid, expired, or already used. Request a new link.";
    }
    if (error.status === 429) return rateLimitMessage(error);
    if (error.status === 504) return "The request took too long. Check your connection and try again.";
    if (error.status >= 500) return "The booking service is unavailable. Try again shortly.";
    return "Could not reset your password. Request a new link and try again.";
  }
  return "Could not reach the booking service. Check your connection and try again.";
}

export function describeForgotError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return rateLimitMessage(error);
    if (error.status === 504) return "The request took too long. Check your connection and try again.";
    if (error.status >= 500) return "The booking service is unavailable. Try again shortly.";
    return "Could not send your request. Check the email address and try again.";
  }
  return "Could not reach the booking service. Check your connection and try again.";
}
