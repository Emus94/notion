# Plan działania — Przypominacz

Audyt aplikacji v1.18 podzielony na 5 zespołów. Lista punktów w kolejności
priorytetu. Status punktów aktualizowany po każdym wdrożeniu.

---

## 🎨 Team 1 — UX/UI

Fokus: projekty, tagi, wyświetlanie listy.

### Co działa
- 3 zakładki (Zaplanowane / Cykliczne / Zakończone) z licznikami
- Karta przypomnienia pokazuje projekt, tagi kolorami, thumbnail zdjęcia
- Edycja przez dotknięcie kafla

### Krytyczne luki
Projekty i tagi **żyją w izolacji** — można je tworzyć, ale nie ma jak
filtrować listy po nich. Cały value-add jest zmarnowany.

### Lista punktów

| Prio | Punkt | Wysiłek | Status |
|---|---|---|---|
| P0 | Kolorowy pasek projektu na lewej krawędzi karty (4dp) | 1h | ✅ |
| P0 | Filter chipsy nad listą — Wszystkie / projekty / tagi | 3h | ✅ |
| P0 | Liczniki w ekranach zarządzania (`Praca • 12 zadań`) | 1h | ✅ |
| P1 | Swipe-to-delete z undo (`ItemTouchHelper` + `Snackbar`) | 2h | ✅ |
| P1 | Swipe-right = oznacz zakończone (bez czekania na alarm) | 1h | ✅ |
| P1 | Materialowe `Chip` dla tagów w kartach + pickerze | 2h | ✅ |
| P2 | Long-press = multi-select + bulk actions | 4h | ✅ |
| P2 | Wyszukiwarka z `SearchView` w toolbar | 2h | ✅ |
| P2 | Puste stany z ilustracją + CTA button | 1h | ✅ |
| P3 | Pola sortowania (data/tytuł/projekt) | 1h | ✅ |

---

## 🎭 Team 2 — Wizualny

Fokus: lekkość, charakter, gotowe kompozycje motywów.

### Diagnoza
Obecny charakter to "techniczny budzik admin-panel". Kolory są ciemne
i nasycone. Funkcjonalne ale surowe — wygląda jak wewnętrzne narzędzie
korporacyjne, nie jak produkt konsumencki 2025.

**Cel:** *ciepły, uważny, codzienny* — jak notatnik na nocnej szafce.

### 6 nowych kompozycji motywów

| Nazwa | Charakter | Primary | Accent | Dla kogo |
|---|---|---|---|---|
| **Mgła** | Sennie, miękko, introwertycznie | `#B4A7D6` | `#E8B4BC` | Medytacja, poranne rutyny |
| **Papier** | Analogowo, minimalizm | `#D4A574` | `#C84B31` | Moleskine fans, vintage |
| **Świt** | Ciepło, optymistycznie | `#FFAD8F` | `#FF5E7A` | Poranne check-listy |
| **Noc miejska** | Dark mode z charakterem | `#1A1F3A` | `#00E5FF` | Night owls, neon vibes |
| **Las** | Naturalnie, zdrowo | `#5B8C5A` | `#D4A574` | Aktywni, ogrodnicy |
| **Ocean** | Profesjonalnie, chłodno | `#4FC3F7` | `#FF6F61` | Biuro, klasyka |

### Pozostałe zmiany wizualne

| Prio | Punkt | Wysiłek | Status |
|---|---|---|---|
| P0 | 3 nowe palety lekkie: Mgła, Papier, Świt | 1h | ✅ |
| P0 | Naprawić hardcoded `#888` → `textColorSecondary` | 5 min | ✅ |
| P0 | Card corner radius 12dp → 16dp + padding 14dp → 18dp | 15 min | ✅ |
| P1 | Typografia Inter (free) zamiast systemowej | 30 min | ✅ (system sans-serif-medium) |
| P1 | Delete button 40dp → 48dp (tap target) | 2 min | ✅ |
| P2 | Gradient toolbar (`primary → primary+8%`) | 30 min | ✅ |
| P2 | Material You / Dynamic Colors (Android 12+) | 1h | ⏳ |
| P2 | FAB micro-animation (scale down on tap) | 20 min | ✅ |
| P3 | Empty states z wektorowymi ilustracjami | 2h | ✅ |
| P3 | Pozostałe 3 palety: Noc miejska, Las, Ocean | 1h | ✅ |

---

## 🚀 Team 3 — Dodatkowe funkcje

### Szybkie wygrane (1-3 dni każda)

| Punkt | Uzasadnienie | Status |
|---|---|---|
| **Priorytety** (niski/normalny/wysoki/pilny) | Quick win, wielki impact wizualny | ✅ |
| **Dashboard widget "Dziś"** | 3 najbliższe przypomnienia z dzisiaj na ekranie głównym | ✅ |
| **Export/Import JSON** | Zwiększa zaufanie, umożliwia backup | ✅ |
| **Streak counter** | Gamifikacja, retencja | ✅ |
| **Statystyki tygodnia** | "6 zakończonych, 2 pominięte w tym tygodniu" | ✅ |
| **Szablony** | "Wyrzuć śmieci" / "Weź lek" bez żmudnego wpisywania | ✅ |
| **Snooze przez shake** | Potrząśnij telefonem = drzemka 5 min | ✅ |
| **Share target** (Android) | Udostępnij tekst / zdjęcie z innej aplikacji | ✅ |
| **Fraza auto-zapisu** (konfigurowalna) | Wpisujesz magiczną frazę → od razu zapis | ✅ |
| **Auto-backup + zewn. folder (SAF / Dysk)** | Kopia przetrwa odinstalowanie | ✅ |

### Średnie inwestycje (1-2 tyg. każda)

| Punkt | Uzasadnienie | Status |
|---|---|---|
| **Lokalizacyjne przypomnienia** | Killer feature, geofencing | ✅ |
| **Własny recurrence** | "Co 3 dni", "1. pon miesiąca", "weekdays only" | ✅ (co N dni) |
| **Voice input** | "Hej Przypominacz, dentysta jutro o 10" | ✅ |
| **Subtaski / checklist mode** | "Zakupy" z listą produktów | ⏳ |
| **Google Calendar import** | Read-only sync ze zdarzeń | ⏳ |
| **Backup do Google Drive** | Codzienny auto-backup | ✅ (przez SAF) |
| **Nowy picker kolorów** | Gotowe palety + ulubione + harmonia | ✅ |

### Ambitne (>2 tyg.)

| Punkt | Uzasadnienie |
|---|---|
| **AI suggestions** | "Zauważyłam, że co wtorek dodajesz Siłownia. Zrobić cyklicznym?" |
| **Shared reminders** | Współdzielone zakupy przez link/QR |
| **Habit tracker mode** | Nawyki z heatmapą 30 dni |
| **Wear OS companion** | Następne 3 przypomnienia na zegarku |
| **Focus mode** | Pomodoro 25 min |

### Top 3 dla tożsamości aplikacji
1. **Priorytety** — najtańsze, największy visual impact
2. **Dashboard widget "Dziś"** — alarm app = użytkownik ma widget na homescreenie
3. **Lokalizacyjne przypomnienia** — prawdziwy differentiator

---

## 🔍 Team 4 — Upierdliwi poszukiwacze błędów

### Błędy wizualne / kolorystyczne

1. `item_reminder.xml:63` — hardcoded `#888` dla notatek, **niewidoczne w dark mode**
2. `item_reminder.xml:81` — hardcoded `#888` dla statusu, ten sam problem
3. `item_reminder.xml:90` — `@android:drawable/ic_menu_delete` system icon, złamany kontrast na paletach o niskiej luminancji
4. `activity_alarm.xml:79` — hardcoded `#E63946` + białe tekst, potem nadpisywane dynamicznie w Kotlin — redundantne
5. `widget_add.xml` — `#1D3557` hardcoded, widget pokazuje Granat przy pierwszym render
6. Paleta "Błękit" — accent `#FFB703` (żółty) = przycisk Zapisz jaskrawo żółty, słaba czytelność
7. `TextInputLayout` w formularzu — nie override'ujemy kolorów, podkreślenie focused nie zmienia koloru z paletą
8. `DatePickerDialog` / `TimePickerDialog` — używa systemowego motywu, ignoruje paletę
9. `PopupMenu` z toolbara — nie podąża za paletą

### Błędy funkcjonalne

10. `ReminderAdapter.onBindViewHolder` — bitmap loading na main thread, jank na scrollu
11. `ReminderAdapter.submit` — `notifyDataSetChanged()` zamiast DiffUtil — flicker
12. `MainActivity.refresh` — liczniki stale gdy alarm cyklicznego odpali się w tle
13. `AddReminderActivity` auto-focus + keyboard — race z ScrollView, klawiatura może przykryć Zapisz
14. `ImageStorage` — brak cleanup przy usunięciu reminder (leak plików obrazów)
15. `AlarmReceiver.startActivity` — silently swallows failures (brak logów)
16. `showCustomSnoozeDialog` — brak walidacji max minut
17. `NaturalDateParser` — brak "za chwilę", "wieczorem", "rano", "kwadrans"
18. `AlarmActivity` preview mode nie pokazuje obrazka tła
19. Brak walidacji unikalności nazw projektów/tagów
20. Widget nie odświeża się po dodaniu/usunięciu przypomnienia

### Accessibility

21. `contentDescription` — kilka ImageView używa labelu formularza zamiast opisu
22. Tap targets — `btnDelete` 40dp, WCAG wymaga 48dp
23. Tekst `#888` — kontrast 3.5:1 w light mode, poniżej WCAG AA (4.5:1)
24. Paddings w dp zamiast sp — zachodzenie tekstu przy accessibility font scaling

### Priorytety fix'ów

| Prio | Fix | Wysiłek | Status |
|---|---|---|---|
| P0 | `#888` → `textColorSecondary` | 2 min | ✅ |
| P0 | ImageStorage cleanup przy delete | 5 min | ✅ |
| P0 | Snooze max 10080 min validation | 5 min | ✅ |
| P0 | Tap target 48dp | 2 min | ⏳ |
| P1 | Widget initial bg → `colorPrimary` attr | 2 min | ✅ |
| P1 | DiffUtil w ReminderAdapter | 30 min | ✅ |
| P1 | Unikalność nazw projektów/tagów | 10 min | ✅ |
| P2 | Async image loading + cache | 2h | ✅ |
| P2 | NaturalDateParser rozszerzenie | 1h | ✅ |
| P2 | DatePicker/TimePicker z custom motywem | 30 min | ✅ (Material* pickers) |

---

## 💼 Team 5 — Biznesowy

### Analiza rynku
- **Konkurencja direct**: Todoist (€36/rok), TickTick (€27/rok), Google Keep/Tasks (free)
- **Twoja nisza**: "to-do z prawdziwym budzikiem" — żadna z topowych to-do nie forsuje full-screen alarmu, żadna alarm-apka nie ma projektów/tagów/zdjęć
- **Grupa docelowa**: ADHD community, rodzice, seniorzy, zapominalscy, polski rynek first

### Rekomendowany model: Freemium + one-time IAP

**Free:**
- Wszystkie podstawowe przypomnienia
- 2 palety (Granat, Świt)
- 1 projekt, 5 tagów
- Systemowy dzwonek + głośność
- Widget, pełnoekranowy alarm, drzemka

**Premium (€4.99 one-time):**
- Wszystkie motywy (11 palet)
- Custom kolory motywu i alarmu
- Unlimited projekty + tagi
- Zdjęcia w przypomnieniach
- Własne dźwięki alarmu
- Układ ekranu alarmu
- Export/Import
- Lokalizacyjne przypomnienia (po dodaniu)
- Priorytety (po dodaniu)

### Plan krok po kroku

#### Faza 1 — Przygotowanie (3-4 tygodnie)

**Tydzień 1 — Polish product**
1. Wdroż fixy z team'u 4 (P0-P1)
2. Dodaj 3 nowe palety lekkie
3. Dodaj filter chipsy
4. Dodaj priorytety

**Tydzień 2 — Identity + assets**
5. Wykup `przypominacz.pl`
6. Logo warianty
7. Screenshoty do Play Store (2-8 sztuk, USP = pełnoekranowy alarm)
8. Nagranie promo 30s
9. Opis do Play Store (PL + EN, optimized keywords)
10. Polityka prywatności (GitHub Pages)

**Tydzień 3 — Infrastruktura**
11. Google Play Developer account (€25 jednorazowo)
12. Content rating, data safety
13. Internal testing (5-10 osób)
14. Closed beta (20-30 osób z r/Polska, r/androidapps)

**Tydzień 4 — Soft launch**
15. Open Testing na Play Store
16. Post na Reddit r/androidapps, r/Polska, Wykop.pl, X, Mastodon

#### Faza 2 — Premiera produkcyjna (1 tydzień)
17. Production, cena €4.99 (launch discount €2.99 w 1 tyg)
18. Zgłoszenia: Android.com.pl, Tabletowo, Komputer Świat, Chip.pl, AndroidWorld, Product Hunt

#### Faza 3 — Wzrost (miesiące 2-6)
19. ASO iteracja co 2 tyg.
20. Odpowiadanie na recenzje codziennie
21. Release'y co 2 tyg.
22. Content marketing (Medium)
23. Influencer reach (3-5 YouTuberów)
24. Mała kampania płatna (€50-100)

### Projekcje
**Conservative:** ~€3500-4500/rok 1
**Optimistic** (viral hit): €15,000-25,000/rok 1

### Koszty startowe
- Developer account: €25
- Domena: €12/rok
- Assets (DIY): €0
- **Suma: €40-150**

### Komunikat główny
> **"Prawdziwy budzik, nie kolejne ciche powiadomienie."**

### Red flags
- ❌ Subskrypcja (zniszczy trust)
- ❌ Reklamy (natychmiast 1-star)
- ❌ Pay-to-unlock podstawowego alarmu
- ❌ Cloud sync na start (backend liability)
- ❌ Kopiowanie Todoista (utrata differentiatora)

---

## 📋 Top 10 akcji w kolejności priorytetu

Jeśli masz ograniczony czas, weź te:

1. **[BUGS P0]** `#888` → `textColorSecondary` — 2 min, krytyczne w dark mode
2. **[UX P0]** Kolorowy pasek projektu na lewej krawędzi karty — 1h
3. **[UX P0]** Filter chipsy nad listą — 3h, odblokowuje value projektów/tagów
4. **[BUGS P0]** ImageStorage cleanup przy delete — 5 min
5. **[WIZUALNY]** 3 palety lekkie (Mgła, Papier, Świt) — 1h
6. **[UX P1]** Swipe-to-complete + swipe-to-delete z undo — 2h
7. **[FEATURES]** Priorytety przypomnień (4 poziomy) — 3h
8. **[BUGS P1]** DiffUtil w ReminderAdapter — 30 min
9. **[WIZUALNY]** Card radius 16dp + paddingi — 20 min
10. **[BIZNES]** Screenshoty + Play Store listing — 1 dzień

---

## Legenda statusów

- ⏳ Planowane
- 🚧 W trakcie
- ✅ Zakończone
- ❌ Odrzucone / niepotrzebne
