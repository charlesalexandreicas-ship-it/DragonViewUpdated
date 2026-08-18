# Screen dependency trees

## Authentication
- `src/main/java/ph/dragonview/mobile/ui/LoginActivity.java`
  - `src/main/res/layout/activity_login.xml`
  - `src/main/res/values/strings.xml`
  - `src/main/res/drawable/dragon_auth_background.xml`
  - `src/main/res/drawable/ic_dragon_fruit.xml`

## Dashboard
- `src/main/java/ph/dragonview/mobile/ui/dashboard/DashboardFragment.java`
  - `src/main/res/layout/fragment_dashboard.xml`
  - `src/main/res/values/themes.xml`

## Inventory and Harvest
- `src/main/java/ph/dragonview/mobile/ui/inventory/InventoryFragment.java`
  - `src/main/res/layout/fragment_inventory.xml`
  - `src/main/java/ph/dragonview/mobile/ui/inventory/InventoryAdapter.java`
    - `src/main/res/layout/item_inventory_lot.xml`
- `src/main/java/ph/dragonview/mobile/ui/inventory/InventoryDetailsFragment.java`
  - `src/main/res/layout/fragment_inventory_details.xml`
- `src/main/java/ph/dragonview/mobile/ui/inventory/HarvestFragment.java`
  - `src/main/res/layout/fragment_harvest.xml`
  - `src/main/res/layout/item_harvest_entry.xml`

## Sales
- `src/main/java/ph/dragonview/mobile/ui/sales/SalesFragment.java`
  - `src/main/res/layout/fragment_sales.xml`
  - `src/main/res/layout/dialog_sale.xml`
  - `src/main/res/layout/dialog_sale_item.xml`
  - `src/main/res/layout/dialog_price.xml`
  - `src/main/java/ph/dragonview/mobile/ui/sales/SalesAdapter.java`
    - `src/main/res/layout/item_sale.xml`

## Planting, Analytics, and Scanner
- `src/main/java/ph/dragonview/mobile/ui/planting/PlantingFragment.java`
  - `src/main/res/layout/fragment_planting.xml`
  - `src/main/res/layout/dialog_planting.xml`
  - `src/main/res/layout/item_planting.xml`
- `src/main/java/ph/dragonview/mobile/ui/analytics/AnalyticsFragment.java`
  - `src/main/res/layout/fragment_analytics.xml`
  - `src/main/res/layout/item_analytics_summary.xml`
- `src/main/java/ph/dragonview/mobile/ui/scanner/ScannerFragment.java`
  - `src/main/res/layout/fragment_scanner.xml`

All authenticated screens are wrapped by `activity_main.xml`, `main_navigation.xml`, `bottom_navigation_menu.xml`, and `toolbar_menu.xml`.
