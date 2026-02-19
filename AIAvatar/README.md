# 🤖 AI Avatar - Android App

Aplikacja Android z animowanym avatarem AI (styl Fortnite) do rozmów z modelami LLM uruchomionymi lokalnie na Mac Mini przez Ollama.

---

## 📱 Funkcje aplikacji

- **Animowany avatar 3D** - postać w stylu Fortnite z:
  - Synchronizacją ust podczas mówienia (lip sync)
  - Efektem "słuchania" (pulsujące pierścienie)
  - Animacją "myślenia" (obracający się pierścień)
  - Efektem unoszenia się (floating)
  - Mruganiem
  - Energetycznymi cząsteczkami

- **Tryb głosowy** - jak Gemini Live:
  - Naciśnij mikrofon → mów → AI odpowiada głosem (TTS po polsku)
  - Automatyczne rozpoznawanie mowy (STT) po polsku
  
- **Tryb czatu** - wpisuj wiadomości, avatar reaguje
  
- **Status połączenia** - zielona/czerwona kropka w prawym górnym rogu
- **Wybór modelu** - kliknij kropkę, wybierz dostępny model z listy

---

## 🏗️ Struktura projektu

```
AIAvatar/
├── app/src/main/java/com/aiavatar/app/
│   ├── MainActivity.kt           # Główna aktywność
│   ├── audio/
│   │   └── AudioManager.kt       # Mowa (STT + TTS)
│   ├── avatar/
│   │   └── AnimatedAvatar.kt     # Avatar Canvas (Compose)
│   ├── network/
│   │   └── OllamaApiClient.kt    # API Ollama (streaming)
│   ├── ui/
│   │   └── MainScreen.kt         # Główny interfejs
│   └── viewmodel/
│       └── MainViewModel.kt      # Logika aplikacji
└── setup_mac_mini.sh             # Skrypt konfiguracji backendu
```

---

## 🖥️ Konfiguracja backendu (Mac Mini 2012)

### Krok 1: Zainstaluj Ollama

```bash
# Na Mac Mini przez Terminal:
curl -fsSL https://ollama.com/install.sh | sh

# LUB uruchom skrypt:
chmod +x setup_mac_mini.sh
./setup_mac_mini.sh
```

### Krok 2: Uruchom Ollama z dostępem sieciowym

```bash
# WAŻNE: Musi nasłuchiwać na 0.0.0.0 (nie tylko localhost)
OLLAMA_HOST=0.0.0.0 ollama serve
```

Aby uruchamiało się automatycznie przy starcie macOS, stwórz plik LaunchAgent:

```bash
# ~/Library/LaunchAgents/com.ollama.serve.plist
cat > ~/Library/LaunchAgents/com.ollama.serve.plist << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.ollama.serve</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/bin/ollama</string>
        <string>serve</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>OLLAMA_HOST</key>
        <string>0.0.0.0</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
</dict>
</plist>
EOF

launchctl load ~/Library/LaunchAgents/com.ollama.serve.plist
```

### Krok 3: Pobierz model(e)

Dla 8GB RAM rekomendowane:
```bash
ollama pull llama3.2:3b      # ~2GB, bardzo szybki
ollama pull llama3.2          # ~4GB, dobry balans
ollama pull mistral           # ~4GB, dobry po polsku
```

### Krok 4: Sprawdź firewall

System Preferences → Security & Privacy → Firewall → Firewall Options → Dodaj Ollama lub wyłącz firewall dla sieci lokalnej.

### Krok 5: Przetestuj z telefonu

```bash
# Z telefonu lub komputera w tej samej sieci:
curl http://192.168.0.177:11434/api/tags
```

---

## 📲 Budowanie aplikacji Android

### Wymagania
- Android Studio Hedgehog (2023.1.1) lub nowszy
- JDK 17
- Android SDK API 26+
- Telefon/emulator z Android 8.0+

### Kroki

1. **Otwórz projekt** w Android Studio
2. **Synchronizuj Gradle** (File → Sync Project with Gradle Files)
3. **Podłącz telefon** przez USB lub użyj emulatora
4. **Kliknij Run** (zielony trójkąt)

### Uprawnienia wymagane przez aplikację
- `INTERNET` - połączenie z Ollama API
- `RECORD_AUDIO` - rozpoznawanie mowy

---

## ⚙️ Konfiguracja IP serwera

Jeśli IP Mac Mini jest inne niż `192.168.0.177`, zmień w:

**`app/src/main/java/com/aiavatar/app/network/OllamaApiClient.kt`** linia 1:
```kotlin
class OllamaApiClient(
    private val baseUrl: String = "http://192.168.0.177:11434"  // ← zmień tutaj
)
```

I w **`MainViewModel.kt`**:
```kotlin
private val apiClient = OllamaApiClient("http://TWOJE_IP:11434")
```

---

## 🎨 Design

- Dominujący kolor: **granat** (#050D1F → #0D1B4B)
- Akcenty: **cyan** (#00D4FF), niebieski (#1230A0)
- Tło: dynamiczna siatka
- Avatar: canvas-rendered, w pełni animowany
- Typografia: system font, bold/black weights

---

## 🐛 Rozwiązywanie problemów

### "Brak połączenia z serwerem"
- Sprawdź czy Ollama działa: `curl http://192.168.0.177:11434/api/tags`
- Upewnij się że Mac Mini i telefon są w tej samej sieci WiFi
- Sprawdź firewall macOS
- Upewnij się że Ollama uruchomiona z `OLLAMA_HOST=0.0.0.0`

### Mikrofon nie działa
- Aplikacja prosi o uprawnienie RECORD_AUDIO przy pierwszym uruchomieniu
- Sprawdź Ustawienia → Aplikacje → AI Avatar → Uprawnienia

### Model odpowiada po angielsku
- System prompt jest ustawiony na polski
- Niektóre małe modele mogą ignorować instrukcje językowe
- Spróbuj `mistral` lub `llama3.2` zamiast mniejszych modeli
