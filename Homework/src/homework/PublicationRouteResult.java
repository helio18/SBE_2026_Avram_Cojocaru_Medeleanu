package homework;

public class PublicationRouteResult {

    private final int deliveries;
    private final long totalLatencyNanos;
    private final int brokerVisits;

    public PublicationRouteResult(int deliveries, long totalLatencyNanos, int brokerVisits) {
        this.deliveries = deliveries;
        this.totalLatencyNanos = totalLatencyNanos;
        this.brokerVisits = brokerVisits;
    }

    public int getDeliveries() {
        return deliveries;
    }

    public long getTotalLatencyNanos() {
        return totalLatencyNanos;
    }

    public int getBrokerVisits() {
        return brokerVisits;
    }
}
