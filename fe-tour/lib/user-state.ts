import { dismissFlash } from "./flash";

type Cleanup = () => void;

const cleanups = new Set<Cleanup>();
const inFlight = new Set<AbortController>();

/**
 * Registers an in-memory cache reset. The returned function unregisters it.
 * Every account page must reset its own rendered data on a session identity
 * change; the account boundary handles remounting, while this registry covers
 * caches that outlive a component.
 */
export function registerPerUserCleanup(cleanup: Cleanup): () => void {
  cleanups.add(cleanup);
  return () => {
    cleanups.delete(cleanup);
  };
}

/**
 * Tracks a per-user read so a logout or account switch aborts it immediately,
 * before the component tree has finished unmounting.
 */
export function registerUserRequest(controller: AbortController): () => void {
  if (controller.signal.aborted) return () => {};
  inFlight.add(controller);
  return () => {
    inFlight.delete(controller);
  };
}

/**
 * Clears data that belongs to the signed-in account. Runs on logout, on an
 * account switch in this tab, and when another tab switches or clears the
 * account. Never leaves the previous account's message or caches behind.
 */
export function clearPerUserClientState(): void {
  for (const controller of [...inFlight]) {
    inFlight.delete(controller);
    controller.abort();
  }

  for (const cleanup of [...cleanups]) {
    try {
      cleanup();
    } catch {
      // One broken cache must not prevent the remaining resets.
    }
  }

  dismissFlash();
}
