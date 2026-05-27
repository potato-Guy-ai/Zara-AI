"""
Zara AI Core Tests
Run: python -m pytest zara/ai_core/tests/test_intent.py -v
No Android required.
"""

import pytest
import re
from typing import Optional, Tuple


# ── Minimal Python mirror of ZaraAIEngine intent logic ─────────────────────

WAKE_PHRASES = ["hey zara", "wake up zara"]

def detect_wake_phrase(audio_text: str) -> bool:
    return any(p in audio_text.lower() for p in WAKE_PHRASES)

def match_intent(text: str) -> Tuple[str, Optional[str]]:
    """Returns (intent_name, extracted_entity)"""
    t = text.lower().strip()

    # Check specific phone-noun phrases BEFORE broad call/phone keyword
    if re.search(r'\b(silent|mute)\b', t):
        return ("silent_mode", "on")

    if re.search(r'\b(lock)\b', t) and re.search(r'\b(phone|screen|device)\b', t):
        return ("lock_screen", None)

    if re.search(r'\b(restart|reboot)\b', t):
        return ("reboot_confirm", None)

    if re.search(r'\b(call|ring|dial)\b', t) or (re.search(r'\bphone\b', t) and not re.search(r'\b(lock|silent|mute)\b', t)):
        entity = re.search(r'(?:call|ring|dial|phone)\s+(\w[\w\s]{0,20})', t)
        return ("call", entity.group(1).strip() if entity else None)

    if re.search(r'\b(send|text|message)\b', t):
        contact = re.search(r'(?:to|message|text)\s+(\w+)', t)
        msg = re.search(r'(?:saying|message|that)\s+(.+)', t)
        return ("sms", f"{contact.group(1) if contact else None}:{msg.group(1) if msg else None}")

    if 'wifi' in t or 'wi-fi' in t:
        on = any(w in t for w in ['on', 'enable', 'turn on'])
        return ("wifi", "on" if on else "off")

    if 'bluetooth' in t:
        on = any(w in t for w in ['on', 'enable'])
        return ("bluetooth", "on" if on else "off")

    if 'flashlight' in t or 'torch' in t:
        on = 'on' in t or 'enable' in t
        return ("flashlight", "on" if on else "off")

    if re.search(r'\b(silent|mute)\b', t):
        return ("silent_mode", "on")

    if re.search(r'\bvolume\b', t):
        dir_ = "up" if re.search(r'\b(up|max|higher)\b', t) else "down"
        return ("volume", dir_)

    if re.search(r'\b(open|launch|start)\b', t):
        app = re.search(r'(?:open|launch|start)\s+(\w[\w\s]{0,20})', t)
        return ("open_app", app.group(1).strip() if app else None)

    if re.search(r'\b(what time|current time)\b', t):
        return ("get_time", None)

    if re.search(r'\b(today|date|what day)\b', t):
        return ("get_date", None)

    return ("unknown", None)


# ── Tests ───────────────────────────────────────────────────────────────────

class TestWakeWord:
    def test_hey_zara(self):
        assert detect_wake_phrase("hey zara") is True

    def test_wake_up_zara(self):
        assert detect_wake_phrase("wake up zara") is True

    def test_case_insensitive(self):
        assert detect_wake_phrase("HEY ZARA") is True

    def test_no_match(self):
        assert detect_wake_phrase("hello siri") is False

    def test_partial_match(self):
        assert detect_wake_phrase("ok hey zara what's up") is True


class TestCallIntent:
    def test_call_mom(self):
        intent, entity = match_intent("call mom")
        assert intent == "call"
        assert entity == "mom"

    def test_ring_contact(self):
        intent, entity = match_intent("ring Ahmed")
        assert intent == "call"
        assert entity == "ahmed"

    def test_call_number(self):
        intent, entity = match_intent("call 9876543210")
        assert intent == "call"

    def test_no_contact(self):
        intent, entity = match_intent("make a call")
        assert intent == "call"
        assert entity is None


class TestSMSIntent:
    def test_send_message(self):
        intent, entity = match_intent("send a message to John saying hello there")
        assert intent == "sms"
        assert "john" in entity.lower()

    def test_text_friend(self):
        intent, _ = match_intent("text Sarah")
        assert intent == "sms"


class TestDeviceControl:
    def test_wifi_on(self):
        intent, entity = match_intent("turn on wifi")
        assert intent == "wifi"
        assert entity == "on"

    def test_wifi_off(self):
        intent, entity = match_intent("disable wifi")
        assert intent == "wifi"
        assert entity == "off"

    def test_bluetooth_on(self):
        intent, entity = match_intent("enable bluetooth")
        assert intent == "bluetooth"
        assert entity == "on"

    def test_flashlight_on(self):
        intent, entity = match_intent("turn on flashlight")
        assert intent == "flashlight"
        assert entity == "on"

    def test_flashlight_torch(self):
        intent, entity = match_intent("torch off")
        assert intent == "flashlight"
        assert entity == "off"

    def test_silent(self):
        intent, entity = match_intent("put phone on silent")
        assert intent == "silent_mode"

    def test_volume_up(self):
        intent, entity = match_intent("volume up")
        assert intent == "volume"
        assert entity == "up"

    def test_volume_down(self):
        intent, entity = match_intent("lower the volume")
        assert intent == "volume"
        assert entity == "down"


class TestAppLaunch:
    def test_open_whatsapp(self):
        intent, entity = match_intent("open WhatsApp")
        assert intent == "open_app"
        assert "whatsapp" in entity.lower()

    def test_launch_spotify(self):
        intent, entity = match_intent("launch Spotify")
        assert intent == "open_app"
        assert "spotify" in entity.lower()


class TestInformational:
    def test_time_query(self):
        intent, _ = match_intent("what time is it")
        assert intent == "get_time"

    def test_date_query(self):
        intent, _ = match_intent("what's today's date")
        assert intent == "get_date"


class TestSystemActions:
    def test_lock_phone(self):
        intent, _ = match_intent("lock the phone")
        assert intent == "lock_screen"

    def test_restart(self):
        intent, _ = match_intent("restart the phone")
        assert intent == "reboot_confirm"


class TestUnknown:
    def test_gibberish(self):
        intent, _ = match_intent("flibbertigibbet")
        assert intent == "unknown"

    def test_empty(self):
        intent, _ = match_intent("")
        assert intent == "unknown"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
