package ph.dragonview.mobile.ui;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.hamcrest.Matchers.allOf;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.chip.Chip;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.model.FruitPrice;
import ph.dragonview.mobile.data.model.InventoryLot;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.databinding.FragmentDashboardBinding;
import ph.dragonview.mobile.databinding.FragmentAnalyticsBinding;
import ph.dragonview.mobile.ui.analytics.AnalyticsFragment;
import ph.dragonview.mobile.ui.inventory.HarvestFragment;
import ph.dragonview.mobile.ui.analytics.RevenueTrendChartView;
import ph.dragonview.mobile.ui.planting.PlantingFragment;
import ph.dragonview.mobile.ui.sales.SalesFragment;

/** Local UI regression checks. Never signs in or submits valid cloud records. */
@RunWith(AndroidJUnit4.class)
public final class HomeCleanupInstrumentedTest {
    private Context target() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private Context themed(float fontScale) {
        Configuration config = new Configuration(target().getResources().getConfiguration());
        config.fontScale = fontScale;
        return new ContextThemeWrapper(target().createConfigurationContext(config), R.style.Theme_DragonView);
    }

    private void measure(View view, int widthDp, int heightDp) {
        float density = view.getResources().getDisplayMetrics().density;
        view.measure(View.MeasureSpec.makeMeasureSpec((int) (widthDp * density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((int) (heightDp * density), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    private void capture(View view, String name) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        save(bitmap, name);
    }

    private void save(Bitmap bitmap, String name) {
        File directory = new File(target().getFilesDir(), "ui-cleanup");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception error) {
            throw new AssertionError(error);
        } finally {
            bitmap.recycle();
        }
    }

    @Test public void homeSummaryAndQuickActionsFitAtSmallAndLargeTextSizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.5f}) {
                for (int width : new int[]{320, 360, 411, 600}) {
                    FragmentDashboardBinding ui = FragmentDashboardBinding.inflate(
                            LayoutInflater.from(themed(scale)));
                    // Explicit fixtures for rendering, not persisted or displayed as live farm data.
                    ui.inventoryValue.setText("12500");
                    ui.batchesValue.setText("12");
                    ui.monthlyRevenueValue.setText("₱125,000.00");
                    ui.plantingValue.setText("16");
                    measure(ui.getRoot(), width, 1200);
                    assertEquals(ui.viewInventoryAction.getHeight(), ui.viewSalesAction.getHeight());
                    assertEquals(ui.viewAnalyticsButton.getHeight(), ui.viewPlantGuidanceAction.getHeight());
                    assertEquals(1, ui.monthlyRevenueValue.getLineCount());
                    assertTrue(ui.monthlyRevenueValue.getLayout()
                            .getEllipsisCount(ui.monthlyRevenueValue.getLineCount() - 1) == 0);
                    View[] cards = {
                            (View) ui.batchesValue.getParent(),
                            (View) ui.inventoryValue.getParent(),
                            (View) ui.monthlyRevenueValue.getParent(),
                            (View) ui.plantingValue.getParent()
                    };
                    for (int index = 1; index < cards.length; index++) {
                        assertEquals(cards[0].getWidth(), cards[index].getWidth());
                        assertEquals(cards[0].getHeight(), cards[index].getHeight());
                    }
                    capture(ui.getRoot(), "home-" + width + "-" + scale);
                }
            }
        });
    }

    @Test public void homeQuickActionsKeepTheirIcons() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            FragmentDashboardBinding ui = FragmentDashboardBinding.inflate(
                    LayoutInflater.from(themed(1f)));
            measure(ui.getRoot(), 360, 900);
            MaterialButton[] actions = {ui.viewInventoryAction, ui.viewSalesAction,
                    ui.viewAnalyticsButton, ui.viewPlantGuidanceAction};
            String[] labels = {"View stock", "Sales", "Analytics", "Guidance"};
            for (int index = 0; index < actions.length; index++) {
                assertEquals(labels[index], actions[index].getText().toString());
            }
            for (MaterialButton action : actions) {
                assertNotNull(action.getIcon());
                assertTrue(action.getIconSize() > 0);
            }
        });
    }

    @Test public void attachedHomeActionsRenderWithIcons() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                FragmentDashboardBinding ui = FragmentDashboardBinding.inflate(activity.getLayoutInflater());
                activity.setContentView(ui.getRoot());
                measure(ui.getRoot(), 360, 900);
                capture(ui.getRoot(), "home-attached-360");
            });
        }
    }

    @Test public void reportingPillsWrapAndPreserveAllFourPeriodMappings() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.5f}) {
                FragmentAnalyticsBinding ui = FragmentAnalyticsBinding.inflate(LayoutInflater.from(themed(scale)));
                measure(ui.getRoot(), 320, 900);
                int[] ids = {R.id.periodDailyButton, R.id.periodWeeklyButton,
                        R.id.periodMonthlyButton, R.id.periodAnnualButton};
                String[] periods = {"daily", "weekly", "monthly", "yearly"};
                AnalyticsFragment fragment = new AnalyticsFragment();
                try {
                    Field binding = AnalyticsFragment.class.getDeclaredField("binding");
                    binding.setAccessible(true);
                    binding.set(fragment, ui);
                    Method selected = AnalyticsFragment.class.getDeclaredMethod("selectedPeriod");
                    selected.setAccessible(true);
                    assertEquals(ids[0], ui.periodInput.getCheckedChipId());
                    for (int i = 0; i < ids.length; i++) {
                        Chip chip = ui.periodInput.findViewById(ids[i]);
                        chip.performClick();
                        assertEquals(ids[i], ui.periodInput.getCheckedChipId());
                        assertEquals(1, ui.periodInput.getCheckedChipIds().size());
                        assertEquals(periods[i], selected.invoke(fragment));
                        chip.performClick();
                        assertTrue(chip.isChecked()); // Selection cannot be cleared.
                        float density = chip.getResources().getDisplayMetrics().density;
                        assertTrue(chip.getHeight() >= 48 * density);
                        assertTrue(chip.getRight() <= ui.periodInput.getWidth());
                        if (i > 0) {
                            View previous = ui.periodInput.getChildAt(i - 1);
                            assertTrue(chip.getTop() > previous.getTop()
                                    || chip.getLeft() - previous.getRight() >= 8 * density);
                        }
                    }
                    if (scale > 1f) assertTrue(ui.periodAnnualButton.getTop() > ui.periodDailyButton.getTop());
                    ui.periodInput.check(ids[0]);
                    capture(ui.getRoot(), "analytics-320-" + scale);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            }
        });
    }

    @Test public void onePointRevenueTrendDrawsOnlyItsObservedPoint() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RevenueTrendChartView chart = new RevenueTrendChartView(themed(1f), null);
            chart.setData(Collections.singletonList(
                    new SalesAnalytics.Trend("Sep 18", 1250d, 12)), "daily");
            measure(chart, 360, 280);
            Bitmap bitmap = Bitmap.createBitmap(
                    chart.getWidth(), chart.getHeight(), Bitmap.Config.ARGB_8888);
            chart.draw(new Canvas(bitmap));
            int expected = target().getColor(R.color.dragon_magenta);
            int matchingPixels = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    if (bitmap.getPixel(x, y) == expected) matchingPixels++;
                }
            }
            assertTrue("A one-point series must show its observed point",
                    matchingPixels > 5);
            assertTrue("A one-point series must not invent a horizontal line",
                    matchingPixels < chart.getWidth());
            assertTrue(String.valueOf(chart.getContentDescription()).contains("daily"));
            save(bitmap, "analytics-single-point");
        });
    }

    @Test public void newSaleOpensWhenPricesAndInventoryAreReady() {
        assumeFalse(new SessionManager(target()).isAuthenticated());
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                SalesFragment fragment = new SalesFragment();
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment).commitNow();
                try {
                    setField(fragment, "saleDataGeneration", 1000);
                    setField(fragment, "salePrerequisitesLoading", false);
                    setField(fragment, "salePrerequisitesReady", true);
                    setField(fragment, "prices", Collections.singletonList(
                            new FruitPrice(1, "A", "MEDIUM", 100)));
                    setField(fragment, "inventory", Collections.singletonList(
                            new InventoryLot(1, "TEST-BATCH", "2026-09-18",
                                    "MEDIUM", "A", 10, true)));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            onView(withId(R.id.newSaleButton)).perform(click());
            onView(withText("New Sale")).check(matches(isDisplayed()));
            onView(withText("Cancel")).perform(click());
        }
    }

    @Test public void saveHarvestCommitsThePendingCombination() {
        assumeFalse(new SessionManager(target()).isAuthenticated());
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                HarvestFragment fragment = new HarvestFragment();
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment).commitNow();
                try {
                    Field bindingField = HarvestFragment.class.getDeclaredField("binding");
                    bindingField.setAccessible(true);
                    Object binding = bindingField.get(fragment);
                    Field piecesInput = binding.getClass().getField("piecesInput");
                    ((TextView) piecesInput.get(binding)).setText("25");
                    Method commit = HarvestFragment.class
                            .getDeclaredMethod("commitEditorForSave");
                    commit.setAccessible(true);
                    assertEquals(true, commit.invoke(fragment));
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            onView(withText("1 combination • 25 pieces")).check(matches(isDisplayed()));
        }
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test public void everyQuickActionCanReturnToHome() {
        // Reuse the developer's signed-in session without creating or changing cloud records.
        assumeTrue(new SessionManager(target()).isAuthenticated());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.List<int[]> routes = new java.util.ArrayList<>();
            routes.add(new int[]{R.id.viewInventoryAction, R.id.inventoryFragment});
            routes.add(new int[]{R.id.viewSalesAction, R.id.salesFragment});
            if (new SessionManager(target()).isWorkspaceOwner()) {
                routes.add(new int[]{R.id.viewAnalyticsButton, R.id.analyticsFragment});
            }
            routes.add(new int[]{R.id.viewPlantGuidanceAction, R.id.plantingFragment});
            for (int[] route : routes) {
                onView(withId(route[0])).perform(click());
                assertDestination(scenario, route[1]);
                onView(allOf(withId(R.id.dashboardFragment),
                        isDescendantOfA(withId(R.id.bottomNavigation)))).perform(click());
                assertDestination(scenario, R.id.dashboardFragment);
                onView(allOf(withText("Farm overview"),
                        isDescendantOfA(withId(R.id.nav_host)))).check(matches(isDisplayed()));
            }
        }
    }

    @Test public void workspaceScreenIsReachableAndUsesFocusedNavigation() {
        assumeTrue(new SessionManager(target()).isAuthenticated());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);
                assertNotNull(host);
                host.getNavController().navigate(R.id.settingsFragment);
            });
            onView(withId(R.id.workspaceCard)).perform(click());
            assertDestination(scenario, R.id.workspaceFragment);
            onView(withText("ACTIVE WORKSPACE")).check(matches(isDisplayed()));
            onView(withId(R.id.createWorkspaceButton)).check(matches(isDisplayed()));
            onView(withId(R.id.joinWorkspaceButton)).check(matches(isDisplayed()));
            onView(withId(R.id.bottomNavigation)).check(matches(
                    withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.scannerButton)).check(matches(
                    withEffectiveVisibility(Visibility.GONE)));
            scenario.onActivity(activity -> assertTrue(activity.onSupportNavigateUp()));
            assertDestination(scenario, R.id.settingsFragment);
        }
    }

    @Test public void plantGuidanceActionsUseBalancedHierarchy() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View details = LayoutInflater.from(themed(1f))
                    .inflate(R.layout.fragment_planting_details, null, false);
            measure(details, 360, 900);
            View milestone = details.findViewById(R.id.updateStageButton);
            View observation = details.findViewById(R.id.addRecordButton);
            View photo = details.findViewById(R.id.addPhotoButton);
            assertEquals(observation.getWidth(), photo.getWidth());
            assertEquals(observation.getHeight(), photo.getHeight());
            assertEquals(observation.getTop(), photo.getTop());
            assertTrue(milestone.getWidth() > observation.getWidth());
            View secondaryRow = (View) observation.getParent();
            assertTrue(secondaryRow.getTop() >= milestone.getBottom());
            capture(details, "plant-guidance-360");
        });
    }

    @Test public void workspaceActionsStayFlat() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View workspace = LayoutInflater.from(themed(1f))
                    .inflate(R.layout.fragment_workspace, null, false);
            measure(workspace, 360, 1100);
            int[] ids = {R.id.createWorkspaceButton, R.id.joinWorkspaceButton,
                    R.id.regenerateInviteButton, R.id.deleteWorkspaceButton};
            for (int id : ids) {
                View button = workspace.findViewById(id);
                assertNotNull(button);
                assertNull(button.getStateListAnimator());
                button.setPressed(true);
                assertEquals(0f, button.getElevation(), 0f);
                assertEquals(0f, button.getTranslationZ(), 0f);
                button.setPressed(false);
            }
        });
    }

    @Test public void ownerCanReachPriceManagement() {
        SessionManager session = new SessionManager(target());
        assumeTrue(session.isAuthenticated());
        assumeFalse(session.isWorkspaceFarmer());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.nav_host);
                assertNotNull(host);
                host.getNavController().navigate(R.id.salesFragment);
            });
            onView(withId(R.id.priceButton)).check(matches(isDisplayed())).perform(click());
            onView(withText("Price Management")).check(matches(isDisplayed()));
            onView(withText("Cancel")).perform(click());
        }
    }

    private void assertDestination(ActivityScenario<MainActivity> scenario, int destinationId) {
        scenario.onActivity(activity -> {
            NavHostFragment host = (NavHostFragment) activity.getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host);
            assertNotNull(host);
            assertNotNull(host.getNavController().getCurrentDestination());
            assertEquals(destinationId, host.getNavController().getCurrentDestination().getId());
        });
    }

    @Test public void recordPlantingActionsStayFlatAndInvalidSaveDoesNotDismiss() {
        // Do not touch a developer's authenticated session or create test cloud data.
        assumeFalse(new SessionManager(target()).isAuthenticated());
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> activity.getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new PlantingFragment()).commitNow());
            onView(withId(R.id.addButton)).perform(click());
            for (String label : new String[]{"Cancel", "Save"}) {
                onView(withText(label)).check((view, error) -> {
                    if (error != null) throw error;
                    assertNull(view.getStateListAnimator());
                    view.setPressed(true);
                    assertEquals(0f, view.getElevation(), 0f);
                    assertEquals(0f, view.getTranslationZ(), 0f);
                    view.setPressed(false);
                });
            }
            onView(withText("Save")).perform(click());
            onView(withId(R.id.errorText)).check(matches(withSubstring("Enter a farm section")));
            save(InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(), "record-planting");
            onView(withText("Cancel")).perform(click());
            onView(withId(R.id.addButton)).check(matches(isDisplayed()));
        }
    }
}
