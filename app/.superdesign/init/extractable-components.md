# Extractable components

## AppShell
- Source: `src/main/res/layout/activity_main.xml`
- Category: layout
- Description: Gradient Material toolbar, navigation host, and five-item labeled bottom navigation.
- Extractable props: activeDestination, title, showScannerAction.
- Hardcoded: Home, Inventory, Sales, Planting, Analytics; scanner and sign-out actions.

## InventoryLotCard
- Source: `src/main/res/layout/item_inventory_lot.xml`
- Category: basic
- Description: FIFO lot summary with batch, size, grade, pieces, date, and aging status.
- Extractable props: batchNumber, size, grade, availablePieces, harvestDate, warningState.

## SaleSummaryCard
- Source: `src/main/res/layout/item_sale.xml`
- Category: basic
- Description: Completed sale summary card.
- Extractable props: customer, date, total, paymentMethod, pieces, weight.

## PlantingGroupCard
- Source: `src/main/res/layout/item_planting.xml`
- Category: basic
- Description: Planting group facts and current growth guidance.
- Extractable props: recordNumber, graftingDate, variety, location, plantCount, readiness.

## MetricCard
- Source: `src/main/res/values/themes.xml` style `DashboardMetric`
- Category: basic
- Description: Centered dashboard KPI tile.
- Extractable props: label, value, selected/status color.
