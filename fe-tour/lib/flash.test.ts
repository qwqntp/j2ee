import { describe, expect, it, vi } from "vitest";
import { createFlashStore } from "./flash";
import type { StorageLike } from "./session";

function memoryStorage(seed: Record<string, string> = {}): StorageLike & {
  read: (key: string) => string | null;
} {
  const data = new Map(Object.entries(seed));
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

describe("createFlashStore", () => {
  it("publishes a flash and notifies subscribers", () => {
    const storage = memoryStorage();
    const store = createFlashStore(storage);
    const listener = vi.fn();
    store.subscribe(listener);

    store.publish("Welcome back, Lan!");

    expect(store.getSnapshot()?.message).toBe("Welcome back, Lan!");
    expect(listener).toHaveBeenCalledTimes(1);
    expect(storage.read("tour_flash")).toBeTruthy();
  });

  it("restores a flash left in session storage", () => {
    const storage = memoryStorage({
      tour_flash: JSON.stringify({ id: 1, message: "Account created" }),
    });
    const store = createFlashStore(storage);

    expect(store.getSnapshot()?.message).toBe("Account created");
  });

  it("dismisses a flash and clears storage", () => {
    const storage = memoryStorage();
    const store = createFlashStore(storage);
    store.publish("Welcome");
    const listener = vi.fn();
    store.subscribe(listener);

    store.dismiss();

    expect(store.getSnapshot()).toBeNull();
    expect(listener).toHaveBeenCalledTimes(1);
    expect(storage.read("tour_flash")).toBeNull();
  });

  it("stops notifying after unsubscribe", () => {
    const store = createFlashStore(memoryStorage());
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);

    unsubscribe();
    store.publish("Welcome");

    expect(listener).not.toHaveBeenCalled();
  });
});
