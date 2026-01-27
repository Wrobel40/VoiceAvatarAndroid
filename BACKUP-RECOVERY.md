# 🛡️ BACKUP & RECOVERY - Claude Memory

## Auto-Backup

**Status:** ✅ Aktywny
**Częstotliwość:** Co 6 godzin (cron)
**Lokalizacja:** Git repo w `/home/marcin/clawd/`

## Jak sprawdzić backupy

```bash
cd /home/marcin/clawd
git log --oneline --graph -10
```

## Jak przywrócić starą wersję

### Przywróć cały workspace do punktu w czasie:
```bash
cd /home/marcin/clawd
git log --oneline  # znajdź commit hash
git checkout <hash>  # np. git checkout 1969b02
```

### Przywróć tylko jeden plik:
```bash
git checkout <hash> -- MEMORY.md
```

### Wróć do najnowszej wersji:
```bash
git checkout master
```

## Jak ręcznie zrobić backup

```bash
/home/marcin/backup_claude_memory.sh
```

## Scenariusze ratunkowe

### Scenariusz 1: Claude usunął swoje memory
```bash
cd /home/marcin/clawd
git log --oneline | head -5  # znajdź ostatni dobry commit
git checkout <hash>
```

### Scenariusz 2: Claude zmienił coś ważnego błędnie
```bash
git diff  # zobacz co się zmieniło
git checkout HEAD -- <file>  # przywróć konkretny plik
```

### Scenariusz 3: Chcę zobaczyć historię zmian
```bash
git log -p MEMORY.md  # pokaż wszystkie zmiany w MEMORY.md
```

## Monitoring

**Log backupów:** `/home/marcin/backup.log`
```bash
tail -f /home/marcin/backup.log
```

**Cron status:**
```bash
crontab -l
```

## Notatki

- Backup jest **lokalny** (na RPi) - rozważ remote backup (GitHub?)
- Git trzyma **całą historię** - możesz wrócić do dowolnego momentu
- Auto-backup = bezpieczeństwo przed samouszkodzeniem Claude 😅
