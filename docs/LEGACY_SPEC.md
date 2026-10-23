# ماراثون القراءة (Reading Marathon) — Product & Domain Spec extracted from code

Source: `/home/user/Reading-Tracker-Dashboard-Cloud` (Streamlit + Firestore + Google Sheets/Forms).
All files read in full: `app.py`, `main.py`, `db_manager.py`, `auth_manager.py`, `utils.py`, `pdf_reporter.py`, `chart_generator.py`, `style_manager.py`, `firebase_config.py`, `pages/1_📈_لوحة_التحكم_العامة.py` (abbrev. **P1**), `pages/2_🎯_تحليلات_التحديات.py` (**P2**), `pages/3_⚙️_الإدارة_والإعدادات.py` (**P3**), `pages/4_❓_عن_التطبيق.py` (**P4**), `README.md`, plus `pipeline.html`, `requirements.txt`.

Note on file names: `utils.py` actually holds the Plotly chart theme (`apply_chart_theme`, `CHART_COLORS`); `chart_generator.py` holds the chart builders; `style_manager.py` only injects sidebar CSS. No code uses a "pages read" metric: **all reading is measured in minutes.** Nothing stores streaks or badges; badges are computed when a page is viewed.

---

## 1. Users & roles, onboarding, how members submit

### 1.1 Roles
| Role | Who | Has an account? | What they do |
|---|---|---|---|
| **Organiser / supervisor (المشرف)** | The person who signs in with Google | Yes. Google OAuth; Firestore doc `users/{googleUserId}` | Everything in the app: set up, members, challenges, rules, sync, log editor, reports, delete account |
| **Member / reader (العضو / القارئ / المشارك)** | Group participants | **No.** A member is only a `name` string in `members` | Fills the Google Form. The member is picked from the dropdown list of names (`اسمك`). Members never open the app |

- P4:142: "هذا التطبيق مصمم خصيصاً **لمشرفي المجموعات القرائية**. أنت، كمشرف، الشخص الوحيد الذي يحتاج إلى استخدام هذه المنصة..."
- One Google account = one workspace = one group. There is no group entity, no co-admins and no sharing. All data lives under `users/{user_id}` (db_manager.py:5-26).
- Identity: `user_id = userinfo.id` (Google numeric id), with `user_email` (auth_manager.py:99-102).

### 1.2 Google OAuth
- Scopes (auth_manager.py:14-21):
  - `https://www.googleapis.com/auth/spreadsheets`
  - `https://www.googleapis.com/auth/drive.file`
  - `https://www.googleapis.com/auth/forms.body`
  - `openid`, `.../userinfo.profile`, `.../userinfo.email`
- Web flow with fixed `redirect_uri="https://reading-marathon.streamlit.app"` (auth_manager.py:33), `access_type='offline', prompt='consent'` (auth_manager.py:131).
- A refresh token is required. If it is missing, the app shows an error and tells the user to revoke the app at myaccount.google.com/permissions (auth_manager.py:93-97).
- First login runs `create_new_user_workspace` (db_manager.py:43-77), which writes the default settings and rules. The refresh token is saved to `settings/config.refresh_token` (auth_manager.py:107; db_manager.py:283-294).
- Page-refresh persistence: `?user_id=<id>` is put in the URL. On reload, credentials are rebuilt from the refresh token stored for that id (auth_manager.py:117-127, 36-58). **This is a security hole; see §11.**
- Login screen copy: "🚀 أهلاً بك في \"ماراثون القراءة\"", "للبدء، يرجى ربط حسابك في جوجل.", button "🔗 **الربط بحساب جوجل والبدء**" (auth_manager.py:132-134).
- Logout clears the session and query params (auth_manager.py:147-163). `revoke_google_token` exists (auth_manager.py:166-179) but its call in account deletion is commented out (P3:690-696).

### 1.3 Setup wizard (app.py:92-242)
Setup counts as complete when `spreadsheet_url` and `form_url` are set **and** there is at least one member **and** at least one period (app.py:98-103). Until then the wizard shows the first missing step:

1. **"الخطوة 1: إضافة أعضاء فريقك"** (app.py:112-127). A text area takes one name per line (placeholder `خالد\nسارة\n...`). Each name is stripped and empty lines are dropped. Calls `db.add_members` → `{name, is_active: True}`.
2. **"الخطوة 2: إنشاء أدوات جوجل"** (app.py:130-212)
   - Default title is `بيانات ماراثون القراءة - {email local-part}` and can be edited (app.py:133-135).
   - `gc.create(title)` creates the Sheet, then `spreadsheet_url` is saved (app.py:140-141).
   - The Forms API `forms().create` creates the Form with `info.title` and `documentTitle` both set to the title (app.py:146-148).
   - `batchUpdate` then adds the description and 6 questions (app.py:150-158):

| idx | Title (exact) | Type | Required | Options |
|---|---|---|---|---|
| — | description: "يرجى ملء هذا النموذج يومياً لتسجيل نشاطك في تحدي القراءة. بالتوفيق!" | | | |
| 0 | `اسمك` | choiceQuestion DROP_DOWN | **yes** | member names in insertion order |
| 1 | `تاريخ القراءة` | dateQuestion (`includeTime:false, includeYear:true`) | **yes** | |
| 2 | `مدة قراءة الكتاب المشترك (اختياري)` | timeQuestion `duration:true` | no | |
| 3 | `مدة قراءة كتاب آخر (اختياري)` | timeQuestion `duration:true` | no | |
| 4 | `ما هي الاقتباسات التي أرسلتها اليوم؟ (اختياري)` | CHECKBOX | no | `أرسلت اقتباساً من الكتاب المشترك`, `أرسلت اقتباساً من كتاب آخر` |
| 5 | `إنجازات الكتب والنقاش (اختر فقط عند حدوثه لأول مرة)` | CHECKBOX | no | `أنهيت الكتاب المشترك`, `أنهيت كتاباً آخر`, `حضرت جلسة النقاش` |

   - The item id of the name question comes from `update_result['replies'][1]['createItem']['itemId']` and is saved as `member_question_id`. `form_id` and `form_url` (= `responderUri`) are saved too (app.py:162-165).
   - Manual linking steps shown to the organiser once (app.py:169-179):
     1. Open `https://docs.google.com/forms/d/{form_id}/edit`.
     2. Go to Responses → "Link to Sheets" → "Select existing spreadsheet".
     3. Rename the new response tab to exactly **`Form Responses 1`**.
     4. In the Sheet, set File > Settings > Locale to **United Kingdom** so dates are DD/MM/YYYY.
   - A "تحقق من الإعدادات وتابع" button would delete the default `Sheet1` (app.py:181-198). It never works; see §11.
   - If the error contains `invalid_grant`/`revoked`, a re-consent help message is shown (app.py:202-207).
3. **"الخطوة 3: إنشاء أول تحدي لك"** (app.py:216-242). The form asks for book title, author, publication year (default this year), start date (default today) and end date (default today+30). The challenge gets the **global default rules**. Title and author are required. **Dates are not validated here.**

After setup, the home page shows 4 navigation cards (app.py:295-333): لوحة التحكم العامة / تحليلات التحديات / الإدارة والإعدادات / عن التطبيق.

### 1.4 How members submit
- Members only use the public Form link (`form_url`). The organiser copies it from Admin → "🔗 الروابط والأدوات" (P3:371-378).
- There is one form row per submission. The member chooses the **reading date** (`تاريخ القراءة`), so backdating is allowed by design (P4:258-259: a member who forgot a day picks that day's date).
- Durations are Google Forms duration answers (H:MM:SS).
- Quotes are a per-day yes/no per book type. Achievements are ticked "only when they happen for the first time".
- Several submissions for the same day are allowed and are all summed.

---

## 2. Firestore data model

Root: `users/{user_id}` (db_manager.py:5-26). Every read via `get_subcollection_as_df` adds a synthetic `{collection}_id` = doc id, e.g. `members_id`, `periods_id`, `books_id`, `logs_id`, `achievements_id`, `member_stats_id` (db_manager.py:114-125).

### `users/{user_id}` (doc)
| field | type | source |
|---|---|---|
| `email` | string | db_manager.py:54-57 |
| `created_at` | timestamp (`pd.Timestamp.now()`) | db_manager.py:56 |

### `users/{uid}/settings/config` (single doc)
| field | type | notes |
|---|---|---|
| `spreadsheet_url` | string | '' at creation |
| `form_url` | string | responderUri |
| `form_id` | string | |
| `member_question_id` | string | Forms itemId of `اسمك` |
| `refresh_token` | string or null | **plaintext** OAuth refresh token |
(db_manager.py:60-66)

### `users/{uid}/global_rules/rules` (single doc) — default points system
| field | type | default |
|---|---|---|
| `minutes_per_point_common` | int | **10** |
| `minutes_per_point_other` | int | **5** |
| `finish_common_book_points` | int | **50** |
| `finish_other_book_points` | int | **25** |
| `quote_common_book_points` | int | **3** |
| `quote_other_book_points` | int | **1** |
| `attend_discussion_points` | int | **25** |
(db_manager.py:69-77; also the UI fallback defaults in P3:351-357)

### `users/{uid}/members/{auto-id}`
| field | type |
|---|---|
| `name` | string (this is the join key with the Sheet) |
| `is_active` | bool (True on create) |
(db_manager.py:162-168, 170-176)

### `users/{uid}/books/{auto-id}`
| field | type |
|---|---|
| `title` | string (unique per workspace; duplicates are rejected) |
| `author` | string |
| `publication_year` | int |
(db_manager.py:184-194)

### `users/{uid}/periods/{auto-id}` — a **challenge** (التحدي)
| field | type | notes |
|---|---|---|
| `start_date` | string `YYYY-MM-DD` | inclusive |
| `end_date` | string `YYYY-MM-DD` | inclusive |
| `common_book_id` | string → books | |
| the 7 rule fields above | int | **a snapshot** copied from the default rules or custom values when the challenge is created |
(db_manager.py:197-199)

When loaded, periods are left-joined with books, and the book fields are renamed to `book_title`, `book_author`, `book_year` (db_manager.py:138-141; P3:177-179).

### `users/{uid}/logs/{auto-id}` — one per form row (rebuilt on every sync)
| field | type | notes |
|---|---|---|
| `timestamp` | string | raw form `Timestamp` |
| `member_id` | string → members | |
| `submission_date` | string **`DD/MM/YYYY`** | the reading date |
| `common_book_minutes` | int | |
| `other_book_minutes` | int | |
| `submitted_common_quote` | int 0/1 | |
| `submitted_other_quote` | int 0/1 | |
(main.py:109-115)

### `users/{uid}/achievements/{auto-id}` (rebuilt on every sync)
| field | type | notes |
|---|---|---|
| `member_id` | string | |
| `achievement_type` | enum `FINISHED_COMMON_BOOK` \| `ATTENDED_DISCUSSION` \| `FINISHED_OTHER_BOOK` | |
| `achievement_date` | string **`YYYY-MM-DD`** | = reading date of the row, not the timestamp |
| `period_id` | string → periods | |
| `book_id` | string or null | common book id for FINISHED_COMMON_BOOK, else null |
(main.py:123-128)

### `users/{uid}/member_stats/{member_id}` (derived cache, fully rebuilt)
| field | type |
|---|---|
| `total_points` | int |
| `total_reading_minutes_common` | int |
| `total_reading_minutes_other` | int |
| `total_common_books_read` | int (count of FINISHED_COMMON_BOOK) |
| `total_other_books_read` | int (count of FINISHED_OTHER_BOOK) |
| `total_quotes_submitted` | int (common + other quote flags) |
| `meetings_attended` | int (count of ATTENDED_DISCUSSION) |
| `last_log_date` | string `YYYY-MM-DD` or null (not used in the UI) |
| `last_quote_date` | string `YYYY-MM-DD` or null (not used in the UI) |
(main.py:156-161, 195-208; db_manager.py:236-248)

These collections do **not** exist: groups, challenges-as-separate-from-periods, badges, streaks, notifications, comments, quote texts.

Three different date formats are stored: periods `YYYY-MM-DD`, logs `DD/MM/YYYY`, achievements `YYYY-MM-DD`.

---

## 3. Challenge model

- **A challenge (تحدي) = a `period`.** It has an inclusive date range `[start_date, end_date]`, **exactly one common book** (الكتاب المشترك), and its own snapshot of the 7 scoring rules.
- **Common book vs free reading:**
  - Every log has two independent minute fields. `common_book_minutes` is reading in the challenge's common book. `other_book_minutes` is "كتاب آخر", meaning **any other book**.
  - Other books are never recorded by title. They are anonymous, and "finished another book" is only a counter.
  - Members can do either or both (P4:184).
- **Status**, computed against `date.today()` (P2:322-327; P3:250-261):
  - `start > today` → future: "(المقبل) ⏳" / "التحديات المقبلة"
  - `end < today` → past: "(السابق) 🏁" / "التحديات المنتهية"
  - otherwise → active: "(الحالي) 🟢" / "التحدي النشط حالياً"
- **Mapping logs to a challenge is by date only.** A log belongs to the **first** period with `start ≤ reading_date ≤ end` (main.py:119, 171; P2:141). Logs dated outside every period are stored and count toward minutes and hours, but **earn 0 points**. Achievements submitted on a reading date outside every period are **dropped**.
- **No overlap rule** (P3:596-603). A new challenge's start must be after the latest `end_date` among all periods. The suggested start is `max(end_date) + 1 day` and the suggested end is `start + 30 days`. Validations, in order:
  1. `new_start <= last_end_date` → "⛔ التواريخ متداخلة..."
  2. Title and author are required.
  3. `new_start >= new_end` → error (so the end must be strictly after the start).
  
  As a result, challenges can only be appended at the end of the timeline. Gaps between challenges are allowed. The first challenge created in the setup wizard is not validated at all.
- **Default vs custom rules** (P3:588-652). Creating a challenge has two steps:
  1. Book and dates.
  2. "📈 استخدام النظام الافتراضي" copies `global_rules/rules`, or "🛠️ تخصيص القوانين لهذا التحدي" opens a form pre-filled with the defaults. All 7 fields are ints with `min_value=0`.

  The rules are copied into the period doc, so later edits to the default rules **do not affect existing challenges**. P3:346 says: "هذه هي القوانين الافتراضية التي سيتم تطبيقها على أي تحدي جديد تقوم بإنشائه." Rules cannot be edited after a challenge is created (there is only a read-only "ℹ️ عرض نظام النقاط" dialog, P3:559-568).
- **Book uniqueness:** `add_book_and_challenge` rejects a title that already exists in `books`: "خطأ: كتاب بعنوان '...' موجود بالفعل" (db_manager.py:184-187). The same book therefore cannot be reused for a second challenge.
- **Deleting a challenge** (db_manager.py:250-279; P3:281, 551-586):
  - Only past or future challenges can be deleted. The delete button of the active challenge is `disabled`.
  - The user must type the exact phrase `أوافق على حذف {book_title}`.
  - Deletion removes all achievements with that `period_id`, then the period, then the book if no other period references it.
  - Logs are **not** deleted because they are date-based. They stop earning points after the next stats rebuild. **Note:** `delete_challenge` does not recompute `member_stats`, so totals are stale until the next sync.
- **Winner reward** (product rule, not code): the top scorer of a challenge picks the next common book (P4:193).

---

## 4. Scoring algorithm (exact)

Implemented in `main.calculate_and_update_stats` (main.py:133-212) and duplicated in P2:
- `calculate_detailed_points_sources` (P2:126-165) for the all-time per-source breakdown
- per-challenge "podium" points (P2:383-421)

### 4.1 Inputs per log (from the sheet, main.py:65-71, 105-115)
- `cm` = `common_book_minutes`
- `om` = `other_book_minutes`
- Duration parsing: split on `:` into ints `[h, m, s]` → **`h*60 + m`**. Seconds are discarded. A value that is not a string, or that has a non-int part, → 0.
- `cq` = 1 if the quote answer contains the substring `'الكتاب المشترك'`, else 0. `oq` = 1 if it contains `'كتاب آخر'`, else 0. The value is **at most one quote per type per log row.**

### 4.2 Per-log points (rules R taken from the period that contains the log's reading date)
```
if R.minutes_per_point_common > 0:  pts += cm // R.minutes_per_point_common      # floor, PER LOG
if R.minutes_per_point_other  > 0:  pts += om // R.minutes_per_point_other       # floor, PER LOG
pts += cq * R.quote_common_book_points
pts += oq * R.quote_other_book_points
```
(main.py:173-180). If no period contains the date, the log earns **0 points**. A `minutes_per_point_* = 0` setting turns minute points off; it does not cause a division by zero.

Floor division is applied **to each log row separately**, so leftover minutes are lost on every log. For example, 3 logs of 9 common minutes at 10 min/pt give 0 points, while one log of 27 minutes gives 2.

### 4.3 Achievement points (rules R taken from `periods[achievement.period_id]`)
```
FINISHED_COMMON_BOOK  -> + R.finish_common_book_points   (default 50)
ATTENDED_DISCUSSION   -> + R.attend_discussion_points    (default 25)
FINISHED_OTHER_BOOK   -> + R.finish_other_book_points    (default 25)
```
(main.py:182-193). An achievement whose `period_id` no longer exists earns nothing.

### 4.4 Achievement creation and dedup rules (main.py:117-128)
Achievements are only created if the row's reading date falls inside a period `P`:
- `'أنهيت الكتاب المشترك'` in the answer, and the member has no `FINISHED_COMMON_BOOK` for P yet → create it with `book_id = P.common_book_id`. **At most 1 per member per challenge.**
- `'حضرت جلسة النقاش'`, and the member has no `ATTENDED_DISCUSSION` for P yet → create it. **At most 1 per member per challenge.**
- `'أنهيت كتاباً آخر'` → create `FINISHED_OTHER_BOOK` **every time it is ticked, with no limit**.

Rows are processed sorted by `Timestamp` (main.py:81), so the earliest submission wins for the deduplicated types. The dedup check is a live Firestore query (`has_achievement`, db_manager.py:150-158) against achievements written earlier in the same sync.

### 4.5 Total
```
total_points(member) =
   Σ over logs L of member with period P(L.date) exists:
        [P.mpc>0] * floor(L.cm / P.mpc) + [P.mpo>0] * floor(L.om / P.mpo)
      + L.cq * P.qc + L.oq * P.qo
 + Σ over achievements A of member with A.period_id in periods:
        points_for(A.type, periods[A.period_id])
```
**Default values:**

| Rule | Default |
|---|---|
| Common book minutes per point | 10 min/pt |
| Other book minutes per point | 5 min/pt |
| Common-book quote | +3 |
| Other-book quote | +1 |
| Finish common book | +50 |
| Finish other book | +25 |
| Attend discussion | +25 |

Other-book reading earns points twice as fast on purpose, to keep members who don't like the common book reading; finishing the common book and quotes from it are worth more (P4:183-191).

**Not in the scoring:** pages, daily caps, streak bonuses, badge points, late penalties, and any decay. Badges give **no points**.

**Worked example (defaults):** one log of common 0:45:00, other 0:22:00, both quotes ticked → 45//10 = 4, 22//5 = 4, +3, +1 = **12 pts**. Finishing the common book and attending the discussion in the same challenge adds 50 + 25 → 87.

### 4.6 Per-challenge points (P2:383-421)
- Participants are the members with ≥1 log dated inside `[start, end]`.
- Points are computed on the member's **summed** minutes in that window. **floor(Σcm / mpc)** is applied once, **not per log**, so it differs from the all-time engine.
- Quotes use the summed counts. Achievements are the ones whose `period_id` = the selected challenge. All use the selected challenge's rules.
- Members whose only activity is an achievement (no logs in range) are left out.

### 4.7 Points-source breakdown (reader card, P2:126-165)
Uses the same per-log floor as main.py. All-time totals are split into 7 buckets:
- قراءة الكتاب المشترك
- قراءة كتب أخرى
- اقتباسات (الكتاب المشترك)
- اقتباسات (كتب أخرى)
- إنهاء الكتاب المشترك
- إنهاء كتب أخرى
- حضور النقاش

### 4.8 Derived member stats (main.py:195-208)
- `total_reading_minutes_common/other` = sums over **all** logs, including logs outside any period.
- `total_quotes_submitted` = Σ(cq + oq).
- `last_log_date` = max date. `last_quote_date` = max date with any quote.
- The book and meeting counts are counts of achievements by type.

---

## 5. Badges / achievements

### 5.1 Recorded achievements (Firestore, give points)
| type | Form option | Limit | Points field (default) |
|---|---|---|---|
| `FINISHED_COMMON_BOOK` | أنهيت الكتاب المشترك | 1 per member per challenge | `finish_common_book_points` (50) |
| `ATTENDED_DISCUSSION` | حضرت جلسة النقاش | 1 per member per challenge | `attend_discussion_points` (25) |
| `FINISHED_OTHER_BOOK` | أنهيت كتاباً آخر | unlimited | `finish_other_book_points` (25) |

### 5.2 Badges (الأوسمة والشارات): computed on the fly in the reader card, 0 points (P2:668-700)
All three are evaluated on **all-time** data for the member.

| Icon | Arabic text (exact) | Exact condition |
|---|---|---|
| ✍️ | `وسام الفيلسوف: إرسال أكثر من 10 اقتباسات.` | `member_stats.total_quotes_submitted > 10` (strictly, so 11 or more) (P2:670-671) |
| 🏃‍♂️ | `وسام العدّاء: إنهاء كتاب في الأسبوع الأول.` | Any `FINISHED_COMMON_BOOK` with `(achievement_date − that period's start_date).days <= 7`, i.e. reading-date offset 0..7, which is 8 calendar days. Awarded once (`break`) (P2:673-681) |
| 💯 | `وسام المثابرة: القراءة لـ {max_streak} أيام متتالية.` | Take the sorted unique log dates (a log counts even with 0 minutes). Require ≥7 unique dates, then compute the longest run of consecutive calendar days. Award if `max_streak >= 7`. The text shows the actual streak length (P2:683-694) |

- Empty state: "لا توجد أوسمة بعد. استمر في القراءة لفتحها!"
- P4:207-209 describes them as: Philosopher ">10 quotes"; Runner "finishes the common book within 7 days or less of the challenge start"; Persistence "7+ consecutive days".

### 5.3 Hall of Fame titles (لوحة شرف الأبطال): leaderboard titles on the dashboard, 0 points (P1:455-517)
Ties: every member with the max value is listed, joined by ", ". If the max is 0 or NaN, the title shows "لا يوجد".

| Title | Metric | Value label |
|---|---|---|
| 🧠 العقل المدبّر | max `member_stats.total_points` | `{int} نقطة` |
| ⏳ سيد الساعات | max (common + other minutes) | `{h:.1f} ساعة` |
| 📚 الديدان القارئ | max (common + other books finished) | `{int} كتب` |
| 💎 صائد الدرر | max Σ quotes (from logs) | `{int} اقتباساً` |
| 🏃‍♂️ صاحب النَفَس الطويل | max # distinct reading dates | `{int} يوم قراءة` |
| ⚡ العدّاء السريع | max minutes summed on one calendar day | `{h:.1f} ساعة في يوم` |
| ⭐ نجم الأسبوع | max minutes in one week, `freq='W-SAT'` (weeks end Saturday, i.e. Sun–Sat) | `{h:.1f} ساعة في أسبوع` |
| 💪 عملاق الشهر | max minutes in one calendar month (`freq='ME'`) | `{h:.1f} ساعة في شهر` |

---

## 6. Dashboards / analytics

### 6.1 Global dashboard — "📈 لوحة التحكم العامة" (P1)
Data is cached with `ttl=300` s (P1:319).

**A. News ticker "📰 آخر أخبار الماراثون (آخر 7 أيام)"** (P1:165-315, 352-369)
- If there are no logs, or the first log is less than 7 days old → "أهلاً بكم في ماراثون القراءة! نتطلع لرؤية إنجازاتكم."
- Otherwise it computes the hero winners as of today and as of today−7 (`get_heroes_at_date`) and compares the two sets of names for each title:
  - Nobody held the title last week → "🏆 إنجاز غير مسبوق: X أصبح أول من يحصل على لقب 'T'!"
  - New names, and last week's holders are still there → "🤝 منافسة على القمة: X ينضم إلى الصدارة في لقب 'T'!"
  - New names otherwise → "🥇 صعود جديد: X يتصدر قائمة 'T' هذا الأسبوع!"
  - No changes → "الأبطال يحافظون على مواقعهم! استمروا في العطاء هذا الأسبوع."
- If either side is empty → "جاري تحليل البيانات الأسبوعية..."
- **Caveat:** the "العقل المدبّر" title here uses the proxy `total_reading_minutes / 10`, not real points (P1:242).

**B. KPIs "📊 مؤشرات الأداء الرئيسية"** (P1:387-421)
| Label | Formula |
|---|---|
| ⏳ إجمالي ساعات القراءة | `int(Σ(common+other minutes over member_stats) // 60)`, thousands separator |
| 📚 إجمالي الكتب المنهَاة | Σ(total_common_books_read + total_other_books_read) |
| ✍️ إجمالي الاقتباسات | Σ total_quotes_submitted |
| 👥 الأعضاء النشطون | count members `is_active == True` |
| 🗓️ إجمالي أيام القراءة | # distinct `submission_date` across all logs |
| 🏁 التحديات المكتملة | # periods with `end_date < today` |

**C. Hall of Fame:** see §5.3.

**D. Charts "📊 التحليلات الشاملة والمتصدرون"** (P1:527-596; chart_generator.py)
- Date range for the time charts: first log date → today, daily.
- Charts:
  1. **نمو القراءة التراكمي**: area chart of cumulative hours per day (`cumsum(minutes)/60`), with days that have no logs filled as 0 (chart_generator.py:7-27).
  2. **نشاط القراءة الأسبوعي**: bar chart of the % of total minutes per weekday. Order: السبت، الأحد، الاثنين، الثلاثاء، الأربعاء، الخميس، الجمعة. Labels `{x:.1f}%` (chart_generator.py:29-74).
  3. **إيقاع القراءة اليومي للفريق**: line chart with markers of team hours per day (chart_generator.py:76-98).
  4. **⭐ المتصدرون بالنقاط**: horizontal bar of the top 10 by `total_points` (chart_generator.py:100-117).
  5. **🎯 تركيز القراءة**: donut of common vs other minutes, labels الكتاب المشترك / الكتب الأخرى, center text "التوزيع" (chart_generator.py:142-172).
  6. **⏳ المتصدرون بالساعات**: horizontal bar of the top 10 by total hours, shown to 1 decimal (chart_generator.py:119-140).
- Charts use RTL tweaks (y-axis on the right, x reversed).
- Palette: primary #2980B9, secondary #8E44AD, green #27AE60, orange #F39C12, red #E74C3C (utils.py:5-15).

**E. PDF export:** see §8.

### 6.2 Challenge analytics — "🎯 تحليلات التحديات" (P2)
- **Selector** "اختر تحدياً لعرض تحليلاته:" (P2:337-357)
  - Order: future (by start ascending), then active, then past (by start descending).
  - Option label: `{book_title} | {start} إلى {end}{status}`.
  - Defaults to the active challenge.
- Window: logs with reading date in `[start, end]`; achievements with `period_id` = the selected challenge.

**Tab "📝 ملخص التحدي"**
- **Challenge news "🎯 آخر أخبار تحدي \"{title}\""** (P2:229-283)
  - Not started → "⏳ الاستعدادات جارية: سينطلق تحدي '...' في تاريخ ...".
  - No finishers → "🏃‍♂️ السباق محتدم: لا يزال الجميع يتنافس لإنهاء كتاب '...'. من سيكون أول المنجزين؟"
  - Otherwise it looks at the most recent finish date:
    - Several people finished that day → "🎉 إنجاز جماعي: ... أنهوا الكتاب معًا في يوم ..."
    - One person, who is also the first ever finisher → "🏁 شرارة الإنجاز الأولى: X هو أول من عبر خط النهاية وأنهى الكتاب!"
    - Otherwise → "👍 ويستمر السباق: X ينضم إلى قائمة المنجزين."
  - Count line: "بطل واحد فقط وصل إلى خط النهاية حتى الآن." or "ملخص: N أبطال أتموا قراءة الكتاب بنجاح حتى الآن."
  - After the end date:
    - With attendees → "🗣️ جلسة نقاش مثمرة: نُشيد بحضور ... للجلسة الختامية."
    - Without → "ℹ️ ملاحظة: لم يتم تسجيل حضور لأي عضو في جلسة النقاش الختامية."
- **"لوحة المؤشرات العامة"** (P2:455-507)
  - Progress gauge:
    - `total_days = (end−start).days` (or 1)
    - `days_passed = (today−start).days + 1`
    - `progress = min(1, days_passed/total_days)*100`
    - Title "انقضى {days_passed} من {total_days} يوم"
  - ⏳ مجموع ساعات القراءة = `int(Σ minutes // 60)`.
  - ✍️ الاقتباسات المرسلة = Σ(cq + oq).
  - 👥 المشاركون الفعليون = # distinct members with logs in the window.
  - 📊 متوسط القراءة/عضو (دقيقة/يوم) = `Σminutes / days_passed / active_participants`. **days_passed is not capped at the end date** (bug, §11).
- **"التحليلات البيانية للتحدي"** (P2:509-561): the same 6 charts as §6.1.D, scoped to the challenge.
  - Date range: start → min(today, end).
  - Leaderboards and donut use the per-challenge podium points (§4.6).
- PDF export (challenge report).

**Tab "🧑‍💻 بطاقة القارئ"** (P2:618-807). This tab is **all-time, not per challenge**, even though it sits under a challenge.
- Selector "اختر قارئاً لعرض بطاقته:" lists all names from member_stats, sorted, **including inactive members**.
- KPIs: ⭐ إجمالي النقاط (`member_stats.total_points`), ⏳ إجمالي الساعات (1 decimal), ✍️ إجمالي الاقتباسات.
- 🏅 الأوسمة والشارات: §5.2.
- 📊 مصادر النقاط: donut of the 7 buckets (§4.7), with zero buckets hidden. Colors (chart_generator.py:186-191).
- "التحليلات البيانية لأداء القارئ": growth, weekly activity and rhythm charts for this member, from their first log to today.
- "خريطة التزام: {name}": GitHub-style heatmap (P2:167-227).
  - Rows are weekdays in the order الأحد … السبت. Columns are **ISO week numbers**, reversed for RTL.
  - Cell value = minutes per day. Range: first to last log date. Colorscale Greens.
  - Hover text: "YYYY-MM-DD (weekday)<br>دقائق القراءة: N". X ticks are month abbreviations. X title "أسابيع التحدي".
- PDF export (reader report).

### 6.3 About page — "❓ عن التطبيق" (P4)
Static help in 10 expanders. It describes the philosophy, the badges and the KPIs (see §10 for titles).

---

## 7. Admin features — "⚙️ الإدارة والإعدادات" (P3)

### 7.1 Member management "👥 إدارة المشاركين" (P3:191-241, 506-548)
- **Add:** "➕ إضافة مشارك" opens a dialog "اسم العضو الجديد:", which calls `add_members([name.strip()])`.
  - The Form dropdown is then rewritten with **all active names sorted** (`update_form_members`, P3:125-162). This is a Forms `updateItem` on `member_question_id` with `updateMask: questionItem.question.choiceQuestion.options`.
  - Duplicate names are not checked.
- **Grids:** النشطون (5 per row, 🚫 "تعطيل العضو") and الأرشيف (struck-through names, 🔄 "إعادة تنشيط العضو").
- **Deactivate/reactivate:** toggles `is_active` and rewrites the form options so the name is removed or re-added.
  - Data is kept. Deactivated members **still** appear in member_stats, the leaderboards, the hall of fame and the reader card, which contradicts P4:162.
- There is no rename and no hard delete of members.

### 7.2 Challenge planning "📅 إدارة التحديات" (P3:245-333, 550-652)
- Sections: التحدي النشط حالياً, التحديات المنتهية (start descending), التحديات المقبلة (start ascending).
- Each card shows the book title, author and `start إلى end`, with buttons ℹ️ (rules dialog) and 🗑️ (delete; disabled for the active challenge).
- Add flow: "➕ إضافة تحدي" opens "➕ إضافة تحدي جديد (الخطوة 1 من 2)", then "اختر نظام النقاط (الخطوة 2 من 2)", then optionally "تخصيص قوانين التحدي". Validation is described in §3.
- Delete: dialog "🚫 تأكيد الحذف النهائي" with the typed phrase.
- There is no edit of dates, book or rules after creation.

### 7.3 Settings "⚙️ الإعدادات المتقدمة والأدوات" (3 tabs)
1. **🎯 نظام النقاط** (P3:345-369): edits `global_rules/rules`, all 7 ints ≥0. Button "💾 حفظ الإعدادات الافتراضية". Applies only to future challenges.
2. **🔗 الروابط والأدوات** (P3:371-378): shows `form_url` under "🔗 رابط المشاركة".
3. **📝 محرر السجلات (محرر السجلات الذكي)** (P3:380-492)
   - "⬇️ تحميل أحدث السجلات للتعديل" pulls the sheet live (not Firestore). Each row keeps `sheet_row_index = idx + 2`.
   - Checkbox answers are exploded into booleans: `ach_finish_common`, `ach_finish_other`, `ach_attend_discussion`, `quote_common`, `quote_other`.
   - In the editable grid, name and timestamp are read-only. Date, both minute fields (as text) and the 5 checkboxes can be edited.
   - "💾 حفظ التعديلات في Google Sheet":
     1. Changed rows are found by string comparison.
     2. Date, minute fields and the rebuilt comma-joined checkbox strings are written back with `worksheet.batch_update`.
     3. A **full sync** (`run_data_update`) runs.
   - It edits existing rows only: no add or delete (P4:247).

### 7.4 Danger zone "🗑️ منطقة الخطر: حذف الحساب" (P3:494-502, 654-704)
- The user must type their email to confirm.
- Deletion steps:
  1. Delete the Sheet (`gc.del_spreadsheet`).
  2. Delete the Form via Drive v3 `files.delete`.
  3. Delete every subcollection doc and the user doc.
  4. Log out.
- Revoking the token is commented out.

### 7.5 Sync button
- Sidebar on the home page: "🔄 تحديث وسحب البيانات" (app.py:76-89).
- The sync log is shown in the "عرض تفاصيل سجل التحديث" expander, and `st.cache_data` is cleared.

---

## 8. PDF reports (pdf_reporter.py)

**Common to all three reports:**
- A4, FPDF2, font **Amiri-Regular.ttf** (regular only). Arabic text goes through `arabic_reshaper` + `python-bidi`.
- Cover pages use `cover_page.png` as a background at 50% alpha.
- Footer on every page except page 1: page number in the center and "تقرير ماراثون القراءة - YYYY-MM-DD" on the right (pdf_reporter.py:101-115).
- Charts are Plotly figures rendered by Kaleido as PNG (800×450, scale 2), placed two per page (`add_dual_chart_pages`).
- KPI grid: 3 columns, card 25 mm high, value 16 pt over label 11 pt.
- Hall-of-fame grid: 4 columns, title / name / value.

| Report | File name | Contents |
|---|---|---|
| **Dashboard** (`add_dashboard_report`, :281-311) | `ReadingMarathon_Report_Dashboard_{date}.pdf` | Cover "تقرير ماراثون القراءة" / "لوحة التحكم العامة" / "تاريخ الإصدار: …" → "مؤشرات الأداء الرئيسية" (6 KPIs §6.1.B) → "لوحة شرف الأبطال" (8 heroes with name + value) → 6 charts: نمو القراءة التراكمي، نشاط القراءة الأسبوعي، إيقاع القراءة اليومي للفريق، المتصدرون بالنقاط، تركيز القراءة، المتصدرون بالساعات |
| **Challenge** (`add_challenge_report`, :365-406) | `ReadingMarathon_Report_Challenge_{title_}_{date}.pdf` | Cover "تقرير تحدي:\n{title}", "تأليف: {author}", "الفترة: {start} إلى {end}", "مدة التحدي: {(end−start).days} يوم" → "مؤشرات أداء التحدي": مجموع ساعات القراءة، المشاركون الفعليون، الاقتباسات المرسلة، متوسط القراءة اليومي/عضو ("{x:.1f} د") → "المشاركون في التحدي": 3-column table "من حضروا النقاش" / "من أنهوا الكتاب" / "جميع المشاركين" with zebra rows → 6 charts titled "… للتحدي" (growth, points, hours, focus, weekly, rhythm). Note: the participants section adds a new page, so page 2 holds only the KPIs |
| **Reader** (`add_reader_report`, :431-482) | `ReadingMarathon_Report_Reader_{name_}_{date}.pdf` | Cover "تقرير أداء القارئ" + name + issue date → "المؤشرات الإجمالية" (إجمالي النقاط، إجمالي الساعات، إجمالي الاقتباسات) → "مصادر النقاط" donut → "الأوسمة والشارات" bullet list (or "لا توجد أوسمة بعد.") → charts نمو القراءة التراكمي، نشاط القراءة الأسبوعي، إيقاع القراءة اليومي → last page "خريطة التزام: {name}" heatmap |

---

## 9. Import/sync pipeline (main.py `run_data_update`)

**Trigger:** manual only.
- The sidebar button on the home page, or automatically after a log-editor save (P3:484).
- There is no schedule and no webhook. Pages cache Firestore reads for 300 s, and the cache is cleared after a sync.

**Steps** (main.py:6-63):
1. Read `settings/config.spreadsheet_url`. If it is missing, abort with an error message.
2. `open_by_url(...).worksheet("Form Responses 1").get_all_records()` loads the whole sheet as a DataFrame. A missing worksheet → abort with a message.
3. If the sheet is empty, log "ℹ️ لا توجد بيانات جديدة في الجدول." and **leave the existing data unchanged**.
4. Load members, periods (joined with books), logs and achievements. If there are no members or no periods → abort.
5. **Full wipe:** delete every doc in `logs` and `achievements`, one by one (`clear_subcollection`, not batched).
6. `process_all_data` (main.py:73-131):
   - Sort the rows by `Timestamp`.
   - Skip a row if:
     - `Timestamp` is empty;
     - `تاريخ القراءة` does not parse as **`%d/%m/%Y`** (the part before the first space). This is why the locale must be UK.
     - `اسمك` (stripped) does not **exactly** match any member `name`. All members are matched, inactive included. If two members share a name, the last one in the map wins.
   - Column lookups have legacy fallbacks:
     - common minutes: `مدة قراءة الكتاب المشترك` or `… (اختياري)`
     - other minutes: `مدة قراءة كتاب آخر (إن وجد)` or `… (اختياري)`
     - quotes: `ما هي الاقتباسات التي أرسلتها اليوم؟ (اختر كل ما ينطبق)` or `… (اختياري)`
     - achievements: `إنجازات الكتب والنقاش` or `… (اختر فقط عند حدوثه لأول مرة)`
   - Each row becomes one log, plus 0–3 achievements (§4.4), written in a single Firestore batch per row.
7. `calculate_and_update_stats`: recompute per-member stats (§4), wipe `member_stats` and rewrite it with doc id = member_id.
8. The returned log lines are shown in the UI.

**Dedup and validation:**
- **Log rows are never deduplicated.** Duplicate submissions for the same day are all counted.
- Achievement dedup works only for common-book finish and discussion, per member per period.
- **Not validated:**
  - minute ranges (e.g. 23 h/day is accepted)
  - future reading dates
  - durations with seconds only
  - members who ticked an achievement while their reading date is outside a period: the achievement is dropped silently
- The count reported in the log is the rows processed. Skipped rows are not reported individually.

**Cost:** O(rows) Firestore writes plus up to 2 `has_achievement` queries per row, on every sync, because the whole history is reprocessed.

---

## 10. Arabic UX copy (keep the familiar wording)

**App / brand:** ماراثون القراءة · منصة ماراثون القراءة · "الصفحة الرئيسية | ماراثون القراءة" · sidebar title "لوحة التحكم" · "أهلاً بك! {email}" · "🚪 تسجيل الخروج" · "🔄 تحديث وسحب البيانات" · toast "اكتملت عملية المزامنة بنجاح!"

**Pages:** 📈 لوحة التحكم العامة · 🎯 تحليلات التحديات · ⚙️ الإدارة والإعدادات · ❓ عن التطبيق

**Core terms:**

| Term | Arabic |
|---|---|
| challenge / challenges | تحدي / التحديات |
| common book | الكتاب المشترك |
| other book(s) | كتاب آخر / الكتب الأخرى |
| quote | اقتباس / الاقتباسات |
| discussion session | جلسة النقاش (الجلسة الختامية) |
| member / participant / reader | عضو / مشارك / قارئ |
| supervisor | المشرف |
| points | نقاط |
| points system | نظام النقاط |
| default / custom points system | النظام الافتراضي / نظام نقاط مخصوص |
| badges | الأوسمة والشارات |
| reading log | سجل القراءة |
| sync | المزامنة |
| archive | الأرشيف |
| active (members) | النشطون |
| status labels | (الحالي) 🟢 / (السابق) 🏁 / (المقبل) ⏳ |

**Dashboard sections:**
- 📰 آخر أخبار الماراثون (آخر 7 أيام)
- 📊 مؤشرات الأداء الرئيسية
- 🌟 لوحة شرف الأبطال
- 📊 التحليلات الشاملة والمتصدرون
- 🖨️ تصدير تقرير الأداء (PDF)
- 🚀 إنشاء وتصدير تقرير لوحة التحكم
- 📥 تحميل التقرير الآن

**KPI labels:**
- إجمالي ساعات القراءة · إجمالي الكتب المنهَاة · إجمالي الاقتباسات · الأعضاء النشطون · إجمالي أيام القراءة · التحديات المكتملة
- Challenge: مجموع ساعات القراءة · الاقتباسات المرسلة · المشاركون الفعليون · متوسط القراءة/عضو (دقيقة/يوم)
- Reader: إجمالي النقاط · إجمالي الساعات

**Hero titles:** 🧠 العقل المدبّر · ⏳ سيد الساعات · 📚 الديدان القارئ · 💎 صائد الدرر · 🏃‍♂️ صاحب النَفَس الطويل · ⚡ العدّاء السريع · ⭐ نجم الأسبوع · 💪 عملاق الشهر

**Badges:** ✍️ وسام الفيلسوف · 🏃‍♂️ وسام العدّاء · 💯 وسام المثابرة

**Chart titles:** نمو القراءة التراكمي · نشاط القراءة الأسبوعي · إيقاع القراءة اليومي (للفريق) · ⭐ المتصدرون بالنقاط · ⏳ المتصدرون بالساعات · 🎯 تركيز القراءة · 📊 مصادر النقاط · خريطة التزام · أسابيع التحدي · مجموع الساعات التراكمي · نسبة النشاط (%) · يوم الأسبوع

**Points-source labels:** قراءة الكتاب المشترك · قراءة كتب أخرى · اقتباسات (الكتاب المشترك) · اقتباسات (كتب أخرى) · إنهاء الكتاب المشترك · إنهاء كتب أخرى · حضور النقاش

**Challenge page:**
- اختر تحدياً لعرض تحليلاته: · تحليلات تحدي: {title}
- Tabs: 📝 ملخص التحدي · 🧑‍💻 بطاقة القارئ
- Sections: لوحة المؤشرات العامة · التحليلات البيانية للتحدي · اختر قارئاً لعرض بطاقته: · التحليلات البيانية لأداء القارئ
- Gauge: انقضى X من Y يوم

**Admin:**
- Sections: 👥 إدارة المشاركين · ➕ إضافة مشارك · النشطون · الأرشيف · تعطيل العضو · إعادة تنشيط العضو
- Challenges: 📅 إدارة التحديات · ➕ إضافة تحدي · التحدي النشط حالياً · التحديات المنتهية · التحديات المقبلة · عرض نظام النقاط · حذف التحدي
- Rules: 📈 استخدام النظام الافتراضي · 🛠️ تخصيص القوانين لهذا التحدي
- Tools: ⚙️ الإعدادات المتقدمة والأدوات · 🎯 نظام النقاط · 🔗 الروابط والأدوات · 🔗 رابط المشاركة · 📝 محرر السجلات الذكي · 💾 حفظ الإعدادات الافتراضية · 🗑️ منطقة الخطر: حذف الحساب

**Rule field labels:**
- دقائق قراءة الكتاب المشترك لكل نقطة:
- دقائق قراءة كتاب آخر لكل نقطة:
- نقاط اقتباس الكتاب المشترك:
- نقاط اقتباس كتاب آخر:
- نقاط إنهاء الكتاب المشترك:
- نقاط إنهاء كتاب آخر:
- نقاط حضور جلسة النقاش:

**Log-editor column labels:** أنهى المشترك؟ · أنهى آخر؟ · حضر النقاش؟ · اقتباس مشترك؟ · اقتباس آخر؟ · دقائق (مشترك) · دقائق (آخر) · تاريخ القراءة · الاسم · ختم الوقت

**Form copy:** listed in §1.3.

**Weekdays:** السبت الأحد الاثنين الثلاثاء الأربعاء الخميس الجمعة. The week starts on Saturday in the weekly chart and on Sunday in the heatmap.

**About-page expanders:**
- 🎯 الفكرة والجمهور المستهدف
- ⚙️ آلية عمل التطبيق
- 👥 إدارة المشاركين: إضافة وتعطيل
- 📖 إدارة التحديات: إضافة وحذف
- 🧠 فلسفة التحفيز الذكي: وسائل كسب النقاط (sub-sections: حل لمشكلة "الكتاب الإجباري" / تحفيز التفاعل عبر "الاقتباسات" / تتويج التجربة بـ "جلسة النقاش" / الهدف الأسمى من النقاط)
- 🏆 نظام النقاط والأوسمة
- 📊 أدوات التحليل والمتابعة
- ⚙️ أدوات إدارية متقدمة
- 💡 أسئلة شائعة وملاحظات هامة
- 🧑‍💻 عن المطور

---

## 11. Weaknesses / bugs / limitations

### Security
1. **Account takeover via URL** (auth_manager.py:117-127). Any visitor who opens `?user_id=<victim google id>` gets the victim's credentials. They are rebuilt from the refresh token stored in Firestore, with no proof that the visitor owns the account.
2. Refresh tokens are stored in **plaintext** in Firestore (db_manager.py:283-294). Account deletion does not revoke them; the call is commented out (P3:691-696).
3. HTML injection: member names and book titles are interpolated into `unsafe_allow_html` markup without escaping (e.g. P1:296, P3:214, P2:271-274).

### Data integrity and sync
4. The sync wipes and rebuilds everything, doc by doc and non-atomically (main.py:47-52; db_manager.py:226-234). A crash mid-sync leaves partial or empty data. The cost grows with history.
5. No dedup of log rows; double submissions count twice. No cap on minutes and no rejection of future dates.
6. Members are joined **by name string**:
   - Renaming breaks history.
   - Duplicate names collide (main.py:78).
   - A name typed differently from the dropdown is silently skipped.
7. The date format depends on the Sheet locale being UK. Wrongly formatted rows are dropped silently (main.py:91-97).
8. Duration parsing drops seconds. A duration like `0:00:45` counts as 0. Non-string cells count as 0.
9. **Discussion attendance is lost** if it is reported with a reading date after the challenge `end_date`. The discussion usually happens at the end, so organisers must make sure members report it inside the window. Same for finishing the common book after the end.
10. `FINISHED_OTHER_BOOK` is unlimited and has no book identity, so it can be farmed for 25 points per tick.
11. Quotes are binary per row: one quote per type per submission. Several quotes require several submissions, which also counts as more "reading days".
12. Deleting a challenge does not recompute `member_stats` (stale until the next sync). Its logs remain and lose their points.
13. Rules cannot be edited once a challenge exists. A book title cannot be reused across challenges.
14. Challenges can only be appended after the last end date. Gaps are allowed, and logs in gaps earn 0 points. The setup wizard does not validate the first challenge's dates (start can be after end).

### Scoring inconsistencies
15. Per-log floor in the global engine vs floor of the sum in the per-challenge podium (§4.2 vs §4.6). The same member can show different points for one challenge in the two places.
16. The news ticker uses `minutes/10` as a proxy for points (P1:242). The "العقل المدبّر" news can disagree with the real hall of fame.

### Analytics bugs
17. Challenge `avg_daily_reading` divides by `days_passed`, which keeps growing after the challenge ends (P2:461, 474). Averages of past challenges shrink every day.
18. Gauge off-by-one: `total_days = (end−start).days` (exclusive) but `days_passed` is +1 (inclusive). The PDF "مدة التحدي" is also exclusive (P2:569).
19. The heatmap uses ISO week numbers as columns (P2:189), so ranges that cross a year boundary or ISO week 53 are misordered or merged.
20. "Runner" badge: `<= 7` days means offset 0–7, which is 8 calendar days. It is evaluated across all challenges, so it is awarded at most once overall. It is not a per-challenge badge.
21. "Persistence" streak counts any submission, even 0 minutes. It is all-time, not per challenge.
22. Inactive members still appear in leaderboards, the hall of fame and the reader card, which contradicts the About page (P4:162). The member-stat engine also includes them.
23. The reader card lives under the challenge page but is all-time and ignores the selected challenge.
24. `last_log_date` and `last_quote_date` are computed but never shown, so there is no "inactive since X days" alert.
25. Hero title "الديدان القارئ" is likely a typo or odd wording (the usual phrase is "دودة الكتب" / "الدودة القارئة"). Confirm with the owner before keeping it.

### Setup and UX
26. The setup wizard's "تحقق من الإعدادات وتابع" button is nested inside another button's `if` block (app.py:137, 181), so it can never fire. On the next rerun the wizard jumps to step 3 because the URLs are set. The linking instructions (rename the tab to `Form Responses 1`, set the UK locale) disappear after one render. The verify code also uses a stale `user_settings` with an empty URL.
27. The Form↔Sheet link and the tab rename are manual steps that are easy to get wrong. The only error appears later, at sync time.
28. No member-facing app, no reminders or notifications, and no per-member login. Members cannot see their own score.
29. Single organiser per workspace; no co-admins and no multiple groups per account.
30. Charts reverse the x-axis for RTL (`xaxis_autorange='reversed'`) even on time axes. A native app should use proper RTL layouts instead.
31. PDF generation needs Kaleido and headless Chromium (packages.txt), which is heavy and fragile.
32. Hard-coded redirect URI `https://reading-marathon.streamlit.app`.
33. `drive.file` scope plus deleting a Form via the Drive API works only for files the app created.

### Recommendations for the Android rebuild (derived)
- Give members stable IDs. Optionally keep Forms import for backward compatibility, but key entries by member id.
- Make logging native, with server-side validation and dedup: one entry per member per day, or an explicit multi-entry design.
- Compute scores incrementally (on write) instead of a full rebuild.
- Choose one rounding policy, either per log or per challenge sum, and use it everywhere.
- Allow a grace window after `end_date` for discussion and finish achievements, or let the organiser record attendance directly.
- Store all dates as ISO `LocalDate`.
- Persist badges with dates, and decide whether they are per challenge.
- Exclude inactive members from leaderboards, or make it a filter.
- Generate PDFs natively (Android `PdfDocument`) or server-side.
