"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ApiError, verifyEmail } from "@/lib/api";
import { IconArrowRight, IconCheck, IconMail } from "./icons";

type State =
  | { kind: "idle" }
  | { kind: "verifying" }
  | { kind: "success" }
  | { kind: "error"; message: string };

export function VerifyEmail() {
  const params = useSearchParams();
  const token = params.get("token")?.trim() ?? "";
  const [state, setState] = useState<State>({ kind: "idle" });
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    let active = true;

    async function verify() {
      if (!token) {
        setState({
          kind: "error",
          message: "This verification link is missing its token.",
        });
        return;
      }

      setState({ kind: "verifying" });
      try {
        await verifyEmail(token);
        if (active) setState({ kind: "success" });
      } catch (err) {
        if (!active) return;
        setState({
          kind: "error",
          message:
            err instanceof ApiError
              ? err.message
              : "Could not reach the booking service. Try the link again.",
        });
      }
    }

    void verify();
    return () => {
      active = false;
    };
  }, [token]);

  if (state.kind === "verifying" || state.kind === "idle") {
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

  if (state.kind === "success") {
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
      <p className="mt-3 text-sm leading-6 text-muted">{state.message}</p>
      <p className="mt-3 text-sm leading-6 text-muted">
        Verification links expire after 2 days and can only be used once. Sign in to check your
        profile; changing the email address sends a fresh link.
      </p>
      <div className="mt-6 flex flex-wrap justify-center gap-3">
        <Link href="/account/profile" className="btn-primary">
          Open your profile
        </Link>
        <Link href="/login" className="btn-secondary">
          Sign in
        </Link>
      </div>
    </div>
  );
}
