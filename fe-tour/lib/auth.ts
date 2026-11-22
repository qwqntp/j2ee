"use client";

import {
  ApiError,
  getMe,
  googleSignIn as apiGoogleSignIn,
  login as apiLogin,
  logout as apiLogout,
  refresh as apiRefresh,
  register as apiRegister,
} from "./api";
import {
  clearSession as clearStoredSession,
  getStoredSession,
  saveSession as saveStoredSession,
  useSession,
} from "./session";
import type { Account, AuthSession } from "./types";

export { getStoredSession, useSession };

let sessionCheck: Promise<AuthSession | null> | null = null;
let sessionEpoch = 0;

export function saveSession(session: AuthSession): void {
  sessionEpoch += 1;
  sessionCheck = null;
  saveStoredSession(session);
}

export function clearSession(): void {
  sessionEpoch += 1;
  sessionCheck = null;
  clearStoredSession();
}

export async function signIn(input: {
  username: string;
  password: string;
}): Promise<AuthSession> {
  const session = await apiLogin(input);
  saveSession(session);
  return session;
}

export async function signUp(input: {
  username: string;
  password: string;
  email: string;
}): Promise<AuthSession> {
  const session = await apiRegister(input);
  saveSession(session);
  return session;
}

export async function signInWithGoogle(idToken: string): Promise<AuthSession> {
  const session = await apiGoogleSignIn(idToken);
  saveSession(session);
  return session;
}

export async function signOut(): Promise<void> {
  const session = getStoredSession();
  try {
    await apiLogout(session?.accessToken);
  } finally {
    clearSession();
  }
}

export function ensureSession(): Promise<AuthSession | null> {
  if (sessionCheck) return sessionCheck;
  const epoch = sessionEpoch;
  const pending = resolveSession(epoch);
  sessionCheck = pending;
  void pending.finally(() => {
    if (sessionCheck === pending) sessionCheck = null;
  });
  return pending;
}

async function resolveSession(epoch: number): Promise<AuthSession | null> {
  const stored = getStoredSession();
  if (stored) {
    try {
      const account = await getMe(stored.accessToken);
      if (epoch !== sessionEpoch) return getStoredSession();
      const session = { accessToken: stored.accessToken, account };
      saveSession(session);
      return session;
    } catch (error) {
      if (epoch !== sessionEpoch) return getStoredSession();
      if (!isUnauthorized(error)) {
        // A timeout or outage should not sign the visitor out.
        return stored;
      }
    }
  }

  try {
    const session = await apiRefresh();
    if (epoch !== sessionEpoch) return getStoredSession();
    saveSession(session);
    return session;
  } catch (error) {
    if (epoch !== sessionEpoch) return getStoredSession();
    if (isUnauthorized(error)) {
      clearSession();
      return null;
    }
    return stored;
  }
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

export async function currentAccount(): Promise<Account | null> {
  const session = await ensureSession();
  if (!session) return null;
  try {
    return await getMe(session.accessToken);
  } catch {
    clearSession();
    return null;
  }
}
