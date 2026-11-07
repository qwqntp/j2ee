"use client";

import { useEffect } from "react";
import { dismissFlash, useFlash } from "@/lib/flash";
import { IconCheck } from "./icons";

export function FlashToast() {
  const flash = useFlash();

  useEffect(() => {
    if (!flash) return;
    const timeout = window.setTimeout(() => dismissFlash(), 5000);
    return () => window.clearTimeout(timeout);
  }, [flash]);

  if (!flash) return null;

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex justify-center sm:inset-x-auto sm:right-6 sm:bottom-6 sm:justify-end">
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-[3px] border border-forest-deep bg-forest px-4 py-3 text-ivory shadow-panel"
      >
        <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white/15">
          <IconCheck className="h-4 w-4" />
        </span>
        <p className="flex-1 text-sm leading-6">{flash.message}</p>
        <button
          type="button"
          onClick={dismissFlash}
          aria-label="Dismiss notification"
          className="-m-2 grid h-11 w-11 shrink-0 place-items-center rounded-[3px] text-ivory/75 transition-colors hover:bg-white/10 hover:text-ivory"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.75}
            strokeLinecap="round"
            className="h-4 w-4"
            aria-hidden="true"
          >
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
        </button>
      </div>
    </div>
  );
}
