"use client";

import { useSyncExternalStore } from "react";
import type { StorageLike } from "./session";

export type FlashMessage = {
  id: number;
  message: string;
};

export type FlashStore = {
  getSnapshot(): FlashMessage | null;
  subscribe(listener: () => void): () => void;
  publish(message: string): void;
  dismiss(): void;
};

const FLASH_KEY = "tour_flash";

export function createFlashStore(storage: StorageLike): FlashStore {
  const listeners = new Set<() => void>();
  let cached: FlashMessage | null | undefined;

  function read(): FlashMessage | null {
    try {
      const raw = storage.getItem(FLASH_KEY);
      if (!raw) return null;
      return JSON.parse(raw) as FlashMessage;
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
    publish(message) {
      const flash: FlashMessage = { id: Date.now(), message };
      cached = flash;
      try {
        storage.setItem(FLASH_KEY, JSON.stringify(flash));
      } catch {
        // Private mode can reject storage; the in-page store still has the message.
      }
      notify();
    },
    dismiss() {
      cached = null;
      try {
        storage.removeItem(FLASH_KEY);
      } catch {
        // Nothing to clean up when storage is unavailable.
      }
      notify();
    },
  };
}

let browserFlash: FlashStore | null = null;

function getBrowserFlash(): FlashStore {
  if (!browserFlash) {
    browserFlash = createFlashStore(window.sessionStorage);
  }
  return browserFlash;
}

export function publishFlash(message: string): void {
  if (typeof window === "undefined") return;
  getBrowserFlash().publish(message);
}

export function dismissFlash(): void {
  if (typeof window === "undefined") return;
  getBrowserFlash().dismiss();
}

function subscribeToFlash(listener: () => void): () => void {
  return getBrowserFlash().subscribe(listener);
}

function getFlashSnapshot(): FlashMessage | null {
  return getBrowserFlash().getSnapshot();
}

function getServerFlashSnapshot(): FlashMessage | null {
  return null;
}

export function useFlash(): FlashMessage | null {
  return useSyncExternalStore(subscribeToFlash, getFlashSnapshot, getServerFlashSnapshot);
}
