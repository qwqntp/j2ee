# Departures modal — progress

- [x] Analyze the current inline section and existing dialog components.
- [x] Create the departures dialog component.
- [x] Wire the dialog into `admin-tours.tsx`.
- [x] Update or add tests.
- [x] Run frontend lint, tests, and build.
- [x] Provide a manual verification checklist.

The admin table's Departures button selects a tour and displays the section below the table. The public `/tours` page instead links tour cards to detail pages; it has no equivalent inline Departures button.

`AdminFormDialog` uses native modal dialogs and restores focus; `AdminConfirmDialog` uses a separate native modal dialog. The form dialog does not currently implement backdrop dismissal or body scroll locking. The departure list is already included in the tours response.

## Status

The departures component is wired to the selected tour. Backdrop hit-testing has focused Vitest coverage; no existing tests assumed the inline section. The public page is unchanged.

## Changes and design decisions

- Created `fe-tour/components/admin/tour-departures-dialog.tsx`: moved the departure form, list, CRUD handlers and delete confirmation into the dialog. The existing API functions and payloads are retained; booked, capacity and remaining seats are shown together.
- Modified `fe-tour/components/admin/admin-form-dialog.tsx`: opt-in backdrop dismissal, body scroll lock and an 85dvh height limit. Native modal dialogs provide the top layer and focus containment; the existing form and confirmation dialogs are used for nested actions.
- Created `fe-tour/lib/admin-dialog.ts` and `fe-tour/lib/admin-dialog.test.ts` for backdrop hit-testing.
- Modified `fe-tour/components/admin-tours.tsx` only in these regions:
  - Imports and departure-specific types/constants: replaced departure-related imports with the new dialog import and moved the departure form type/status list.
  - State and handlers: moved departure form, save, delete and confirmation state and functions to the new component; retained selected tour ID and existing list reload state.
  - Page error condition: removed the moved departure form state from the condition.
  - Departures button: removed the reset of the moved form state; it still selects the clicked tour.
  - Below the tours table: removed the old inline departures section and confirmation, and render the new dialog with the current selected tour, loading/error state, dismissal and existing reload callback.
  - The tour form, including image handling, was not edited.
- The selected tour is resolved from the current tours list on each render, so a successful list reload supplies the latest departures without another fetch. Existing automatic selection following tour creation is retained.

## Verification

- `npm.cmd run lint`: passed.
- `npm.cmd test`: passed, 27 files and 203 tests.
- `npm.cmd run build`: passed, including Next.js TypeScript checking.
- `npx.cmd tsc --noEmit`: passed.
- PowerShell blocked the `npm.ps1` launcher; the commands above use the equivalent Windows `npm.cmd` launcher.
- Browser interactions, focus trapping/restoration, screen-reader behavior, mobile layout, and live API operations have not been verified manually.

## Manual checklist (not yet performed)

- [ ] Open Departures and confirm the tour name and list appear in a dialog.
- [ ] Create, edit, change status, and delete a departure; confirm the list refreshes and the dialog stays open.
- [ ] Open and cancel the nested delete confirmation; verify focus returns to the departures dialog.
- [ ] Close with Escape, the close button, and the backdrop; verify focus returns to the opening button.
- [ ] Check focus remains inside the active dialog, and that body scrolling is locked.
- [ ] Check a long list and forms at mobile width.
