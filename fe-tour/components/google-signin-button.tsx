"use client";

import Script from "next/script";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { signInWithGoogle } from "@/lib/auth";
import { publishFlash } from "@/lib/flash";
import { loginDestination } from "@/lib/admin-permissions";
import {
  googleSignInErrorMessage,
  resolveGoogleClientId,
  withTimeout,
} from "@/lib/google-signin";

const CLIENT_ID = resolveGoogleClientId(process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID);
const SCRIPT_ID = "google-identity-services";
const SCRIPT_SRC = "https://accounts.google.com/gsi/client";

type CredentialResponse = {
  credential?: string;
};

type GoogleButtonOptions = {
  type?: "standard" | "icon";
  theme?: "outline" | "filled_blue" | "filled_black";
  size?: "large" | "medium" | "small";
  text?: "signin_with" | "signup_with" | "continue_with" | "signin";
  shape?: "rectangular" | "pill" | "circle" | "square";
  logo_alignment?: "left" | "center";
  width?: number;
};

type GoogleIdentity = {
  initialize: (options: {
    client_id: string;
    callback: (response: CredentialResponse) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
  }) => void;
  renderButton: (parent: HTMLElement, options: GoogleButtonOptions) => void;
  disableAutoSelect?: () => void;
};

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleIdentity;
      };
    };
  }
}

type Props = {
  next: string;
};

/**
 * Renders the official Google Identity Services button. Nothing is shown when
 * the public client id is blank, so a self-hosted deployment without Google
 * configuration keeps the email and password form untouched.
 */
export function GoogleSignInButton({ next }: Props) {
  const router = useRouter();
  const hostRef = useRef<HTMLDivElement | null>(null);
  const initializedRef = useRef(false);
  const inFlightRef = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [scriptFailed, setScriptFailed] = useState(false);

  const onCredential = useCallback(
    async (response: CredentialResponse) => {
      if (inFlightRef.current) return;

      const credential = response.credential;
      if (!credential) {
        setError("Google did not return a sign-in credential. Please try again.");
        return;
      }

      inFlightRef.current = true;
      setPending(true);
      setError(null);

      try {
        const session = await withTimeout(signInWithGoogle(credential));
        publishFlash(`Welcome, ${session.account.username}!`);
        router.push(loginDestination(session.account.roleName, next));
        router.refresh();
      } catch (err) {
        setError(googleSignInErrorMessage(err));
      } finally {
        inFlightRef.current = false;
        setPending(false);
      }
    },
    [next, router],
  );

  const renderButton = useCallback(() => {
    if (!CLIENT_ID || initializedRef.current) return;
    const identity = window.google?.accounts?.id;
    const host = hostRef.current;
    if (!identity || !host) return;

    identity.initialize({
      client_id: CLIENT_ID,
      callback: (response) => {
        void onCredential(response);
      },
      auto_select: false,
      cancel_on_tap_outside: true,
    });
    identity.renderButton(host, {
      type: "standard",
      theme: "outline",
      size: "large",
      text: "continue_with",
      shape: "rectangular",
      logo_alignment: "left",
      width: 320,
    });
    initializedRef.current = true;
  }, [onCredential]);

  // The script may already be loaded after client-side navigation between the
  // sign-in and registration pages, so `onReady` is not the only entry point.
  useEffect(() => {
    if (window.google?.accounts?.id) renderButton();
  }, [renderButton]);

  if (!CLIENT_ID) return null;

  return (
    <div className="mt-6">
      <Script
        id={SCRIPT_ID}
        src={SCRIPT_SRC}
        strategy="afterInteractive"
        onReady={renderButton}
        onError={() => setScriptFailed(true)}
      />

      <div ref={hostRef} className="flex min-h-[44px] justify-center" />

      {scriptFailed ? (
        <p role="alert" className="mt-3 text-center text-sm text-terracotta">
          Google sign-in could not load. Check your connection or use your email and password.
        </p>
      ) : null}

      {pending ? (
        <p role="status" className="mt-3 text-center text-sm text-muted">
          Signing you in with Google…
        </p>
      ) : null}

      {error ? (
        <p
          role="alert"
          className="mt-3 rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-3 text-sm text-terracotta"
        >
          {error}
        </p>
      ) : null}

      {!scriptFailed ? (
        <div className="mt-6 flex items-center gap-3" aria-hidden="true">
          <span className="h-px flex-1 bg-line" />
          <span className="text-[11px] uppercase tracking-[0.14em] text-muted">or</span>
          <span className="h-px flex-1 bg-line" />
        </div>
      ) : null}
    </div>
  );
}
