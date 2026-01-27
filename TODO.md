# TODO - Voice Assistant v1

## ⚠️ Co jest do dokończenia w kodzie:

### 1. Clawdbot API Integration ✅ GOTOWE
**Plik:** `voice_assistant_v1.py` → funkcja `ask_claude()`

✅ Użyto OpenAI-compatible endpoint: `/v1/chat/completions`

⚠️ **MUSISZ WŁĄCZYĆ ENDPOINT** - patrz `rpi-setup.md` sekcja "Konfiguracja Clawdbot API"

**TEST:** 
```bash
curl -sS http://localhost:18789/v1/chat/completions \
  -H 'Authorization: Bearer bb283f9626e7a84f6b29bb7c284c2da3e01c64fa39c45d89' \
  -H 'Content-Type: application/json' \
  -H 'x-clawdbot-agent-id: main' \
  -d '{"model":"clawdbot","messages":[{"role":"user","content":"test"}]}'
```

### 2. LuxTTS Integration
**Plik:** `voice_assistant_v1.py` → funkcja `text_to_speech()`

Trzeba:
1. Zainicjalizować model LuxTTS w `main()`
2. (Opcjonalnie) Nagrać 3-5s reference audio dla custom voice
3. Zakodować prompt audio
4. Wygenerować speech

**Kod do dodania:**
```python
from zipvoice.luxvoice import LuxTTS

# W main():
lux_tts = LuxTTS('YatharthS/LuxTTS', device='cpu', threads=2)

# Opcjonalnie: custom voice
encoded_prompt = lux_tts.encode_prompt('my_voice.wav', rms=0.01)

# W text_to_speech():
final_wav = lux_tts.generate_speech(
    text, 
    encoded_prompt,  # lub None dla default
    num_steps=4
)
sf.write(output_file, final_wav.numpy().squeeze(), 48000)
```

### 3. Audio Devices
Sprawdź czy `sounddevice` używa właściwych urządzeń (Bluetooth):
```python
import sounddevice as sd
print(sd.query_devices())
```

Możliwe że trzeba ustawić default device:
```python
sd.default.device = [input_device_id, output_device_id]
```

---

## 📋 Kroki testowania:

1. **Zainstaluj biblioteki** (patrz `rpi-setup.md`)
2. **Uruchom skrypt:** `python voice_assistant_v1.py`
3. **Test nagrywania:** Czy nagrywa z mikrofonu BT?
4. **Test Whisper:** Czy transkrybuje dobrze po polsku?
5. **Test Claude API:** Czy dostaje odpowiedź?
6. **Dodaj LuxTTS** (krok 2 w TODO)
7. **Test TTS:** Czy generuje i odtwarza mowę?

---

## 🔧 Problemy do rozwiązania:

- [ ] Clawdbot API endpoint
- [ ] LuxTTS integracja
- [ ] Audio devices configuration
- [ ] Reference voice dla LuxTTS (opcjonalne)
- [ ] Error handling (co jeśli STT nic nie rozpozna?)
- [ ] Optymalizacja (czy Whisper base jest wystarczająco szybki?)

---

**Jak będzie działać wersja 1, dodamy wake word (openWakeWord) jako v2.**
