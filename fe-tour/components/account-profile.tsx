"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import {
  ApiError,
  changePassword,
  getProfile,
  updateProfile,
} from "@/lib/api";
import { clearSession, ensureSession, saveSession } from "@/lib/auth";
import { publishFlash } from "@/lib/flash";
import {
  profilePayload,
  validatePasswordChange,
  validateProfile,
  type ProfileFormValues,
} from "@/lib/profile";
import type { Profile } from "@/lib/types";
import { ProfileSkeleton } from "./skeleton";

const PROFILE_PATH = "/account/profile";
const loginHref = `/login?next=${encodeURIComponent(PROFILE_PATH)}`;

const EMPTY_VALUES: ProfileFormValues = {
  name: "",
  email: "",
  phone: "",
  address: "",
  dateOfBirth: "",
};

function valuesFromProfile(profile: Profile): ProfileFormValues {
  return {
    name: profile.name ?? "",
    email: profile.email ?? "",
    phone: profile.phone ?? "",
    address: profile.address ?? "",
    dateOfBirth: profile.dateOfBirth ?? "",
  };
}

export function AccountProfile() {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let active = true;

    ensureSession().then((session) => {
      if (!active) return;
      if (session) {
        setReady(true);
      } else {
        router.replace(loginHref);
      }
    });

    return () => {
      active = false;
    };
  }, [router]);

  if (!ready) return <ProfileSkeleton />;

  return (
    <div className="grid gap-8 lg:grid-cols-[1.15fr_0.85fr] lg:items-start">
      <ProfileForm />
      <ChangePasswordForm />
    </div>
  );
}

function ProfileForm() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [values, setValues] = useState<ProfileFormValues>(EMPTY_VALUES);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;

    async function load() {
      setLoading(true);
      const session = await ensureSession();
      if (!active) return;
      if (!session) {
        router.replace(loginHref);
        return;
      }

      try {
        const result = await getProfile(session.accessToken);
        if (!active) return;
        setProfile(result);
        setValues(valuesFromProfile(result));
        setError(null);
        setLoading(false);
      } catch (err) {
        if (!active) return;
        if (err instanceof ApiError && err.status === 401) {
          clearSession();
          router.replace(loginHref);
          return;
        }
        setError(err instanceof ApiError ? err.message : "Could not load your profile.");
        setLoading(false);
      }
    }

    void load();
    return () => {
      active = false;
    };
  }, [reloadKey, router]);

  function update<K extends keyof ProfileFormValues>(field: K, value: ProfileFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
    setNotice(null);
  }

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const validation = validateProfile(values);
    if (validation) {
      setError(validation);
      setNotice(null);
      return;
    }

    setPending(true);
    setError(null);
    setNotice(null);

    try {
      const session = await ensureSession();
      if (!session) {
        router.replace(loginHref);
        return;
      }

      const previousEmail = profile?.email ?? null;
      const updated = await updateProfile(session.accessToken, profilePayload(values));
      const emailChanged =
        updated.email !== null &&
        updated.email.toLowerCase() !== (previousEmail ?? "").toLowerCase();

      setProfile(updated);
      setValues(valuesFromProfile(updated));
      setNotice(
        emailChanged
          ? "Profile saved. We sent a verification link to the new email address."
          : "Profile saved.",
      );
      publishFlash("Your profile has been saved.");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      setError(err instanceof ApiError ? err.message : "Could not save your profile. Try again.");
    } finally {
      setPending(false);
    }
  }

  if (loading) {
    return (
      <div className="rounded-[4px] border border-line bg-white p-6">
        <p className="text-sm text-muted">Loading your profile…</p>
      </div>
    );
  }

  if (!profile) {
    return (
      <div role="alert" className="rounded-[4px] border border-terracotta/40 bg-terracotta/10 p-6">
        <p className="font-medium text-terracotta">We could not load your profile.</p>
        <p className="mt-1 text-sm leading-6 text-muted">{error}</p>
        <button
          type="button"
          onClick={() => setReloadKey((key) => key + 1)}
          className="btn-secondary mt-4"
        >
          Try again
        </button>
      </div>
    );
  }

  const verified = profile.emailVerified === 1;

  return (
    <section className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line pb-5">
        <div>
          <p className="eyebrow text-terracotta">Account details</p>
          <h2 className="mt-2 font-display text-2xl tracking-tight">{profile.username}</h2>
          <p className="mt-1 text-sm text-muted">
            Username <span className="font-medium text-charcoal">{profile.username}</span>
          </p>
        </div>
        <span
          className={`rounded-[2px] border px-2.5 py-1 text-[11px] font-semibold tracking-wide ${
            verified
              ? "border-forest/40 bg-white text-forest"
              : "border-terracotta/50 bg-white text-terracotta"
          }`}
        >
          {verified ? "Email verified" : "Email not verified"}
        </span>
      </div>

      {!verified ? (
        <p className="mt-5 rounded-[3px] border border-line bg-ivory/70 p-4 text-sm leading-6 text-muted">
          Your email is not verified yet. Use the link in the verification email, or change the
          address below and we will send a fresh link. Changing it resets verification until the
          new address is confirmed.
        </p>
      ) : null}

      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        <div className="grid gap-5 sm:grid-cols-2">
          <div>
            <label htmlFor="profile-name" className="label">
              Full name <span className="font-normal text-muted">(required)</span>
            </label>
            <input
              id="profile-name"
              autoComplete="name"
              required
              maxLength={255}
              value={values.name}
              onChange={(event) => update("name", event.target.value)}
              className="field"
              placeholder="Your name"
            />
          </div>

          <div>
            <label htmlFor="profile-email" className="label">
              Email
            </label>
            <input
              id="profile-email"
              type="email"
              autoComplete="email"
              maxLength={255}
              value={values.email}
              onChange={(event) => update("email", event.target.value)}
              className="field"
              placeholder="you@example.com"
            />
            <p className="mt-1.5 text-xs leading-5 text-muted">
              Changing this address requires re-verification.
            </p>
          </div>

          <div>
            <label htmlFor="profile-phone" className="label">
              Phone <span className="font-normal text-muted">(optional)</span>
            </label>
            <input
              id="profile-phone"
              type="tel"
              autoComplete="tel"
              maxLength={20}
              value={values.phone}
              onChange={(event) => update("phone", event.target.value)}
              className="field"
              placeholder="+84 ..."
            />
          </div>

          <div>
            <label htmlFor="profile-birth" className="label">
              Date of birth <span className="font-normal text-muted">(optional)</span>
            </label>
            <input
              id="profile-birth"
              type="date"
              value={values.dateOfBirth}
              onChange={(event) => update("dateOfBirth", event.target.value)}
              className="field"
            />
          </div>

          <div className="sm:col-span-2">
            <label htmlFor="profile-address" className="label">
              Address <span className="font-normal text-muted">(optional)</span>
            </label>
            <input
              id="profile-address"
              autoComplete="street-address"
              maxLength={255}
              value={values.address}
              onChange={(event) => update("address", event.target.value)}
              className="field"
              placeholder="Street, city, country"
            />
          </div>
        </div>

        {error ? (
          <p
            role="alert"
            className="rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-3 text-sm text-terracotta"
          >
            {error}
          </p>
        ) : null}

        {notice ? (
          <p
            role="status"
            className="rounded-[3px] border border-forest/30 bg-sage/15 p-3 text-sm text-forest"
          >
            {notice}
          </p>
        ) : null}

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
          <p className="text-xs leading-5 text-muted">
            Your name is required. Other details help us prepare your journey.
          </p>
          <button type="submit" disabled={pending} className="btn-primary">
            {pending ? "Saving…" : "Save profile"}
          </button>
        </div>
      </form>
    </section>
  );
}

function ChangePasswordForm() {
  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const validation = validatePasswordChange(currentPassword, newPassword, confirmation);
    if (validation) {
      setError(validation);
      setNotice(null);
      return;
    }

    setPending(true);
    setError(null);
    setNotice(null);

    try {
      const session = await ensureSession();
      if (!session) {
        router.replace(loginHref);
        return;
      }

      const updated = await changePassword(session.accessToken, {
        currentPassword,
        newPassword,
      });
      // Keep the current session usable with the fresh access token from the response.
      saveSession(updated);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmation("");
      setNotice("Password changed. Your current session stays signed in.");
      publishFlash("Your password has been changed.");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        clearSession();
        router.replace(loginHref);
        return;
      }
      // A wrong current password is a 400; keep the user signed in and show it inline.
      setError(err instanceof ApiError ? err.message : "Could not change your password. Try again.");
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="rounded-[4px] border border-line bg-white p-6 sm:p-8">
      <p className="eyebrow text-terracotta">Security</p>
      <h2 className="mt-2 font-display text-2xl tracking-tight">Change password</h2>
      <p className="mt-2 text-sm leading-6 text-muted">
        Use at least 8 characters. Changing your password signs other devices out once their
        current access token expires.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        <div>
          <label htmlFor="current-password" className="label">
            Current password
          </label>
          <input
            id="current-password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={100}
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
            className="field"
            placeholder="Your current password"
          />
        </div>

        <div>
          <label htmlFor="new-password" className="label">
            New password
          </label>
          <input
            id="new-password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={100}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            className="field"
            placeholder="At least 8 characters"
          />
        </div>

        <div>
          <label htmlFor="confirm-password" className="label">
            Confirm new password
          </label>
          <input
            id="confirm-password"
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={100}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            className="field"
            placeholder="Repeat the new password"
          />
        </div>

        {error ? (
          <p
            role="alert"
            className="rounded-[3px] border border-terracotta/40 bg-terracotta/10 p-3 text-sm text-terracotta"
          >
            {error}
          </p>
        ) : null}

        {notice ? (
          <p
            role="status"
            className="rounded-[3px] border border-forest/30 bg-sage/15 p-3 text-sm text-forest"
          >
            {notice}
          </p>
        ) : null}

        <button type="submit" disabled={pending} className="btn-primary w-full">
          {pending ? "Changing…" : "Change password"}
        </button>
      </form>
    </section>
  );
}
