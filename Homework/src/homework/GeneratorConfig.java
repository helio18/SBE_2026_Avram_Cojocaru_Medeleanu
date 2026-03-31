package homework;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeneratorConfig {

    private final int publicationCount;
    private final int subscriptionCount;
    private final long baseSeed;
    private final LinkedHashMap<String, Integer> subscriptionFieldPercentages;
    private final int companyEqualityPercentage;
    private final List<String> companies;
    private final List<String> dates;
    private final double valueMin;
    private final double valueMax;
    private final double dropMin;
    private final double dropMax;
    private final double variationMin;
    private final double variationMax;
    private final int[] benchmarkThreadCounts;

    public GeneratorConfig(
            int publicationCount,
            int subscriptionCount,
            long baseSeed,
            LinkedHashMap<String, Integer> subscriptionFieldPercentages,
            int companyEqualityPercentage,
            List<String> companies,
            List<String> dates,
            double valueMin,
            double valueMax,
            double dropMin,
            double dropMax,
            double variationMin,
            double variationMax,
            int[] benchmarkThreadCounts) {
        this.publicationCount = publicationCount;
        this.subscriptionCount = subscriptionCount;
        this.baseSeed = baseSeed;
        this.subscriptionFieldPercentages = new LinkedHashMap<>(subscriptionFieldPercentages);
        this.companyEqualityPercentage = companyEqualityPercentage;
        this.companies = List.copyOf(companies);
        this.dates = List.copyOf(dates);
        this.valueMin = valueMin;
        this.valueMax = valueMax;
        this.dropMin = dropMin;
        this.dropMax = dropMax;
        this.variationMin = variationMin;
        this.variationMax = variationMax;
        this.benchmarkThreadCounts = benchmarkThreadCounts.clone();
    }

    public static GeneratorConfig defaultConfig() {
        LinkedHashMap<String, Integer> fieldPercentages = new LinkedHashMap<>();
        fieldPercentages.put("company", Integer.valueOf(90));
        fieldPercentages.put("value", Integer.valueOf(70));
        fieldPercentages.put("drop", Integer.valueOf(55));
        fieldPercentages.put("variation", Integer.valueOf(65));
        fieldPercentages.put("date", Integer.valueOf(40));

        return new GeneratorConfig(
                40_000,
                40_000,
                2026_0325L,
                fieldPercentages,
                70,
                List.of(
                        "Google",
                        "Amazon",
                        "Microsoft",
                        "Apple",
                        "Meta",
                        "Netflix",
                        "Nvidia",
                        "Oracle"),
                List.of(
                        "2023-01-15",
                        "2023-03-20",
                        "2023-06-01",
                        "2023-09-12",
                        "2024-01-08",
                        "2024-04-18",
                        "2024-08-30",
                        "2025-02-14"),
                10.0d,
                1000.0d,
                0.0d,
                60.0d,
                -1.0d,
                1.0d,
                new int[] {1, 4});
    }

    public GeneratorConfig withPublicationCount(int newPublicationCount) {
        return new GeneratorConfig(
                newPublicationCount,
                subscriptionCount,
                baseSeed,
                subscriptionFieldPercentages,
                companyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withSubscriptionCount(int newSubscriptionCount) {
        return new GeneratorConfig(
                publicationCount,
                newSubscriptionCount,
                baseSeed,
                subscriptionFieldPercentages,
                companyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withBaseSeed(long newBaseSeed) {
        return new GeneratorConfig(
                publicationCount,
                subscriptionCount,
                newBaseSeed,
                subscriptionFieldPercentages,
                companyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withFieldPercentage(String fieldName, int newPercentage) {
        LinkedHashMap<String, Integer> updatedPercentages = new LinkedHashMap<>(subscriptionFieldPercentages);
        updatedPercentages.put(fieldName, Integer.valueOf(newPercentage));

        return new GeneratorConfig(
                publicationCount,
                subscriptionCount,
                baseSeed,
                updatedPercentages,
                companyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withCompanies(List<String> newCompanies) {
        return new GeneratorConfig(
                publicationCount,
                subscriptionCount,
                baseSeed,
                subscriptionFieldPercentages,
                companyEqualityPercentage,
                newCompanies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withCompanyEqualityPercentage(int newCompanyEqualityPercentage) {
        return new GeneratorConfig(
                publicationCount,
                subscriptionCount,
                baseSeed,
                subscriptionFieldPercentages,
                newCompanyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                benchmarkThreadCounts);
    }

    public GeneratorConfig withBenchmarkThreadCounts(int[] newBenchmarkThreadCounts) {
        return new GeneratorConfig(
                publicationCount,
                subscriptionCount,
                baseSeed,
                subscriptionFieldPercentages,
                companyEqualityPercentage,
                companies,
                dates,
                valueMin,
                valueMax,
                dropMin,
                dropMax,
                variationMin,
                variationMax,
                newBenchmarkThreadCounts);
    }

    public int getPublicationCount() {
        return publicationCount;
    }

    public int getSubscriptionCount() {
        return subscriptionCount;
    }

    public long getBaseSeed() {
        return baseSeed;
    }

    public Map<String, Integer> getSubscriptionFieldPercentages() {
        return new LinkedHashMap<>(subscriptionFieldPercentages);
    }

    public int getCompanyEqualityPercentage() {
        return companyEqualityPercentage;
    }

    public List<String> getCompanies() {
        return companies;
    }

    public List<String> getDates() {
        return dates;
    }

    public double getValueMin() {
        return valueMin;
    }

    public double getValueMax() {
        return valueMax;
    }

    public double getDropMin() {
        return dropMin;
    }

    public double getDropMax() {
        return dropMax;
    }

    public double getVariationMin() {
        return variationMin;
    }

    public double getVariationMax() {
        return variationMax;
    }

    public int[] getBenchmarkThreadCounts() {
        return benchmarkThreadCounts.clone();
    }
}
