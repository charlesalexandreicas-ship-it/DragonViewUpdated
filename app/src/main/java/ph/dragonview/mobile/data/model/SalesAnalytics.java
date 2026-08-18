package ph.dragonview.mobile.data.model;

import java.util.List;

public final class SalesAnalytics {
    private String period;
    private String selectedDate;
    private double previousRevenue;
    private Double comparisonPercent;
    private Totals totals;
    private List<Trend> trend;
    private List<Summary> summary;
    public SalesAnalytics(String period, String selectedDate, double previousRevenue,
                          Double comparisonPercent, Totals totals, List<Trend> trend,
                          List<Summary> summary) {
        this.period = period; this.selectedDate = selectedDate;
        this.previousRevenue = previousRevenue; this.comparisonPercent = comparisonPercent;
        this.totals = totals; this.trend = trend; this.summary = summary;
    }

    public String getPeriod() { return period; }
    public String getSelectedDate() { return selectedDate; }
    public Double getComparisonPercent() { return comparisonPercent; }
    public Totals getTotals() { return totals; }
    public List<Trend> getTrend() { return trend; }
    public List<Summary> getSummary() { return summary; }

    public static final class Trend {
        private String label;
        private double revenue;
        private int pieces;
        public Trend(String label, double revenue, int pieces) {
            this.label = label; this.revenue = revenue; this.pieces = pieces;
        }
        public String getLabel() { return label; }
        public double getRevenue() { return revenue; }
        public int getPieces() { return pieces; }
    }

    public static final class Totals {
        private double revenue;
        private int completedSales;
        private int pieces;
        private double weightKilograms;
        public Totals(double revenue, int completedSales, int pieces,
                      double weightKilograms) {
            this.revenue = revenue; this.completedSales = completedSales;
            this.pieces = pieces; this.weightKilograms = weightKilograms;
        }
        public double getRevenue() { return revenue; }
        public int getCompletedSales() { return completedSales; }
        public int getPieces() { return pieces; }
        public double getWeightKilograms() { return weightKilograms; }
    }

    public static final class Summary {
        private String size;
        private String grade;
        private int pieces;
        private double weightKilograms;
        private double revenue;
        public Summary(String size, String grade, int pieces,
                       double weightKilograms, double revenue) {
            this.size = size; this.grade = grade; this.pieces = pieces;
            this.weightKilograms = weightKilograms; this.revenue = revenue;
        }
        public String getSize() { return size; }
        public String getGrade() { return grade; }
        public int getPieces() { return pieces; }
        public double getWeightKilograms() { return weightKilograms; }
        public double getRevenue() { return revenue; }
    }
}
