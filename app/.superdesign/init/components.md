# Shared UI components

Dragon View is a native Android Java application using Material Components 1.13.0, not a React/web project. Shared primitives are supplied by Material 3 (`MaterialButton`, `MaterialCardView`, `TextInputLayout`, `MaterialToolbar`, `BottomNavigationView`, `AlertDialog`) and configured through Android resources.

## Shared theme styles

Source: `src/main/res/values/themes.xml`

```xml
<style name="DashboardMetric" parent="Widget.Material3.CardView.Filled">
    <item name="android:layout_width">0dp</item>
    <item name="android:layout_height">100dp</item>
    <item name="android:layout_columnWeight">1</item>
    <item name="android:gravity">center</item>
    <item name="android:background">@color/white</item>
    <item name="android:textColor">@color/dragon_magenta</item>
    <item name="android:textSize">24sp</item>
    <item name="android:textStyle">bold</item>
</style>
<style name="DashboardOverviewValue" parent="TextAppearance.Material3.TitleMedium">
    <item name="android:layout_width">match_parent</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:textColor">@color/dragon_magenta</item>
    <item name="android:textStyle">bold</item>
</style>
<style name="DashboardOverviewLabel" parent="TextAppearance.Material3.BodySmall">
    <item name="android:layout_width">match_parent</item>
    <item name="android:layout_height">wrap_content</item>
    <item name="android:layout_marginTop">2dp</item>
</style>
```

## Reusable row layouts

- `src/main/res/layout/item_inventory_lot.xml` - FIFO inventory lot card.
- `src/main/res/layout/item_harvest_entry.xml` - editable/removable size-grade combination.
- `src/main/res/layout/item_sale.xml` - completed sale summary.
- `src/main/res/layout/item_planting.xml` - planting group and maturity guidance.
- `src/main/res/layout/item_analytics_summary.xml` - size/grade analytics summary.

These layouts are inflated by their matching RecyclerView adapters and should retain their actual source as design context.
