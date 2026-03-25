package homework;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DatasetGenerator {

    private static final long PUBLICATION_SEED_STEP = 104_729L;
    private static final long SUBSCRIPTION_SEED_STEP = 130_363L;

    private final GeneratorConfig config;

    public DatasetGenerator(GeneratorConfig config) {
        this.config = config;
    }

    public Publication[] generatePublications(int threadCount) {
        Publication[] publications = new Publication[config.getPublicationCount()];

        runInParallel(publications.length, threadCount, (start, end) -> {
            for (int index = start; index < end; index++) {
                SplittableRandom random = new SplittableRandom(
                        config.getBaseSeed() + ((long) index + 1L) * PUBLICATION_SEED_STEP);

                String company = pickRandom(config.getCompanies(), random);
                double value = randomInRange(random, config.getValueMin(), config.getValueMax());
                double drop = randomInRange(random, config.getDropMin(), config.getDropMax());
                double variation = randomInRange(random, config.getVariationMin(), config.getVariationMax());
                String date = pickRandom(config.getDates(), random);

                publications[index] = new Publication(company, value, drop, variation, date);
            }
        });

        return publications;
    }

    public Subscription[] generateSubscriptions(int threadCount) {
        LinkedHashMap<String, boolean[]> presencePlan = createPresencePlan(config.getSubscriptionCount());
        boolean[] companyEqualityPlan = createCompanyEqualityPlan(presencePlan.get("company"));
        Subscription[] subscriptions = new Subscription[config.getSubscriptionCount()];

        runInParallel(subscriptions.length, threadCount, (start, end) -> {
            for (int index = start; index < end; index++) {
                SplittableRandom random = new SplittableRandom(
                        config.getBaseSeed() + ((long) index + 1L) * SUBSCRIPTION_SEED_STEP);

                List<SubscriptionCondition> conditions = new ArrayList<>();

                if (presencePlan.get("company")[index]) {
                    String operator = companyEqualityPlan[index] ? "=" : "!=";
                    String company = pickRandom(config.getCompanies(), random);
                    conditions.add(SubscriptionCondition.text("company", operator, company));
                }

                if (presencePlan.get("value")[index]) {
                    double value = randomInRange(random, config.getValueMin(), config.getValueMax());
                    conditions.add(SubscriptionCondition.number("value", randomNumericOperator(random), value, 2));
                }

                if (presencePlan.get("drop")[index]) {
                    double drop = randomInRange(random, config.getDropMin(), config.getDropMax());
                    conditions.add(SubscriptionCondition.number("drop", randomNumericOperator(random), drop, 2));
                }

                if (presencePlan.get("variation")[index]) {
                    double variation = randomInRange(
                            random,
                            config.getVariationMin(),
                            config.getVariationMax());
                    conditions.add(SubscriptionCondition.number(
                            "variation",
                            randomNumericOperator(random),
                            variation,
                            4));
                }

                if (presencePlan.get("date")[index]) {
                    String date = pickRandom(config.getDates(), random);
                    conditions.add(SubscriptionCondition.text("date", randomDateOperator(random), date));
                }

                subscriptions[index] = new Subscription(conditions);
            }
        });

        return subscriptions;
    }

    public Map<String, Integer> collectFieldCounts(Subscription[] subscriptions) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String fieldName : config.getSubscriptionFieldPercentages().keySet()) {
            counts.put(fieldName, Integer.valueOf(0));
        }

        for (Subscription subscription : subscriptions) {
            for (SubscriptionCondition condition : subscription.getConditions()) {
                String fieldName = condition.getFieldName();
                counts.put(fieldName, Integer.valueOf(counts.get(fieldName).intValue() + 1));
            }
        }

        return counts;
    }

    public int collectCompanyEqualsCount(Subscription[] subscriptions) {
        int count = 0;

        for (Subscription subscription : subscriptions) {
            for (SubscriptionCondition condition : subscription.getConditions()) {
                if (condition.getFieldName().equals("company") && condition.getOperator().equals("=")) {
                    count++;
                }
            }
        }

        return count;
    }

    public void writePublications(Path outputFile, Publication[] publications) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (int index = 0; index < publications.length; index++) {
                writer.write(publications[index].toFileLine(index + 1));
                writer.newLine();
            }
        }
    }

    public void writeSubscriptions(Path outputFile, Subscription[] subscriptions) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            for (int index = 0; index < subscriptions.length; index++) {
                writer.write(subscriptions[index].toFileLine(index + 1));
                writer.newLine();
            }
        }
    }

    private LinkedHashMap<String, boolean[]> createPresencePlan(int subscriptionCount) {
        LinkedHashMap<String, boolean[]> presencePlan = new LinkedHashMap<>();
        int[] presentCounts = new int[subscriptionCount];
        int offset = 1;

        for (Map.Entry<String, Integer> entry : config.getSubscriptionFieldPercentages().entrySet()) {
            int targetCount = exactCount(subscriptionCount, entry.getValue().intValue());
            boolean[] fieldPresence = createExactBooleanArray(
                    subscriptionCount,
                    targetCount,
                    config.getBaseSeed() + offset * 10_007L);

            presencePlan.put(entry.getKey(), fieldPresence);

            for (int index = 0; index < subscriptionCount; index++) {
                if (fieldPresence[index]) {
                    presentCounts[index]++;
                }
            }

            offset++;
        }

        repairEmptySubscriptions(presencePlan, presentCounts);
        return presencePlan;
    }

    private boolean[] createCompanyEqualityPlan(boolean[] companyPresence) {
        boolean[] companyEquals = new boolean[companyPresence.length];
        List<Integer> companyIndexes = new ArrayList<>();

        for (int index = 0; index < companyPresence.length; index++) {
            if (companyPresence[index]) {
                companyIndexes.add(Integer.valueOf(index));
            }
        }

        shuffle(companyIndexes, new SplittableRandom(config.getBaseSeed() + 99_991L));

        int equalsCount = minimumCount(companyIndexes.size(), config.getCompanyEqualityPercentage());
        for (int index = 0; index < equalsCount; index++) {
            companyEquals[companyIndexes.get(index).intValue()] = true;
        }

        return companyEquals;
    }

    private boolean[] createExactBooleanArray(int totalCount, int trueCount, long seed) {
        boolean[] values = new boolean[totalCount];
        List<Integer> indexes = new ArrayList<>();

        for (int index = 0; index < totalCount; index++) {
            indexes.add(Integer.valueOf(index));
        }

        shuffle(indexes, new SplittableRandom(seed));

        for (int index = 0; index < trueCount; index++) {
            values[indexes.get(index).intValue()] = true;
        }

        return values;
    }

    private void repairEmptySubscriptions(LinkedHashMap<String, boolean[]> presencePlan, int[] presentCounts) {
        LinkedHashMap<String, ArrayDeque<Integer>> donorIndexes = new LinkedHashMap<>();

        for (Map.Entry<String, boolean[]> entry : presencePlan.entrySet()) {
            ArrayDeque<Integer> donors = new ArrayDeque<>();
            boolean[] fieldPresence = entry.getValue();

            for (int index = 0; index < fieldPresence.length; index++) {
                if (fieldPresence[index] && presentCounts[index] > 1) {
                    donors.add(Integer.valueOf(index));
                }
            }

            donorIndexes.put(entry.getKey(), donors);
        }

        for (int index = 0; index < presentCounts.length; index++) {
            if (presentCounts[index] != 0) {
                continue;
            }

            boolean fixed = false;

            for (Map.Entry<String, boolean[]> entry : presencePlan.entrySet()) {
                ArrayDeque<Integer> donors = donorIndexes.get(entry.getKey());
                boolean[] fieldPresence = entry.getValue();

                while (!donors.isEmpty()) {
                    int donorIndex = donors.removeFirst().intValue();

                    if (!fieldPresence[donorIndex] || presentCounts[donorIndex] <= 1) {
                        continue;
                    }

                    fieldPresence[donorIndex] = false;
                    fieldPresence[index] = true;
                    presentCounts[donorIndex]--;
                    presentCounts[index]++;
                    fixed = true;
                    break;
                }

                if (fixed) {
                    break;
                }
            }

            if (!fixed) {
                throw new IllegalStateException(
                        "Could not enforce at least one field in every subscription. Increase percentages.");
            }
        }
    }

    private void runInParallel(int totalCount, int threadCount, RangeTask rangeTask) {
        int safeThreadCount = Math.max(1, Math.min(threadCount, Math.max(1, totalCount)));
        ExecutorService executor = Executors.newFixedThreadPool(safeThreadCount);
        List<Future<?>> futures = new ArrayList<>();
        int chunkSize = (totalCount + safeThreadCount - 1) / safeThreadCount;

        for (int start = 0; start < totalCount; start += chunkSize) {
            int chunkStart = start;
            int chunkEnd = Math.min(start + chunkSize, totalCount);
            futures.add(executor.submit(() -> rangeTask.run(chunkStart, chunkEnd)));
        }

        executor.shutdown();

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception exception) {
                throw new IllegalStateException("Parallel generation failed.", exception);
            }
        }
    }

    private static void shuffle(List<Integer> values, SplittableRandom random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            Integer temp = values.get(index);
            values.set(index, values.get(swapIndex));
            values.set(swapIndex, temp);
        }
    }

    private static <T> T pickRandom(List<T> values, SplittableRandom random) {
        return values.get(random.nextInt(values.size()));
    }

    private static double randomInRange(SplittableRandom random, double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private static String randomNumericOperator(SplittableRandom random) {
        String[] operators = {">=", ">", "<=", "<", "="};
        return operators[random.nextInt(operators.length)];
    }

    private static String randomDateOperator(SplittableRandom random) {
        String[] operators = {"=", "!="};
        return operators[random.nextInt(operators.length)];
    }

    private static int exactCount(int totalCount, int percentage) {
        return (int) Math.round((totalCount * percentage) / 100.0d);
    }

    private static int minimumCount(int totalCount, int percentage) {
        return (int) Math.ceil((totalCount * percentage) / 100.0d);
    }

    @FunctionalInterface
    private interface RangeTask {
        void run(int start, int end);
    }
}
