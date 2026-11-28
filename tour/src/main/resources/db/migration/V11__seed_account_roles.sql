-- Bootstrap roles on fresh databases; preserve existing role IDs and assignments.
-- role_name is not unique in the bootstrap schema, so a guarded insert is required.
INSERT INTO public.account_roles (role_name)
SELECT roles.role_name
FROM (VALUES ('ROLE_USER'), ('ROLE_ADMIN'), ('ROLE_STAFF')) AS roles(role_name)
WHERE NOT EXISTS (
    SELECT 1 FROM public.account_roles existing
    WHERE existing.role_name = roles.role_name
);
