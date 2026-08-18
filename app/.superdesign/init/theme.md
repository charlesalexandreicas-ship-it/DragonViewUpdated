# Theme context

## Compact current token summary

- Framework: Native Android Material 3 DayNight, Java 17, min SDK 24.
- Font: Android `sans`; Material 3 type appearances.
- Current primary: `dragon_magenta #8E0B52`; dark `#65073A`.
- Current supporting green: `#5C8A5C`.
- Current surfaces: warm cream `#FFF8F0`, white `#FFFFFF`, pink variant `#FFE8F3`.
- Current outline: `#D9A9C1`; error `#B3261E`; warning `#8A5700`.
- Scanner surface: `#1A1A1A`.
- Existing explicit radii: authentication card 24dp; overview card 16dp.
- Existing spacing: main page 16dp; authentication 24dp outer / 28dp inner; form vertical gaps 12-20dp.
- Existing elevation: authentication card 4dp; most content cards use low/Material defaults.

## Raw colors

```xml
<resources>
    <color name="dragon_magenta">#8E0B52</color>
    <color name="dragon_magenta_dark">#65073A</color>
    <color name="dragon_violet_red">#C71585</color>
    <color name="dragon_deep_pink">#FF1493</color>
    <color name="dragon_green">#5C8A5C</color>
    <color name="dragon_surface">#FFF8F0</color>
    <color name="dragon_surface_variant">#FFE8F3</color>
    <color name="dragon_outline">#D9A9C1</color>
    <color name="dragon_green_soft">#E6F0E6</color>
    <color name="dragon_error">#B3261E</color>
    <color name="fifo_warning">#8A5700</color>
    <color name="fifo_warning_soft">#FFF0CC</color>
    <color name="fifo_urgent_soft">#FFE2DE</color>
    <color name="dragon_scanner">#1A1A1A</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#1A1A1A</color>
</resources>
```

## Raw theme mapping

```xml
<style name="Theme.DragonView" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">@color/dragon_magenta</item>
    <item name="colorOnPrimary">@color/white</item>
    <item name="colorPrimaryContainer">@color/dragon_surface_variant</item>
    <item name="colorOnPrimaryContainer">@color/dragon_magenta_dark</item>
    <item name="colorSecondary">@color/dragon_green</item>
    <item name="colorOnSecondary">@color/white</item>
    <item name="colorSecondaryContainer">@color/dragon_green_soft</item>
    <item name="colorSurface">@color/dragon_surface</item>
    <item name="colorSurfaceContainer">@color/white</item>
    <item name="colorOnSurface">@color/black</item>
    <item name="colorOutline">@color/dragon_outline</item>
    <item name="colorError">@color/dragon_error</item>
    <item name="android:fontFamily">sans</item>
</style>
```
