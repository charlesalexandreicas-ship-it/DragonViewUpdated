package ph.dragonview.mobile.data.model;

import java.util.List;

public final class CompleteSaleRequest {
    private final Customer customer;
    private final List<Item> items;
    private final Payment payment;

    public CompleteSaleRequest(String name, String address, String contactNumber,
                               String emailAddress, List<Item> items, String method,
                               String amountPaid, String reference, String provider) {
        customer = new Customer(name, address, contactNumber, emailAddress);
        this.items = items;
        payment = new Payment(method, amountPaid, reference, provider);
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
        public Item(String size, String grade, int pieces, double kilograms) {
            this.size = size; this.grade = grade; this.pieces = pieces;
            this.totalWeightKilograms = String.format(java.util.Locale.US, "%.3f", kilograms);
        }
        public String getSize() { return size; }
        public String getGrade() { return grade; }
        public int getPieces() { return pieces; }
        public double getWeightKilograms() {
            return Double.parseDouble(totalWeightKilograms);
        }
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
