# GLB Test - Minimalna aplikacja testowa

Prosta aplikacja Android do testowania plików GLB z Filament.

## Co to robi?
- Tylko ładuje plik GLB
- Renderuje w Filament z podstawowym oświetleniem
- Pokazuje status na ekranie
- Bez UI, bez głosu, bez czatu - minimalna apka

## Jak używać?
1. Zbuduj i zainstaluj APK
2. Kliknij przycisk "📁 Wczytaj GLB"
3. Wybierz swój plik GLB
4. Zobacz czy model się wyświetla

## Jeśli widzisz czarny ekran:
- Sprawdź logi w Logcat (tag: `GLB-TEST`)
- Model może wymagać tekstur
- Może być problem z materiałami PBR
- Emulator może nie wspierać wszystkich funkcji

## Struktura
```
app/src/main/java/com/glbtest/app/
└── MainActivity.kt    # Główna aktywność (tylko ładowanie GLB)
```

## Zależności
- Filament 1.54.5
- Android SDK 34
- Min SDK 26
