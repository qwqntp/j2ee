# Admin list state implementation plan

Use page-specific schemas over shared URL validation and navigation. Sensitive
searches are stored only in per-account, per-page session storage and restored
before list requests. Preserve existing API contracts and search interactions.

1. Implement and test validated URL state and private-search storage.
2. Integrate submitted-search lists and detail return navigation.
3. Integrate debounced-search lists, then remaining paginated modules.
4. Verify validation, history, request behavior, type checking, tests and build.

Risk: browser history must not expose sensitive searches. Never move values
from sensitive search controls into the query string.
