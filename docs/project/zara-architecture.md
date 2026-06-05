# Zara Architecture

## Project Vision

Zara is an on-device AI voice assistant designed to provide a fast, privacy-friendly, always-available assistant experience similar to Google Assistant while remaining modular and extensible.

The long-term goal is:

* Wake word activation
* Natural speech interaction
* Action execution
* Context awareness
* Semantic understanding
* Multi-app integration
* Developer extensibility

---

# Architectural Layers

## Layer 1 — Wakeword Layer

Purpose:

Detect the wake phrase:

"Hey Zara"

Responsibilities:

* Continuous passive listening
* Low battery consumption
* Wake trigger generation
* False-positive minimization

Current Status:

Partially implemented.

Key Components:

* WakeWordManager
* EnergyVadEngine

Notes:

Current implementation contains a stub EnergyVadEngine. Future production implementation will replace this with a real low-power wakeword pipeline.

---

## Layer 2 — Speech Recognition Layer

Purpose:

Convert speech into text.

Responsibilities:

* SpeechRecognizer lifecycle
* Audio focus management
* STT correction
* Partial/final transcription

Current Status:

Working.

Key Components:

* SttManager
* SttCorrectionLayer

Recent Fixes:

* AudioFocus lifecycle management
* Callback cleanup
* STT correction boundary fix

---

## Layer 3 — Intent Understanding Layer

Purpose:

Determine what the user wants.

Examples:

* Open app
* Send message
* Call contact
* Play media

Current Status:

Working for basic intents.

Key Components:

* LocalIntentClassifier

Current Limitation:

Intent classification exists but reusable slot extraction is not yet implemented.

---

## Layer 4 — Entity / Slot Extraction Layer

Purpose:

Extract structured information from commands.

Example:

Input:

send hi to malvin on whatsapp

Output:

Intent:
SEND_MESSAGE

Slots:

recipient=malvin
message=hi
app=whatsapp

Current Status:

Not implemented.

Next Major Architecture Goal.

---

## Layer 5 — Action Planning Layer

Purpose:

Convert intent + slots into executable actions.

Example:

Intent:
SEND_MESSAGE

Slots:
recipient=malvin
message=hello
app=whatsapp

↓

Action Plan:
Open WhatsApp
Open chat
Insert message

Current Status:

Partially implemented.

---

## Layer 6 — Action Execution Layer

Purpose:

Perform actions on the device.

Current Status:

Working.

Key Components:

* ActionExecutor
* AppActions

Responsibilities:

* Launch apps
* Execute device actions
* Route execution requests

---

## Layer 7 — Context / Memory Layer

Purpose:

Maintain conversational and operational context.

Examples:

* Follow-up commands
* Previous references
* Multi-turn interaction

Current Status:

Not implemented.

---

## Layer 8 — Semantic Understanding Layer

Purpose:

Handle imperfect speech and natural language.

Technology Direction:

MiniLM fallback.

Responsibilities:

* Semantic similarity
* Robust intent understanding
* Recovery from STT mistakes

Current Status:

Planned.

Not yet implemented.

---

## Layer 9 — Conversation Layer

Purpose:

Natural multi-turn dialogue.

Examples:

"Send a message to Malvin."

"What should it say?"

"Tell him I'll be late."

Current Status:

Planned.

---

## Layer 10 — Extensibility Layer

Purpose:

Developer integrations and future plugins.

Examples:

* External skills
* APIs
* Automation workflows

Current Status:

Future roadmap.

---

# Core Design Principles

1. On-device first
2. Low latency
3. Low battery usage
4. Modular architecture
5. Reusable components
6. Layered design
7. Replaceable implementations
8. Production-grade reliability

---

# Current Focus

Current architecture milestone:

Layer 4

Entity / Slot Extraction Layer
