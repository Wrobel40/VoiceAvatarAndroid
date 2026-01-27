#!/usr/bin/env python3
"""
Voice Assistant Prototype v1
Prosty test: Enter → nagraj → STT → Claude → TTS → play
"""

import sounddevice as sd
import soundfile as sf
import numpy as np
import requests
import json
from faster_whisper import WhisperModel
import sys
import os

# =============================================================================
# KONFIGURACJA
# =============================================================================

# Audio settings
SAMPLE_RATE = 16000  # 16kHz dla Whisper
RECORD_DURATION = 5  # sekundy nagrywania
AUDIO_FILE = "recorded_audio.wav"
OUTPUT_AUDIO = "response_audio.wav"

# Clawdbot API
CLAWDBOT_URL = "http://localhost:18789"  # Zmień jeśli potrzeba
CLAWDBOT_TOKEN = "bb283f9626e7a84f6b29bb7c284c2da3e01c64fa39c45d89"  # Z Twojego configu

# Whisper model (tiny/base/small/medium)
WHISPER_MODEL = "base"  # base to kompromis między szybkością a jakością

# =============================================================================
# FUNKCJE
# =============================================================================

def record_audio(duration=RECORD_DURATION, sample_rate=SAMPLE_RATE):
    """Nagraj audio z mikrofonu"""
    print(f"🎤 Nagrywam przez {duration} sekund...")
    audio = sd.rec(int(duration * sample_rate), 
                   samplerate=sample_rate, 
                   channels=1, 
                   dtype='int16')
    sd.wait()
    print("✓ Nagranie zakończone")
    return audio

def save_audio(audio, filename=AUDIO_FILE, sample_rate=SAMPLE_RATE):
    """Zapisz audio do pliku WAV"""
    sf.write(filename, audio, sample_rate)
    print(f"✓ Zapisano do {filename}")

def transcribe_audio(audio_file, model):
    """Transkrypcja audio → tekst (Whisper)"""
    print("🔤 Transkrybuję audio...")
    segments, info = model.transcribe(audio_file, language="pl")
    
    text = ""
    for segment in segments:
        text += segment.text + " "
    
    text = text.strip()
    print(f"✓ Rozpoznano: \"{text}\"")
    return text

def ask_claude(text):
    """Wyślij pytanie do Claude przez Clawdbot API (OpenAI-compatible)"""
    print("🤖 Pytam Claude...")
    
    url = f"{CLAWDBOT_URL}/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {CLAWDBOT_TOKEN}",
        "Content-Type": "application/json",
        "x-clawdbot-agent-id": "main"
    }
    payload = {
        "model": "clawdbot",
        "messages": [
            {"role": "user", "content": text}
        ],
        "stream": False
    }
    
    try:
        response = requests.post(url, headers=headers, json=payload, timeout=60)
        response.raise_for_status()
        data = response.json()
        
        # OpenAI format: data.choices[0].message.content
        answer = data.get("choices", [{}])[0].get("message", {}).get("content", "")
        
        if not answer:
            answer = "Przepraszam, otrzymałem pustą odpowiedź."
        
        print(f"✓ Claude odpowiedział: \"{answer[:100]}...\"")
        return answer
        
    except Exception as e:
        print(f"❌ Błąd API: {e}")
        return "Przepraszam, nie mogę teraz odpowiedzieć."

def text_to_speech(text, output_file=OUTPUT_AUDIO):
    """Generuj mowę z tekstu (LuxTTS)"""
    print("🔊 Generuję mowę...")
    
    # TODO: Integracja z LuxTTS
    # Na razie placeholder - wymaga inicjalizacji modelu
    
    # from zipvoice.luxvoice import LuxTTS
    # lux_tts = LuxTTS('YatharthS/LuxTTS', device='cpu', threads=2)
    # encoded_prompt = lux_tts.encode_prompt('reference_voice.wav', rms=0.01)
    # final_wav = lux_tts.generate_speech(text, encoded_prompt, num_steps=4)
    # sf.write(output_file, final_wav.numpy().squeeze(), 48000)
    
    print("⚠️  LuxTTS not implemented yet - używam placeholder")
    # Tymczasowo: zwróć info że trzeba dodać TTS
    return None

def play_audio(audio_file, sample_rate=48000):
    """Odtwórz audio przez głośnik"""
    print("🔊 Odtwarzam odpowiedź...")
    data, sr = sf.read(audio_file)
    sd.play(data, sr)
    sd.wait()
    print("✓ Odtworzono")

# =============================================================================
# GŁÓWNA PĘTLA
# =============================================================================

def main():
    print("=" * 60)
    print("Voice Assistant Prototype v1")
    print("=" * 60)
    print()
    
    # Inicjalizacja Whisper
    print("Ładuję model Whisper...")
    whisper_model = WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")
    print("✓ Whisper gotowy")
    print()
    
    # TODO: Inicjalizacja LuxTTS
    # lux_tts = ...
    
    while True:
        print("-" * 60)
        input("Naciśnij ENTER aby nagrać pytanie (Ctrl+C aby wyjść)...")
        
        try:
            # 1. Nagraj
            audio = record_audio()
            save_audio(audio)
            
            # 2. STT (Whisper)
            text = transcribe_audio(AUDIO_FILE, whisper_model)
            
            if not text:
                print("⚠️  Nie rozpoznano tekstu, spróbuj ponownie")
                continue
            
            # 3. LLM (Claude)
            response = ask_claude(text)
            
            # 4. TTS (LuxTTS)
            audio_file = text_to_speech(response)
            
            # 5. Play
            if audio_file and os.path.exists(audio_file):
                play_audio(audio_file)
            else:
                print(f"📝 Odpowiedź (tekst): {response}")
            
            print()
            
        except KeyboardInterrupt:
            print("\n\n👋 Do zobaczenia!")
            break
        except Exception as e:
            print(f"❌ Błąd: {e}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    main()
