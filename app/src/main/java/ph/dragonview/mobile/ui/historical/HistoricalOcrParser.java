package ph.dragonview.mobile.ui.historical;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ph.dragonview.mobile.data.model.HistoricalRecordDraft;

/** Best-effort transcription of a paper table; the owner must review all fields. */
final class HistoricalOcrParser {
    private static final Pattern WRITTEN_DATE = Pattern.compile(
            "(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december)\\s+([0-3]?\\d),?\\s+(20\\d{2})\\b");
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "\\b([01]?\\d)[/.-]([0-3]?\\d)[/.-](20\\d{2})\\b");
    private static final Pattern SIZE = Pattern.compile(
            "(?i)\\b(extra[ -]?small|blemished|jumbo|large|medium|small|xs|xl|[lsm])\\b");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(?:₱|PHP\\s*|P\\s*)?([0-9][0-9,]*(?:\\.[0-9]{1,3})?)");
    private static final Pattern PIECES_HEADING = Pattern.compile("(?i)\\b(?:pcs|pieces)\\b");

    static Result parse(String source) {
        String text = source == null ? "" : source;
        Set<String> dates = readDates(text);
        String date = dates.size() == 1 ? dates.iterator().next() : "";
        Double grandTotal = null;
        List<HistoricalRecordDraft.Item> items = new ArrayList<>();
        // Old piece-based pages cannot safely be interpreted as kilograms.
        boolean pieceBasedPage = PIECES_HEADING.matcher(text).find();
        for (String line : text.split("\\R")) {
            String normalized = line.trim();
            if (normalized.toLowerCase(Locale.US).matches(".*grand\\s*total.*")) {
                List<Double> amounts = numbers(normalized);
                if (!amounts.isEmpty()) grandTotal = amounts.get(amounts.size() - 1);
                continue;
            }
            if (pieceBasedPage) continue;
            Matcher size = SIZE.matcher(normalized);
            if (!size.find()) continue;
            List<Double> amounts = numbers(normalized.substring(size.end()));
            if (amounts.size() < 3) continue;
            int offset = amounts.size() - 3;
            double kilograms = amounts.get(offset);
            if (kilograms <= 0) continue;
            items.add(new HistoricalRecordDraft.Item(
                    normalizeSize(size.group()), kilograms,
                    amounts.get(offset + 1), amounts.get(offset + 2)));
        }
        return new Result(date, items, grandTotal, dates.size() > 1);
    }

    private static Set<String> readDates(String text) {
        Set<String> dates = new LinkedHashSet<>();
        Matcher written = WRITTEN_DATE.matcher(text);
        while (written.find()) {
            String value = written.group(1) + " " + written.group(2) + ", " + written.group(3);
            String date = parseDate(value, "MMMM d, yyyy");
            if (!date.isEmpty()) dates.add(date);
        }
        Matcher numeric = NUMERIC_DATE.matcher(text);
        while (numeric.find()) {
            String date = parseDate(numeric.group(), "M/d/yyyy", "M-M-yyyy", "M.M.yyyy");
            if (!date.isEmpty()) dates.add(date);
        }
        return dates;
    }

    private static String parseDate(String value, String... patterns) {
        for (String pattern : patterns) {
            try {
                SimpleDateFormat input = new SimpleDateFormat(pattern, Locale.US);
                input.setLenient(false);
                Date date = input.parse(value);
                return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
            } catch (ParseException ignored) { }
        }
        return "";
    }

    private static List<Double> numbers(String value) {
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = AMOUNT.matcher(value);
        while (matcher.find()) {
            try { numbers.add(Double.parseDouble(matcher.group(1).replace(",", ""))); }
            catch (NumberFormatException ignored) { }
        }
        return numbers;
    }

    private static String normalizeSize(String value) {
        String normalized = value.toUpperCase(Locale.US).replace(' ', '_').replace('-', '_');
        if ("XS".equals(normalized)) return "EXTRA_SMALL";
        if ("XL".equals(normalized) || "J".equals(normalized)) return "JUMBO";
        if ("L".equals(normalized)) return "LARGE";
        if ("M".equals(normalized)) return "MEDIUM";
        if ("S".equals(normalized)) return "SMALL";
        return normalized;
    }

    static final class Result {
        private final String date;
        private final List<HistoricalRecordDraft.Item> items;
        private final Double grandTotal;
        private final boolean multipleDates;

        private Result(String date, List<HistoricalRecordDraft.Item> items, Double grandTotal,
                       boolean multipleDates) {
            this.date = date;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.grandTotal = grandTotal;
            this.multipleDates = multipleDates;
        }

        String date() { return date; }
        List<HistoricalRecordDraft.Item> items() { return items; }
        Double grandTotal() { return grandTotal; }
        boolean multipleDates() { return multipleDates; }
    }
}
