package ph.dragonview.mobile.ui.analytics;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Calendar period shown by Analytics, using the same Asia/Manila boundaries as SQL. */
final class AnalyticsDateRange {
    private static final TimeZone MANILA = TimeZone.getTimeZone("Asia/Manila");
    private final String period;
    private final Calendar start;
    private final Calendar end;

    private AnalyticsDateRange(String period, Calendar start, Calendar end) {
        this.period = period;
        this.start = start;
        this.end = end;
    }

    static AnalyticsDateRange forDate(String period, String date) {
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            iso.setLenient(false);
            iso.setTimeZone(MANILA);
            Calendar start = Calendar.getInstance(MANILA, Locale.US);
            start.setTime(iso.parse(date));
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            if ("weekly".equals(period)) {
                while (start.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    start.add(Calendar.DAY_OF_MONTH, -1);
                }
            } else if ("monthly".equals(period)) {
                start.set(Calendar.DAY_OF_MONTH, 1);
            } else if ("yearly".equals(period)) {
                start.set(Calendar.DAY_OF_YEAR, 1);
            } else if (!"daily".equals(period)) {
                throw new IllegalArgumentException("Unsupported reporting period");
            }
            Calendar end = (Calendar) start.clone();
            if ("daily".equals(period)) end.add(Calendar.DAY_OF_MONTH, 1);
            else if ("weekly".equals(period)) end.add(Calendar.DAY_OF_MONTH, 7);
            else if ("monthly".equals(period)) end.add(Calendar.MONTH, 1);
            else end.add(Calendar.YEAR, 1);
            end.add(Calendar.DAY_OF_MONTH, -1);
            return new AnalyticsDateRange(period, start, end);
        } catch (ParseException | NullPointerException error) {
            throw new IllegalArgumentException("Invalid reporting date", error);
        }
    }

    AnalyticsDateRange previous() {
        Calendar previous = (Calendar) start.clone();
        if ("daily".equals(period)) previous.add(Calendar.DAY_OF_MONTH, -1);
        else if ("weekly".equals(period)) previous.add(Calendar.DAY_OF_MONTH, -7);
        else if ("monthly".equals(period)) previous.add(Calendar.MONTH, -1);
        else previous.add(Calendar.YEAR, -1);
        return forDate(period, format(previous, "yyyy-MM-dd"));
    }

    static String previousYearAnchor(String date) {
        return format(forDate("yearly", date).previous().start, "yyyy-MM-dd");
    }

    static AnalyticsDateRange yearlyThrough(String cutoff, String yearAnchor) {
        AnalyticsDateRange year = forDate("yearly", yearAnchor);
        AnalyticsDateRange source = forDate("daily", cutoff);
        Calendar end = (Calendar) source.end.clone();
        // Calendar year addition clamps Feb 29 when the comparison year is not a leap year.
        end.add(Calendar.YEAR, year.start.get(Calendar.YEAR) - end.get(Calendar.YEAR));
        return new AnalyticsDateRange("yearly", year.start, end);
    }

    String compactLabel() {
        if ("monthly".equals(period)) return format(start, "MMM yyyy");
        if ("yearly".equals(period)) return format(start, "yyyy");
        return label();
    }

    String label() {
        if ("daily".equals(period)) return format(start, "MMM d, yyyy");
        if (start.get(Calendar.YEAR) != end.get(Calendar.YEAR)) {
            return format(start, "MMM d, yyyy") + "–" + format(end, "MMM d, yyyy");
        }
        if (start.get(Calendar.MONTH) != end.get(Calendar.MONTH)) {
            return format(start, "MMM d") + "–" + format(end, "MMM d, yyyy");
        }
        return format(start, "MMM d") + "–" + format(end, "d, yyyy");
    }

    private static String format(Calendar date, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
        formatter.setTimeZone(MANILA);
        return formatter.format(date.getTime());
    }
}
