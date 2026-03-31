package homework;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProjectEvaluationResult {

    private final String scenarioName;
    private final int companyEqualityPercentage;
    private final long emittedPublications;
    private final long successfulDeliveries;
    private final long matchedPublications;
    private final double averageLatencyMillis;
    private final double matchingRatePercent;
    private final double averageRegistrationHops;
    private final double averagePublicationHops;
    private final Map<String, Integer> subscriptionsPerBroker;
    private final Path publicationPoolFile;
    private final Path subscriptionsFile;
    private final Path reportFile;

    public ProjectEvaluationResult(
            String scenarioName,
            int companyEqualityPercentage,
            long emittedPublications,
            long successfulDeliveries,
            long matchedPublications,
            double averageLatencyMillis,
            double matchingRatePercent,
            double averageRegistrationHops,
            double averagePublicationHops,
            Map<String, Integer> subscriptionsPerBroker,
            Path publicationPoolFile,
            Path subscriptionsFile,
            Path reportFile) {
        this.scenarioName = scenarioName;
        this.companyEqualityPercentage = companyEqualityPercentage;
        this.emittedPublications = emittedPublications;
        this.successfulDeliveries = successfulDeliveries;
        this.matchedPublications = matchedPublications;
        this.averageLatencyMillis = averageLatencyMillis;
        this.matchingRatePercent = matchingRatePercent;
        this.averageRegistrationHops = averageRegistrationHops;
        this.averagePublicationHops = averagePublicationHops;
        this.subscriptionsPerBroker = new LinkedHashMap<>(subscriptionsPerBroker);
        this.publicationPoolFile = publicationPoolFile;
        this.subscriptionsFile = subscriptionsFile;
        this.reportFile = reportFile;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public int getCompanyEqualityPercentage() {
        return companyEqualityPercentage;
    }

    public long getEmittedPublications() {
        return emittedPublications;
    }

    public long getSuccessfulDeliveries() {
        return successfulDeliveries;
    }

    public long getMatchedPublications() {
        return matchedPublications;
    }

    public double getAverageLatencyMillis() {
        return averageLatencyMillis;
    }

    public double getMatchingRatePercent() {
        return matchingRatePercent;
    }

    public double getAverageRegistrationHops() {
        return averageRegistrationHops;
    }

    public double getAveragePublicationHops() {
        return averagePublicationHops;
    }

    public Map<String, Integer> getSubscriptionsPerBroker() {
        return new LinkedHashMap<>(subscriptionsPerBroker);
    }

    public Path getPublicationPoolFile() {
        return publicationPoolFile;
    }

    public Path getSubscriptionsFile() {
        return subscriptionsFile;
    }

    public Path getReportFile() {
        return reportFile;
    }
}
