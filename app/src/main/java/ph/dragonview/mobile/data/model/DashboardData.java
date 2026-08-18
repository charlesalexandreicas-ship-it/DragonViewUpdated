package ph.dragonview.mobile.data.model;

public final class DashboardData {
    private Summary summary;
    private AnalyticsOverview analyticsOverview;

    public DashboardData(Summary summary, AnalyticsOverview analyticsOverview) {
        this.summary = summary;
        this.analyticsOverview = analyticsOverview;
    }

    public Summary getSummary() { return summary; }
    public AnalyticsOverview getAnalyticsOverview() { return analyticsOverview; }

    public static final class Summary {
        private int inventoryPieces;
        private int activeBatches;
        private int salesToday;
        private double monthlyRevenue;
        private int plantingGroups;
        private int classificationsToday;

        public Summary(int inventoryPieces, int activeBatches, int salesToday,
                       double monthlyRevenue, int plantingGroups,
                       int classificationsToday) {
            this.inventoryPieces = inventoryPieces;
            this.activeBatches = activeBatches;
            this.salesToday = salesToday;
            this.monthlyRevenue = monthlyRevenue;
            this.plantingGroups = plantingGroups;
            this.classificationsToday = classificationsToday;
        }

        public int getInventoryPieces() { return inventoryPieces; }
        public int getActiveBatches() { return activeBatches; }
        public int getSalesToday() { return salesToday; }
        public double getMonthlyRevenue() { return monthlyRevenue; }
        public int getPlantingGroups() { return plantingGroups; }
        public int getClassificationsToday() { return classificationsToday; }
    }

    public static final class AnalyticsOverview {
        private double revenueToday;
        private int piecesSoldToday;
        private double weightSoldToday;
        private Double revenueChangePercent;

        public AnalyticsOverview(double revenueToday, int piecesSoldToday,
                                 double weightSoldToday, Double revenueChangePercent) {
            this.revenueToday = revenueToday;
            this.piecesSoldToday = piecesSoldToday;
            this.weightSoldToday = weightSoldToday;
            this.revenueChangePercent = revenueChangePercent;
        }

        public double getRevenueToday() { return revenueToday; }
        public int getPiecesSoldToday() { return piecesSoldToday; }
        public double getWeightSoldToday() { return weightSoldToday; }
        public Double getRevenueChangePercent() { return revenueChangePercent; }
    }
}
