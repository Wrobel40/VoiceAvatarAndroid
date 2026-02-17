#!/usr/bin/env python3
"""
OpenClaw Voice Webhook Server v3
STT via Whisper (base model), TTS via sherpa-onnx
"""

import base64
import json
import os
import subprocess
import tempfile
import http.server
import socketserver
import traceback
from pathlib import Path
from datetime import datetime

PORT = 8080
AUDIO_DIR = Path(tempfile.gettempdir()) / "openclaw_voice"
AUDIO_DIR.mkdir(exist_ok=True)
LOG_FILE = Path("/tmp/voice_server.log")

def log(msg):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    line = f"[{timestamp}] {msg}"
    print(line, flush=True)
    with open(LOG_FILE, "a") as f:
        f.write(line + "\n")

def transcribe_wav2vec(audio_path):
    """Transkrypcja via Wav2Vec2 (lepszy dla polskiego)."""
    import subprocess
    audio_str = str(audio_path)
    try:
        cmd = [
            "bash", "-c",
            "KMP_DUPLICATE_LIB_OK=TRUE python3.11 -c \""
            "import torch; "
            "from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor; "
            "import librosa; "
            "processor = Wav2Vec2Processor.from_pretrained('facebook/wav2vec2-large-xlsr-53-polish'); "
            "model = Wav2Vec2ForCTC.from_pretrained('facebook/wav2vec2-large-xlsr-53-polish'); "
            "audio, sr = librosa.load(\\\"" + audio_str + "\\\", sr=16000); "
            "inputs = processor(audio, sampling_rate=16000, return_tensors='pt', padding=True); "
            "with torch.no_grad(): "
            "    logits = model(inputs.input_values).logits; "
            "    predicted_ids = torch.argmax(logits, dim=-1); "
            "    transcription = processor.batch_decode(predicted_ids)[0]; "
            "print(transcription)\""
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120, env={**os.environ, "KMP_DUPLICATE_LIB_OK": "TRUE"})
        if result.returncode == 0 and result.stdout.strip():
            return result.stdout.strip()
        else:
            log(f"Wav2Vec error: {result.stderr[:200] if result.stderr else result.stdout[:200]}")
            return None
    except subprocess.TimeoutExpired:
        log("Wav2Vec timeout")
        return None
    except Exception as e:
        log(f"Wav2Vec exception: {e}")
        return None

def transcribe_whisper(audio_path):
    """Transkrypcja via faster-Whisper (python3.11)."""
    audio_str = str(audio_path)
    try:
        cmd = [
            "bash", "-c",
            "KMP_DUPLICATE_LIB_OK=TRUE python3.11 -c \""
            "from faster_whisper import WhisperModel; "
            "model = WhisperModel('base', device=\\\"cpu\\\", compute_type=\\\"int8\\\"); "
            "segments, info = model.transcribe(\\\"" + audio_str + "\\\", language=\\\"pl\\\"); "
            "print(' '.join([s.text for s in segments]))\""
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60, env={**os.environ, "KMP_DUPLICATE_LIB_OK": "TRUE"})
        if result.returncode == 0:
            return result.stdout.strip()
        else:
            log(f"Whisper error: {result.stderr[:200]}")
            return None
    except subprocess.TimeoutExpired:
        log("Whisper timeout")
        return None
    except Exception as e:
        log(f"Whisper exception: {e}")
        return None

def generate_speech(text):
    """TTS via sherpa-onnx."""
    try:
        output_path = AUDIO_DIR / f"response_{os.urandom(4).hex()}.wav"
        cmd = [
            "bash", "-c",
            f'export SHERPA_ONNX_MODEL_DIR="/Users/apple/.openclaw/tools/sherpa-onnx-tts/models/vits-piper-pl_PL-meski_wg_glos-medium" && '
            f'/Users/apple/bin/sherpa-onnx-tts -o "{output_path}" "{text}" 2>&1'
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0 and output_path.exists():
            with open(output_path, 'rb') as f:
                audio_data = base64.b64encode(f.read()).decode()
            os.remove(output_path)
            return audio_data
        return None
    except Exception as e:
        log(f"TTS error: {e}")
        return None

def call_llm(text):
    """Wywołanie LLM przez lokalny Ollama (MiniMax)."""
    import requests
    
    try:
        base_url = "http://127.0.0.1:11434/v1"
        
        system_prompt = """Jesteś Kimi - pomocny, miły asystent głosowy. 
Odpowiadaj krótko i zwięźle, naturalnym językiem polskim (1-2 zdania).
Bądź konkretny."""
        
        payload = {
            "model": "minimax-m2.5:cloud",
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": text}
            ],
            "max_tokens": 100,
            "temperature": 0.7
        }
        
        resp = requests.post(
            f"{base_url}/chat/completions",
            json=payload,
            timeout=20
        )
        
        if resp.status_code == 200:
            result = resp.json()
            return result["choices"][0]["message"]["content"]
        else:
            log(f"LLM error: {resp.status_code} - {resp.text[:200]}")
            return None
            
    except Exception as e:
        log(f"LLM exception: {e}")
        return None

def process_message(text):
    """Przetwarzanie tekstu przez LLM."""
    # Najpierw sprawdź proste komendy
    text_lower = text.lower().strip()
    
    if text_lower in ["cześć", "hej", "część", "dzień dobry"]:
        return "Cześć! Jestem Kimi - Twój asystent głosowy. Słucham Cię!"
    
    if "test" in text_lower and len(text_lower) < 20:
        return "Test udany! Wszystko działa. Jestem gotowy do rozmowy."
    
    # Wywołaj LLM
    log("Wywołanie LLM...")
    response = call_llm(text)
    
    if response:
        return response
    else:
        # Fallback gdy LLM nie działa
        return f"Słyszę: {text}"

class VoiceHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass
    
    def do_POST(self):
        client = self.client_address[0]
        if self.path == '/voice':
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length)
            
            try:
                data = json.loads(body)
                log(f"POST from {client}: {list(data.keys())}")
                
                if 'audio' in data:
                    audio_data = base64.b64decode(data['audio'])
                    audio_path = AUDIO_DIR / f"input_{os.urandom(4).hex()}.wav"
                    with open(audio_path, 'wb') as f:
                        f.write(audio_data)
                    
                    log("Transkrypcja Whisper...")
                    text = transcribe_wav2vec(audio_path)
                    log(f"STT: {text}")
                    
                    if text:
                        response = process_message(text)
                    else:
                        response = "Nie zrozumiałem. Powtórz proszę."
                    
                    log(f"Response: {response}")
                    log("Generowanie TTS...")
                    audio = generate_speech(response)
                    log(f"TTS audio: {'OK' if audio else 'FAIL'}")
                    os.remove(audio_path)
                    
                    self._send_json({"text": response, "audio": audio, "mode": "voice"})
                    return
                
                if 'text' in data:
                    response = f"Echo: {data['text']}"
                    audio = generate_speech(response)
                    self._send_json({"text": response, "audio": audio, "mode": "test"})
                    return
                
                self._send_error(400, "Brak audio/text")
                    
            except Exception as e:
                log(f"Error: {e}")
                self._send_error(500, str(e))
        else:
            self._send_error(404, "Not found")
    
    def _send_json(self, data):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())
    
    def _send_error(self, code, msg):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps({"error": msg}).encode())
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

if __name__ == "__main__":
    log("=== Voice Webhook Server (Wav2Vec2 STT) ===")
    
    # Preload model Wav2Vec2 przy starcie żeby było szybciej
    log("Ładowanie modelu Wav2Vec2 (to chwilę trwa)...")
    try:
        cmd = [
            "bash", "-c",
            "KMP_DUPLICATE_LIB_OK=TRUE python3.11 -c \""
            "import torch; "
            "from transformers import Wav2Vec2ForCTC, Wav2Vec2Processor; "
            "print('Pobieranie modelu...'); "
            "processor = Wav2Vec2Processor.from_pretrained('jonatasgr/wav2vec2-large-xlsr-53-polish'); "
            "print('Pobieranie modelu CTC...'); "
            "model = Wav2Vec2ForCTC.from_pretrained('jonatasgr/wav2vec2-large-xlsr-53-polish'); "
            "print('Model gotowy!')\""
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300, env={**os.environ, "KMP_DUPLICATE_LIB_OK": "TRUE"})
        if result.returncode == 0:
            log(f"Wav2Vec2 preload: {result.stdout.strip()}")
        else:
            log(f"Wav2Vec2 preload warning: {result.stderr[:100]}")
    except Exception as e:
        log(f"Wav2Vec2 preload error: {e}")
    
    log("Uruchamianie serwera HTTP...")
    with socketserver.TCPServer(("", PORT), VoiceHandler) as httpd:
        log(f"Serwer na porcie {PORT}")
        httpd.serve_forever()
