# RPi4 Voice Assistant - Setup Instructions

## Instalacja bibliotek

### 1. Aktywuj venv
```bash
cd ~/assistant
source bin/activate
```

### 2. Zainstaluj faster-whisper (STT)
```bash
pip install faster-whisper
```

### 3. Zainstaluj LuxTTS (TTS)
```bash
cd ~/assistant
git clone https://github.com/ysharma3501/LuxTTS.git
cd LuxTTS
pip install -r requirements.txt
cd ..
```

### 4. Zainstaluj dodatkowe biblioteki
```bash
pip install soundfile librosa
```

### 5. Biblioteki już zainstalowane (z wcześniej):
- anthropic ✓
- sounddevice ✓
- numpy ✓
- scipy ✓

## Konfiguracja Clawdbot API

### Włącz OpenAI-compatible endpoint

Na maszynie gdzie działa Clawdbot Gateway (WSL/Linux):

```bash
clawdbot configure
```

Dodaj do konfiguracji:
```json
{
  "gateway": {
    "http": {
      "endpoints": {
        "chatCompletions": { "enabled": true }
      }
    }
  }
}
```

Albo użyj `config.patch`:
```bash
# TODO: Marcin, możesz to zrobić przez tool gateway patch
```

Restart Clawdbot:
```bash
clawdbot gateway restart
```

### Sprawdź czy działa:
```bash
curl -sS http://localhost:18789/v1/chat/completions \
  -H 'Authorization: Bearer bb283f9626e7a84f6b29bb7c284c2da3e01c64fa39c45d89' \
  -H 'Content-Type: application/json' \
  -H 'x-clawdbot-agent-id: main' \
  -d '{"model":"clawdbot","messages":[{"role":"user","content":"test"}]}'
```

### Połączenie z RPi4:
- Jeśli RPi4 i Gateway są na tej samej sieci: użyj IP maszyny z Gateway
- Jeśli są na tej samej maszynie: `localhost:18789`
- W `voice_assistant_v1.py` ustaw `CLAWDBOT_URL`

---

**Po instalacji przejdź do `voice_assistant_v1.py`**
