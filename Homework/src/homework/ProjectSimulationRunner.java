package homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;

public class ProjectSimulationRunner {

    private final GeneratorConfig baseGeneratorConfig;
    private final ProjectConfig projectConfig;

    public ProjectSimulationRunner(GeneratorConfig baseGeneratorConfig, ProjectConfig projectConfig) {
        this.baseGeneratorConfig = baseGeneratorConfig;
        this.projectConfig = projectConfig;
    }

    public List<ProjectEvaluationResult> run(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        List<ProjectEvaluationResult> results = new ArrayList<>();
        results.add(runScenario(outputDir.resolve("scenario-equals-100"), 100));
        results.add(runScenario(outputDir.resolve("scenario-equals-25"), 25));
        return results;
    }

    private ProjectEvaluationResult runScenario(Path scenarioDir, int companyEqualityPercentage) throws IOException {
        Files.createDirectories(scenarioDir);

        GeneratorConfig projectGeneratorConfig = createProjectGeneratorConfig();

        DatasetGenerator publicationGenerator = new DatasetGenerator(
                projectGeneratorConfig.withPublicationCount(projectConfig.getPublicationPoolSize()));
        Publication[] publicationPool = publicationGenerator.generatePublications(projectConfig.getGenerationThreadCount());
        Path publicationPoolFile = scenarioDir.resolve("publication-pool.txt");
        publicationGenerator.writePublications(publicationPoolFile, publicationPool);

        GeneratorConfig subscriptionConfig = createSimpleSubscriptionConfig(projectGeneratorConfig, companyEqualityPercentage);
        DatasetGenerator subscriptionGenerator = new DatasetGenerator(subscriptionConfig);
        Subscription[] subscriptions = subscriptionGenerator.generateSubscriptions(projectConfig.getGenerationThreadCount());
        Path subscriptionsFile = scenarioDir.resolve("simple-subscriptions.txt");
        subscriptionGenerator.writeSubscriptions(subscriptionsFile, subscriptions);

        List<BrokerNode> brokers = createBrokers(projectConfig.getBrokerCount());
        List<SubscriberNode> subscribers = createSubscribers(projectConfig.getSubscriberCount());
        SubscriptionRouter router = new SubscriptionRouter();
        SplittableRandom registrationRandom = new SplittableRandom(baseGeneratorConfig.getBaseSeed() + companyEqualityPercentage);

        long totalRegistrationHops = 0L;
        for (int index = 0; index < subscriptions.length; index++) {
            SubscriberNode subscriber = subscribers.get(registrationRandom.nextInt(subscribers.size()));
            int entryBrokerIndex = registrationRandom.nextInt(brokers.size());

            RegisteredSubscription registeredSubscription = new RegisteredSubscription(
                    "subscription-" + (index + 1),
                    subscriber,
                    subscriptions[index]);

            totalRegistrationHops += router.routeSubscription(brokers, entryBrokerIndex, registeredSubscription);
        }

        ProjectMetrics metrics = new ProjectMetrics();
        long deadlineNanos = System.nanoTime() + (projectConfig.getEvaluationSeconds() * 1_000_000_000L);
        List<Thread> publisherThreads = new ArrayList<>();

        for (int publisherIndex = 0; publisherIndex < projectConfig.getPublisherCount(); publisherIndex++) {
            Thread publisherThread = new Thread(
                    new PublisherWorker(
                            publisherIndex,
                            publicationPool,
                            brokers,
                            deadlineNanos,
                            projectConfig.getPublishIntervalMillis(),
                            baseGeneratorConfig.getBaseSeed(),
                            metrics),
                    "publisher-" + (publisherIndex + 1));
            publisherThreads.add(publisherThread);
            publisherThread.start();
        }

        for (Thread publisherThread : publisherThreads) {
            try {
                publisherThread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Publisher simulation interrupted.", exception);
            }
        }

        Map<String, Integer> subscriptionsPerBroker = new LinkedHashMap<>();
        for (BrokerNode broker : brokers) {
            subscriptionsPerBroker.put(broker.getBrokerId(), Integer.valueOf(broker.getStoredSubscriptionCount()));
        }

        double averageLatencyMillis = metrics.deliveries.get() == 0L
                ? 0.0d
                : (metrics.totalLatencyNanos.get() / 1_000_000.0d) / metrics.deliveries.get();
        double matchingRatePercent = metrics.emittedPublications.get() == 0L
                ? 0.0d
                : (100.0d * metrics.matchedPublications.get()) / metrics.emittedPublications.get();
        double averageRegistrationHops = subscriptions.length == 0
                ? 0.0d
                : (double) totalRegistrationHops / subscriptions.length;
        double averagePublicationHops = metrics.emittedPublications.get() == 0L
                ? 0.0d
                : (double) metrics.totalPublicationHops.get() / metrics.emittedPublications.get();

        Path reportFile = scenarioDir.resolve("scenario-report.txt");
        ProjectEvaluationResult result = new ProjectEvaluationResult(
                "equals-" + companyEqualityPercentage,
                companyEqualityPercentage,
                metrics.emittedPublications.get(),
                metrics.deliveries.get(),
                metrics.matchedPublications.get(),
                averageLatencyMillis,
                matchingRatePercent,
                averageRegistrationHops,
                averagePublicationHops,
                subscriptionsPerBroker,
                publicationPoolFile,
                subscriptionsFile,
                reportFile);

        writeScenarioReport(reportFile, result);
        return result;
    }

    public void writeProjectReadme(
            Path readmeFile,
            SystemInfo systemInfo,
            List<ProjectEvaluationResult> results) throws IOException {
        ProjectEvaluationResult equals100 = results.get(0);
        ProjectEvaluationResult equals25 = results.get(1);

        StringBuilder builder = new StringBuilder();
        builder.append("# Homework - Sistem publish/subscribe content-based\n\n");
        builder.append("Acest proiect extinde generatorul initial de date intr-o simulare completa de sistem pub/sub.\n");
        builder.append("Implementarea acopera partea non-bonus din cerinta: publisheri, brokeri, subscriberi,\n");
        builder.append("rutare intre brokeri si evaluare pentru doua scenarii de matching.\n\n");

        builder.append("## Arhitectura implementata\n\n");
        builder.append("- `")
                .append(projectConfig.getPublisherCount())
                .append("` publisheri care emit un flux continuu de publicatii generate din generatorul initial\n");
        builder.append("- `")
                .append(projectConfig.getBrokerCount())
                .append("` brokeri intr-un overlay de tip inel; fiecare broker stocheaza doar o parte din subscriptii\n");
        builder.append("- `")
                .append(projectConfig.getSubscriberCount())
                .append("` subscriberi simulati care se conecteaza aleatoriu la brokeri pentru a inregistra subscriptii\n");
        builder.append("- subscriptiile aceluiasi subscriber sunt distribuite balansat pe brokeri printr-un mecanism round-robin\n");
        builder.append("- fiecare publicatie trece prin tot overlay-ul, fiecare broker facand matching doar pe subscriptiile locale\n\n");

        builder.append("## Configuratia evaluarii\n\n");
        builder.append("- subscriptii simple inregistrate per scenariu: `")
                .append(projectConfig.getSimpleSubscriptionCount())
                .append("`\n");
        builder.append("- pool de publicatii: `")
                .append(projectConfig.getPublicationPoolSize())
                .append("`\n");
        builder.append("- brokeri: `")
                .append(projectConfig.getBrokerCount())
                .append("`\n");
        builder.append("- publisheri: `")
                .append(projectConfig.getPublisherCount())
                .append("`\n");
        builder.append("- subscriberi: `")
                .append(projectConfig.getSubscriberCount())
                .append("`\n");
        builder.append("- durata feed-ului continuu: `")
                .append(projectConfig.getEvaluationSeconds())
                .append("` secunde\n");
        builder.append("- interval intre doua publicatii emise de acelasi publisher: `")
                .append(projectConfig.getPublishIntervalMillis())
                .append("` ms\n\n");

        builder.append("## Rezultate scenarii\n\n");
        builder.append("| Scenariu | Egalitate company | Publicatii emise | Livrari reusite | Publicatii potrivite | Latenta medie ms | Matching rate | Hop-uri medii publicatii |\n");
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        appendScenarioRow(builder, equals100);
        appendScenarioRow(builder, equals25);

        builder.append('\n');
        builder.append("## Interpretare\n\n");
        builder.append("- `Livrari reusite` = numarul total de notificari trimise subscriberilor prin reteaua de brokeri\n");
        builder.append("- `Publicatii potrivite` = numarul de publicatii care au avut cel putin un match\n");
        builder.append("- `Matching rate` = procentul de publicatii care au avut cel putin un match\n");
        builder.append("- comparatia intre scenariul `100% =` si `25% =` arata impactul distributiei operatorului `=` asupra matching-ului\n\n");

        builder.append("## Balansare subscriptii pe brokeri\n\n");
        writeBrokerDistribution(builder, equals100);

        builder.append('\n');
        builder.append("## Fisiere generate\n\n");
        for (ProjectEvaluationResult result : results) {
            builder.append("- scenariu `")
                    .append(result.getScenarioName())
                    .append("`:\n");
            builder.append("  - `")
                    .append(result.getPublicationPoolFile())
                    .append("`\n");
            builder.append("  - `")
                    .append(result.getSubscriptionsFile())
                    .append("`\n");
            builder.append("  - `")
                    .append(result.getReportFile())
                    .append("`\n");
        }

        builder.append('\n');
        builder.append("## Specificatii masina\n\n");
        builder.append("- CPU: `").append(systemInfo.getCpuModel()).append("`\n");
        builder.append("- logical cores: `").append(systemInfo.getLogicalCores()).append("`\n");
        builder.append("- OS: `").append(systemInfo.getOsName()).append("`\n");
        builder.append("- Java: `").append(systemInfo.getJavaVersion()).append("`\n");
        builder.append("- raport generat la: `")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("`\n");

        Files.writeString(readmeFile, builder.toString());
    }

    private void writeScenarioReport(Path reportFile, ProjectEvaluationResult result) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Scenario: ").append(result.getScenarioName()).append('\n');
        builder.append("Company equality: ").append(result.getCompanyEqualityPercentage()).append("%\n");
        builder.append("Emitted publications: ").append(result.getEmittedPublications()).append('\n');
        builder.append("Successful deliveries: ").append(result.getSuccessfulDeliveries()).append('\n');
        builder.append("Matched publications: ").append(result.getMatchedPublications()).append('\n');
        builder.append("Average latency ms: ")
                .append(String.format(Locale.US, "%.4f", result.getAverageLatencyMillis()))
                .append('\n');
        builder.append("Matching rate percent: ")
                .append(String.format(Locale.US, "%.4f", result.getMatchingRatePercent()))
                .append('\n');
        builder.append("Average registration hops: ")
                .append(String.format(Locale.US, "%.4f", result.getAverageRegistrationHops()))
                .append('\n');
        builder.append("Average publication hops: ")
                .append(String.format(Locale.US, "%.4f", result.getAveragePublicationHops()))
                .append('\n');
        builder.append("Subscriptions per broker:\n");

        for (Map.Entry<String, Integer> entry : result.getSubscriptionsPerBroker().entrySet()) {
            builder.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append('\n');
        }

        Files.writeString(reportFile, builder.toString());
    }

    private GeneratorConfig createSimpleSubscriptionConfig(
            GeneratorConfig projectGeneratorConfig,
            int companyEqualityPercentage) {
        return projectGeneratorConfig
                .withPublicationCount(projectConfig.getPublicationPoolSize())
                .withSubscriptionCount(projectConfig.getSimpleSubscriptionCount())
                .withFieldPercentage("company", 100)
                .withFieldPercentage("value", 0)
                .withFieldPercentage("drop", 0)
                .withFieldPercentage("variation", 0)
                .withFieldPercentage("date", 0)
                .withCompanyEqualityPercentage(companyEqualityPercentage);
    }

    private GeneratorConfig createProjectGeneratorConfig() {
        return baseGeneratorConfig.withCompanies(createLargeCompanyDomain());
    }

    private List<String> createLargeCompanyDomain() {
        int companyCount = Math.max(projectConfig.getSimpleSubscriptionCount() * 2, 20000);
        List<String> companies = new ArrayList<>();

        for (int index = 0; index < companyCount; index++) {
            companies.add(String.format(Locale.US, "Company-%05d", index + 1));
        }

        return companies;
    }

    private List<BrokerNode> createBrokers(int brokerCount) {
        List<BrokerNode> brokers = new ArrayList<>();
        for (int index = 0; index < brokerCount; index++) {
            brokers.add(new BrokerNode("broker-" + (index + 1)));
        }
        return brokers;
    }

    private List<SubscriberNode> createSubscribers(int subscriberCount) {
        List<SubscriberNode> subscribers = new ArrayList<>();
        for (int index = 0; index < subscriberCount; index++) {
            subscribers.add(new SubscriberNode("subscriber-" + (index + 1)));
        }
        return subscribers;
    }

    private PublicationRouteResult routePublication(List<BrokerNode> brokers, int entryBrokerIndex, Publication publication, long emitNanoTime) {
        Set<String> notifiedSubscribers = new HashSet<>();
        int totalDeliveries = 0;
        long totalLatencyNanos = 0L;

        for (int offset = 0; offset < brokers.size(); offset++) {
            BrokerNode broker = brokers.get((entryBrokerIndex + offset) % brokers.size());
            PublicationRouteResult partialResult = broker.matchPublication(publication, emitNanoTime, notifiedSubscribers);
            totalDeliveries += partialResult.getDeliveries();
            totalLatencyNanos += partialResult.getTotalLatencyNanos();
        }

        return new PublicationRouteResult(totalDeliveries, totalLatencyNanos, brokers.size());
    }

    private void appendScenarioRow(StringBuilder builder, ProjectEvaluationResult result) {
        builder.append("| ")
                .append(result.getScenarioName())
                .append(" | ")
                .append(result.getCompanyEqualityPercentage())
                .append("% | ")
                .append(result.getEmittedPublications())
                .append(" | ")
                .append(result.getSuccessfulDeliveries())
                .append(" | ")
                .append(result.getMatchedPublications())
                .append(" | ")
                .append(String.format(Locale.US, "%.4f", result.getAverageLatencyMillis()))
                .append(" | ")
                .append(String.format(Locale.US, "%.4f", result.getMatchingRatePercent()))
                .append("% | ")
                .append(String.format(Locale.US, "%.2f", result.getAveragePublicationHops()))
                .append(" |\n");
    }

    private void writeBrokerDistribution(StringBuilder builder, ProjectEvaluationResult result) {
        for (Map.Entry<String, Integer> entry : result.getSubscriptionsPerBroker().entrySet()) {
            builder.append("- ")
                    .append(entry.getKey())
                    .append(": `")
                    .append(entry.getValue())
                    .append("` subscriptii stocate\n");
        }
        builder.append("- hop-uri medii la inregistrare: `")
                .append(String.format(Locale.US, "%.2f", result.getAverageRegistrationHops()))
                .append("`\n");
    }

    private static final class ProjectMetrics {
        private final AtomicLong emittedPublications = new AtomicLong();
        private final AtomicLong deliveries = new AtomicLong();
        private final AtomicLong matchedPublications = new AtomicLong();
        private final AtomicLong totalLatencyNanos = new AtomicLong();
        private final AtomicLong totalPublicationHops = new AtomicLong();
    }

    private final class PublisherWorker implements Runnable {

        private final int publisherIndex;
        private final Publication[] publicationPool;
        private final List<BrokerNode> brokers;
        private final long deadlineNanos;
        private final int publishIntervalMillis;
        private final long baseSeed;
        private final ProjectMetrics metrics;

        private PublisherWorker(
                int publisherIndex,
                Publication[] publicationPool,
                List<BrokerNode> brokers,
                long deadlineNanos,
                int publishIntervalMillis,
                long baseSeed,
                ProjectMetrics metrics) {
            this.publisherIndex = publisherIndex;
            this.publicationPool = publicationPool;
            this.brokers = brokers;
            this.deadlineNanos = deadlineNanos;
            this.publishIntervalMillis = publishIntervalMillis;
            this.baseSeed = baseSeed;
            this.metrics = metrics;
        }

        @Override
        public void run() {
            SplittableRandom random = new SplittableRandom(baseSeed + 1000L + publisherIndex);
            int localIndex = 0;

            while (System.nanoTime() < deadlineNanos) {
                Publication publication = publicationPool[localIndex % publicationPool.length];
                long emitNanoTime = System.nanoTime();
                int entryBrokerIndex = random.nextInt(brokers.size());
                PublicationRouteResult routeResult = routePublication(
                        brokers,
                        entryBrokerIndex,
                        publication,
                        emitNanoTime);

                metrics.emittedPublications.incrementAndGet();
                metrics.deliveries.addAndGet(routeResult.getDeliveries());
                metrics.totalLatencyNanos.addAndGet(routeResult.getTotalLatencyNanos());
                metrics.totalPublicationHops.addAndGet(routeResult.getBrokerVisits());

                if (routeResult.getDeliveries() > 0) {
                    metrics.matchedPublications.incrementAndGet();
                }

                localIndex++;

                if (publishIntervalMillis > 0) {
                    try {
                        Thread.sleep(publishIntervalMillis);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
