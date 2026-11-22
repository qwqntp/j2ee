"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { AdminIcon } from "./admin-icons";

export function AdminFormDialog({ open, title, busy = false, narrow = false, onDismiss, children }: {
  open: boolean;
  title: string;
  busy?: boolean;
  narrow?: boolean;
  onDismiss: () => void;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const id = useId();

  useEffect(() => {
    const element = dialog.current;
    if (!open || !element) return;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (!element.open) element.showModal();
    heading.current?.focus();
    return () => {
      if (element.open) element.close();
      if (opener?.isConnected) opener.focus();
    };
  }, [open]);

  return (
    <dialog
      ref={dialog}
      aria-labelledby={id}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onDismiss();
      }}
      className={`admin-panel admin-form-dialog m-auto text-charcoal shadow-panel backdrop:bg-charcoal/55 print:hidden ${narrow ? "max-w-xl" : "max-w-5xl"}`}
    >
      {open && (
        <>
          <div className="flex items-center justify-between gap-3 border-b border-line/70 px-4 py-3 sm:px-6">
            <h2 id={id} ref={heading} tabIndex={-1} className="wrap-anywhere text-xl font-semibold tracking-tight outline-none">
              {title}
            </h2>
            <button type="button" onClick={onDismiss} disabled={busy} aria-label="Close dialog"
              className="grid size-11 shrink-0 cursor-pointer place-items-center rounded-lg border border-line/80 hover:bg-ivory disabled:opacity-60">
              <AdminIcon name="close" />
            </button>
          </div>
          <div className="admin-form-dialog-body">{children}</div>
        </>
      )}
    </dialog>
  );
}
