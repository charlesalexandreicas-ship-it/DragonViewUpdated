# Navigation map

Router: Android Navigation Component.
Full config: `src/main/res/navigation/main_navigation.xml`.

| Destination | Label | Component | Actions |
|---|---|---|---|
| dashboardFragment | Dashboard | `ui/dashboard/DashboardFragment.java` | View analytics |
| inventoryFragment | Inventory | `ui/inventory/InventoryFragment.java` | Record harvest, open details |
| inventoryDetailsFragment | Inventory Details | `ui/inventory/InventoryDetailsFragment.java` | Adjust quantity, regrade |
| harvestFragment | Record Harvest | `ui/inventory/HarvestFragment.java` | Add/update/remove combination, save batch |
| salesFragment | Sales | `ui/sales/SalesFragment.java` | New sale, add fruit item, manage prices |
| plantingFragment | Plant Guidance | `ui/planting/PlantingFragment.java` | Record planting group |
| analyticsFragment | Sales Analytics | `ui/analytics/AnalyticsFragment.java` | Select period/date |
| scannerFragment | Quality Scanner | `ui/scanner/ScannerFragment.java` | Upload, scan, retake; currently deferred |

Authentication entry: `ui/LoginActivity.java` with sign-in/create-account modes. Main shell: `ui/MainActivity.java`.
