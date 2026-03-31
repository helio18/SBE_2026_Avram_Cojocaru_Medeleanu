package homework;

public class ProjectConfig {

    private final int publicationPoolSize;
    private final int simpleSubscriptionCount;
    private final int brokerCount;
    private final int publisherCount;
    private final int subscriberCount;
    private final int evaluationSeconds;
    private final int publishIntervalMillis;
    private final int generationThreadCount;

    public ProjectConfig(
            int publicationPoolSize,
            int simpleSubscriptionCount,
            int brokerCount,
            int publisherCount,
            int subscriberCount,
            int evaluationSeconds,
            int publishIntervalMillis,
            int generationThreadCount) {
        validatePositive("publicationPoolSize", publicationPoolSize);
        validatePositive("simpleSubscriptionCount", simpleSubscriptionCount);
        validateRange("brokerCount", brokerCount, 2, 3);
        validateRange("publisherCount", publisherCount, 1, 2);
        validateRange("subscriberCount", subscriberCount, 2, 3);
        validatePositive("evaluationSeconds", evaluationSeconds);
        validateNonNegative("publishIntervalMillis", publishIntervalMillis);
        validatePositive("generationThreadCount", generationThreadCount);

        this.publicationPoolSize = publicationPoolSize;
        this.simpleSubscriptionCount = simpleSubscriptionCount;
        this.brokerCount = brokerCount;
        this.publisherCount = publisherCount;
        this.subscriberCount = subscriberCount;
        this.evaluationSeconds = evaluationSeconds;
        this.publishIntervalMillis = publishIntervalMillis;
        this.generationThreadCount = generationThreadCount;
    }

    public static ProjectConfig defaultConfig() {
        return new ProjectConfig(5000, 10000, 3, 2, 3, 180, 50, 4);
    }

    public ProjectConfig withPublicationPoolSize(int newPublicationPoolSize) {
        return new ProjectConfig(
                newPublicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                publisherCount,
                subscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withSimpleSubscriptionCount(int newSimpleSubscriptionCount) {
        return new ProjectConfig(
                publicationPoolSize,
                newSimpleSubscriptionCount,
                brokerCount,
                publisherCount,
                subscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withBrokerCount(int newBrokerCount) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                newBrokerCount,
                publisherCount,
                subscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withPublisherCount(int newPublisherCount) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                newPublisherCount,
                subscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withSubscriberCount(int newSubscriberCount) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                publisherCount,
                newSubscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withEvaluationSeconds(int newEvaluationSeconds) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                publisherCount,
                subscriberCount,
                newEvaluationSeconds,
                publishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withPublishIntervalMillis(int newPublishIntervalMillis) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                publisherCount,
                subscriberCount,
                evaluationSeconds,
                newPublishIntervalMillis,
                generationThreadCount);
    }

    public ProjectConfig withGenerationThreadCount(int newGenerationThreadCount) {
        return new ProjectConfig(
                publicationPoolSize,
                simpleSubscriptionCount,
                brokerCount,
                publisherCount,
                subscriberCount,
                evaluationSeconds,
                publishIntervalMillis,
                newGenerationThreadCount);
    }

    public int getPublicationPoolSize() {
        return publicationPoolSize;
    }

    public int getSimpleSubscriptionCount() {
        return simpleSubscriptionCount;
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

    public int getEvaluationSeconds() {
        return evaluationSeconds;
    }

    public int getPublishIntervalMillis() {
        return publishIntervalMillis;
    }

    public int getGenerationThreadCount() {
        return generationThreadCount;
    }

    private static void validatePositive(String fieldName, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " trebuie sa fie > 0.");
        }
    }

    private static void validateNonNegative(String fieldName, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " trebuie sa fie >= 0.");
        }
    }

    private static void validateRange(String fieldName, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    fieldName + " trebuie sa fie intre " + min + " si " + max + ".");
        }
    }
}
