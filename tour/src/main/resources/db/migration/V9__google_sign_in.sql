-- Google Identity Services sign-in.
-- google_sub is the identity key; the email is never used as the key.
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64) NULL;

-- Existing password accounts keep working; Google-only accounts set this to false.
ALTER TABLE public.accounts
    ADD COLUMN IF NOT EXISTS password_set BOOLEAN NOT NULL DEFAULT TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_google_sub
    ON public.accounts (google_sub)
    WHERE google_sub IS NOT NULL;
