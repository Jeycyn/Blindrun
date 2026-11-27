# BlindRun Kenya — Code Review & Fix Log (2026-11-27)

Scope: every Kotlin source file, the Gradle/manifest configuration and `supabase/schema.sql`.

> **Verification status.** The review environment had no Kotlin compiler, Gradle or Postgres, so
> the fixes below are **not compiled or run**. They were checked by careful reading, bracket-balance
> checks and new unit tests (`BlindRunDomainUnitTest`) that you should run. See *How to verify*.

Severity: **Critical** = security hole or safety-relevant failure · **High** = data loss / wrong
behaviour in normal use · **Medium** = incorrect in edge cases · **Low** = hygiene.

---

## 1. Fixed in this patch

### Critical
| # | Where | Problem | Fix |
|---|---|---|---|
| C1 | `schema.sql` RLS + `AuthScreen` + `BlindRunRepository` | **Anyone could make themselves admin.** The register screen offered an *Admin* chip, the app sent `role=admin`, and the RLS policies (`profiles_insert_self`, `profiles_update_self_or_admin`) never restricted the `role` column — a signed-in user could also `PATCH` their own role directly with the public anon key. | DB trigger `profiles_guard_role` blocks non-admins from creating/changing admin roles; Admin chip removed; repository coerces `admin → athlete` for self-service paths (register, offline mode, metadata fallback). |
| C2 | `BleWaistBeltManager.onServicesDiscovered` | **Obstacle notifications could silently never be enabled.** It fired a read, a descriptor write, another read… back-to-back. Android runs one GATT operation at a time; the extras fail silently, and the obstacle CCCD write (the safety-critical one) was issued *after* the battery ones. | Serial GATT operation queue with 3 s watchdog; obstacle CCCD is enqueued first. |
| C3 | `BlindRunViewModel` timer + GPS callback | **Lost updates on the live race.** The 1 Hz timer and each GPS fix copied a stale `activeRace.value` and wrote it back, erasing each other's changes (distance lost, elapsed time moving backwards). | All updates go through `mutateActiveRace` (mutex + read-latest-from-DB). |

### High
| # | Where | Problem | Fix |
|---|---|---|---|
| H1 | `syncQueuedRecords` | Rows were marked synced by writing back the *uploaded snapshot*, overwriting anything changed during the request (e.g. live race distance) and flagging it as synced. | `markXSynced` compares the current row with the snapshot inside a transaction; append-only tables use `UPDATE … SET synced = 1 WHERE id IN (…)`. |
| H2 | `syncQueuedRecords` | Failed batches were ignored and the UI said "All records up to date". Access token was refreshed only at app start, so after ~1 h every upload returned 401 silently. | Token refreshed before each network operation; failures collected → `SyncState.ERROR` with a message ("Session expired — sign in again…"). |
| H3 | `SupabaseApiService` + RLS | `merge-duplicates` on GPS/obstacle/health needs an UPDATE policy that doesn't exist, so re-sending any already-stored row (lost response, retry) failed the **whole batch** forever. | `Prefer: resolution=ignore-duplicates` for append-only tables. |
| H4 | Device sync + `devices_write` policy | Admin-registered belts have owner `""`, sent as an invalid uuid (HTTP 400 for the whole device batch). Athletes also could not *claim* an unowned belt (UPDATE policy required existing owner = self). | Blank owner → `null`; split `devices_insert/update/delete` policies allowing claim of unowned belts. |
| H5 | `observeHardwareEvents` | `collectLatest` cancelled the handler for a packet when the next arrived → dropped haptic cues and un-saved obstacle events during bursts. | `collect`, plus per-direction throttling (HIGH severity never throttled). |
| H6 | `calculateMinDeviationMeters` | Deviation was measured to the nearest **waypoint**, not the route line. With GPX points 50–200 m apart, a runner mid-segment triggered false "off route" alarms. | Point-to-segment distance (new `distanceToSegmentMeters`). |
| H7 | GPS callback | An alert + haptic fired on **every** GPS fix while off route (hundreds of rows, synced to the coach). | One alert per excursion, repeat cue every 15 s, hysteresis to clear. |
| H8 | Manifest | `allowBackup=true` with default rules: access/refresh tokens (DataStore) and the offline health database went to cloud backup. | `allowBackup=false`; explicit exclude-all backup + device-transfer rules. |
| H9 | `signOut` | Local Room data stayed on the phone; coach/admin/"all" queries then exposed the previous user's cached profiles (phone numbers, emergency contacts), alerts and races to the next person to sign in or use Offline Mode. | Flush queue → wipe local DB when nothing is unsynced. |

### Medium / Low
| # | Where | Fix |
|---|---|---|
| M1 | `pullCloudData`, sign-in | A cloud pull overwrote unsynced local profile/device edits. Now skips rows with local changes. |
| M2 | `saveProfile` | An admin editing *someone else's* role overwrote the admin's own haptic/audio settings. Only applied when the profile is the signed-in user. |
| M3 | `createOrUpdateRace` | A coach editing an athlete's race created a duplicate (lookup only searched own races). |
| M4 | App restart | A race left `running` after process death was never resumed or closed. Now set to `paused` at start-up. |
| M5 | `BleWaistBeltManager` | `StateFlow` read-modify-write from main, binder and IO threads raced. Now `update { }`. |
| M6 | `parseObstaclePacket` | Unknown direction fell back to **FRONT** (garbage → a false front obstacle). Now rejected. |
| M7 | BLE | Any MAC from a QR/saved pairing was trusted. Now disconnects if the BlindRun GATT service is absent. |
| M8 | `observePendingSyncCount` | Ignored GPS/alerts and only counted the latest 50/100 rows. Now exact `COUNT(*)` per table. |
| M9 | `acknowledgeAlert` | Acknowledgement never re-queued, so it never synced. Sets `synced = 0`. |
| M10 | Timer | Elapsed time counted ticks (`+1`), drifting/losing time under load. Now wall-clock based. |
| M11 | `connectBleDevice` | Synthetic serial used 4 hex chars of the MAC (collisions → unique-constraint failures). Uses the full MAC. |
| M12 | Obstacle events | `device_serial` stored the MAC. Now stores the paired belt serial. |
| M13 | Sync | A large offline backlog (thousands of GPS rows) was sent as one request that could time out. Now 500 rows per batch. |
| L1 | `SupabaseClient` | HTTP logging active in release. Now debug-only, headers redacted. |
| L2 | `schema.sql` | `SECURITY DEFINER` helpers without `search_path`; no `updated_at` maintenance; no indexes. Added. |

New unit tests: segment-based deviation (on-route mid-segment, past-the-end), malformed obstacle frames.

---

## 2. Open items (not changed — need a decision or a larger change)

| Priority | Item | Why / suggested approach |
|---|---|---|
| **High** | **No foreground service.** BLE, GPS and the race timer live in the Activity's ViewModel. With the screen off or the app backgrounded, Android throttles location and may kill the process; background vibration from a non-foreground app is also suppressed. For a running app for blind athletes this is the main real-world risk. | Add a `RaceTrackingService` (`foregroundServiceType="location|connectedDevice"`, `FOREGROUND_SERVICE_LOCATION` / `FOREGROUND_SERVICE_CONNECTED_DEVICE`, ongoing notification) that owns the managers and the race mutex; ViewModel binds to it. Use alarm-usage `VibrationAttributes` for safety cues. Request `POST_NOTIFICATIONS` (declared but never requested today). |
| **High** | **Consent default.** `consented_coach_sharing` defaults to `true` (opt-out) in the schema, DTO and domain model. Health data is sensitive personal data (Kenya Data Protection Act, 2019 — please confirm with counsel). | Default to `false` and ask explicitly in onboarding. Also consider applying the consent flag to GPS tracks, phone number and emergency contact, which assigned coaches can currently read regardless. |
| Medium | `fallbackToDestructiveMigration(dropAllTables = true)` with `exportSchema = false`: any schema bump deletes un-synced runs. | Enable schema export and write real `Migration`s before the first public release. |
| Medium | Coach assignments and audit logs are written to Room but **not queued** — if offline, they exist only locally and are never uploaded. Remote deletions of assignments are never applied locally (`isNotEmpty()` guard). | Add a `synced` flag + queue, and replace-all on a successful pull. |
| Medium | Cloud pull only fetches profiles, devices and assignments. A reinstall or second phone shows no run history, though `getRaces/getHealthRecords` already exist in the API service. | Pull races (+ health/alerts) after sign-in, preferring local unsynced rows. |
| Medium | `connectGatt(autoConnect = true)` for the *first* connection can be slow on some phones. | Connect with `autoConnect = false` first, fall back to `true` for reconnects. Request a larger MTU if frames grow. |
| Medium | Release build: `isMinifyEnabled = false`; `moshi-kotlin` reflection + codegen both included. | Enable R8 with keep rules, drop the reflective factory if all DTOs use codegen. |
| Low | Password minimum is 6 characters. | Require ≥ 8 and match the Supabase project setting. |
| Low | Calories are a flat 62 kcal/km estimate; `String.format` without `Locale` in spoken/visible strings. | Use the watch's calories or a weight-based formula; pass `Locale.getDefault()` explicitly. |
| Low | Scan has no service-UUID filter (needed for reliable screen-off scanning on Android 8.1+). | `ScanFilter.Builder().setServiceUuid(...)`. |
| Low | No repository/ViewModel tests; only domain-level unit tests. | Add Room in-memory + `MockWebServer` tests for sync (partial failure, 401, retry). |

---

## 3. How to verify

1. `./gradlew :app:testDebugUnitTest` — includes the two new tests.
2. **SQL:** in a scratch Supabase project run `schema.sql`; then as a normal user try
   `update profiles set role='admin' where id = auth.uid();` → must fail with *Only administrators can change roles*.
3. **Sync:** create a race offline, go online, sync; kill the network mid-sync and confirm the message
   is an error (not "up to date") and the rows stay queued; re-sync succeeds without duplicates.
4. **BLE (real belt):** connect and confirm obstacle packets arrive immediately after connection.
5. **Race:** run 10 minutes with a GPX route; confirm distance and elapsed time both advance,
   no alerts while on the line, and exactly one alert when you leave it.
6. **Shared phone:** sign out, sign in as another user → no previous profiles/races visible.
