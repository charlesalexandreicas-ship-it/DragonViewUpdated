package ph.dragonview.mobile.data.model;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public final class InventoryBatch {
    private final String batchNumber;
    private final String harvestDate;
    private final int itemCount;
    private final int availablePieces;
    private final int nextOutItems;

    public InventoryBatch(String batchNumber, String harvestDate, int itemCount,
                          int availablePieces, int nextOutItems) {
        this.batchNumber = batchNumber;
        this.harvestDate = harvestDate;
        this.itemCount = itemCount;
        this.availablePieces = availablePieces;
        this.nextOutItems = nextOutItems;
    }

    public String getBatchNumber() { return batchNumber; }
    public String getHarvestDate() { return harvestDate; }
    public int getItemCount() { return itemCount; }
    public int getAvailablePieces() { return availablePieces; }
    public int getNextOutItems() { return nextOutItems; }

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

    private static void zeroTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
}
