package ph.dragonview.mobile.ui.planting;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ContextThemeWrapper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.SessionManager;
import ph.dragonview.mobile.data.local.LocalRepository;
import ph.dragonview.mobile.data.model.PlantingGroup;
import com.google.android.material.button.MaterialButton;

/** Lets a workspace place existing planting groups on one user-chosen farm image. */
public final class FarmLayoutFragment extends Fragment {
    private final ArrayList<PlantingGroup> groups = new ArrayList<>();
    private final ArrayList<Section> sections = new ArrayList<>();
    private LinearLayout content;
    private Drawing imageView;
    private String imageUri;
    private String storageKey;
    private boolean editing;
    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::acceptImage);

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(requireContext().getColor(R.color.botanical_canvas));
        content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content);
        return scroll;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        storageKey = "farm_layout_" + new SessionManager(requireContext()).getActiveWorkspaceId();
        restore();
        LocalRepository.get(requireContext()).planting(new LocalRepository.Callback<>() {
            @Override public void onSuccess(List<PlantingGroup> rows) {
                if (!isAdded()) return;
                groups.clear();
                groups.addAll(rows);
                render();
            }
            @Override public void onError(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
        render();
    }

    /** Keeps the source image available after the picker closes and validates it before saving. */
    private void acceptImage(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                BitmapFactory.Options bounds = boundsOnly();
                if (input == null) throw new IllegalArgumentException("Choose a readable image.");
                BitmapFactory.decodeStream(input, null, bounds);
                if (bounds.outWidth <= 0) {
                    throw new IllegalArgumentException("Choose a readable image.");
                }
            }
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!sections.isEmpty()) {
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Replace farm image?")
                        .setMessage("Existing section outlines will be removed because they belong to the current image.")
                        .setNegativeButton("Keep current image", null)
                        .setPositiveButton("Replace", (dialog, which) -> setImage(uri))
                        .show();
            } else setImage(uri);
        } catch (Exception error) {
            Toast.makeText(requireContext(), "Could not open that image.", Toast.LENGTH_LONG).show();
        }
    }

    private void setImage(Uri uri) {
        imageUri = uri.toString();
        sections.clear(); // Outlines use coordinates relative to the chosen image.
        save();
        render();
    }

    private static BitmapFactory.Options boundsOnly() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        return options;
    }

    /** Renders empty, drawing, and saved states from the same stored layout. */
    private void render() {
        if (content == null || !isAdded()) return;
        content.removeAllViews();
        if (imageUri == null) {
            title("Add a farm image", 20);
            label("Choose an overhead photo or farm plan to draw sections.");
            action("Choose image", () -> imagePicker.launch(new String[]{"image/*"}));
            return;
        }
        action("Change image", () -> imagePicker.launch(new String[]{"image/*"}));
        action("View full screen", this::showFullScreen);
        if (editing) label("Tap at least three corners to draw a section. Drag a point to adjust it.");
        imageView = new Drawing(requireContext());
        content.addView(imageView, new LinearLayout.LayoutParams(-1, dp(340)));
        if (editing) {
            action("Undo point", () -> imageView.undo());
            action("Finish outline", this::editSection);
            action("Cancel", () -> { editing = false; imageView.clearDraft(); render(); });
        } else {
            label(sections.size() + (sections.size() == 1 ? " section" : " sections"));
            if (sections.isEmpty()) label("Tap Add section to mark a planting area.");
            action("Add section", () -> { editing = true; render(); });
            for (Section section : sections) {
                PlantingGroup linked = findGroup(section.groupId);
                action(section.name + "  ·  " + (linked == null ? "No group linked" : linked.getRecordNumber())
                        + (section.area.isEmpty() ? "" : "  ·  " + section.area + " m²"),
                        () -> showSection(section));
            }
        }
    }

    /** Opens a larger read-only map without changing the saved image or section coordinates. */
    private void showFullScreen() {
        Dialog dialog = new Dialog(requireContext());
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        Drawing preview = new Drawing(requireContext(), true);
        preview.setContentDescription("Farm map. Pinch to zoom and drag to move.");
        panel.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(requireContext());
        controls.setPadding(dp(8), dp(4), dp(8), dp(8));
        for (String name : new String[]{"Zoom out", "Reset", "Zoom in", "Close"}) {
            MaterialButton button = new MaterialButton(new ContextThemeWrapper(
                    requireContext(), R.style.Widget_DragonView_BotanicalAction));
            button.setText(name);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setOnClickListener(v -> {
                if ("Close".equals(name)) dialog.dismiss();
                else if ("Reset".equals(name)) preview.resetView();
                else preview.setZoom(preview.zoom + ("Zoom in".equals(name) ? 0.5f : -0.5f));
            });
            controls.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
        }
        panel.addView(controls);
        dialog.setContentView(panel);
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
    }

    /** Collects the outline metadata only after at least three corners exist. */
    private void editSection() {
        if (imageView.draft.size() < 3) {
            Toast.makeText(requireContext(), "Mark at least three corners.", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), 0, dp(16), 0);
        EditText name = new EditText(requireContext());
        name.setHint("Section name");
        name.setSingleLine(true);
        form.addView(name);
        EditText area = new EditText(requireContext());
        area.setHint("Area in m² (optional, entered manually)");
        area.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(area);
        TextView groupLabel = new TextView(requireContext());
        groupLabel.setText("Link a planting group (optional)");
        form.addView(groupLabel);
        Spinner group = new Spinner(requireContext());
        group.setContentDescription(groupLabel.getText());
        ArrayList<String> options = new ArrayList<>();
        options.add("No planting group linked");
        for (PlantingGroup row : groups) options.add(row.getRecordNumber());
        group.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, options));
        form.addView(group);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Save section").setView(ph.dragonview.mobile.ui.ScrollableForm.wrap(form))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(-1).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            String squareMeters = area.getText().toString().trim();
            if (value.isEmpty()) { name.setError("Enter a section name."); return; }
            if (!squareMeters.isEmpty()) {
                try {
                    double size = Double.parseDouble(squareMeters);
                    if (!Double.isFinite(size) || size <= 0) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    area.setError("Enter a positive area."); return;
                }
            }
            long groupId = group.getSelectedItemPosition() == 0 ? 0
                    : groups.get(group.getSelectedItemPosition() - 1).getId();
            sections.add(new Section(value, squareMeters, groupId, new ArrayList<>(imageView.draft)));
            save();
            dialog.dismiss();
            editing = false;
            render();
        }));
        dialog.show();
    }

    private void showSection(Section section) {
        PlantingGroup linked = findGroup(section.groupId);
        String detail = linked == null ? "No planting group linked" : linked.getRecordNumber()
                + " · " + linked.getCurrentStage().name().replace('_', ' ').toLowerCase();
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(section.name).setMessage(detail + (section.area.isEmpty() ? "" : "\n" + section.area + " m² (entered manually)"))
                .setPositiveButton(linked == null ? "Close" : "View record", (dialog, which) -> {
                    if (linked == null) return;
                    Bundle args = new Bundle();
                    args.putLong("plantingId", linked.getId());
                    NavHostFragment.findNavController(this).navigate(R.id.action_farm_layout_to_details, args);
                })
                .setNeutralButton("Remove", (dialog, which) -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Remove section?")
                        .setMessage("This removes the outline and its group link from this layout.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (confirm, choice) -> {
                            sections.remove(section);
                            save();
                            render();
                        }).show()).setNegativeButton("Cancel", null).show();
    }

    private PlantingGroup findGroup(long id) {
        for (PlantingGroup group : groups) if (group.getId() == id) return group;
        return null;
    }

    /** Saves normalized corners so outlines survive screen size changes. */
    private void save() {
        JSONArray rows = new JSONArray();
        try {
            for (Section section : sections) {
                JSONObject row = new JSONObject();
                row.put("name", section.name).put("area", section.area).put("groupId", section.groupId);
                JSONArray corners = new JSONArray();
                for (float[] point : section.points) corners.put(new JSONArray().put(point[0]).put(point[1]));
                row.put("points", corners);
                rows.put(row);
            }
        } catch (JSONException error) { throw new IllegalStateException(error); }
        requireContext().getSharedPreferences(storageKey, Context.MODE_PRIVATE).edit()
                .putString("image", imageUri).putString("sections", rows.toString()).apply();
    }

    private void restore() {
        imageUri = requireContext().getSharedPreferences(storageKey, Context.MODE_PRIVATE).getString("image", null);
        String raw = requireContext().getSharedPreferences(storageKey, Context.MODE_PRIVATE).getString("sections", "[]");
        try {
            JSONArray rows = new JSONArray(raw);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                JSONArray corners = row.getJSONArray("points");
                ArrayList<float[]> points = new ArrayList<>();
                for (int j = 0; j < corners.length(); j++) {
                    JSONArray xy = corners.getJSONArray(j);
                    points.add(new float[]{(float) xy.getDouble(0), (float) xy.getDouble(1)});
                }
                sections.add(new Section(row.getString("name"), row.optString("area"),
                        row.optLong("groupId"), points));
            }
        } catch (JSONException error) { sections.clear(); }
    }

    private void title(String value, int sp) {
        TextView label = new TextView(requireContext());
        label.setText(value);
        label.setTextColor(Color.rgb(25, 37, 31));
        label.setTextSize(sp);
        label.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.manrope_700));
        label.setPadding(0, dp(10), 0, dp(10));
        content.addView(label);
    }

    private void label(String value) {
        TextView label = new TextView(requireContext());
        label.setText(value);
        label.setTextColor(Color.rgb(77, 90, 82));
        label.setTextSize(14);
        label.setPadding(0, dp(8), 0, dp(8));
        content.addView(label);
    }

    private void action(String value, Runnable run) {
        MaterialButton button = new MaterialButton(new ContextThemeWrapper(requireContext(), R.style.Widget_DragonView_BotanicalAction));
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(52));
        layout.topMargin = dp(8);
        content.addView(button, layout);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    static float boundedZoom(float value) { return Math.max(1f, Math.min(4f, value)); }

    private static final class Section {
        final String name, area;
        final long groupId;
        final ArrayList<float[]> points;
        Section(String name, String area, long groupId, ArrayList<float[]> points) {
            this.name = name; this.area = area; this.groupId = groupId; this.points = points;
        }
    }

    /** Draws saved polygons above the image and keeps draft corners in image-relative coordinates. */
    private final class Drawing extends View {
        final ArrayList<float[]> draft = new ArrayList<>();
        private final Paint paint = new Paint(3);
        private Bitmap image;
        private RectF imageRect = new RectF();
        private int dragging = -1;
        private final boolean previewMode;
        private float zoom = 1f, panX, panY, lastX, lastY, pinchDistance;

        Drawing(Context context) { this(context, false); }

        Drawing(Context context, boolean previewMode) {
            super(context);
            this.previewMode = previewMode;
            paint.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(context, R.font.manrope_700));
            try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(imageUri))) {
                BitmapFactory.Options size = boundsOnly();
                BitmapFactory.decodeStream(input, null, size);
                int sample = 1;
                while (size.outWidth / sample > 1800 || size.outHeight / sample > 1800) sample *= 2;
                try (InputStream scaled = context.getContentResolver().openInputStream(Uri.parse(imageUri))) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    image = BitmapFactory.decodeStream(scaled, null, options);
                }
            } catch (Exception ignored) { image = null; }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);
            if (image == null) return;
            float scale = Math.min(getWidth() / (float) image.getWidth(), getHeight() / (float) image.getHeight());
            float width = image.getWidth() * scale, height = image.getHeight() * scale;
            imageRect.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                    (getWidth() + width) / 2, (getHeight() + height) / 2);
            canvas.save();
            if (previewMode) {
                canvas.scale(zoom, zoom, getWidth() / 2f, getHeight() / 2f);
                canvas.translate(panX, panY);
            }
            canvas.drawBitmap(image, null, imageRect, paint);
            for (Section section : sections) {
                polygon(canvas, section.points, Color.rgb(36, 99, 70), false);
                float x = 0, y = 0;
                for (float[] point : section.points) { x += point[0]; y += point[1]; }
                if (!section.points.isEmpty()) {
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(dp(14));
                    canvas.drawText(section.name, imageRect.left + x / section.points.size() * imageRect.width(),
                            imageRect.top + y / section.points.size() * imageRect.height(), paint);
                }
            }
            polygon(canvas, draft, Color.rgb(229, 21, 100), editing);
            canvas.restore();
        }

        private void polygon(Canvas canvas, List<float[]> points, int color, boolean handles) {
            if (points.isEmpty()) return;
            Path path = new Path();
            for (int i = 0; i < points.size(); i++) {
                float x = imageRect.left + points.get(i)[0] * imageRect.width();
                float y = imageRect.top + points.get(i)[1] * imageRect.height();
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            if (points.size() >= 3) path.close();
            paint.setColor((color & 0x00ffffff) | 0x44000000);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            if (handles) for (float[] point : points) {
                float x = imageRect.left + point[0] * imageRect.width();
                float y = imageRect.top + point[1] * imageRect.height();
                paint.setColor(Color.WHITE); canvas.drawCircle(x, y, dp(8), paint);
                paint.setColor(color); canvas.drawCircle(x, y, dp(5), paint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (previewMode) return movePreview(event);
            if (!editing || imageRect.isEmpty()) return false;
            float x = event.getX(), y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!imageRect.contains(x, y)) return false;
                // The drawing surface owns this gesture so the page cannot intercept a point drag.
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = -1;
                for (int i = 0; i < draft.size(); i++) {
                    float dx = x - (imageRect.left + draft.get(i)[0] * imageRect.width());
                    float dy = y - (imageRect.top + draft.get(i)[1] * imageRect.height());
                    if (dx * dx + dy * dy < dp(24) * dp(24)) { dragging = i; break; }
                }
                if (dragging < 0) {
                    draft.add(normalized(x, y));
                    dragging = draft.size() - 1;
                }
                invalidate(); return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging >= 0) {
                draft.set(dragging, normalized(x, y)); invalidate(); return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                dragging = -1;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                return true;
            }
            return true;
        }

        /** Keeps zoom and pan inside the visible map area while preserving read-only sections. */
        private void setZoom(float value) {
            zoom = boundedZoom(value);
            float limitX = getWidth() * (zoom - 1f) / (2f * zoom);
            float limitY = getHeight() * (zoom - 1f) / (2f * zoom);
            panX = Math.max(-limitX, Math.min(limitX, panX));
            panY = Math.max(-limitY, Math.min(limitY, panY));
            invalidate();
        }

        private void resetView() { panX = panY = 0f; setZoom(1f); }

        private boolean movePreview(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastX = event.getX(); lastY = event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2) {
                pinchDistance = (float) Math.hypot(event.getX(0) - event.getX(1),
                        event.getY(0) - event.getY(1));
            } else if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
                float distance = (float) Math.hypot(event.getX(0) - event.getX(1),
                        event.getY(0) - event.getY(1));
                if (pinchDistance > 0f) setZoom(zoom * distance / pinchDistance);
                pinchDistance = distance;
            } else if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1) {
                panX += (event.getX() - lastX) / zoom;
                panY += (event.getY() - lastY) / zoom;
                setZoom(zoom);
                lastX = event.getX(); lastY = event.getY();
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                pinchDistance = 0f;
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                lastX = event.getX(remaining); lastY = event.getY(remaining);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (action == MotionEvent.ACTION_UP) performClick();
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }

        private float[] normalized(float x, float y) {
            return new float[]{Math.max(0, Math.min(1, (x - imageRect.left) / imageRect.width())),
                    Math.max(0, Math.min(1, (y - imageRect.top) / imageRect.height()))};
        }
        void undo() { if (!draft.isEmpty()) { draft.remove(draft.size() - 1); invalidate(); } }
        void clearDraft() { draft.clear(); invalidate(); }
    }
}
