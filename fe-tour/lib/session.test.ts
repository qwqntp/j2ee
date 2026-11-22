import { describe, expect, it, vi } from "vitest";
import { accountScope, createSessionStore, type StorageLike } from "./session";
import type { AuthSession } from "./types";

function memoryStorage(): StorageLike & { read: (key: string) => string | null } {
  const data = new Map<string, string>();
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => {
      data.set(key, value);
    },
    removeItem: (key) => {
      data.delete(key);
    },
    read: (key) => data.get(key) ?? null,
  };
}

const session: AuthSession = {
  accessToken: "access-token",
  account: {
    id: 1,
    username: "lan",
    email: "lan@example.com",
    emailVerified: 0,
    status: 1,
    roleId: 2,
    roleName: "CUSTOMER",
    createdAt: null,
    updatedAt: null,
  },
};

const otherSession: AuthSession = {
  accessToken: "other-token",
  account: { ...session.account, id: 2, username: "bao", email: "bao@example.com" },
};

describe("accountScope", () => {
  it("changes when the signed-in account changes and clears on logout", () => {
    expect(accountScope(session)).not.toBe(accountScope(otherSession));
    expect(accountScope(null)).toBeNull();
  });
});

describe("createSessionStore", () => {
  it("stores a session and notifies subscribers", () => {
    const storage = memoryStorage();
    const store = createSessionStore(storage);
    const listener = vi.fn();
    store.subscribe(listener);

    store.set(session);

    expect(store.getSnapshot()).toEqual(session);
    expect(storage.read("tour_access_token")).toBe("access-token");
    expect(JSON.parse(storage.read("tour_account") ?? "null")).toEqual(session.account);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("clears storage and snapshot on clear", () => {
    const storage = memoryStorage();
    const store = createSessionStore(storage);
    store.set(session);
    const listener = vi.fn();
    store.subscribe(listener);

    store.clear();

    expect(store.getSnapshot()).toBeNull();
    expect(storage.read("tour_access_token")).toBeNull();
    expect(storage.read("tour_account")).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it("re-reads storage after another tab changes it", () => {
    const storage = memoryStorage();
    const store = createSessionStore(storage);
    expect(store.getSnapshot()).toBeNull();

    storage.setItem("tour_access_token", session.accessToken);
    storage.setItem("tour_account", JSON.stringify(session.account));
    store.invalidate();

    expect(store.getSnapshot()).toEqual(session);
  });

  it("stops notifying after unsubscribe", () => {
    const store = createSessionStore(memoryStorage());
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    unsubscribe();
    store.set(session);

    expect(listener).not.toHaveBeenCalled();
  });

  it("resets per-user state on logout and account change, not on token refresh", () => {
    const reset = vi.fn();
    const store = createSessionStore(memoryStorage(), reset);

    store.set(session);
    store.set({ ...session, accessToken: "refreshed-token" });
    expect(reset).not.toHaveBeenCalled();

    store.set(otherSession);
    expect(reset).toHaveBeenCalledTimes(1);

    store.clear();
    expect(reset).toHaveBeenCalledTimes(2);
  });

  it("resets per-user state when another tab switches account", () => {
    const storage = memoryStorage();
    const reset = vi.fn();
    const store = createSessionStore(storage, reset);
    store.set(session);

    storage.setItem("tour_access_token", otherSession.accessToken);
    storage.setItem("tour_account", JSON.stringify(otherSession.account));
    store.invalidate();

    expect(reset).toHaveBeenCalledTimes(1);
    expect(store.getSnapshot()).toEqual(otherSession);
  });

  it("does not reset when another tab only refreshes the same account token", () => {
    const storage = memoryStorage();
    const reset = vi.fn();
    const store = createSessionStore(storage, reset);
    store.set(session);

    storage.setItem("tour_access_token", "refreshed-token");
    store.invalidate();

    expect(reset).not.toHaveBeenCalled();
  });
});
