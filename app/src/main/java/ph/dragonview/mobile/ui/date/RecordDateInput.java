package ph.dragonview.mobile.ui.date;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/** Keeps a numeric date field in MM/DD/YYYY form without opening a calendar. */
public final class RecordDateInput {
    private RecordDateInput() { }

    public static void attach(EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            private boolean formatting;
            private String before = "";
            private int deletionStart;
            private int removed;

            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {
                before = value.toString();
                deletionStart = start;
                removed = after == 0 ? count : 0;
            }

            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }

            @Override public void afterTextChanged(Editable value) {
                if (formatting) return;
                int selection = Math.max(0, Math.min(field.getSelectionStart(), value.length()));
                int digitsBeforeCursor = value.subSequence(0, selection).toString()
                        .replaceAll("\\D", "").length();
                String digits = value.toString().replaceAll("\\D", "");
                // Backspacing over an inserted slash should remove the preceding digit too.
                if (removed == 1 && deletionStart < before.length()
                        && before.charAt(deletionStart) == '/') {
                    int preceding = before.substring(0, deletionStart)
                            .replaceAll("\\D", "").length() - 1;
                    if (preceding >= 0 && preceding < digits.length()) {
                        digits = digits.substring(0, preceding) + digits.substring(preceding + 1);
                        digitsBeforeCursor = Math.max(0, digitsBeforeCursor - 1);
                    }
                }
                String formatted = RecordDate.formatDigits(digits);
                if (formatted.contentEquals(value)) return;
                formatting = true;
                field.setText(formatted);
                int cursor = 0;
                int seen = 0;
                while (cursor < formatted.length() && seen < digitsBeforeCursor) {
                    if (Character.isDigit(formatted.charAt(cursor))) seen++;
                    cursor++;
                }
                if (cursor < formatted.length() && formatted.charAt(cursor) == '/') cursor++;
                field.setSelection(cursor);
                formatting = false;
            }
        });
    }
}
