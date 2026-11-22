"use client";

import { useEffect, useId, useRef } from "react";

export function AdminConfirmDialog({ message, onDismiss, onConfirm }: {
  message: string | null;
  onDismiss: () => void;
  onConfirm: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (message && dialog && !dialog.open) {
      dialog.showModal();
      cancel.current?.focus();
    }
  }, [message]);

  return (
    <dialog ref={ref} aria-labelledby={titleId} aria-describedby={descriptionId}
      onClose={onDismiss}
      className="admin-panel m-auto w-[calc(100%_-_2rem)] max-w-md p-5 text-charcoal shadow-panel backdrop:bg-charcoal/50">
      <h2 id={titleId} className="text-lg font-semibold tracking-tight">Delete departure?</h2>
      <p id={descriptionId} className="mt-2 text-sm leading-6 text-muted">{message}</p>
      <div className="mt-5 flex flex-wrap justify-end gap-2">
        <button ref={cancel} type="button" className="btn-secondary"
          onClick={() => ref.current?.close()}>Cancel</button>
        <button type="button" className="admin-danger-action border border-red-200 bg-red-50"
          onClick={() => { ref.current?.close(); onConfirm(); }}>Delete departure</button>
      </div>
    </dialog>
  );
}
