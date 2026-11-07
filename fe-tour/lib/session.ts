"use client";

import { useSyncExternalStore } from "react";
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

export function createSessionStore(storage: StorageLike): SessionStore {
  const listeners = new Set<() => void>();
  let cached: AuthSession | null | undefined;

  function read(): AuthSession | null {
    const accessToken = storage.getItem(TOKEN_KEY);
    const rawAccount = storage.getItem(ACCOUNT_KEY);
    if (!accessToken || !rawAccount) return null;
    try {
      return { accessToken, account: JSON.parse(rawAccount) as Account };
    } catch {
      return null;
    }
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
      storage.setItem(TOKEN_KEY, session.accessToken);
      storage.setItem(ACCOUNT_KEY, JSON.stringify(session.account));
      cached = session;
      notify();
    },
    clear() {
      storage.removeItem(TOKEN_KEY);
      storage.removeItem(ACCOUNT_KEY);
      cached = null;
      notify();
    },
    invalidate() {
      cached = undefined;
      notify();
    },
  };
}

let browserStore: SessionStore | null = null;

function getBrowserStore(): SessionStore {
  if (!browserStore) {
    browserStore = createSessionStore(window.localStorage);
    window.addEventListener("storage", (event) => {
      if (event.key === null || event.key === TOKEN_KEY || event.key === ACCOUNT_KEY) {
        browserStore?.invalidate();
      }
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
