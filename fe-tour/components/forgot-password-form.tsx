"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { forgotPassword } from "@/lib/api";
import { describeForgotError, NEUTRAL_RESET_MESSAGE, RESET_TIMEOUT_MS } from "@/lib/reset-password";

export function ForgotPasswordForm() {
  const [pending, setPending] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || success) return;
    const email = String(new FormData(event.currentTarget).get("email") ?? "").trim();
    inFlight.current = true;
    setPending(true);
    setError(null);
    const watchdog = setTimeout(() => {
      setPending(false);
      setError("The request took too long. Check your connection and try again.");
    }, RESET_TIMEOUT_MS);
    try {
      await forgotPassword(email);
      setSuccess(true);
    } catch (failure) {
      setError(describeForgotError(failure));
    } finally {
      clearTimeout(watchdog);
      inFlight.current = false;
      setPending(false);
    }
  }

  return (
    <div className="mx-auto max-w-md rounded-[4px] border border-line bg-white p-6 sm:p-8">
      <h1 className="font-display text-3xl tracking-tight">Find your way back.</h1>
      {success ? (
        <div role="status" aria-live="polite" className="mt-5 space-y-5 text-sm leading-6 text-muted">
          <p>{NEUTRAL_RESET_MESSAGE}</p>
          <Link href="/login" className="btn-secondary w-full">Back to sign in</Link>
        </div>
      ) : (
        <>
          <p className="mt-2 text-sm leading-6 text-muted">
            Enter your account email to request a password reset link.
          </p>
          <form onSubmit={onSubmit} className="mt-6 space-y-5">
            <div>
              <label htmlFor="recovery-email" className="label">Email</label>
              <input id="recovery-email" name="email" type="email" autoComplete="email"
                required maxLength={255} className="field" placeholder="you@example.com" />
            </div>
            {error ? <p role="alert" className="text-sm text-terracotta">{error}</p> : null}
            <button type="submit" disabled={pending} className="btn-primary w-full">
              {pending ? "Sending…" : "Request reset link"}
            </button>
          </form>
          <Link href="/login" className="mt-5 inline-block text-sm font-medium text-forest hover:underline">
            Back to sign in
          </Link>
        </>
      )}
    </div>
  );
}
