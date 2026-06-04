package homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeworkApp {

    public static void main(String[] args) throws IOException {
        CommandLineOptions options = CommandLineOptions.parse(args);
        if (options.isHelpRequested()) {
            System.out.println(CommandLineOptions.usageText());
            return;
        }

        GeneratorConfig config = options.applyTo(GeneratorConfig.defaultConfig());
        DatasetGenerator generator = new DatasetGenerator(config);
        SystemInfo systemInfo = SystemInfo.read();
        Path outputDir = options.getOutputDir();
        Files.createDirectories(outputDir);

        int brokerCount = options.getBrokerCount() != null ? options.getBrokerCount().intValue() : 3;
        int publisherCount = options.getPublisherCount() != null ? options.getPublisherCount().intValue() : 2;
        int subscriberCount = options.getSubscriberCount() != null ? options.getSubscriberCount().intValue() : 3;
        long feedDurationMillis = options.getFeedDurationMillis() != null ? options.getFeedDurationMillis().longValue() : 180_000L;

        PubSubSimulation simulation = new PubSubSimulation(generator, config, brokerCount, publisherCount, subscriberCount, feedDurationMillis);
        List<PubSubSimulationResult> results = new ArrayList<>();

        for (int threadCount : config.getBenchmarkThreadCounts()) {
            Path runDir = outputDir.resolve("threads-" + threadCount);
            PubSubSimulationResult result = simulation.run(threadCount, runDir);
            results.add(result);

            System.out.println(
                    "Finished run with "
                            + threadCount
                            + " thread(s): delivered "
                            + result.getSuccessfulPublicationCount()
                            + " publications");
        }

        Path readmeFile = outputDir.resolve("README.md");
        writeReadme(readmeFile, config, systemInfo, results);

        if (outputDir.normalize().equals(Path.of("output"))) {
            writeReadme(Path.of("README.md"), config, systemInfo, results);
        }

        System.out.println("README.md updated with simulation results.");
    }

    private static void writeReadme(
            Path readmeFile,
            GeneratorConfig config,
            SystemInfo systemInfo,
            List<PubSubSimulationResult> results) throws IOException {
        PubSubSimulationResult referenceResult = results.get(results.size() - 1);

        StringBuilder builder = new StringBuilder();
        builder.append("# Homework - Publish/Subscribe Content-Based System\n\n");
        builder.append("Acest proiect implementeaza o arhitectura publish/subscribe content-based in Java.\n");
        builder.append("Publicatiile si subscriptiile sunt generate determinist, apoi sunt rutate printr-un overlay de brokeri\n");
        builder.append("care distribuie fragmentele de subscriptii pe mai multe noduri si calculeaza matching-ul pe continut.\n\n");

        builder.append("## Configuratie\n\n");
        builder.append("- publicatii generate: `").append(config.getPublicationCount()).append("`\n");
        builder.append("- subscriptii generate: `").append(config.getSubscriptionCount()).append("`\n");
        builder.append("- thread-uri testate: `").append(formatThreadCounts(config.getBenchmarkThreadCounts())).append("`\n");
        builder.append("- brokeri in overlay: `").append(referenceResult.getBrokerCount()).append("`\n");
        builder.append("- publisheri simulati: `").append(referenceResult.getPublisherCount()).append("`\n");
        builder.append("- subscriberi simulati: `").append(referenceResult.getSubscriberCount()).append("`\n");
        builder.append("- durata feed-ului: `").append(referenceResult.getFeedDurationMillis()).append(" ms`\n\n");

        builder.append("## Rezultate\n\n");
        builder.append("| Threads | Publicatii livrate | Notificari | Latență medie ms | Matching 100% | Matching 25% |\n");
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");
        for (PubSubSimulationResult result : results) {
            builder.append("| ")
                    .append(result.getThreadCount())
                    .append(" | ")
                    .append(result.getSuccessfulPublicationCount())
                    .append(" | ")
                    .append(result.getNotificationCount())
                    .append(" | ")
                    .append(formatPercent(result.getAverageDeliveryLatencyMillis()))
                    .append(" | ")
                    .append(formatPercent(result.getMatchingRateAtOneHundredPercent()))
                    .append("% | ")
                    .append(formatPercent(result.getMatchingRateAtTwentyFivePercent()))
                    .append("% |\n");
        }

        builder.append('\n');
        builder.append("## Evaluare\n\n");
        long feedMillis = referenceResult.getFeedDurationMillis();
        String feedDisplay = (feedMillis % 60000L == 0L)
            ? (String.format("%d minute(s)", feedMillis / 60000L))
            : (String.format(Locale.US, "%d ms", feedMillis));
        builder.append("- feed continuu simulat: `").append(feedDisplay).append("`\n");
        builder.append("- subscriptii simple folosite la comparatie: `10,000`\n");
        builder.append("- raportul include numarul de publicatii livrate cu succes, latenta medie si rata de matching pentru company equality `100%` vs `25%`.\n\n");

        builder.append("## Specificatii masina\n\n");
        builder.append("- CPU: `").append(systemInfo.getCpuModel()).append("`\n");
        builder.append("- logical cores: `").append(systemInfo.getLogicalCores()).append("`\n");
        builder.append("- OS: `").append(systemInfo.getOsName()).append("`\n");
        builder.append("- Java: `").append(systemInfo.getJavaVersion()).append("`\n");
        builder.append("- raport generat la: `")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("`\n\n");

        builder.append("## Fisiere generate\n\n");
        for (PubSubSimulationResult result : results) {
            builder.append("- run `")
                    .append(result.getThreadCount())
                    .append("` thread-uri:\n");
            builder.append("  - `").append(result.getPublicationFile()).append("`\n");
            builder.append("  - `").append(result.getSubscriptionFile()).append("`\n");
            builder.append("  - `").append(result.getSummaryFile()).append("`\n");
        }

        builder.append('\n');
        builder.append("## Rulare\n\n");
        builder.append("Din terminal, din folderul `Homework`:\n\n");
        builder.append("```bash\n");
        builder.append("Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName } | javac -d bin\n");
        builder.append("java -cp bin homework.HomeworkApp\n");
        builder.append("```\n");

        builder.append('\n');
        builder.append("Exemplu pentru rulare custom:\n\n");
        builder.append("```bash\n");
        builder.append("java -cp bin homework.HomeworkApp --publications=1000 --subscriptions=1000 --threads=1,4 --output=output/test-small\n");
        builder.append("```\n");

        Files.writeString(readmeFile, builder.toString());
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String formatThreadCounts(int[] threadCounts) {
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < threadCounts.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(threadCounts[index]);
        }

        return builder.toString();
    }
}
