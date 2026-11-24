package ph.dragonview.mobile.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.model.SessionUser;

/** Shows one short, dismissible instruction on the screen where an action is available. */
final class InAppGuide {
    static final int[] DESTINATIONS = {R.id.dashboardFragment, R.id.inventoryFragment,
            R.id.scannerFragment, R.id.plantingFragment, R.id.analyticsFragment,
            R.id.salesFragment};
    static final String[] NAMES = {"Home", "Inventory", "Camera", "Planting", "Analytics", "Sales"};

    private final FragmentActivity activity;
    private final OnBackPressedCallback guideBack;
    private int visibleDestination;
    private final String userKey;
    private FrameLayout overlay;

    InAppGuide(FragmentActivity activity, SessionManager session) {
        this.activity = activity;
        SessionUser user = session.getUser();
        userKey = user == null ? "unknown" : user.getId();
        // Back dismisses the visible guide before the navigation host can leave the screen.
        guideBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                finish(visibleDestination);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, guideBack);
    }

    static @Nullable Step stepFor(int destination) {
        if (destination == R.id.dashboardFragment) return new Step(R.id.farmOverviewCards,
                "Farm overview", "See your stock, monthly revenue, and plant groups at a glance.");
        if (destination == R.id.inventoryFragment) return new Step(R.id.inventoryActions,
                "Manage inventory", "Record harvest to add stock. Edit prices here, then open a batch to see its details.");
        if (destination == R.id.scannerFragment) return new Step(R.id.scannerActions,
                "Scan fruit", "Capture a photo or upload one. Review the detected size and grade.");
        if (destination == R.id.plantingFragment) return new Step(R.id.recordActions,
                "Planting workflow", "Tap Plant or Graft to record a group. Open a group below to follow its guidance.");
        if (destination == R.id.analyticsFragment) return new Step(R.id.periodInput,
                "Review results", "Choose a period to see sales and revenue for that time.");
        if (destination == R.id.salesFragment) return new Step(R.id.salesHeader,
                "Record a sale", "Set prices here, then tap New sale to record what was sold.");
        return null;
    }

    void show(int destination, boolean replay) {
        Step step = stepFor(destination);
        boolean registeredHere = activity.getSharedPreferences("onboarding_guides", Context.MODE_PRIVATE)
                .getBoolean(userKey + "_registered_here", false);
        if (!replay && !registeredHere) return;
        if (step == null || (!replay && seen(destination))) return;
        // Navigation reports its destination before the fragment has finished laying out.
        activity.findViewById(R.id.nav_host).postDelayed(() -> showWhenReady(destination, step, 0), 180);
    }

    private void showWhenReady(int destination, Step step, int attempt) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        // FragmentContainerView does not reliably expose its controller through the Activity lookup.
        androidx.navigation.fragment.NavHostFragment host = (androidx.navigation.fragment.NavHostFragment)
                activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (host == null) return;
        androidx.navigation.NavDestination current = host.getNavController().getCurrentDestination();
        if (current == null || current.getId() != destination) return;
        View target = activity.findViewById(step.targetId);
        if (target == null || !target.isShown() || target.getWidth() == 0) {
            if (attempt < 3) activity.findViewById(R.id.nav_host).postDelayed(
                    () -> showWhenReady(destination, step, attempt + 1), 250);
            return;
        }
        dismiss();
        ViewGroup root = activity.findViewById(android.R.id.content);
        overlay = new FrameLayout(activity);
        visibleDestination = destination;
        guideBack.setEnabled(true);
        overlay.setClickable(true);
        root.addView(overlay, new ViewGroup.LayoutParams(-1, -1));
        FrameLayout openedOverlay = overlay;
        overlay.post(() -> {
            if (overlay != openedOverlay) return;
            if (!target.isShown()) {
                dismiss();
                return;
            }
            int[] targetLocation = new int[2], overlayLocation = new int[2];
            target.getLocationOnScreen(targetLocation);
            overlay.getLocationOnScreen(overlayLocation);
            RectF focus = new RectF(targetLocation[0] - overlayLocation[0],
                    targetLocation[1] - overlayLocation[1],
                    targetLocation[0] - overlayLocation[0] + target.getWidth(),
                    targetLocation[1] - overlayLocation[1] + target.getHeight());
            Spotlight spotlight = new Spotlight(activity, focus);
            overlay.addView(spotlight, new FrameLayout.LayoutParams(-1, -1));
            addControls(destination, step, focus, spotlight);
        });
    }

    private void addControls(int destination, Step step, RectF focus, Spotlight spotlight) {
        TextView skip = text("Skip", 15, Color.WHITE, true);
        skip.setGravity(Gravity.CENTER);
        skip.setOnClickListener(v -> finish(destination));
        FrameLayout.LayoutParams skipParams = new FrameLayout.LayoutParams(dp(64), dp(48), Gravity.TOP | Gravity.END);
        skipParams.setMargins(0, dp(8), dp(12), 0);
        overlay.addView(skip, skipParams);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        int verticalPadding = destination == R.id.dashboardFragment ? dp(10) : dp(18);
        card.setPadding(dp(20), verticalPadding, dp(20), verticalPadding);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(20));
        card.setBackground(background);
        card.setElevation(dp(8));
        card.addView(text(step.title, 20, activity.getColor(R.color.botanical_ink), true));
        TextView body = text(step.body, 15, activity.getColor(R.color.botanical_ink), false);
        body.setPadding(0, dp(8), 0, dp(12));
        card.addView(body);
        TextView done = text("Got it", 15, Color.WHITE, true);
        done.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable buttonBackground = new android.graphics.drawable.GradientDrawable();
        buttonBackground.setColor(activity.getColor(R.color.botanical_accent));
        buttonBackground.setCornerRadius(dp(12));
        done.setBackground(buttonBackground);
        done.setMinHeight(dp(48));
        done.setPadding(dp(12), dp(8), dp(12), dp(8));
        done.setOnClickListener(v -> finish(destination));
        card.addView(done);
        card.measure(View.MeasureSpec.makeMeasureSpec(overlay.getWidth() - dp(32), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int cardHeight = card.getMeasuredHeight();
        boolean fitsBelow = focus.bottom + dp(8) + cardHeight + dp(12) <= overlay.getHeight();
        boolean fitsAbove = dp(64) + cardHeight + dp(8) <= focus.top;
        boolean cardAtTop = !fitsBelow && fitsAbove;
        // If neither side fits, keep the instructions readable without a partly covered spotlight.
        if (!fitsBelow && !fitsAbove) spotlight.focus.setEmpty();
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2,
                cardAtTop ? Gravity.TOP : Gravity.BOTTOM);
        cardParams.setMargins(dp(16), cardAtTop ? dp(64) : 0, dp(16), cardAtTop ? 0 : dp(12));
        overlay.addView(card, cardParams);
    }

    private void finish(int destination) {
        activity.getSharedPreferences("onboarding_guides", Context.MODE_PRIVATE).edit()
                .putBoolean(userKey + "_" + destination, true).apply();
        dismiss();
    }

    private boolean seen(int destination) {
        return activity.getSharedPreferences("onboarding_guides", Context.MODE_PRIVATE)
                .getBoolean(userKey + "_" + destination, false);
    }

    void dismiss() {
        guideBack.setEnabled(false);
        if (overlay == null) return;
        ViewGroup parent = (ViewGroup) overlay.getParent();
        if (parent != null) parent.removeView(overlay);
        overlay = null;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                activity, bold ? R.font.manrope_700 : R.font.manrope_400));
        return view;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    static final class Step {
        final int targetId;
        final String title, body;
        Step(int targetId, String title, String body) {
            this.targetId = targetId;
            this.title = title;
            this.body = body;
        }
    }

    /** Dims the screen while leaving the actual control unobscured. */
    private static final class Spotlight extends View {
        private final RectF focus;
        private final Paint paint = new Paint(3);
        Spotlight(Context context, RectF focus) { super(context); this.focus = focus; }
        @Override protected void onDraw(Canvas canvas) {
            if (focus.isEmpty()) {
                canvas.drawColor(0x99000000);
                return;
            }
            float left = Math.max(0, focus.left - 6), top = Math.max(0, focus.top - 6);
            float right = Math.min(getWidth(), focus.right + 6), bottom = Math.min(getHeight(), focus.bottom + 6);
            paint.setColor(0x99000000);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(0, 0, getWidth(), top, paint);
            canvas.drawRect(0, bottom, getWidth(), getHeight(), paint);
            canvas.drawRect(0, top, left, bottom, paint);
            canvas.drawRect(right, top, getWidth(), bottom, paint);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), 16, 16, paint);
        }
    }
}
