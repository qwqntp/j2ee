import { ApiError } from "./api";

/** The public OAuth client id is optional: a blank value hides the Google button. */
export function resolveGoogleClientId(value: string | undefined | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

/** Maps backend/network failures to copy a visitor can act on. */
export function googleSignInErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return error.message || "Google sign-in could not be verified. Please try again.";
      case 403:
        return "This account is disabled. Contact support if you think this is a mistake.";
      case 429:
        return error.retryAfterSeconds !== null
          ? `Too many sign-in attempts. Try again in ${error.retryAfterSeconds} seconds.`
          : "Too many sign-in attempts. Please wait a minute and try again.";
      case 503:
        return "Google sign-in is not configured on this server.";
      case 504:
        return "Google sign-in timed out. Please try again.";
      default:
        return error.message || "Google sign-in failed. Please try again.";
    }
  }
  return "Could not reach the booking service. Please try again.";
}

/**
 * The API client already aborts at 10 seconds; this is a second safety net so a
 * promise that never settles cannot leave the button spinning forever.
 */
export function withTimeout<T>(promise: Promise<T>, milliseconds = 15_000): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new ApiError(504, "Google sign-in timed out. Please try again."));
    }, milliseconds);

    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}
