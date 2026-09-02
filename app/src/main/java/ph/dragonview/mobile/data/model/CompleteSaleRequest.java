package ph.dragonview.mobile.data.model;

import java.util.List;

public final class CompleteSaleRequest {
    public static final String MODE_SELECTED_ITEMS = "SELECTED_ITEMS";
    public static final String MODE_ENTIRE_BATCH = "ENTIRE_BATCH";

    private final Customer customer;
    private final List<Item> items;
    private final Payment payment;
    private final String saleMode;
    private final String batchNumber;
    private final String fifoOverrideReason;

    public CompleteSaleRequest(String name, String address, String contactNumber,
                               String emailAddress, List<Item> items, String method,
                               String amountPaid, String reference, String provider) {
        this(name, address, contactNumber, emailAddress, items, method, amountPaid,
                reference, provider, MODE_SELECTED_ITEMS, null, null);
    }

    public CompleteSaleRequest(String name, String address, String contactNumber,
                               String emailAddress, List<Item> items, String method,
                               String amountPaid, String reference, String provider,
                               String saleMode, String batchNumber,
                               String fifoOverrideReason) {
        customer = new Customer(name, address, contactNumber, emailAddress);
        this.items = items;
        payment = new Payment(method, amountPaid, reference, provider);
        this.saleMode = saleMode;
        this.batchNumber = batchNumber;
        this.fifoOverrideReason = fifoOverrideReason;
    }
    public String getCustomerName() { return customer.name; }
    public String getCustomerAddress() { return customer.address; }
    public String getCustomerContactNumber() { return customer.contactNumber; }
    public String getCustomerEmailAddress() { return customer.emailAddress; }
    public List<Item> getItems() { return items; }
    public String getPaymentMethod() { return payment.method; }
    public double getAmountPaid() { return Double.parseDouble(payment.amountPaid); }
    public String getPaymentReference() { return payment.reference; }
    public String getProvider() { return payment.otherEWalletProvider; }
    public String getSaleMode() { return saleMode; }
    public String getBatchNumber() { return batchNumber; }
    public String getFifoOverrideReason() { return fifoOverrideReason; }
    public boolean isEntireBatch() { return MODE_ENTIRE_BATCH.equals(saleMode); }

    private static final class Customer {
        private final String name, address, contactNumber, emailAddress;
        Customer(String name, String address, String contactNumber, String emailAddress) {
            this.name = name; this.address = address; this.contactNumber = contactNumber;
            this.emailAddress = emailAddress;
        }
    }

    public static final class Item {
        private final String size, grade, totalWeightKilograms;
        private final int pieces;
        private final Long inventoryId;
        public Item(String size, String grade, int pieces, double kilograms) {
            this(size, grade, pieces, kilograms, null);
        }
        public Item(String size, String grade, int pieces, double kilograms,
                    Long inventoryId) {
            this.size = size; this.grade = grade; this.pieces = pieces;
            this.totalWeightKilograms = String.format(java.util.Locale.US, "%.3f", kilograms);
            this.inventoryId = inventoryId;
        }
        public String getSize() { return size; }
        public String getGrade() { return grade; }
        public int getPieces() { return pieces; }
        public double getWeightKilograms() {
            return Double.parseDouble(totalWeightKilograms);
        }
        public Long getInventoryId() { return inventoryId; }
    }

    private static final class Payment {
        private final String method, amountPaid;
        private final String reference;
        private final String otherEWalletProvider;
        Payment(String method, String amountPaid, String reference, String provider) {
            this.method = method; this.amountPaid = amountPaid;
            this.reference = reference == null || reference.isEmpty() ? null : reference;
            this.otherEWalletProvider = provider == null || provider.isEmpty() ? null : provider;
        }
    }
}
