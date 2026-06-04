package homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class PubSubSimulation {

    private static final int EVALUATION_SUBSCRIPTION_COUNT = 10_000;

    private final int brokerCount;
    private final int publisherCount;
    private final int subscriberCount;
    private final long feedDurationMillis;

    private final DatasetGenerator generator;
    private final GeneratorConfig config;

    public PubSubSimulation(DatasetGenerator generator, GeneratorConfig config, int brokerCount, int publisherCount, int subscriberCount, long feedDurationMillis) {
        this.generator = generator;
        this.config = config;
        this.brokerCount = brokerCount;
        this.publisherCount = publisherCount;
        this.subscriberCount = subscriberCount;
        this.feedDurationMillis = feedDurationMillis;
    }

    public PubSubSimulationResult run(int threadCount, Path runDir) throws IOException {
        Files.createDirectories(runDir);

        Publication[] publications = generator.generatePublications(threadCount);
        Subscription[] subscriptions = generator.generateSubscriptions(threadCount);

        Path publicationFile = runDir.resolve("publications.txt");
        Path subscriptionFile = runDir.resolve("subscriptions.txt");
        generator.writePublications(publicationFile, publications);
        generator.writeSubscriptions(subscriptionFile, subscriptions);

        BrokerOverlay overlay = new BrokerOverlay(brokerCount);
        for (Subscription subscription : subscriptions) {
            overlay.registerSubscription(subscription);
        }

        long feedIntervalMillis = Math.max(1L, feedDurationMillis / Math.max(1, publications.length));
        long successfulPublicationCount = 0L;
        long totalNotifications = 0L;
        long totalLatencyMillis = 0L;

        for (int index = 0; index < publications.length; index++) {
            BrokerOverlay.PublicationRouteResult routeResult = overlay.routePublication(publications[index]);

            if (!routeResult.getMatchedSubscriptionIds().isEmpty()) {
                successfulPublicationCount++;
                totalNotifications += routeResult.getMatchedSubscriptionIds().size();
                totalLatencyMillis += routeResult.getDeliveryLatencyMillis();
            }
        }

        MatchingSummary matching100 = evaluateMatchingRate(threadCount, publications, 100);
        MatchingSummary matching25 = evaluateMatchingRate(threadCount, publications, 25);

        double averageLatencyMillis = successfulPublicationCount == 0L
                ? 0.0d
                : (double) totalLatencyMillis / (double) successfulPublicationCount;

        Path summaryFile = runDir.resolve("summary.txt");
        writeSummary(
                summaryFile,
                threadCount,
                publications.length,
                subscriptions.length,
                successfulPublicationCount,
                totalNotifications,
                averageLatencyMillis,
                matching100,
                matching25,
                overlay);

        return new PubSubSimulationResult(
                threadCount,
                publications.length,
                subscriptions.length,
            brokerCount,
            publisherCount,
            subscriberCount,
            feedDurationMillis,
                successfulPublicationCount,
                totalNotifications,
                averageLatencyMillis,
                matching100.matchingRate,
                matching25.matchingRate,
                publicationFile,
                subscriptionFile,
                summaryFile);
    }

    private MatchingSummary evaluateMatchingRate(int threadCount, Publication[] publications, int companyEqualityPercentage) {
        Subscription[] simpleSubscriptions = generator.generateSimpleCompanySubscriptions(
            EVALUATION_SUBSCRIPTION_COUNT,
            threadCount,
            companyEqualityPercentage,
            subscriberCount);

        BrokerOverlay overlay = new BrokerOverlay(brokerCount);
        for (Subscription subscription : simpleSubscriptions) {
            overlay.registerSubscription(subscription);
        }

        long matches = 0L;
        long totalChecks = (long) publications.length * (long) simpleSubscriptions.length;

        for (Publication publication : publications) {
            BrokerOverlay.PublicationRouteResult routeResult = overlay.routePublication(publication);
            matches += routeResult.getMatchedSubscriptionIds().size();
        }

        double matchingRate = totalChecks == 0L ? 0.0d : (100.0d * matches) / (double) totalChecks;
        return new MatchingSummary(matches, totalChecks, matchingRate);
    }

    private void writeSummary(
            Path summaryFile,
            int threadCount,
            int publicationCount,
            int subscriptionCount,
            long successfulPublicationCount,
            long notificationCount,
            double averageLatencyMillis,
            MatchingSummary matching100,
            MatchingSummary matching25,
            BrokerOverlay overlay) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Threads: ").append(threadCount).append('\n');
        builder.append("Publishers: ").append(publisherCount).append('\n');
        builder.append("Subscribers: ").append(subscriberCount).append('\n');
        builder.append("Brokers: ").append(brokerCount).append('\n');
        builder.append("Publications: ").append(publicationCount).append('\n');
        builder.append("Subscriptions: ").append(subscriptionCount).append('\n');
        builder.append("Feed duration ms: ").append(feedDurationMillis).append('\n');
        builder.append("Successful publications delivered: ").append(successfulPublicationCount).append('\n');
        builder.append("Delivered notifications: ").append(notificationCount).append('\n');
        builder.append("Average delivery latency ms: ")
                .append(String.format(Locale.US, "%.2f", averageLatencyMillis))
                .append('\n');
        builder.append('\n');
        builder.append("Overlay distribution:\n");
        for (BrokerNode broker : overlay.getBrokers()) {
            builder.append("- ")
                    .append(broker.getBrokerId())
                    .append(": subscriptions=")
                    .append(broker.getLocalSubscriptionCount())
                    .append(", fragments=")
                    .append(broker.getLocalConditionCount())
                    .append('\n');
        }

        builder.append('\n');
        builder.append("Matching comparison (10,000 simple subscriptions):\n");
        builder.append("- company equality 100%: matches=")
                .append(matching100.matches)
                .append(", checks=")
                .append(matching100.totalChecks)
                .append(", rate=")
                .append(String.format(Locale.US, "%.2f", matching100.matchingRate))
                .append("%\n");
        builder.append("- company equality 25%: matches=")
                .append(matching25.matches)
                .append(", checks=")
                .append(matching25.totalChecks)
                .append(", rate=")
                .append(String.format(Locale.US, "%.2f", matching25.matchingRate))
                .append("%\n");

        Files.writeString(summaryFile, builder.toString());
    }

    private static final class MatchingSummary {
        private final long matches;
        private final long totalChecks;
        private final double matchingRate;

        private MatchingSummary(long matches, long totalChecks, double matchingRate) {
            this.matches = matches;
            this.totalChecks = totalChecks;
            this.matchingRate = matchingRate;
        }
    }
}