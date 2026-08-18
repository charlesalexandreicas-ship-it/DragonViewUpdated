# Dragon View complete UI/UX concept set

Source of truth: `C:\AndreiCopy\app\src\main`.

These concept boards were generated from the implemented Android navigation, XML layouts, and Java action handlers. They deliberately retain the current palette as a provisional baseline so color, typography, component shape, and layout can be reviewed separately.

## Boards and covered states

1. `01-authentication.png`
   - Sign in
   - Create local account
   - Inline validation and loading
2. `02-dashboard-and-shell.png`
   - Populated farm overview
   - Dashboard loading/empty state
   - App bar, scanner action, overflow sign-out, and bottom navigation
3. `03-fifo-inventory.png`
   - Populated FIFO list
   - Empty inventory
   - Loading/error/retry
4. `04-record-harvest.png`
   - Empty harvest form
   - Added size/grade combinations
   - Edit/update/remove combination, duplicate validation, and date picker
5. `05-inventory-details-actions.png`
   - Inventory facts and transaction history
   - Adjust quantity dialog
   - Regrade dialog and Grade C restriction
6. `06-sales-and-prices.png`
   - Sales list
   - Empty sales state
   - Price Management and invalid-price validation
7. `07-new-sale-workflow.png`
   - Empty New Sale form
   - Add Fruit Item dialog and availability validation
   - Populated electronic checkout and payment validation
8. `08-planting-guidance.png`
   - Planting group list and ready state
   - Empty planting state
   - Record Planting Group, date picker, and validation
9. `09-sales-analytics.png`
   - KPIs, revenue trend, and size/grade summary
   - Date picker
   - Empty/loading selected-period state
10. `10-quality-scanner.png`
    - Awaiting image/classification
    - Image preview
    - Implemented deferred-model Snackbar state

## Verified implementation boundaries

- Bottom navigation is Home, Inventory, Sales, Planting, Analytics.
- Quality Scanner is opened from the toolbar.
- Inventory uses pieces, size, grade, batch, harvest date, and FIFO ordering.
- Harvest sizes are EXTRA_SMALL, SMALL, MEDIUM, LARGE, and JUMBO; grades are A, B, and C.
- Sales payments are CASH, GCASH, MAYA, OTHER_E_WALLET, and BANK_TRANSFER.
- Planting currently supports creation but not edit or delete.
- Existing sales are listed but do not have edit, delete, or refund actions.
- The current scanner actions only show `MobileNetV2 model integration is deferred.`
- Farmer management, notification center, and profile/settings screens are not implemented and are excluded.

## Provisional design tokens shown

- Primary: `#8E0B52`
- Primary dark: `#65073A`
- Supporting green: `#5C8A5C`
- Warm background: `#FFF8F0`
- Warning: `#8A5700`
- Scanner surface: `#1A1A1A`

## Prompt set

Built-in image generation was used in `ui-mockup` mode. Each board prompt specified the exact implemented screen labels, fields, actions, validation states, navigation constraints, Material 3 Android presentation, current provisional tokens, and prohibited unimplemented features. Targeted `precise-object-edit` passes corrected the Inventory app-bar label and fruit imagery, the Sales bottom-navigation labels, and the New Sale dragon-fruit imagery.

These are visual direction boards, not pixel-accurate screenshots or implementation assets. Once the design direction is approved, each state can be refined into a standalone screen specification and then implemented in the Android resources.
