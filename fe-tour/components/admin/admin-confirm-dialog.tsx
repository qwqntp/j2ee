"use client";

import { useEffect, useId, useRef } from "react";
import { AdminIcon } from "./admin-icons";

export function AdminConfirmDialog({ title, target, consequence, confirmLabel, onDismiss, onConfirm }: {
  title: string;
  target: string | null;
  consequence: string;
  confirmLabel: string;
  onDismiss: () => void;
  onConfirm: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (target !== null && dialog && !dialog.open) {
      dialog.showModal();
      cancel.current?.focus();
    } else if (target === null && dialog?.open) {
      dialog.close();
    }
  }, [target]);

  return (
    <dialog ref={ref} aria-labelledby={titleId} aria-describedby={descriptionId}
      onClose={onDismiss}
      className="admin-panel m-auto w-[calc(100%_-_2rem)] max-w-md p-5 text-charcoal shadow-panel backdrop:bg-charcoal/55">
      <span className="admin-dialog-icon"><AdminIcon name="delete" /></span>
      <h2 id={titleId} className="text-xl font-semibold tracking-tight">{title}</h2>
      <div id={descriptionId} className="mt-3 grid gap-2 text-sm leading-6">
        <p className="rounded-lg border border-line bg-ivory/65 p-3 font-semibold wrap-anywhere">{target}</p>
        <p className="text-muted">{consequence}</p>
      </div>
      <div className="admin-form-actions">
        <button ref={cancel} type="button" className="btn-secondary"
          onClick={() => ref.current?.close()}>Cancel</button>
        <button type="button" className="admin-destructive"
          onClick={() => { ref.current?.close(); onConfirm(); }}>{confirmLabel}</button>
      </div>
    </dialog>
  );
}
