package ph.dragonview.mobile.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.widget.ImageViewCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.theme.MaterialComponentsViewInflater;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.SalesAnalytics;
import ph.dragonview.mobile.ui.analytics.RevenueTrendChartView;

/**
 * Session-free color regression checks. Inflates layouts without starting an Activity,
 * Fragment, repository, or network request. Preview values exist only in these views.
 * PNGs are layout renders, not proof of authentication or live Supabase behavior.
 */
@RunWith(AndroidJUnit4.class)
public final class PaletteInstrumentedTest {
    private static final int WIDTH_DP = 360;
    private static final int HEIGHT_DP = 1200;
    private static final int[] NIGHT_MODES = {
            Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES
    };

    private Context target() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private Context themed(int nightMode) {
        Configuration configuration = new Configuration(target().getResources().getConfiguration());
        configuration.fontScale = 1f;
        configuration.screenWidthDp = WIDTH_DP;
        configuration.screenHeightDp = HEIGHT_DP;
        configuration.smallestScreenWidthDp = WIDTH_DP;
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return new ContextThemeWrapper(target().createConfigurationContext(configuration),
                R.style.Theme_DragonView);
    }

    private LayoutInflater inflater(Context context) {
        // Mirror the app's Material/AppCompat view substitution without launching an
        // Activity. In particular plain ImageView tags must consume app:tint.
        LayoutInflater inflater = LayoutInflater.from(context).cloneInContext(context);
        MaterialComponentsViewInflater materialInflater = new MaterialComponentsViewInflater();
        inflater.setFactory2(new LayoutInflater.Factory2() {
            @Override public View onCreateView(View parent, String name, Context nodeContext,
                    AttributeSet attrs) {
                return materialInflater.createView(parent, name, nodeContext, attrs,
                        false, true, true, false);
            }

            @Override public View onCreateView(String name, Context nodeContext, AttributeSet attrs) {
                return onCreateView(null, name, nodeContext, attrs);
            }
        });
        return inflater;
    }

    @Test public void renderAllRecoloredScreensInBothSystemAppearanceModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[] layouts = {
                    R.layout.activity_login,
                    R.layout.fragment_dashboard,
                    R.layout.fragment_inventory,
                    R.layout.fragment_planting,
                    R.layout.fragment_analytics,
                    R.layout.fragment_historical_records,
                    R.layout.fragment_historical_capture,
                    R.layout.fragment_historical_editor,
                    R.layout.item_historical_record,
                    R.layout.item_historical_row,
                    R.layout.item_inventory_lot,
                    R.layout.item_planting,
                    R.layout.fragment_settings,
                    R.layout.dialog_planting,
                    R.layout.fragment_scanner,
                    R.layout.fragment_harvest,
                    R.layout.fragment_inventory_details,
                    R.layout.fragment_planting_details,
                    R.layout.drawer_header,
                    R.layout.dialog_grafting,
                    R.layout.dialog_price,
                    R.layout.dialog_sale
            };
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                for (int layout : layouts) {
                    View view = inflater(context).inflate(layout, null, false);
                    if (layout == R.layout.fragment_dashboard) {
                        // Visual fixtures only: never stored as farm records or sent to Supabase.
                        setPreviewText(view, R.id.inventoryValue, "12500");
                        setPreviewText(view, R.id.batchesValue, "12");
                        setPreviewText(view, R.id.monthlyRevenueValue, "₱125,000.00");
                        setPreviewText(view, R.id.plantingValue, "16");
                    }
                    if (layout == R.layout.fragment_analytics) {
                        setPreviewText(view, R.id.periodInput, "Monthly");
                        setPreviewText(view, R.id.dateInput, "Aug 2026");
                        setPreviewText(view, R.id.revenueText, "₱12,000");
                        setPreviewText(view, R.id.salesText, "24");
                        setPreviewText(view, R.id.piecesText, "612");
                        setPreviewText(view, R.id.weightText, "185 kg");
                        setPreviewText(view, R.id.comparisonText, "+33.3% vs Jul 2026");
                        setPreviewText(view, R.id.trendLegendText,
                                "━━ Aug 2026    ━ ━ Jul 2026");
                        RevenueTrendChartView chart = view.findViewById(R.id.trendChart);
                        chart.setData(Arrays.asList(
                                new SalesAnalytics.Trend("1", 2000, 0),
                                new SalesAnalytics.Trend("8", 5000, 0),
                                new SalesAnalytics.Trend("15", 3000, 0),
                                new SalesAnalytics.Trend("22", 0, 0),
                                new SalesAnalytics.Trend("29", 4000, 0)),
                                Arrays.asList(
                                        new SalesAnalytics.Trend("1", 800, 0),
                                        new SalesAnalytics.Trend("8", 2000, 0),
                                        new SalesAnalytics.Trend("15", 1800, 0),
                                        new SalesAnalytics.Trend("22", 0, 0),
                                        new SalesAnalytics.Trend("29", 1500, 0)), "monthly");
                    }
                    if (layout == R.layout.fragment_historical_editor) {
                        setPreviewText(view, R.id.dateInput, "08/12/2026");
                        LinearLayout container = view.findViewById(R.id.rowsContainer);
                        View saleRow = inflater(context).inflate(R.layout.item_historical_row,
                                container, false);
                        setPreviewText(saleRow, R.id.rowTitle, "Large");
                        setPreviewText(saleRow, R.id.sizeInput, "Large");
                        setPreviewText(saleRow, R.id.weightInput, "140");
                        setPreviewText(saleRow, R.id.priceInput, "100");
                        setPreviewText(saleRow, R.id.calculatedSubtotalText, "Subtotal ₱14,000.00");
                        setPreviewText(saleRow, R.id.paperSubtotalInput, "14000");
                        setPreviewText(saleRow, R.id.recordTypeInput, "Actual sale");
                        container.addView(saleRow);
                        setPreviewText(view, R.id.subtotalSumText,
                                "Total from completed rows: ₱14,000.00");
                        setPreviewText(view, R.id.grandTotalInput, "14000");
                        view.findViewById(R.id.grandTotalLayout).setVisibility(View.VISIBLE);
                    }
                    if (layout == R.layout.item_inventory_lot) {
                        // Fixture values expose the expanded row layout without touching farm data.
                        setPreviewText(view, R.id.batchText, "#1042");
                        setPreviewText(view, R.id.piecesText, "72 pcs");
                        setPreviewText(view, R.id.compositionText, "2 sizes · 2 grades");
                        setPreviewText(view, R.id.dateText, "Oct 7, 2026");
                        setPreviewText(view, R.id.breakdownText,
                                "AVAILABLE BY SIZE & GRADE\nLarge · A  ·  48 pcs\nMedium · B  ·  24 pcs");
                        view.findViewById(R.id.expandedContent).setVisibility(View.VISIBLE);
                    }
                    if (layout == R.layout.item_planting) {
                        setPreviewText(view, R.id.recordText, "Farm Section A-01");
                        setPreviewText(view, R.id.detailsText, "63 cuttings · Planted Aug 12");
                        setPreviewText(view, R.id.stageText, "Flowering");
                        view.findViewById(R.id.compactGuidanceButton).setVisibility(View.GONE);
                        view.findViewById(R.id.expandedContent).setVisibility(View.VISIBLE);
                    }
                    measure(view);
                    assertTrue(view.getWidth() > 0);
                    assertTrue(view.getHeight() > 0);
                    String resourceName = context.getResources().getResourceEntryName(layout);
                    capture(view, resourceName + "-360-" + modeName(nightMode));
                }
            }
        });
    }

    @Test public void semanticBodyTextAndPrimaryActionMeetMinimumContrastInBothModes() {
        for (int nightMode : NIGHT_MODES) {
            Context context = themed(nightMode);
            int[] foregrounds = {R.color.ink, R.color.muted_ink};
            int[] surfaces = {R.color.dragon_surface, R.color.neo_surface_high};
            for (int foreground : foregrounds) {
                for (int background : surfaces) {
                    assertContrast(context, foreground, background, nightMode);
                }
            }
            assertContrast(context, R.color.white, R.color.action_primary, nightMode);
        }
    }

    @Test public void signInButtonUsesPrimaryActionFillAndReadableOnColorInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View login = inflater(context).inflate(R.layout.activity_login, null, false);
                MaterialButton button = login.findViewById(R.id.signInButton);
                assertNotNull(button);
                assertNotNull(button.getBackgroundTintList());
                int fill = button.getBackgroundTintList().getColorForState(
                        button.getDrawableState(), button.getBackgroundTintList().getDefaultColor());
                assertEquals(ContextCompat.getColor(context, R.color.action_primary), fill);
                assertEquals(ContextCompat.getColor(context, R.color.white), button.getCurrentTextColor());
                assertTrue(modeName(nightMode) + " sign-in label contrast must be at least 4.5:1",
                        ColorUtils.calculateContrast(button.getCurrentTextColor(), fill) >= 4.5);
            }
        });
    }

    @Test public void inflatedButtonsRetainReadableLabelsWhilePressedInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View login = inflater(context).inflate(R.layout.activity_login, null, false);
                View home = inflater(context).inflate(R.layout.fragment_dashboard, null, false);
                View planting = inflater(context).inflate(R.layout.fragment_planting, null, false);
                MaterialButton[] buttons = {
                        login.findViewById(R.id.signInButton),
                        home.findViewById(R.id.viewInventoryAction),
                        home.findViewById(R.id.viewSalesAction),
                        home.findViewById(R.id.viewAnalyticsButton),
                        home.findViewById(R.id.viewPlantGuidanceAction),
                        planting.findViewById(R.id.addButton),
                        planting.findViewById(R.id.graftButton)
                };
                for (MaterialButton button : buttons) {
                    assertNotNull(button);
                    for (boolean pressed : new boolean[]{false, true}) {
                        button.setPressed(pressed);
                        button.refreshDrawableState();
                        int background = resolvedColor(button.getBackgroundTintList(), button);
                        if (pressed) {
                            background = compositeRipple(button.getRippleColor(), button, background);
                        }
                        assertWidgetContrast(button, background, nightMode,
                                pressed ? "pressed" : "resting");
                    }
                    button.setPressed(false);
                }
            }
        });
    }

    @Test public void analyticsFilterButtonsRetainContrastInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View analytics = inflater(context).inflate(R.layout.fragment_analytics, null, false);
                for (int id : new int[]{R.id.periodInput, R.id.dateInput}) {
                    MaterialButton button = analytics.findViewById(id);
                    for (boolean pressed : new boolean[]{false, true}) {
                        button.setPressed(pressed);
                        button.refreshDrawableState();
                        int background = resolvedColor(button.getBackgroundTintList(), button);
                        if (pressed) {
                            background = compositeRipple(button.getRippleColor(), button, background);
                        }
                        assertWidgetContrast(button, background, nightMode, "pressed=" + pressed);
                    }
                }
            }
        });
    }

    @Test public void recordPlantingIconMatchesReadablePrimaryLabelInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View planting = inflater(context).inflate(R.layout.fragment_planting, null, false);
                MaterialButton button = planting.findViewById(R.id.addButton);
                assertNotNull(button.getIcon());
                for (boolean pressed : new boolean[]{false, true}) {
                    button.setPressed(pressed);
                    button.refreshDrawableState();
                    int icon = resolvedColor(button.getIconTint(), button);
                    assertEquals(modeName(nightMode) + " Record planting icon must match its label",
                            button.getCurrentTextColor(), icon);
                    int background = resolvedColor(button.getBackgroundTintList(), button);
                    if (pressed) {
                        background = compositeRipple(button.getRippleColor(), button, background);
                    }
                    assertWidgetContrast(button, background, nightMode,
                            pressed ? "pressed icon/label" : "resting icon/label");
                }
            }
        });
    }

    @Test public void snackbarAndDialogThemeRolesStayReadableInBothModes() {
        for (int nightMode : NIGHT_MODES) {
            Context context = themed(nightMode);
            int background = themeColor(context, com.google.android.material.R.attr.colorSurfaceInverse);
            int label = themeColor(context, com.google.android.material.R.attr.colorOnSurfaceInverse);
            int action = themeColor(context, com.google.android.material.R.attr.colorPrimaryInverse);
            assertEquals("Snackbar must use the inverse surface",
                    ContextCompat.getColor(context, R.color.ink), background);
            assertTrue(modeName(nightMode) + " Snackbar label contrast",
                    ColorUtils.calculateContrast(label, background) >= 4.5);
            assertTrue(modeName(nightMode) + " Snackbar action contrast",
                    ColorUtils.calculateContrast(action, background) >= 4.5);
            Context dialogContext = new AlertDialog.Builder(context).getContext();
            assertEquals("AppCompat dialog title/footer must use the light card surface",
                    ContextCompat.getColor(context, R.color.neo_surface_high),
                    themeColor(dialogContext, android.R.attr.colorBackground));
        }
    }

    @Test public void restingLoginFieldOutlinesContrastWithCardInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View login = inflater(context).inflate(R.layout.activity_login, null, false);
                // Keep focus on the fixture root so this checks resting, not focused, strokes.
                login.setFocusableInTouchMode(true);
                login.requestFocus();
                measure(login);
                int card = ContextCompat.getColor(context, R.color.neo_surface_high);
                for (int id : new int[]{R.id.emailLayout, R.id.passwordLayout}) {
                    TextInputLayout field = login.findViewById(id);
                    assertNotNull(field.getEditText());
                    assertTrue("Input must be unfocused for resting-stroke test", !field.hasFocus());
                    Drawable background = field.getEditText().getBackground();
                    assertTrue("Editable outline must have a MaterialShapeDrawable background",
                            background instanceof MaterialShapeDrawable);
                    MaterialShapeDrawable shape = (MaterialShapeDrawable) background;
                    assertTrue("Outline must have nonzero width", shape.getStrokeWidth() > 0);
                    ColorStateList strokeColors = shape.getStrokeColor();
                    assertNotNull(strokeColors);
                    int stroke = strokeColors.getColorForState(shape.getState(), strokeColors.getDefaultColor());
                    double ratio = ColorUtils.calculateContrast(stroke, card);
                    assertTrue(modeName(nightMode) + " " + context.getResources().getResourceEntryName(id)
                            + " resting stroke contrast=" + ratio + ":1, expected at least 3:1", ratio >= 3.0);
                }
            }
        });
    }

    @Test public void saleTypeSelectionIsDistinctAndReadableWhilePressedInBothModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int nightMode : NIGHT_MODES) {
                Context context = themed(nightMode);
                View sale = inflater(context).inflate(R.layout.dialog_sale, null, false);
                MaterialButtonToggleGroup group = sale.findViewById(R.id.saleModeGroup);
                assertTrue(group.isSingleSelection());
                assertTrue(group.isSelectionRequired());
                int[] optionIds = {R.id.selectedItemsButton, R.id.entireBatchButton};
                for (int selectedId : optionIds) {
                    group.check(selectedId);
                    MaterialButton selected = sale.findViewById(selectedId);
                    selected.setChecked(false);
                    assertTrue("Required selection cannot be cleared", selected.isChecked());
                    int selectedFill = resolvedColor(selected.getBackgroundTintList(), selected);
                    int otherId = selectedId == R.id.selectedItemsButton
                            ? R.id.entireBatchButton : R.id.selectedItemsButton;
                    MaterialButton other = sale.findViewById(otherId);
                    assertTrue("Selected sale type needs a distinct fill",
                            selectedFill != resolvedColor(other.getBackgroundTintList(), other));
                    for (MaterialButton option : new MaterialButton[]{selected, other}) {
                        for (boolean pressed : new boolean[]{false, true}) {
                            option.setPressed(pressed);
                            option.refreshDrawableState();
                            int background = resolvedColor(option.getBackgroundTintList(), option);
                            if (pressed) {
                                background = compositeRipple(option.getRippleColor(), option, background);
                            }
                            assertWidgetContrast(option, background, nightMode,
                                    "checked=" + option.isChecked() + ", pressed=" + pressed);
                        }
                        option.setPressed(false);
                    }
                }
            }
        });
    }

    @Test public void hermeticInflaterAppliesAppCompatImageTints() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = themed(Configuration.UI_MODE_NIGHT_NO);
            View home = inflater(context).inflate(R.layout.fragment_dashboard, null, false);
            View inventoryValue = home.findViewById(R.id.inventoryValue);
            ViewGroup firstMetric = (ViewGroup) inventoryValue.getParent();
            View icon = firstMetric.getChildAt(0);
            assertTrue("Plain ImageView must be upgraded as in AppCompatActivity",
                    icon instanceof AppCompatImageView);
            assertEquals("Home metric app:tint must be honored in layout renders",
                    ContextCompat.getColor(context, R.color.dragon_magenta),
                    resolvedColor(ImageViewCompat.getImageTintList((ImageView) icon), icon));
        });
    }

    private int resolvedColor(ColorStateList colors, View view) {
        assertNotNull("Color state list required for " + view.getClass().getSimpleName(), colors);
        return colors.getColorForState(view.getDrawableState(), colors.getDefaultColor());
    }

    private int compositeRipple(ColorStateList rippleColors, View view, int background) {
        int ripple = resolvedColor(rippleColors, view);
        // Evaluate full coverage of the configured translucent state layer. Do not
        // mistake its RGB value for an opaque fill or depend on animation timing.
        return ColorUtils.compositeColors(ripple, background);
    }

    private void assertWidgetContrast(TextView view, int background, int nightMode, String state) {
        String name = view.getResources().getResourceEntryName(view.getId());
        assertEquals(name + " background must resolve opaque", 255, Color.alpha(background));
        double ratio = ColorUtils.calculateContrast(view.getCurrentTextColor(), background);
        assertTrue(modeName(nightMode) + " " + name + " " + state + " contrast=" + ratio
                + ":1, expected at least 4.5:1", ratio >= 4.5);
    }

    private int themeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        assertTrue("Theme color must resolve: " + attribute,
                context.getTheme().resolveAttribute(attribute, value, true));
        return value.resourceId == 0 ? value.data : ContextCompat.getColor(context, value.resourceId);
    }

    private void assertContrast(Context context, int foregroundId, int backgroundId, int nightMode) {
        int foreground = ContextCompat.getColor(context, foregroundId);
        int background = ContextCompat.getColor(context, backgroundId);
        String foregroundName = context.getResources().getResourceEntryName(foregroundId);
        String backgroundName = context.getResources().getResourceEntryName(backgroundId);
        assertEquals(backgroundName + " must be an opaque surface", 255, Color.alpha(background));
        double ratio = ColorUtils.calculateContrast(foreground, background);
        assertTrue(modeName(nightMode) + ": " + foregroundName + " on " + backgroundName
                + " measured " + ratio + ":1, expected at least 4.5:1", ratio >= 4.5);
    }

    private void setPreviewText(View root, int id, String text) {
        TextView label = root.findViewById(id);
        assertNotNull(label);
        label.setText(text);
    }

    private void measure(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.round(WIDTH_DP * density), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.round(HEIGHT_DP * density), View.MeasureSpec.EXACTLY));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.jumpDrawablesToCurrentState();
    }

    private void capture(View view, String name) {
        File externalDirectory = target().getExternalFilesDir(null);
        assertNotNull("External files directory is needed for local review captures", externalDirectory);
        File directory = new File(externalDirectory, "palette-review");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ContextCompat.getColor(view.getContext(), R.color.dragon_surface));
        view.draw(canvas);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (IOException error) {
            throw new AssertionError("Unable to save palette preview " + name, error);
        } finally {
            bitmap.recycle();
        }
    }

    private String modeName(int nightMode) {
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? "system-dark" : "system-light";
    }
}
