# Chitti — Project Context

A single reference for what Chitti is, who is building it, where it stands, and what still needs doing before the hackathon.

**Sources:** the idea brief (`Chitti-iQOO-Hackathon-Idea-Brief.docx`, 17 September 2026), the current code on branch `apple-hig-redesign`, the README, the `brain/` notes and the `PPT/` design specs. Where they disagree, the brief wins for event facts and the code wins for features. The conflicts are listed in §11.

*Last updated: 19 September 2026.*

---

## 1. Team

**Owl Coders**: 3 members, student category.

| Member | Role |
|---|---|
| **K. Nandi Vardhan Reddy** | Team lead, lead developer and architecture (submits the Phase 1 form) |
| **V. Jaya Sai Krishna** | UI/UX design |
| **G. Pavan Sai Manikanta** | Testing and debugging |

Repository: github.com/NandiVardhan2007/chitti

---

## 2. Event

| | |
|---|---|
| **Event** | iQOO Hackathon 2026, **Hyderabad City Battle** |
| **Dates** | 26–27 September 2026 (30-hour build) |
| **Track** | **Productivity** |
| **Category** | Student |
| **Phase 1 (idea submission)** | Deadline **22 September 2026**. The team leader submits; the plan is to submit by **20 September**. |
| **Phase 1 deliverable** | Idea form plus a PDF/PPT deck (max 25 MB) or a link |

### Judging criteria (from the organisers)
| Criterion | How Chitti answers it |
|---|---|
| **Phone-first execution** | It depends on Android's notification listener, calendar, alarms and share sheet. It cannot exist as a web app. |
| **AI integration** | An on-device LLM does the understanding. Privacy makes local AI necessary, because nobody would upload private chats to a server. The organisers' guidance says local models score higher than cloud-only APIs. |
| **Office Kit utilisation** | Plan the phone-only build hours around Office Kit and practise with it before the event. |
| **Real-world utility** | Everyone has missed a message in a noisy group chat. |
| **Pitch quality** | It can be explained in one sentence, and the live demo needs no explanation. |

---

## 3. Idea statement

**Name:** *Chitti* is Telugu for a small note or slip.

**Title (for the form):** Chitti – Never Miss a Deadline Buried in Your Group Chats

**One-line pitch:** "The moment a deadline lands in your WhatsApp group, Chitti catches it – before it gets buried under 200 other messages."

### The problem
Important commitments arrive as ordinary messages and are buried within minutes. By the brief's estimate, a student or working professional in India receives around 150–300 notifications a day from WhatsApp groups, SMS, email and college or office apps, and only a handful of them need action. Examples:

- "Submit the record by Friday 5 pm"
- "Fee due on 25th"
- "Class shifted to 2 pm, Lab 3"
- "Send me the PPT tonight"

To-do and calendar apps only help if you type every task in yourself, and almost nobody does. People lose marks, pay late fees and miss meetings as a result.

### The solution
Chitti reads each incoming notification. A small open model running on the phone decides whether the message holds a **deadline, payment, meeting or request**. If it does, the model extracts **what, when and who** and turns the message into an action card: one tap adds it to the calendar, sets a reminder or drafts a reply. It understands code-mixed **Telugu-English and Hinglish**, which is how people actually message. No message ever leaves the phone, and it works with no internet connection.

### Form description (paste-ready)
> Chitti is an Android app that catches commitments the moment they arrive. It listens to incoming notifications from WhatsApp, SMS and email. A small open-source LLM running fully on the iQOO phone detects deadlines, payments, meetings and requests, even in code-mixed Telugu-English and Hinglish. Each one becomes an action card: add to calendar, set a reminder or draft a reply in one tap. No message ever leaves the device, so private chats stay private and the app works offline. It is built for students and working professionals who lose important messages in noisy group chats. A two-stage pipeline, a lightweight filter followed by the LLM, keeps battery use low.

### Why us (edit before submitting)
> Our idea needs on-device AI for a real reason: nobody would upload their private chats to a cloud server, so local inference is the only way this product can exist. We face this problem every day in our own college WhatsApp groups, so we can test on real messages. Chitti also handles Telugu-English and Hinglish messages, which most productivity tools ignore.

---

## 4. How it works

```
Notification arrives
  → Dedupe (hash, 5-minute window)        → LinkGuard scans any links
  → Quick filter (keywords + dates; drops spam, OTPs, chit-chat)
  → On-device LLM: Gemma 2B INT4 via MediaPipe (rule/regex fallback)
        → what · when · who · category · urgency, scored for priority
  → Stored locally (Room)  → Today screen
  → Act: calendar · reminder (real alarm) · draft reply · share
```

Only messages that pass the quick filter reach the LLM, which keeps battery use low. If the model isn't loaded yet, the rule-based extractor takes over, so nothing is lost.

---

## 5. What is built (verified in the code)

### Core idea: notification to commitment
- **Capture:** a `NotificationListenerService` with a SHA-256 deduplication window of 5 minutes
- **Filter:** `NotificationFilter` drops noise before anything reaches the model
- **Extract:** Gemma 2B INT4 through MediaPipe, returning JSON, with a rule/regex fallback. Handles code-mixed text such as "25th lopu fee kattali, marchipovaddu".
- **Score:** `ImportanceScorer` sets priority from urgency, time cues and category
- **Today screen:** everything Chitti caught, split into *Urgent* and *Needs you*. Complete with the circle or a swipe; tap to **Reply** (an on-device draft), **Calendar** or **Remind**.
- **Real reminders:** alarms are persisted, scheduled with AlarmManager and re-armed after a reboot

### Assistant (beyond the brief)
- **Voice:** tap the mic for a full-screen listening overlay with a live transcript and spoken replies (Android SpeechRecognizer and TextToSpeech)
- **Ask:** type or speak, with suggestions built from your own data. It opens apps, toggles the flashlight, sets reminders from natural phrases ("in 10 minutes", "at 5 pm", "tomorrow at 9"), answers "what's pending", and answers general questions with the local model.
- **Memory:** "What Chitti knows": facts about you (college, branch, people, projects) used when you ask about yourself
- **Action registry:** 17 whitelisted actions, with confirmation required for risky ones such as sending an SMS. Every action is logged in "What Chitti did".

### Safety and convenience (beyond the brief)
- **LinkGuard:** once Chitti is set as the link opener (Settings → Links, or onboarding), **every link tapped in any app opens in Chitti first**. It is checked on the phone (lookalike brands such as `phonepe.secure-verify.xyz`, fake KYC/UPI, bank typosquats, scam domain endings, shorteners) and, when online, with **Google Safe Browsing**. Safe links show how they were checked, then open in the user's chosen browser; unsafe links explain every reason, with *Go back* as the main action. Links inside incoming notifications get the same check and raise an alert if dangerous.
- **Autofill:** fills your saved profile into forms in other apps
- **Accessibility typing:** types text into another app on command

### App and tooling
- **Three tabs**: Today, Ask and Library, with an Apple-HIG-style design: light and dark themes, large titles, grouped lists, frosted glass bars
- **Extraction lab:** a hidden screen (tap Version seven times in Settings) that runs any message through filter → extract → score and shows the fields and latency. Useful for the demo and for testing.
- **Privacy controls:** clear any one kind of data, or erase everything

---

## 6. Gaps against the brief

| In the brief | Status | Why it matters |
|---|---|---|
| **Pop-up card: "Deadline detected – add to calendar?"** | **Not built.** A caught commitment only appears inside the app's Today screen. The only pop-up notifications today are reminders and LinkGuard alerts. | This is step 3 of the live demo and the product's defining moment. **Highest priority.** It should be a heads-up notification with *Add to calendar* and *Remind me* buttons. |
| Evening digest: "3 things you promised today" | Not built | A promised feature. A WorkManager job posting one notification in the evening would cover it. |
| On-device transcription of voice notes | Not built (stretch goal) | Optional |
| Calendar entry in one tap | Opens the calendar app pre-filled, so it takes a second tap to save | Fine for the demo; say "one tap to open, one to save" or insert directly with the calendar permission |
| "Office Kit" use | Unknown: not referenced in the code | It's a judging criterion. Find out what it is and practise with it before the event. |

---

## 7. Review of the idea

**What's strong**
- It has a real reason to be on the device: private chats can't go to a server. That answers the "AI integration" and "phone-first" criteria directly.
- It follows the pattern of the reported Chennai winner (one everyday moment, one visible alert, a very Indian problem) while solving a different problem.
- Code-mixed Telugu-English and Hinglish support is a genuine difference, and it's already working in the rule engine.
- Much more is built than the brief planned, so there's plenty to show.

**Risks to manage**
1. **Scope creep in the pitch.** The code now also covers a voice assistant, chat, memory, LinkGuard, autofill and accessibility typing. Leading with all of it makes Chitti sound like "another assistant". Lead with the one moment, *a deadline in a group chat becomes a card*, and show the rest as support ("and because it already understands your messages, it can also…").
2. **LinkGuard sits close to the Chennai winner** (unsafe payment QR). Keep it a supporting feature so the idea stays clearly original.
3. **The key demo moment isn't built** (see §6). Build it before practising the demo.
4. **Is pre-built code allowed?** The brief plans a 30-hour build at the event, but most of the app already exists. Check the rules, and be ready to explain what's new at the event (for example the pop-up card, the digest and Office Kit integration).
5. **The "nothing leaves the phone" claim:** with Google Safe Browsing on, the one thing sent off the phone is a **link being checked**, and only to Google. Messages, extraction, memory and voice stay on the device. Say it exactly that way ("your messages never leave the phone; only a link is sent to Google to check it"). Offline, the phone check still works on its own.
6. **Model quality:** the local Gemma 2B build is sometimes wrong on general questions. Keep the demo on extraction, which is its strength, and have the rule fallback ready.
7. **Numbers:** `brain/references.md` lists latency figures that were never measured. Measure them in the Extraction lab on the actual demo phone before putting any on a slide.

---

## 8. Live demo (under a minute)

1. Show a noisy WhatsApp group on the demo phone, full of jokes and forwards.
2. A teammate sends: **"Record submission tomorrow 10 am, Lab 2"**.
3. A card appears: **"Deadline detected – add to calendar?"** *(this needs the pop-up from §6)*
4. Tap once, then open the calendar to show the entry.
5. Send **"25th lopu fee kattali, marchipovaddu"** and show that Chitti catches it too.
6. Switch to airplane mode and feed in a saved test notification (or use the Extraction lab) to prove the AI runs on the device.
7. *Optional, if time allows:* ask by voice "What's pending?" to show the assistant.

**Owners:** Pavan Sai Manikanta rehearses the demo and prepares the test messages and a backup plan (the Extraction lab if a live notification is slow). Jaya Sai Krishna owns how the card and the Today screen look on stage.

---

## 9. Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin 2.4, Jetpack Compose (BOM 2026.09), Material 3 |
| Build | AGP 9.4, Gradle 9.7, compileSdk 37, targetSdk 36, minSdk 26 |
| On-device LLM | Gemma 2B INT4 via MediaPipe Tasks GenAI 0.10.14 (`/data/local/tmp/gemma.bin`), with a rule/regex fallback |
| Speech | Android SpeechRecognizer, Android TextToSpeech |
| Storage | Room (SQLite), database version 5 |
| Background | NotificationListenerService, AlarmManager, BootReceiver, WorkManager |
| Design | Apple-HIG-style system: Inter, light and dark themes, spring motion, frosted glass (Haze) on the nav bar, tab bar and voice overlay |
| Test phone | Motorola Edge 50 Pro, Android 16 (confirm the iQOO demo phone model and Android version at the event) |

---

## 10. Pitch deck

**Content**: the brief's six slides:
1. Problem, with a screenshot of a messy group chat (names hidden)
2. Solution, with the action card
3. Architecture, using the pipeline in §4
4. Why on-device: privacy, offline use, battery
5. The 30-hour build plan and who owns what
6. The team and why we care about this problem

**Design** (from `PPT/`): follow the layout of the reference iQOO hackathon deck, on a near-black background with a diagonal grey light sweep and film grain. Use yellow capsule banners, flow chips joined by arrows, 3-column cards and an outlined callout box, with all text in capitals. Apply the iQOO website's styling: yellow **#F0B31C** (deeper #C8920A) on black **#050508**, with **Anton** for titles, **Chakra Petch** for labels, **Inter Tight** for body text and **JetBrains Mono** for technical detail. Jaya Sai Krishna owns the deck design.

---

## 11. Next steps

- [ ] Agree on the title and pitch as a team
- [ ] Edit the "Why us" answer with real team details; fill in Android and LLM proficiency honestly
- [ ] Make the six-slide deck
- [ ] Team lead submits the Phase 1 form by **20 September** (deadline 22 September)
- [ ] Build the **"Deadline detected" pop-up** with Add to calendar / Remind me buttons
- [ ] Build the **evening digest**
- [x] Link checking for every tapped link, on the phone plus Google Safe Browsing (key kept in git-ignored `local.properties`)
- [ ] Check the rules on pre-built code
- [ ] Learn Office Kit and practise with it before 26 September
- [ ] Measure real latencies in the Extraction lab on the demo phone
- [ ] Rehearse the demo end to end in airplane mode
- [ ] Optional: record a 1-minute video walkthrough

---

## 12. Conflicts between sources (what's current)

| Old note | Current truth |
|---|---|
| README: "Productivity & On-Device AI Track" | Track is **Productivity** (brief) |
| README contributors: one member | Three members, roles in §1 |
| `brain/`: file finder / "Long ago" search, ML Kit OCR, document scanning | Removed from the code |
| `brain/`: Silero VAD / ONNX Runtime | Removed; voice uses Android SpeechRecognizer |
| `brain/`, README: 9 screens, "Gemini cosmic" dark theme, skeuomorphic desk | 3 tabs (Today, Ask, Library), Apple-HIG design |
| `brain/references.md` benchmark table | Not measured; don't quote it |
| README: Kotlin 1.9 / AGP 8.2 / SDK 34 | Upgraded (see §9) |
