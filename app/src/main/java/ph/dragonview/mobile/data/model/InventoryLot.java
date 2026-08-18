package ph.dragonview.mobile.data.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class InventoryLot {
    private long id;
    private String batchNumber;
    private String harvestDate;
    private String size;
    private String grade;
    private int availablePieces;
    private boolean nextOut;

    public InventoryLot(long id, String batchNumber, String harvestDate,
                        String size, String grade, int availablePieces,
                        boolean nextOut) {
        this.id = id;
        this.batchNumber = batchNumber;
        this.harvestDate = harvestDate;
        this.size = size;
        this.grade = grade;
        this.availablePieces = availablePieces;
        this.nextOut = nextOut;
    }

    public long getId() { return id; }
    public String getBatchNumber() { return batchNumber; }
    public String getHarvestDate() { return harvestDate; }
    public String getSize() { return size; }
    public String getGrade() { return grade; }
    public int getAvailablePieces() { return availablePieces; }
    public boolean isNextOut() { return nextOut; }

    public int getAgeInDays() {
        try {
            Calendar harvested = Calendar.getInstance();
            harvested.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .parse(harvestDate.substring(0, 10)));
            zeroTime(harvested);
            Calendar today = Calendar.getInstance();
            zeroTime(today);
            return (int) Math.max(0,
                    (today.getTimeInMillis() - harvested.getTimeInMillis())
                            / 86_400_000L);
        } catch (Exception ignored) {
            return 0;
        }
    }

    public String displaySize() {
        if (size == null) return "";
        String value = size.replace('_', ' ').toLowerCase(java.util.Locale.US);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
