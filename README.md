# Przypomnienia z budzikiem (Android)

Prosta aplikacja na Androida, która pozwala dodawać przypomnienia o określonej dacie i godzinie. Gdy nadchodzi czas — telefon **dzwoni jak budzik** (alarmowy dźwięk + wibracje + pełnoekranowy ekran), zamiast zwykłego cichego powiadomienia. Alarm trzeba ręcznie wyłączyć lub odłożyć (drzemka 5 min).

## Funkcje

- Lista przypomnień, dodawanie i usuwanie.
- Wybór daty i godziny.
- Pełnoekranowy alarm budzący telefon — działa nawet na zablokowanym ekranie.
- Dźwięk alarmu (systemowy ringtone) + wibracje, granie zapętlone.
- Dwa przyciski: **Wyłącz alarm** i **Drzemka 5 min**.
- Foreground service utrzymuje dźwięk dopóki użytkownik nie zareaguje.
- Automatyczne ponowne planowanie alarmów po restarcie telefonu.
- Używa `AlarmManager.setAlarmClock()` — bypassuje Doze i jest traktowany jak prawdziwy budzik.

## Build

Otwórz projekt w Android Studio (Hedgehog+) lub:

```bash
./gradlew :app:assembleDebug
```

APK pojawi się w `app/build/outputs/apk/debug/`.

## Wymagane uprawnienia

Aplikacja przy pierwszym uruchomieniu prosi o:

- `POST_NOTIFICATIONS` (Android 13+) — dla notyfikacji full-screen.
- `SCHEDULE_EXACT_ALARM` (Android 12+) — wysyła do ustawień systemowych.

Bez tych uprawnień alarm wciąż zadziała, ale może być nieprecyzyjny lub bez notyfikacji.

## Struktura

```
app/src/main/java/com/example/reminderalarm/
├── MainActivity.kt          # Lista przypomnień
├── AddReminderActivity.kt   # Dodawanie nowego
├── AlarmActivity.kt         # Pełnoekranowy alarm (dismiss/snooze)
├── AlarmReceiver.kt         # BroadcastReceiver - wyzwalany przez AlarmManager
├── AlarmSoundService.kt     # Foreground service grający dźwięk
├── AlarmScheduler.kt        # Wrapper na AlarmManager
├── BootReceiver.kt          # Re-planowanie po reboocie
├── ReminderStore.kt         # Persistencja (SharedPreferences + JSON)
└── Reminder.kt              # Model
```
