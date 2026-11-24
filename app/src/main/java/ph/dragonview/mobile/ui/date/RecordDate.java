package ph.dragonview.mobile.ui.date;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/** Converts the farmer-facing MM/DD/YYYY entry to the ISO date used by records and the API. */
public final class RecordDate {
    private RecordDate() { }

    /** Inserts separators while the user types up to eight date digits. */
    public static String formatDigits(String input) {
        String digits = input == null ? "" : input.replaceAll("\\D", "");
        if (digits.length() > 8) digits = digits.substring(0, 8);
        StringBuilder result = new StringBuilder(digits);
        if (result.length() >= 4) result.insert(4, '/');
        if (result.length() >= 2) result.insert(2, '/');
        return result.toString();
    }

    /** Returns null for incomplete or impossible dates, including invalid leap days. */
    @Nullable public static String toIso(String displayed) {
        if (displayed == null || !displayed.matches("\\d{2}/\\d{2}/\\d{4}")) return null;
        int month = Integer.parseInt(displayed.substring(0, 2));
        int day = Integer.parseInt(displayed.substring(3, 5));
        int year = Integer.parseInt(displayed.substring(6, 10));
        if (year < 1 || month < 1 || month > 12 || day < 1) return null;
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setLenient(false);
        calendar.clear();
        calendar.set(year, month - 1, day);
        try {
            calendar.getTime();
            return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Displays an existing ISO record date in the same format as the editable field. */
    public static String toDisplay(String iso) {
        if (iso == null || !iso.matches("\\d{4}-\\d{2}-\\d{2}")) return "";
        return iso.substring(5, 7) + "/" + iso.substring(8, 10) + "/" + iso.substring(0, 4);
    }

    /** Supplies today's date for forms that previously prefilled the calendar value. */
    public static String todayDisplay() {
        Calendar today = Calendar.getInstance();
        return String.format(Locale.US, "%02d/%02d/%04d",
                today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH),
                today.get(Calendar.YEAR));
    }

    public static String calendarIso(Calendar date) {
        return String.format(Locale.US, "%04d-%02d-%02d", date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH));
    }

    /** Compare parsed ISO dates without introducing time-of-day or UTC offsets. */
    public static boolean isWithinRange(@Nullable String date, String earliest, String latest) {
        return date != null && date.compareTo(earliest) >= 0 && date.compareTo(latest) <= 0;
    }
}
