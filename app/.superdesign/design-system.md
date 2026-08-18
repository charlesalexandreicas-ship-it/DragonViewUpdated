# Dragon View — Obsidian Plum design system proposal

## Direction

Obsidian Plum should feel agricultural, premium, and calm rather than neon or cosmetic. Use deep plum as structural chrome and interaction color; keep long-form content on warm ivory surfaces for daylight readability in farm conditions. Green is reserved for growth/success, not general branding.

## Color tokens

- `obsidian_plum_950`: `#160D17` — navigation bar, scanner-adjacent dark surface.
- `obsidian_plum_900`: `#241126` — primary app chrome and dark containers.
- `obsidian_plum_800`: `#341737` — pressed/strong container.
- `obsidian_plum_700`: `#4A214D` — primary button and active navigation.
- `obsidian_plum_600`: `#623064` — hover/focus/accent.
- `plum_orchid_400`: `#A66BA6` — selected indicator and decorative accent.
- `plum_mist_100`: `#EEE3EE` — primary container and selected row.
- `plum_mist_050`: `#F7F1F6` — subtle tinted panel.
- `warm_ivory`: `#FCF9F4` — app background.
- `surface`: `#FFFFFF` — cards and dialogs.
- `ink`: `#211B21` — primary text.
- `muted_ink`: `#6E646E` — secondary text.
- `outline`: `#D7CBD5` — fields/cards/dividers.
- `growth_green`: `#52745A` — success, healthy growth, ready states.
- `growth_green_soft`: `#E3EEE5` — success container.
- `harvest_amber`: `#A06417` — FIFO aging and warning.
- `error`: `#B3261E` — validation/destructive status.

## Material mapping

- `colorPrimary`: `obsidian_plum_700`
- `colorOnPrimary`: `#FFFFFF`
- `colorPrimaryContainer`: `plum_mist_100`
- `colorOnPrimaryContainer`: `obsidian_plum_900`
- `colorSecondary`: `growth_green`
- `colorSecondaryContainer`: `growth_green_soft`
- `colorSurface`: `warm_ivory`
- `colorSurfaceContainer`: `surface`
- `colorOnSurface`: `ink`
- `colorOutline`: `outline`

## Component treatment

- App bar: solid `obsidian_plum_900`, no gradient, white title/icons.
- Bottom navigation: white or `warm_ivory`, active item `obsidian_plum_700`, subtle `plum_mist_100` indicator.
- Primary button: 12dp radius, `obsidian_plum_700`, white label; pressed `obsidian_plum_800`.
- Secondary/outlined button: 12dp radius, transparent surface, 1dp `obsidian_plum_600` stroke.
- Cards: 14dp radius, white, 1dp outline, minimal elevation; metrics use a plum top rule or icon rather than full saturated fills.
- Text fields: 12dp radius, neutral outline; focused outline `obsidian_plum_600`; errors remain red.
- Dialogs/bottom sheets: 20dp top radius, warm-white surface, plum heading.
- Status colors: plum = selected/action; green = healthy/success; amber = warning/FIFO aging; red = error only.
- Typography: keep system sans for implementation compatibility; use weight and size hierarchy rather than decorative fonts.
- Accessibility: body text contrast at least 4.5:1; never use hue alone to communicate grade/readiness/error.
