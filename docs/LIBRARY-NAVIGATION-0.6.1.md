# Auralis 0.6.1 — library navigation

- Library now defaults to Recently played. User-selected sorting, filters, search,
  shelf category and grid/list mode survive navigating away and back.
- Books grid, compact list, Series, Authors and Collections retain independent
  scroll states. States live above the conditional group-detail screen so opening
  a group cannot discard the parent shelf position.
- Selected group and book IDs are saveable; each group detail has its own saveable
  state scope, preserving its scroll position when returning from a book.
- Bottom navigation and tablet navigation rail save and restore back-stack state.
- No database schema, audio, playback-engine or signing changes.
- Restored The Murder at the Vicarage's previous existing cover file, using an
  artwork-only native transaction. All other fields, database tables and
  DataStore files were checked unchanged before commit. Backup and helper source:
  `device-backups/library-navigation-061/`.

## Validation

Optimized APK build and all 56 JVM regression tests passed. All three device navigation tests passed in 17.165 seconds: grid/detail/tab return,
list/sort return and series/book/shelf return. Device regression
results are archived in `docs/validation/library-0.6.1/`.

The navigation UI tests seed fictional books only into the isolated
`app.auralis.verification` application and clean them up afterwards. They never
seed or delete books in the user's main `app.auralis` library.
