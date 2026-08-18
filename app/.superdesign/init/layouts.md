# Shared layouts

## Application shell

Source: `src/main/res/layout/activity_main.xml`

```xml
<androidx.constraintlayout.widget.ConstraintLayout android:layout_width="match_parent" android:layout_height="match_parent">
    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="0dp"
        android:layout_height="?attr/actionBarSize"
        android:background="@drawable/dragon_gradient"
        android:theme="@style/ThemeOverlay.Material3.ActionBar"
        app:titleTextColor="@color/white" />
    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:navGraph="@navigation/main_navigation" />
    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNavigation"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:background="@color/white"
        app:itemIconTint="@color/navigation_item_color"
        app:itemTextColor="@color/navigation_item_color"
        app:itemActiveIndicatorStyle="@style/Widget.Material3.BottomNavigationView.ActiveIndicator"
        app:labelVisibilityMode="labeled"
        app:menu="@menu/bottom_navigation_menu" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

## Bottom navigation

Source: `src/main/res/menu/bottom_navigation_menu.xml`

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@+id/dashboardFragment" android:icon="@drawable/ic_home" android:title="Home" />
    <item android:id="@+id/inventoryFragment" android:icon="@drawable/ic_inventory" android:title="Inventory" />
    <item android:id="@+id/salesFragment" android:icon="@drawable/ic_sales" android:title="Sales" />
    <item android:id="@+id/plantingFragment" android:icon="@drawable/ic_plant" android:title="Planting" />
    <item android:id="@+id/analyticsFragment" android:icon="@drawable/ic_chart" android:title="Analytics" />
</menu>
```

The toolbar also exposes `Quality Scanner` and overflow `Sign out` actions from `src/main/res/menu/toolbar_menu.xml`.
