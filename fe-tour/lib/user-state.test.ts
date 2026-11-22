import { describe, expect, it, vi } from "vitest";
import {
  clearPerUserClientState,
  registerPerUserCleanup,
  registerUserRequest,
} from "./user-state";

describe("per-user client state", () => {
  it("aborts in-flight reads and runs registered cleanups", () => {
    const controller = new AbortController();
    const unregisterRequest = registerUserRequest(controller);
    const cleanup = vi.fn();
    const unregisterCleanup = registerPerUserCleanup(cleanup);

    clearPerUserClientState();

    expect(controller.signal.aborted).toBe(true);
    expect(cleanup).toHaveBeenCalledTimes(1);

    unregisterRequest();
    unregisterCleanup();
  });

  it("stops tracking a request after its owner unregisters", () => {
    const controller = new AbortController();
    const unregister = registerUserRequest(controller);
    unregister();

    clearPerUserClientState();

    expect(controller.signal.aborted).toBe(false);
  });

  it("does not run a cleanup after its owner unregisters", () => {
    const cleanup = vi.fn();
    const unregister = registerPerUserCleanup(cleanup);
    unregister();

    clearPerUserClientState();

    expect(cleanup).not.toHaveBeenCalled();
  });
});
