# Zara AI — layer6-6-minilm-intent

Active development branch for Layer 6.6 MiniLM semantic intent fallback.

## Recent changes
- SemanticIntentMapper: NAVIGATION/APP_CONTROL → null (Fix 1)
- SemanticIntentMapper: REMINDER → SET_TIMER only for duration-only inputs (Fix 2)
- EntityExtractor: extractReminder() now extracts optional task entity
