package ph.dragonview.mobile.data.model;

import java.util.List;

/** A saved historical page and its reviewed size rows. */
public final class HistoricalRecord {
    private String id;
    private String date;
    private String source;
    private Double paperGrandTotal;
    private double totalAmount;
    private double salesAmount;
    private String createdAt;
    private List<HistoricalRecordDraft.Item> items;

    public String getId() { return id; }
    public String getDate() { return date; }
    public String getSource() { return source; }
    public Double getPaperGrandTotal() { return paperGrandTotal; }
    public double getTotalAmount() { return totalAmount; }
    public double getSalesAmount() { return salesAmount; }
    public String getCreatedAt() { return createdAt; }
    public List<HistoricalRecordDraft.Item> getItems() { return items; }
}
