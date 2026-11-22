# Integration boundaries

- **Cast:** Implemented with a scoped, token-protected Wi-Fi range server. See [Cast setup and limitations](CAST.md). Future work includes verifying chapter rollover, seeking and transcript synchronization on multiple receivers, plus more receiver formats, IPv6 and long-duration interruption testing.
- **Sync:** Immutable UUID segment IDs and achievement keys permit duplicate-safe merge. Device source URIs and cover paths are local references, not globally portable identifiers. Introduce media fingerprints and conflict-resolution rules before optional encrypted sync. Never upload files or notes by default.
- **Android Auto:** Extend the service to MediaLibraryService with a properly paginated browse tree and automotive-safe metadata/commands. Keep the existing UI controller contract and service-owned statistics.
- **Voice:** MediaSession transport commands already use the Android media-control path. Rich conversational commands, platform-assistant discovery and permission behavior require separate integration work.
- **Scale:** Move full-history UI calculations into indexed aggregates and incrementally maintained coverage tables. Keep original segment IDs for audit/backup. Benchmark before claiming multi-million-event performance.
