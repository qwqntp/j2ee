"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { verifyEmail } from "@/lib/api";
import { ensureSession } from "@/lib/auth";
import {
  describeVerifyError,
  isConsumedToken,
  MISSING_TOKEN_FAILURE,
  TIMEOUT_FAILURE,
  VERIFY_TIMEOUT_MS,
  type VerifyFailure,
} from "@/lib/verify-email";
import { IconArrowRight, IconCheck, IconMail } from "./icons";

type State =
  | { kind: "verifying" }
  | { kind: "success" }
  | ({ kind: "error" } & VerifyFailure);

export function VerifyEmail() {
  const params = useSearchParams();
  const token = params.get("token")?.trim() ?? "";
  const [state, setState] = useState<State>(() =>
    token ? { kind: "verifying" } : { kind: "error", ...MISSING_TOKEN_FAILURE },
  );
  // Shared across effect invocations so React Strict Mode's second run reuses the
  // in-flight request instead of starting a second call for a single-use token.
  const requestRef = useRef<Promise<void> | null>(null);
  const succeededRef = useRef(false);

  const beginRequest = useCallback(() => {
    if (!token || requestRef.current || succeededRef.current) return;

    const watchdog: { timer?: ReturnType<typeof setTimeout> } = {};

    const request = verifyEmail(token)
      .then(() => {
        succeededRef.current = true;
        setState({ kind: "success" });
        // The backend has flipped email_verified; refresh the cached account so
        // the header and profile reflect it without a full page reload.
        void ensureSession();
      })
      .catch((error: unknown) => {
        // A 400 after this page already succeeded means the first call consumed
        // the token; keep showing success rather than an alarming error.
        if (succeededRef.current && isConsumedToken(error)) {
          setState({ kind: "success" });
          return;
        }
        setState({ kind: "error", ...describeVerifyError(error) });
      })
      .finally(() => {
        if (watchdog.timer !== undefined) clearTimeout(watchdog.timer);
        if (requestRef.current === request) requestRef.current = null;
      });

    requestRef.current = request;

    // apiRequest aborts browser fetches after 10s; this fallback makes sure the
    // page still reaches a terminal state even if a promise never settles.
    watchdog.timer = setTimeout(() => {
      if (requestRef.current !== request) return;
      requestRef.current = null;
      setState({ kind: "error", ...TIMEOUT_FAILURE });
    }, VERIFY_TIMEOUT_MS);
  }, [token]);

  const retry = useCallback(() => {
    if (!token) return;
    // The token is single-use. Once this page has seen a success, never send it
    // again; a later 400 would just mean the first call already consumed it.
    if (succeededRef.current) {
      setState({ kind: "success" });
      return;
    }
    setState({ kind: "verifying" });
    beginRequest();
  }, [beginRequest, token]);

  useEffect(() => {
    // No cleanup cancels the request: the result of the only successful call must
    // always reach state, including across Strict Mode's mount/cleanup/mount.
    beginRequest();
  }, [beginRequest]);

  // A missing token is a terminal state even if the URL changes after mount.
  const displayState: State = token
    ? state
    : { kind: "error", ...MISSING_TOKEN_FAILURE };

  if (displayState.kind === "verifying") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mx-auto max-w-xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-10"
      >
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-[3px] border border-line text-forest">
          <IconMail className="h-6 w-6" />
        </span>
        <h2 className="mt-5 font-display text-3xl tracking-tight">Verifying your email…</h2>
        <p className="mt-2 text-sm leading-6 text-muted">
          This only takes a moment. Keep this page open.
        </p>
      </div>
    );
  }

  if (displayState.kind === "success") {
    return (
      <div
        role="status"
        aria-live="polite"
        className="mx-auto max-w-xl rounded-[4px] border border-line bg-white p-8 text-center sm:p-10"
      >
        <span className="mx-auto grid h-12 w-12 place-items-center rounded-[3px] bg-sage/25 text-forest">
          <IconCheck className="h-6 w-6" />
        </span>
        <p className="eyebrow mt-5 text-terracotta">Email verified</p>
        <h2 className="mt-2 font-display text-3xl tracking-tight">You are all set.</h2>
        <p className="mt-3 text-sm leading-6 text-muted">
          Your email address is confirmed. We can now send booking updates and, if needed, a
          password reset link.
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link href="/account/profile" className="btn-primary">
            Go to your profile
            <IconArrowRight className="h-4 w-4" />
          </Link>
          <Link href="/tours" className="btn-secondary">
            Browse journeys
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div
      role="alert"
      className="mx-auto max-w-xl rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-8 text-center sm:p-10"
    >
      <p className="eyebrow text-terracotta">Verification link</p>
      <h2 className="mt-2 font-display text-3xl tracking-tight">We could not verify this link.</h2>
      <p className="mt-3 text-sm leading-6 text-muted">{displayState.message}</p>
      {displayState.checkProfile ? (
        <p className="mt-3 text-sm leading-6 text-muted">
          Already confirmed this address? Open your profile to check whether it shows as
          verified; if not, change the email there to receive a fresh link.
        </p>
      ) : null}
      <div className="mt-6 flex flex-wrap justify-center gap-3">
        {displayState.retryable ? (
          <button type="button" onClick={retry} className="btn-primary">
            Try again
          </button>
        ) : null}
        <Link
          href="/account/profile"
          className={displayState.retryable ? "btn-secondary" : "btn-primary"}
        >
          Open your profile
          <IconArrowRight className="h-4 w-4" />
        </Link>
        <Link href="/login" className="btn-secondary">
          Sign in
        </Link>
      </div>
    </div>
  );
}
