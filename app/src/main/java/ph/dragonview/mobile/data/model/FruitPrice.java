package ph.dragonview.mobile.data.model;

public final class FruitPrice {
    private long id;
    private String grade;
    private String size;
    private double pricePerKilogram;
    public FruitPrice(long id, String grade, String size, double pricePerKilogram) {
        this.id = id;
        this.grade = grade;
        this.size = size;
        this.pricePerKilogram = pricePerKilogram;
    }
    public long getId() { return id; }
    public String getGrade() { return grade; }
    public String getSize() { return size; }
    public double getPricePerKilogram() { return pricePerKilogram; }
}
