# Chitti — Pitch Deck Brief (for the AI building the slides)

You are building the **Phase 1 pitch deck** for *Chitti*, an Android app entered in the **iQOO Hackathon 2026, Hyderabad City Battle**. This file is everything you need: the story, the exact slide content, the visual design system, and the rules on what may and may not be claimed. Follow it literally where it gives exact text. Where it says *(suggested)*, you may improve the wording, but keep the meaning.

---

## 0. Output requirements

- **Format:** 16:9, 1920×1080 (or 1440×810 pt). Deliver as **.pptx and PDF**. The file must be **under 25 MB** (a submission limit), so compress images.
- **Length:** 9 slides (listed in §4). If a shorter version is needed, merge them as shown in §4.10.
- **Language:** English. **All on-slide text in CAPITALS**, except code-mixed example messages, which keep their original casing inside quote marks.
- **Density:** at most ~35 words of body text per slide. Put detail in the speaker notes; each slide has notes in §4.
- **No stock photos, no AI-generated people, no robot or brain icons, no sparkles, no glowing orbs.** The only imagery is app screenshots or mock-ups, simple line icons, arrows and the background texture.

---

## 1. Facts (use exactly; do not invent others)

| Item | Value |
|---|---|
| Product | **Chitti** (Telugu for *a small note or slip*) |
| Event | **iQOO Hackathon 2026 · Hyderabad City Battle** |
| Event dates | 26–27 September 2026 |
| Track | **Productivity** |
| Category | Student |
| Team | **Owl Coders** (3 members) |
| K. Nandi Vardhan Reddy | Team lead, lead developer and architecture |
| V. Jaya Sai Krishna | UI/UX design |
| G. Pavan Sai Manikanta | Testing and debugging |
| Idea title | **Chitti – Never Miss a Deadline Buried in Your Group Chats** |
| One-line pitch | **"The moment a deadline lands in your WhatsApp group, Chitti catches it – before it gets buried under 200 other messages."** |
| Tagline | **SEES → UNDERSTANDS → REMEMBERS → ACTS** |
| Platform | Android, Kotlin + Jetpack Compose |
| On-device model | Gemma 2B (4-bit) via Google MediaPipe, with a rule-based fallback |

### Judging criteria (the deck must answer each one visibly)
1. Phone-first execution
2. AI integration (local models score higher than cloud-only APIs)
3. Office Kit utilisation
4. Real-world utility
5. Pitch quality

---

## 2. What may and may not be claimed

The deck must be honest; judges will see the live app.

**Working today (can be shown as "built"):**
- Reads incoming notifications (Android NotificationListenerService) and removes duplicates (5-minute window)
- A quick filter drops spam, OTPs and chit-chat before any AI runs
- On-device extraction of **what / when / who / category / urgency**, with Gemma 2B through MediaPipe plus a rule-based fallback
- Understands code-mixed **Telugu-English and Hinglish**, e.g. "25th lopu fee kattali, marchipovaddu"
- A **Today** screen listing everything caught, split into *Urgent* and *Needs you*. Each item has one-tap **Reply** (a draft written on the phone), **Calendar** and **Remind**.
- Real reminders (alarms that survive a reboot)
- A voice assistant (speak or type): "What's pending?", "Remind me to call mom in 30 minutes", "Open WhatsApp", flashlight
- Memory: facts about you (college, branch, people) used when you ask about yourself
- **LinkGuard:** every link tapped in any app opens in Chitti first. It is checked on the phone (lookalike brands, fake KYC/UPI, scam domain endings) and with Google Safe Browsing when online. Safe links open in the user's browser; unsafe ones are stopped with the reasons explained.
- Messages never leave the phone. The only thing ever sent is a link being checked, and only to Google Safe Browsing. Offline, everything including the phone-side link check still works.
- An **Extraction lab** screen that shows exactly what the AI pulled out of a message

**Planned, not built yet (label as "AT THE EVENT", never as done):**
- A **pop-up action card** the moment a commitment is caught: *"Deadline detected – add to calendar?"*
- **Adding to the calendar in one tap** without opening the calendar app
- An **evening digest**: *"3 things you promised today"*
- **Office Kit** integration
- Transcribing voice notes on the phone *(stretch goal)*

**Never claim:**
- Any latency, accuracy or battery number. None has been measured yet. Write *"measured live at the event"* where a number would go.
- That Chitti "never makes mistakes". The small model can be wrong, which is why every action is one tap and needs the user's confirmation.
- Cloud features, subscriptions or accounts. There are none.

The 150–300 notifications-a-day figure is a **team estimate**. Present it as *"~150–300 notifications a day"*, never as a cited statistic.

---

## 3. Design system

Follow the layout of the reference **iQOO hackathon deck**, restyled with the **iQOO hackathon website's** fonts and yellow.

### 3.1 Colours
| Role | Hex |
|---|---|
| Brand yellow (titles, filled pills and cards, arrows, outlines) | **#F0B31C** |
| Yellow, deeper (pressed shade, second tint in diagrams) | **#C8920A** |
| Background base | **#050508** |
| Dark surface for cards (optional) | **#1A1A1A** |
| Text on dark | **#FFFFFF** (secondary: white at 70%) |
| Text on yellow | **#000000** |
| Card outline | 1 px #F0B31C at ~50% opacity |
| Alert (one use only: the "buried / missed" moment) | **#C8102E** |

The deck is **black and yellow only**, plus the single alert red. Use no other colours.

### 3.2 Background
- Full-bleed on every slide: a near-black base (#050508 to #131313) with a **soft diagonal grey light sweep** from top-left to bottom-right (peaking around #3D3D3D to #5C5C5C at ~40% opacity), plus a **fine film-grain / noise texture** (about 4–6% opacity).
- The final slide adds **thin yellow concentric arcs** in the bottom corners.

### 3.3 Fonts (all on Google Fonts)
| Use | Font |
|---|---|
| Slide titles, big statements | **Anton** (400), all caps, tight line height (~0.9), tracking +1% |
| Pills, labels, chips, card titles, small caps lines | **Chakra Petch** SemiBold/Bold, all caps, wide tracking (+8% to +20%) |
| Body text in cards | **Inter Tight** Regular/Italic, all caps for short lines |
| Tech details (stack names, code-like labels) | **JetBrains Mono** Medium |

Title size ~72–96 pt, pill text ~20–24 pt, card body ~18–20 pt, small labels ~14 pt.

### 3.4 Components (reuse these; add nothing else)
- **Pill banner:** a fully rounded yellow bar with black Chakra Petch Bold caps. Use it for the tagline or the key takeaway at the bottom of a slide.
- **Flow chip:** a small rounded pill, either filled yellow or outlined in yellow, joined by yellow → arrows. Use it for step flows.
- **Card:** a rounded rectangle (corner radius ≈ 12% of its height). Cards alternate between **filled yellow with black text** and **yellow outline on dark with a yellow title and white italic body**. Lay them out in 3-column grids.
- **Hexagonal tag:** an outlined, elongated hexagon holding a letter-spaced label: `BY OWL CODERS`.
- **Callout box:** a large outlined rounded box, with a quote on the left and a list of points on the right.
- **Event label:** small white Chakra Petch caps in the top-left corner of every slide: `IQOO HACKATHON 2026 · HYDERABAD`.
- **Slide number:** small, bottom-right, white at 50%.

> Spelling: the team is **OWL CODERS**. Never write "Owl Coaders" or "Owlcoders".

### 3.5 App screenshots
Place phone screenshots inside a simple **black phone frame with rounded corners**, with no brand device mock-ups. The app itself uses a clean Apple-style look (black or white backgrounds, a blue accent, large titles), so it will contrast with the yellow deck; don't recolour screenshots. Where a screenshot isn't supplied, draw a **placeholder box** labelled with the screenshot name from §5.

---

## 4. Slides

### 4.1 Slide 1 — Title
**Layout:** a huge title on the left, a yellow team card on the right, and the hexagonal tag under the title.

- Event label: `IQOO HACKATHON 2026 · HYDERABAD CITY BATTLE`
- Title: **CHITTI**
- Subtitle (Chakra Petch): **NEVER MISS A DEADLINE BURIED IN YOUR GROUP CHATS**
- Hex tag: `BY OWL CODERS`
- Small line: `TRACK: PRODUCTIVITY`
- **Team card** (filled yellow, black text, Inter Tight):
  - K. NANDI VARDHAN REDDY — TEAM LEAD · DEVELOPMENT
  - V. JAYA SAI KRISHNA — UI/UX
  - G. PAVAN SAI MANIKANTA — TESTING & DEBUGGING

**Speaker notes:** "We're Owl Coders. Chitti is Telugu for a small note, the slip a friend passes you so you don't forget. Our app is that slip, for your phone."

---

### 4.2 Slide 2 — Problem hook
**Layout:** a question headline at the top, three message bubbles in the middle (styled as quoted chat lines), and a pill banner at the bottom.

- Headline: **HOW MANY DEADLINES DID YOUR GROUP CHAT BURY TODAY?**
- Small line: `~150–300 NOTIFICATIONS A DAY. A HANDFUL ACTUALLY NEED YOU.`
- Four message chips (outlined yellow, Inter Tight italic, original case):
  - "Submit the record by Friday 5 pm"
  - "Fee due on 25th"
  - "Class shifted to 2 pm, Lab 3"
  - "Send me the PPT tonight"
- Pill: **THEY SCROLL AWAY IN MINUTES → LOST MARKS, LATE FEES, MISSED MEETINGS**
- Optional: a blurred screenshot of a noisy group chat (names hidden) behind the chips. Use `SCREENSHOT: noisy-group-chat`.

**Speaker notes:** "Every one of us has lost a deadline in a college WhatsApp group. The message was there, but 200 jokes and forwards buried it."

---

### 4.3 Slide 3 — Reframe
**Layout:** two rows of flow chips, *Today* on top and *Missing layer* below, each with a label on the left.

- Headline: **IT'S NOT A TO-DO PROBLEM. IT'S A CAPTURE PROBLEM.**
- Row 1, label `TODAY`: chips (outlined) **MESSAGE ARRIVES → BURIED → FORGOTTEN → MISSED**. Draw the "MISSED" chip in the alert red #C8102E; it's the only red on the whole deck.
- Row 2, label `THE MISSING LAYER`: chips (filled yellow) **MESSAGE ARRIVES → CAUGHT → ACTION CARD → DONE**
- Pill: **TO-DO APPS ONLY WORK IF YOU TYPE THE TASK IN. NOBODY DOES.**

**Speaker notes:** "Calendars and to-do apps already exist. The failure happens earlier: nobody copies a message into them. Chitti removes that step."

---

### 4.4 Slide 4 — The idea in one picture
**Layout:** the callout box. On the left, the big quote; on the right, three short points.

- Quote (Anton, yellow): **"THE MOMENT A DEADLINE LANDS IN YOUR WHATSAPP GROUP, CHITTI CATCHES IT."**
- Points (Inter Tight):
  - READS EACH NOTIFICATION AS IT ARRIVES
  - AN AI ON THE PHONE SPOTS DEADLINES, PAYMENTS, MEETINGS, REQUESTS
  - TURNS EACH ONE INTO A ONE-TAP ACTION
- Pill: **SEES → UNDERSTANDS → REMEMBERS → ACTS**

**Speaker notes:** "Think of a friend who reads the group for you and slips you a note only when something needs you. That's Chitti."

---

### 4.5 Slide 5 — Introducing Chitti (how it feels)
**Layout:** a tagline pill at the top, then a **6-step card flow** in two rows of three, joined by arrows. A phone screenshot sits at the right edge.

- Pill: **CHITTI · YOUR DEADLINES, CAUGHT ON THE PHONE**
- Cards (alternate filled and outlined):
  1. **MESSAGE LANDS**: "Record submission tomorrow 10 am, Lab 2"
  2. **CHITTI READS IT**: on the phone, instantly
  3. **SPOTS A DEADLINE**: what · when · who
  4. **ACTION CARD** *(AT THE EVENT)*: "Deadline detected – add to calendar?"
  5. **ONE TAP**: calendar · reminder · draft reply
  6. **TODAY SCREEN**: everything caught, in one place
- Screenshot: `SCREENSHOT: today-screen`

**Speaker notes:** "Steps 1–3 and 5–6 work in the app today. The pop-up card in step 4 is what we build at the event."

---

### 4.6 Slide 6 — Architecture: today vs Chitti
**Layout:** two horizontal flows. The top one ("CLOUD ASSISTANTS") is outlined and dimmed; the bottom one ("CHITTI") uses filled yellow chips. A key-line pill sits at the bottom.

- Top flow, label `CLOUD ASSISTANTS`: **YOUR CHATS → INTERNET → SERVER AI → BACK TO YOU**, with small notes under it: `PRIVACY RISK · NEEDS NETWORK · COSTS PER CALL`
- Bottom flow, label `CHITTI`: **NOTIFICATION → QUICK FILTER → ON-DEVICE AI → ACTION CARD → CALENDAR · REMINDER · REPLY**
- Under the bottom flow, in JetBrains Mono: `EVERYTHING STAYS ON THE PHONE`
- Pill: **ONLY MESSAGES THAT PASS THE QUICK FILTER REACH THE AI → LOW BATTERY USE**

**Speaker notes:** "A cheap keyword-and-date filter runs first, and only likely commitments reach the model. Our design target is that about one message in ten reaches the AI. We'll measure the real ratio at the event."

---

### 4.7 Slide 7 — Why the phone, why iQOO
**Layout:** 3 cards in a row, with a hardware statement below.

- Headline: **THIS CAN ONLY EXIST ON A PHONE**
- Card 1 (filled): **PRIVATE**: YOUR CHATS NEVER LEAVE THE DEVICE. NOBODY WOULD UPLOAD THEIR GROUP CHATS TO A SERVER.
- Card 2 (outlined): **OFFLINE**: WORKS IN AIRPLANE MODE. NO ACCOUNT, NO SUBSCRIPTION.
- Card 3 (filled): **PHONE-ONLY**: NEEDS ANDROID'S NOTIFICATION ACCESS, ALARMS, CALENDAR AND SHARE SHEET. IT CAN'T BE A WEB APP.
- Statement (Anton, yellow): **A 2-BILLION-PARAMETER AI, RUNNING ON THE PHONE IN YOUR HAND.**
- Small line: `JUDGING: PHONE-FIRST ✓ · ON-DEVICE AI ✓ · REAL-WORLD UTILITY ✓`

**Speaker notes:** "The hackathon rewards local AI and phone-first builds. Chitti isn't on-device as a gimmick: privacy makes it the only way the product can exist."

---

### 4.8 Slide 8 — Under the hood
**Layout:** a **6-stage pipeline** of chips across the slide, a tech label under each (JetBrains Mono), then a strip showing code-mixed understanding and a summary pill.

| Stage | Chip | Tech label |
|---|---|---|
| 1 | CAPTURE | `NotificationListenerService` |
| 2 | DE-DUPLICATE | `SHA-256 · 5-MIN WINDOW` |
| 3 | FILTER | `KEYWORDS + DATES` |
| 4 | UNDERSTAND | `GEMMA 2B · MEDIAPIPE + RULE FALLBACK` |
| 5 | REMEMBER | `ROOM DATABASE (ON DEVICE)` |
| 6 | ACT | `ALARMS · CALENDAR · SHARE` |

- Code-mixed strip (outlined card, original case):
  - "25th lopu fee kattali, marchipovaddu" → **FEE PAYMENT · 25TH · URGENT**
  - "Record submission tomorrow 10 am, Lab 2" → **RECORD SUBMISSION · TOMORROW 10 AM · LAB 2**
- Pill: **UNDERSTANDS TELUGU-ENGLISH AND HINGLISH — HOW PEOPLE ACTUALLY TEXT**
- Optional small line: `ALSO BUILT: VOICE ASSISTANT · SCAM-LINK GUARD · PERSONAL MEMORY · FORM AUTOFILL`

**Speaker notes:** "If the model is still loading or unsure, a rule-based extractor takes over, so nothing is lost. The same pipeline also powers a voice assistant and a scam-link guard, but the core is catching commitments."

---

### 4.9 Slide 9 — Thank you and demo
**Layout:** a giant yellow title, the demo steps as small chips, and yellow concentric arcs in the bottom corners.

- Title: **THANK YOU**
- Sub: **LIVE DEMO: UNDER A MINUTE, IN AIRPLANE MODE**
- Demo chips, in order:
  1. NOISY GROUP CHAT
  2. "RECORD SUBMISSION TOMORROW 10 AM, LAB 2"
  3. CARD APPEARS
  4. ONE TAP → CALENDAR
  5. "25TH LOPU FEE KATTALI" CAUGHT TOO
  6. AIRPLANE MODE: STILL WORKS
- Footer: `OWL CODERS · K. NANDI VARDHAN REDDY · V. JAYA SAI KRISHNA · G. PAVAN SAI MANIKANTA`

**Speaker notes:** "Watch: a teammate sends a deadline into a noisy group, and Chitti catches it before anyone scrolls past."

---

### 4.10 If a shorter deck is needed (5 slides)
| 5-slide deck | Take from |
|---|---|
| 1. Problem | Slide 2 (put the team strip from slide 1 at the bottom) |
| 2. Solution | Slides 4 + 5 |
| 3. Architecture | Slides 6 + 8 |
| 4. Why on-device | Slide 7 |
| 5. Team and why we care | Slide 1's team card + slide 9's demo line |

---

## 5. Screenshots to capture (team to supply)

Take these on the phone in **dark mode**, with personal names blurred:

| Name | What it shows |
|---|---|
| `noisy-group-chat` | A busy WhatsApp group with a deadline message buried in it (names hidden) |
| `today-screen` | Chitti's Today tab with 2–3 caught commitments, one marked Urgent |
| `commitment-actions` | A commitment tapped open, showing Reply · Calendar · Remind |
| `extraction-lab` | The Extraction lab showing the fields pulled from "25th lopu fee kattali, marchipovaddu" |
| `voice-overlay` | The voice screen listening, with a live transcript |
| `action-card` | *(after the event build)* the "Deadline detected – add to calendar?" pop-up |

Use **placeholders** until they're supplied.

---

## 6. Submission form text (for reference; not a slide)

**Title:** Chitti – Never Miss a Deadline Buried in Your Group Chats

**Description:**
> Chitti is an Android app that catches commitments the moment they arrive. It listens to incoming notifications from WhatsApp, SMS and email. A small open-source LLM running fully on the iQOO phone detects deadlines, payments, meetings and requests, even in code-mixed Telugu-English and Hinglish. Each one becomes an action card: add to calendar, set a reminder or draft a reply in one tap. No message ever leaves the device, so private chats stay private and the app works offline. It is built for students and working professionals who lose important messages in noisy group chats. A two-stage pipeline, a lightweight filter followed by the LLM, keeps battery use low.

**What makes us stand out:**
> Our idea needs on-device AI for a real reason: nobody would upload their private chats to a cloud server, so local inference is the only way this product can exist. We face this problem every day in our own college WhatsApp groups, so we can test on real messages. Chitti also handles Telugu-English and Hinglish messages, which most productivity tools ignore.

---

## 7. Final checklist for the deck builder

- [ ] 9 slides, 16:9, black and #F0B31C only (plus one red "MISSED" chip on slide 3)
- [ ] Anton titles · Chakra Petch labels · Inter Tight body · JetBrains Mono tech labels
- [ ] Grain + diagonal light sweep background on every slide; arcs on the last slide
- [ ] Event label top-left and slide number bottom-right on every slide
- [ ] Team names spelled exactly as in §1; "OWL CODERS" spelled correctly
- [ ] Nothing in §2's "planned" list is shown as finished; each is marked **AT THE EVENT**
- [ ] No invented numbers; the only figure is "~150–300 notifications a day"
- [ ] No stock photos, robots, brains, sparkles or glows
- [ ] Speaker notes filled from §4
- [ ] Exported as .pptx and PDF, each under 25 MB
