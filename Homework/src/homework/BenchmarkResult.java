package homework;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class BenchmarkResult {

    private final int threadCount;
    private final int publicationCount;
    private final int subscriptionCount;
    private final long publicationGenerationMillis;
    private final long subscriptionGenerationMillis;
    private final long writingMillis;
    private final long totalMillis;
    private final Map<String, Integer> targetFieldCounts;
    private final Map<String, Integer> fieldCounts;
    private final int targetCompanyEqualsCount;
    private final int companyEqualsCount;
    private final Path publicationFile;
    private final Path subscriptionFile;
    private final Path summaryFile;

    public BenchmarkResult(
            int threadCount,
            int publicationCount,
            int subscriptionCount,
            long publicationGenerationMillis,
            long subscriptionGenerationMillis,
            long writingMillis,
            long totalMillis,
            Map<String, Integer> targetFieldCounts,
            Map<String, Integer> fieldCounts,
            int targetCompanyEqualsCount,
            int companyEqualsCount,
            Path publicationFile,
            Path subscriptionFile,
            Path summaryFile) {
        this.threadCount = threadCount;
        this.publicationCount = publicationCount;
        this.subscriptionCount = subscriptionCount;
        this.publicationGenerationMillis = publicationGenerationMillis;
        this.subscriptionGenerationMillis = subscriptionGenerationMillis;
        this.writingMillis = writingMillis;
        this.totalMillis = totalMillis;
        this.targetFieldCounts = new LinkedHashMap<>(targetFieldCounts);
        this.fieldCounts = new LinkedHashMap<>(fieldCounts);
        this.targetCompanyEqualsCount = targetCompanyEqualsCount;
        this.companyEqualsCount = companyEqualsCount;
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

    public long getPublicationGenerationMillis() {
        return publicationGenerationMillis;
    }

    public long getSubscriptionGenerationMillis() {
        return subscriptionGenerationMillis;
    }

    public long getWritingMillis() {
        return writingMillis;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public Map<String, Integer> getFieldCounts() {
        return new LinkedHashMap<>(fieldCounts);
    }

    public Map<String, Integer> getTargetFieldCounts() {
        return new LinkedHashMap<>(targetFieldCounts);
    }

    public int getCompanyPresentCount() {
        Integer value = fieldCounts.get("company");
        return value == null ? 0 : value.intValue();
    }

    public int getTargetCompanyPresentCount() {
        Integer value = targetFieldCounts.get("company");
        return value == null ? 0 : value.intValue();
    }

    public int getTargetCompanyEqualsCount() {
        return targetCompanyEqualsCount;
    }

    public int getCompanyEqualsCount() {
        return companyEqualsCount;
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
