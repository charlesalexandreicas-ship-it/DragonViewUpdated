package ph.dragonview.mobile.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

public final class ScrollableForm {
    private ScrollableForm() {}

    /** Let the dialog resize for the keyboard while keeping all fields reachable. */
    public static ScrollView wrap(View form) {
        ScrollView scroll = new ScrollView(form.getContext());
        scroll.addView(form, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }
}
