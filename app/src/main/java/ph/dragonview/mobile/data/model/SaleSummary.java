package ph.dragonview.mobile.data.model;

public final class SaleSummary {
    private long id;
    private String customerName;
    private String status;
    private String paymentStatus;
    private String paymentMethod;
    private double totalAmount;
    private int totalPieces;
    private String transactionDate;
    public SaleSummary(long id, String customerName, String status,
                       String paymentStatus, String paymentMethod,
                       double totalAmount, int totalPieces, String transactionDate) {
        this.id = id;
        this.customerName = customerName;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.totalPieces = totalPieces;
        this.transactionDate = transactionDate;
    }
    public long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getStatus() { return status; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getPaymentMethod() { return paymentMethod; }
    public double getTotalAmount() { return totalAmount; }
    public int getTotalPieces() { return totalPieces; }
    public String getTransactionDate() { return transactionDate; }
}
