package homework;

import java.nio.file.Path;

public class PubSubSimulationResult {

    private final int threadCount;
    private final int publicationCount;
    private final int subscriptionCount;
    private final int brokerCount;
    private final int publisherCount;
    private final int subscriberCount;
    private final long feedDurationMillis;
    private final long successfulPublicationCount;
    private final long notificationCount;
    private final double averageDeliveryLatencyMillis;
    private final double matchingRateAtOneHundredPercent;
    private final double matchingRateAtTwentyFivePercent;
    private final Path publicationFile;
    private final Path subscriptionFile;
    private final Path summaryFile;

    public PubSubSimulationResult(
            int threadCount,
            int publicationCount,
            int subscriptionCount,
            int brokerCount,
            int publisherCount,
            int subscriberCount,
            long feedDurationMillis,
            long successfulPublicationCount,
            long notificationCount,
            double averageDeliveryLatencyMillis,
            double matchingRateAtOneHundredPercent,
            double matchingRateAtTwentyFivePercent,
            Path publicationFile,
            Path subscriptionFile,
            Path summaryFile) {
        this.threadCount = threadCount;
        this.publicationCount = publicationCount;
        this.subscriptionCount = subscriptionCount;
        this.brokerCount = brokerCount;
        this.publisherCount = publisherCount;
        this.subscriberCount = subscriberCount;
        this.feedDurationMillis = feedDurationMillis;
        this.successfulPublicationCount = successfulPublicationCount;
        this.notificationCount = notificationCount;
        this.averageDeliveryLatencyMillis = averageDeliveryLatencyMillis;
        this.matchingRateAtOneHundredPercent = matchingRateAtOneHundredPercent;
        this.matchingRateAtTwentyFivePercent = matchingRateAtTwentyFivePercent;
        this.publicationFile = publicationFile;
        this.subscriptionFile = subscriptionFile;
        this.summaryFile = summaryFile;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public int getPublicationCount() {
        return publicationCount;
    }

    public int getSubscriptionCount() {
        return subscriptionCount;
    }

    public int getBrokerCount() {
        return brokerCount;
    }

    public int getPublisherCount() {
        return publisherCount;
    }

    public int getSubscriberCount() {
        return subscriberCount;
    }

    public long getFeedDurationMillis() {
        return feedDurationMillis;
    }

    public long getSuccessfulPublicationCount() {
        return successfulPublicationCount;
    }

    public long getNotificationCount() {
        return notificationCount;
    }

    public double getAverageDeliveryLatencyMillis() {
        return averageDeliveryLatencyMillis;
    }

    public double getMatchingRateAtOneHundredPercent() {
        return matchingRateAtOneHundredPercent;
    }

    public double getMatchingRateAtTwentyFivePercent() {
        return matchingRateAtTwentyFivePercent;
    }

    public Path getPublicationFile() {
        return publicationFile;
    }

    public Path getSubscriptionFile() {
        return subscriptionFile;
    }

    public Path getSummaryFile() {
        return summaryFile;
    }
}