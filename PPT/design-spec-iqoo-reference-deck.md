# Design spec — iQOO Hackathon reference deck (Agent Gate by IQOONIC)

Source: "IQOO HACKATHON" PDF, made in Canva, 9 slides, 1440×810 pt (16:9).

## Background
- One full-bleed raster image repeated on every slide (3250×1750 JPEG).
- Near-black base (#030303–#131313) with a soft diagonal grey light sweep (peaks around #3d3d3d–#5c5c5c) from top-left to bottom-right, plus a fine film-grain/noise texture.
- Closing slide adds thin yellow concentric arcs in the bottom corners.

## Colour palette
| Role | Hex |
|---|---|
| Primary accent (titles, filled pills/cards, arrows, outlines) | #FFDE59 |
| Background base | #0E0E0E – #1F1F1F (grain gradient) |
| Text on yellow | #000000 |
| Text on dark | #FFFFFF, or #FFDE59 for headings |
| Card outline | 1px #FFDE59 at reduced opacity |

It uses only two colours: black and yellow, with no other accents.

## Fonts
| Use | Font | Notes |
|---|---|---|
| Slide titles / hero | **Bicubik** | Geometric, squared, all caps, yellow. Canva font, not on Google Fonts; close free alternatives are Orbitron, Michroma or Audiowide |
| Pills, labels, card titles, body | **Space Mono** Bold / Regular / Italic | All caps and wide tracking; card body text is Space Mono Italic |
| Small labels ("IQOO HACKATHON 2026", "THE UNCOMFORTABLE TRUTH") | **Montserrat** Bold | Small, white |
| Team names | **Roboto** Italic | On a yellow card |
| One-off captions | Playfair Display, Copperplate Gothic | Probably unintended; drop them |

## Components
- **Pill banner**: a fully rounded yellow bar with black Space Mono Bold caps text. Used for taglines and the key takeaway at the bottom of a slide.
- **Flow chip**: a small rounded pill, either filled yellow or outlined in yellow, linked by yellow → arrows. Used for "Today vs Our way" flows.
- **Card**: a rounded rectangle with a radius of about 12% of its height. Cards alternate between filled yellow (black text) and outlined yellow on dark (yellow title, white italic body), in 3-column grids.
- **Hexagonal tag**: an outlined elongated hexagon holding a letter-spaced "BY IQOONIC".
- **Callout box**: a large outlined rounded box holding a quote on the left and a list of points on the right.
- All text is uppercase throughout.

## Slide flow
1. Title: event label, project name and team card
2. Problem hook: a question headline, a pill and an outlined capability box
3. Reframe ("It's a trust problem"): Today flow vs Missing layer flow
4. Analogy (banking OTP) → the principle
5. Introducing the product: tagline pill and a 6-step card flow
6. Architecture diagram: Today vs Our solution flow, with a key-line pill
7. Why the phone / why iQOO: 3 cards and a hardware statement
8. Under the hood: a 6-stage pipeline and a summary pill
9. Thank you: a giant yellow title and corner arcs

## Website fonts and colours
See `design-system-iqoo-website.md`. For the Chitti deck, keep this layout, background and set of components, but use the website's fonts (Anton, Chakra Petch, Inter Tight, JetBrains Mono) and its yellow, #F0B31C.
