CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_email_lower
    ON public.accounts (lower(account_email))
    WHERE account_email IS NOT NULL;
