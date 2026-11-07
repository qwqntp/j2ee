import { describe, expect, it, vi } from "vitest";
import { createSessionStore, type StorageLike } from "./session";
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
});
