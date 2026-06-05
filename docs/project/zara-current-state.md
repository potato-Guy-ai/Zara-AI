# Zara Current State

Last Updated: June 2026

---

# Current Development Position

The project has completed foundational stabilization work and is preparing to implement the Entity / Slot Extraction Layer.

---

# Completed Work

## App Launching

Status:

Working.

Features:

* Installed app discovery
* QUERY_ALL_PACKAGES support
* Alias resolution
* Fuzzy matching
* Clarification support

Examples:

* ig → Instagram
* yt → YouTube
* ff → Free Fire
* ff max → Free Fire Max

---

## AppResolver v2

Status:

Complete.

Capabilities:

Priority Order:

1. Exact Match
2. Alias Match
3. StartsWith Match
4. Contains Match
5. Fuzzy Match
6. Clarification

Notes:

Short aliases use explicit alias mapping.

Fuzzy matching is length-gated.

---

## STT Correction Layer

Status:

Complete.

Bug Fixed:

Previously:

instagram
↓

instagramgram

snapchat
↓

snapchatchat

Root Cause:

Unbounded string replacement.

Fix:

Word-boundary regex replacement.

Result:

No duplicate expansions.

---

## Audio Stability

Status:

Complete.

Implemented:

* AudioFocus acquisition
* AudioFocus release
* STT callback cleanup
* Resource lifecycle cleanup

Purpose:

Improve long-session stability and audio coexistence.

---

## Wakeword Stability Audit

Status:

Completed.

Result:

No evidence of:

* Listener accumulation
* Coroutine accumulation
* Restart loops
* Long-session degradation

Notes:

EnergyVadEngine remains a stub and is a future implementation target.

---

# Known Limitations

## Missing Entity Extraction

Examples that are not fully supported:

* send hi to malvin on whatsapp
* call ahmed
* message dad
* email john

Reason:

No generalized slot extraction layer.

---

## Missing Semantic Understanding

Examples:

* Heavy STT mistakes
* Natural language variations

Planned Solution:

MiniLM fallback layer.

---

## Missing Context Layer

Examples:

User:
Send a message to Malvin.

Zara:
What should I send?

User:
Tell him I'll be late.

Currently unsupported.

---

# Active Roadmap

Current Target:

Layer 4

Entity / Slot Extraction Layer

Goals:

* Recipient extraction
* App extraction
* Message extraction
* General slot extraction
* Reusable architecture

Future:

Layer 8

MiniLM Semantic Understanding Layer

Future:

Layer 9

Conversation Layer

---

# Important Files

Intent Layer:

* LocalIntentClassifier.kt

Execution Layer:

* ActionExecutor.kt
* AppActions.kt

Resolver Layer:

* AppResolver.kt

Voice Pipeline:

* VoiceSessionManager.kt
* WakeWordManager.kt
* SttManager.kt

Service Layer:

* ZaraForegroundService.kt

---

# Engineering Rules

1. Audit before implementation.
2. Prove root cause before fixing.
3. Avoid repository-wide audits.
4. Prefer reusable layers over feature-specific logic.
5. Keep CPU and battery usage minimal.
6. Avoid premature optimization.
7. Preserve architectural boundaries.
