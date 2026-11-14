"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useRef, useState } from "react";
import { resetPassword } from "@/lib/api";
import {
  describeResetError,
  MISSING_RESET_TOKEN_FAILURE,
  RESET_TIMEOUT_MS,
  validateResetPassword,
} from "@/lib/reset-password";

type Result = { kind: "error"; message: string } | { kind: "success" } | null;

export function ResetPasswordForm() {
  const token = useSearchParams().get("token")?.trim() ?? "";
  const [result, setResult] = useState<Result>(null);
  const [pending, setPending] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const inFlight = useRef(false);
  const attempted = useRef(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token || inFlight.current || attempted.current) return;

    const data = new FormData(event.currentTarget);
    const password = String(data.get("newPassword") ?? "");
    const confirmation = String(data.get("confirmation") ?? "");
    const validation = validateResetPassword(password, confirmation);
    if (validation) {
      setResult({ kind: "error", message: validation });
      return;
    }

    attempted.current = true;
    setSubmitted(true);
    inFlight.current = true;
    setPending(true);
    setResult(null);
    // The API aborts browser requests after 10s. The watchdog also ends the
    // pending screen if an underlying promise unexpectedly never settles.
    const watchdog = setTimeout(() => {
      setPending(false);
      setResult({ kind: "error", message: "The request took too long. Request a new link and try again." });
    }, RESET_TIMEOUT_MS);
    try {
      await resetPassword(token, password);
      setResult({ kind: "success" });
    } catch (error) {
      setResult({ kind: "error", message: describeResetError(error) });
    } finally {
      clearTimeout(watchdog);
      inFlight.current = false;
      setPending(false);
    }
  }

  const displayResult = token ? result : { kind: "error" as const, message: MISSING_RESET_TOKEN_FAILURE };

  return (
    <div className="mx-auto max-w-md rounded-[4px] border border-line bg-white p-6 sm:p-8">
      <h1 className="font-display text-3xl tracking-tight">Choose a new password.</h1>
      {displayResult?.kind === "success" ? (
        <div role="status" aria-live="polite" className="mt-5 space-y-5 text-sm leading-6 text-muted">
          <p>Your password has been reset. Sign in with your new password.</p>
          <Link href="/login" className="btn-primary w-full">Go to sign in</Link>
        </div>
      ) : !token || (submitted && !pending) ? (
        <div className="mt-5 space-y-5">
          <p role="alert" className="text-sm leading-6 text-terracotta">
            {displayResult?.kind === "error" ? displayResult.message : "Resetting your password…"}
          </p>
          <Link href="/forgot-password" className="btn-secondary w-full">Request a new link</Link>
        </div>
      ) : (
        <>
          <p className="mt-2 text-sm leading-6 text-muted">
            Use 8–100 characters, no more than 72 bytes. Your reset link works once.
          </p>
          <form onSubmit={onSubmit} className="mt-6 space-y-5">
            <div>
              <label htmlFor="new-password" className="label">New password</label>
              <input id="new-password" name="newPassword" type="password" autoComplete="new-password"
                required minLength={8} maxLength={100} className="field" />
            </div>
            <div>
              <label htmlFor="confirm-password" className="label">Confirm new password</label>
              <input id="confirm-password" name="confirmation" type="password" autoComplete="new-password"
                required className="field" />
            </div>
            {displayResult?.kind === "error" ? (
              <p role="alert" className="text-sm text-terracotta">{displayResult.message}</p>
            ) : null}
            <button type="submit" disabled={pending} className="btn-primary w-full">
              {pending ? "Resetting…" : "Reset password"}
            </button>
          </form>
        </>
      )}
    </div>
  );
}
