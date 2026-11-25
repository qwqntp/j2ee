# Admin list state tasks

- [x] Shared URL parsing and updates: validate inputs, preserve unrelated keys;
  verify with unit tests.
- [x] Tours list pagination: one-based URL pages and server-side pagination.
- [x] Private search storage scoped by account and page, cleared on reset,
  logout and account changes; integrate sensitive-search pages.
- [x] Customers and customer-detail bookings: URL pagination and private search.
- [x] Payments: submitted filters, private search and URL pagination.
- [x] Bookings and invoices: debounced private search, URL filters, sorting and
  pagination; invoice detail return link carries non-sensitive list state.
- [x] Catalog: URL resource tab and pagination.
- [x] Accounts and promotions: applicable filters and pagination. Departures
  and passenger manifest have no separate paginated or filtered list controls.
- [x] Run frontend tests, TypeScript and lint.
- [ ] Verify production build and authenticated browser navigation, refresh,
  request counts and detail returns locally.
