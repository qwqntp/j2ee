"use client";

import { useSyncExternalStore } from "react";
import { clearPerUserClientState } from "./user-state";
import type { Account, AuthSession } from "./types";

const TOKEN_KEY = "tour_access_token";
const ACCOUNT_KEY = "tour_account";

export type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export type SessionStore = {
  getSnapshot(): AuthSession | null;
  subscribe(listener: () => void): () => void;
  set(session: AuthSession): void;
  clear(): void;
  invalidate(): void;
};

let accountSeen = false;

/** True once this tab has shown a signed-in account; stays true after logout. */
function getAccountSeenSnapshot(): boolean {
  return accountSeen;
}

function getServerAccountSeenSnapshot(): boolean {
  return false;
}

function subscribeToAccountSeen(listener: () => void): () => void {
  return getBrowserStore().subscribe(listener);
}

export function useAccountSeen(): boolean {
  return useSyncExternalStore(
    subscribeToAccountSeen,
    getAccountSeenSnapshot,
    getServerAccountSeenSnapshot,
  );
}

/** Stable identity used to key account pages and detect an account switch. */
export function accountScope(session: AuthSession | null): string | null {
  return session === null ? null : `account:${session.account.id}`;
}

export function createSessionStore(
  storage: StorageLike,
  onUserStateReset: () => void = () => {},
): SessionStore {
  const listeners = new Set<() => void>();
  let cached: AuthSession | null | undefined;

  function read(): AuthSession | null {
    const accessToken = storage.getItem(TOKEN_KEY);
    const rawAccount = storage.getItem(ACCOUNT_KEY);
    if (!accessToken || !rawAccount) return null;
    try {
      const session = { accessToken, account: JSON.parse(rawAccount) as Account };
      accountSeen = true;
      return session;
    } catch {
      return null;
    }
  }

  function current(): AuthSession | null {
    return cached === undefined ? read() : cached;
  }

  function notify(): void {
    for (const listener of listeners) listener();
  }

  return {
    getSnapshot() {
      if (cached === undefined) {
        cached = read();
      }
      return cached;
    },
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    set(session) {
      const previous = current();
      storage.setItem(TOKEN_KEY, session.accessToken);
      storage.setItem(ACCOUNT_KEY, JSON.stringify(session.account));
      cached = session;
      accountSeen = true;
      if (previous !== null && previous.account.id !== session.account.id) {
        onUserStateReset();
      }
      notify();
    },
    clear() {
      const previous = current();
      storage.removeItem(TOKEN_KEY);
      storage.removeItem(ACCOUNT_KEY);
      cached = null;
      if (previous !== null) onUserStateReset();
      notify();
    },
    invalidate() {
      const previous = current();
      cached = read();
      if (previous !== null && (cached === null || cached.account.id !== previous.account.id)) {
        onUserStateReset();
      }
      notify();
    },
  };
}

let browserStore: SessionStore | null = null;

function getBrowserStore(): SessionStore {
  if (!browserStore) {
    browserStore = createSessionStore(window.localStorage, clearPerUserClientState);
    window.addEventListener("storage", (event) => {
      if (event.key === null || event.key === TOKEN_KEY || event.key === ACCOUNT_KEY) {
        browserStore?.invalidate();
      }
    });
    window.addEventListener("pageshow", (event) => {
      // A back/forward-cache restore keeps the old React tree; re-read the
      // account so the boundary remounts it for the current user.
      if (event.persisted) browserStore?.invalidate();
    });
  }
  return browserStore;
}

export function getStoredSession(): AuthSession | null {
  if (typeof window === "undefined") return null;
  return getBrowserStore().getSnapshot();
}

export function saveSession(session: AuthSession): void {
  if (typeof window === "undefined") return;
  getBrowserStore().set(session);
}

export function clearSession(): void {
  if (typeof window === "undefined") return;
  getBrowserStore().clear();
}

function subscribeToSession(listener: () => void): () => void {
  return getBrowserStore().subscribe(listener);
}

function getSessionSnapshot(): AuthSession | null {
  return getBrowserStore().getSnapshot();
}

function getServerSnapshot(): AuthSession | null {
  return null;
}

export function useSession(): AuthSession | null {
  return useSyncExternalStore(subscribeToSession, getSessionSnapshot, getServerSnapshot);
}
