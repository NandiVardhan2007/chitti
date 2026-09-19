# iQOO Hackathon website design system (iqoo.reskilll.com)

Source: the site's compiled Tailwind v4 CSS and Google Fonts @font-face rules, pasted by the user on 2026-09-19.

## Fonts (all on Google Fonts)
| Token | Family | Used for |
|---|---|---|
| --font-display | **Anton** (400) | All h1–h6 headings, letter-spacing .01em, tight leading (.82–.95) |
| --font-label | **Chakra Petch** (400/500/600/700) | Buttons, nav, labels: uppercase, bold, wide tracking (.05–.3em) |
| --font-sans | **Inter Tight** (300–900, with italic), falling back to Inter | Body text |
| --font-mono | **JetBrains Mono** (400/500/700) | Code and data |

The `.italic-slant` effect is italic plus skew(-10deg).

## Colours
| Token | Hex | Role |
|---|---|---|
| iqoo | #F0B31C | Primary brand yellow: buttons, fills, rings, underlines |
| iqoo-deep | #C8920A | Hover/pressed state, darker yellow |
| iqoo-display | #B9860A | Yellow display text on light backgrounds |
| iqoo-text | #8A6205 | Readable yellow-brown text on light backgrounds |
| iqoo-soft | #FEF6DD | Pale yellow tint for backgrounds |
| ink | #000000 | Main text, dark sections |
| ink-soft | #1A1A1A | Dark surfaces |
| ink-mute | #5A5A5A | Secondary text |
| paper | #FAFAF7 | Page background (light, off-white) |
| paper-deep | #F2F2EC | Alternate light panels |
| line | #E8E5DB | Hairlines and borders |
| dark bg | #050508 | Dark hero/sections |
| alert red | #C8102E / #A30019 | Warnings and emphasis |
| city colours | BLR #F0B31C, HYD #7C4DDA, CHE #2E7CE4, PUNE #E8801F | Per-city accents |

On dark sections, white text runs at 40–90% opacity, and borders are white at 8–15% or yellow at 25–70%.

## Shape and effects
- Buttons have a small radius (.25rem). The primary button is a #F0B31C fill with black Chakra Petch bold uppercase text, turning #C8920A on hover.
- Cards use rounded-2xl or 3xl (1–1.6rem).
- Shadows are soft and deep, e.g. 0 18px 44px -14px rgba(0,0,0,.45). There is also a yellow glow: 0 12px 28px -10px rgba(240,179,28,.75).
- Light hero gradient: radial-gradient(100% 60% at 50% 0, #fff, #f3f2ed 42%, #e8e6df).
- Accent line: a gradient from transparent through iqoo/60 to transparent.

## Mapping onto the reference deck
| Reference deck | Website replacement |
|---|---|
| Bicubik titles | Anton |
| Space Mono pills and labels | Chakra Petch Bold, with wide tracking |
| Space Mono Italic body | Inter Tight (or JetBrains Mono for tech details) |
| Montserrat small labels | Chakra Petch / Inter Tight |
| #FFDE59 yellow | #F0B31C (with #C8920A for depth) |
| Black grain background | #050508 base with the same diagonal grey sweep and grain |
