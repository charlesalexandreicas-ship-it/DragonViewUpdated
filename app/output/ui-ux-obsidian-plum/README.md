# Dragon View — Obsidian Plum redesign

This directory contains the complete ten-board Dragon View UI/UX set recolored with the approved Obsidian Plum direction. The original magenta set remains unchanged in `../ui-ux-full/` for comparison.

## Palette

- App chrome: `#241126`
- Primary actions / active navigation: `#4A214D`
- Pressed / strong container: `#341737`
- Accent orchid: `#A66BA6`
- Selected container: `#EEE3EE`
- Subtle tinted panel: `#F7F1F6`
- App background: `#FCF9F4`
- Card / dialog surface: `#FFFFFF`
- Primary text: `#211B21`
- Secondary text: `#6E646E`
- Outline: `#D7CBD5`
- Growth / success: `#52745A`
- Growth container: `#E3EEE5`
- FIFO warning: `#A06417`
- Error: `#B3261E`
- Scanner dark surface: `#160D17`

## Treatment

- Solid Obsidian Plum app bars replace bright magenta gradients.
- Cards and forms remain white or warm ivory for outdoor readability.
- Plum communicates selection and actions.
- Green is reserved for healthy growth, availability, readiness, and success.
- Amber is reserved for FIFO aging and warnings; red is reserved for validation/errors.
- Components use restrained 12-14dp radii, thin outlines, and minimal elevation.

## Boards

1. Authentication
2. Dashboard and application shell
3. FIFO inventory
4. Record harvest add/edit states
5. Inventory details, quantity adjustment, and regrading
6. Sales and price management
7. New sale and fruit-item workflow
8. Planting guidance and record-group dialog
9. Sales analytics
10. Quality scanner states

## Design review canvas

Superdesign project: https://superdesign.dev/teams/bee02df1-b4ba-49f4-920c-c99c1363479e/projects/54a61e26-40b1-4dec-af80-dc176ac4588a

The canvas contains the current dashboard reproduction and the Obsidian Plum branch. The project-level token specification is stored at `C:\AndreiCopy\app\.superdesign\design-system.md`.

## Generation method

The dashboard comparison was created through the Superdesign existing-UI workflow. The ten saved boards were edited with built-in image generation in `style-transfer` mode, with strict instructions to preserve screen content, labels, actions, values, hierarchy, and layout while changing only color and subtle material surface styling.
