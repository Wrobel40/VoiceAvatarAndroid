#!/bin/bash
# ============================================================
# AI Avatar - Backend Setup Script dla Mac Mini (2012, Monterey)
# IP: 192.168.0.177
# ============================================================

echo "🚀 Konfiguracja backendu AI Avatar..."

# --- 1. Sprawdź czy Ollama jest zainstalowana ---
if ! command -v ollama &> /dev/null; then
    echo "📦 Instalowanie Ollama..."
    curl -fsSL https://ollama.com/install.sh | sh
else
    echo "✅ Ollama już zainstalowana: $(ollama --version)"
fi

# --- 2. Uruchom Ollama z dostępem sieciowym ---
echo "🌐 Uruchamiam Ollama na 0.0.0.0:11434..."
export OLLAMA_HOST=0.0.0.0
pkill ollama 2>/dev/null
sleep 1
ollama serve &
OLLAMA_PID=$!
echo "Ollama PID: $OLLAMA_PID"
sleep 3

# --- 3. Pobierz modele ---
echo "⬇️  Pobieram modele (to może chwilę potrwać)..."

# Lekki model dla 8GB RAM
echo "  → llama3.2:3b (lekki, szybki)"
ollama pull llama3.2:3b

# Średni model - jeśli RAM wystarczy
echo "  → llama3.2 (standardowy)"
ollama pull llama3.2

# Polski model (opcjonalnie)
# ollama pull bielik  # jeśli dostępny

# --- 4. Test połączenia ---
echo ""
echo "🧪 Test API..."
curl -s http://localhost:11434/api/tags | python3 -m json.tool | head -20

# --- 5. Firewall - odblokuj port 11434 ---
echo ""
echo "🔥 Konfiguracja firewalla macOS..."
# Na macOS Monterey możesz to zrobić w Preferencjach Systemowych
# Lub przez pfctl:
echo "Uwaga: Sprawdź w System Preferences > Security > Firewall"
echo "Upewnij się że port 11434 jest otwarty dla połączeń przychodzących"
echo ""
echo "Możesz też tymczasowo wyłączyć firewall:"
echo "  sudo /usr/libexec/ApplicationFirewall/socketfilterfw --setglobalstate off"

# --- 6. Test z zewnątrz ---
LOCAL_IP=$(ifconfig en0 | grep "inet " | awk '{print $2}')
echo ""
echo "✅ Setup zakończony!"
echo ""
echo "📱 Konfiguracja aplikacji Android:"
echo "   IP serwera: $LOCAL_IP (lub 192.168.0.177)"
echo "   Port: 11434"
echo "   URL: http://$LOCAL_IP:11434"
echo ""
echo "🧪 Test z telefonu (w sieci WiFi):"
echo "   curl http://$LOCAL_IP:11434/api/tags"
echo ""
echo "📋 Dostępne modele:"
ollama list
