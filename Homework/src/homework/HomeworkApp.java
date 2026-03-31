package homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        List<BenchmarkResult> results = new ArrayList<>();

        for (int threadCount : config.getBenchmarkThreadCounts()) {
            Path runDir = outputDir.resolve("threads-" + threadCount);
            Files.createDirectories(runDir);
            BenchmarkResult result = runBenchmark(generator, config, threadCount, runDir);
            results.add(result);

            System.out.println(
                    "Finished run with "
                            + threadCount
                            + " thread(s): total "
                            + result.getTotalMillis()
                            + " ms");
        }

        if (outputDir.normalize().equals(Path.of("output"))) {
            writeReadme(Path.of("README.md"), config, systemInfo, results);
        }
        writeReadme(outputDir.resolve("README.md"), config, systemInfo, results);
        System.out.println("README.md updated with benchmark results.");
    }

    private static BenchmarkResult runBenchmark(
            DatasetGenerator generator,
            GeneratorConfig config,
            int threadCount,
            Path runDir) throws IOException {
        long start = System.nanoTime();
        Publication[] publications = generator.generatePublications(threadCount);
        long afterPublications = System.nanoTime();

        Subscription[] subscriptions = generator.generateSubscriptions(threadCount);
        long afterSubscriptions = System.nanoTime();

        Path publicationFile = runDir.resolve("publications.txt");
        Path subscriptionFile = runDir.resolve("subscriptions.txt");
        generator.writePublications(publicationFile, publications);
        generator.writeSubscriptions(subscriptionFile, subscriptions);
        long afterWriting = System.nanoTime();

        Map<String, Integer> fieldCounts = generator.collectFieldCounts(subscriptions);
        int companyEqualsCount = generator.collectCompanyEqualsCount(subscriptions);
        Path summaryFile = runDir.resolve("summary.txt");

        BenchmarkResult result = new BenchmarkResult(
                threadCount,
                config.getPublicationCount(),
                config.getSubscriptionCount(),
                millisBetween(start, afterPublications),
                millisBetween(afterPublications, afterSubscriptions),
                millisBetween(afterSubscriptions, afterWriting),
                millisBetween(start, afterWriting),
                fieldCounts,
                companyEqualsCount,
                publicationFile,
                subscriptionFile,
                summaryFile);

        writeRunSummary(summaryFile, config, result);
        return result;
    }

    private static void writeRunSummary(Path summaryFile, GeneratorConfig config, BenchmarkResult result)
            throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("Threads: ").append(result.getThreadCount()).append('\n');
        builder.append("Publications: ").append(result.getPublicationCount()).append('\n');
        builder.append("Subscriptions: ").append(result.getSubscriptionCount()).append('\n');
        builder.append("Publication generation ms: ")
                .append(result.getPublicationGenerationMillis())
                .append('\n');
        builder.append("Subscription generation ms: ")
                .append(result.getSubscriptionGenerationMillis())
                .append('\n');
        builder.append("Writing ms: ").append(result.getWritingMillis()).append('\n');
        builder.append("Total ms: ").append(result.getTotalMillis()).append('\n');
        builder.append('\n');
        builder.append("Subscription field frequencies:\n");

        for (Map.Entry<String, Integer> entry : result.getFieldCounts().entrySet()) {
            int count = entry.getValue().intValue();
            double percent = 100.0d * count / result.getSubscriptionCount();
            builder.append("- ")
                    .append(entry.getKey())
                    .append(": target ")
                    .append(config.getSubscriptionFieldPercentages().get(entry.getKey()))
                    .append("%, actual ")
                    .append(count)
                    .append(" (")
                    .append(formatPercent(percent))
                    .append("%)")
                    .append('\n');
        }

        double equalsPercent = result.getCompanyPresentCount() == 0
                ? 0.0d
                : 100.0d * result.getCompanyEqualsCount() / result.getCompanyPresentCount();
        builder.append('\n');
        builder.append("Company equality target: at least ")
                .append(config.getCompanyEqualityPercentage())
                .append("%\n");
        builder.append("Company equality actual: ")
                .append(result.getCompanyEqualsCount())
                .append(" / ")
                .append(result.getCompanyPresentCount())
                .append(" (")
                .append(formatPercent(equalsPercent))
                .append("%)\n");

        Files.writeString(summaryFile, builder.toString());
    }

    private static void writeReadme(
            Path readmeFile,
            GeneratorConfig config,
            SystemInfo systemInfo,
            List<BenchmarkResult> results) throws IOException {
        BenchmarkResult baseline = results.get(0);
        BenchmarkResult referenceResult = results.get(results.size() - 1);

        StringBuilder builder = new StringBuilder();
        builder.append("# Homework - Generator de publicatii si subscriptii\n\n");
        builder.append("Acest proiect implementeaza integral cerinta temei in Java, in VS Code.\n");
        builder.append("Generatorul produce seturi de publicatii si subscriptii, salveaza rezultatele in fisiere text,\n");
        builder.append("ruleaza benchmark cu mai multe niveluri de paralelizare si scrie automat acest raport.\n\n");

        builder.append("## Decizii de implementare\n\n");
        builder.append("- paralelizare: `threads`\n");
        builder.append("- limbaj: `Java 21`\n");
        builder.append("- structura publicatie: fixa, cu campurile `company`, `value`, `drop`, `variation`, `date`\n");
        builder.append("- distributia campurilor din subscriptii este controlata exact pe baza unor numere tinta, nu doar random\n");
        builder.append("- pentru campul `company`, operatorul `=` este controlat separat si respecta pragul minim cerut\n");
        builder.append("- fiecare subscriptie contine cel putin un camp\n\n");

        builder.append("## Configuratie folosita\n\n");
        builder.append("- publicatii generate: `").append(config.getPublicationCount()).append("`\n");
        builder.append("- subscriptii generate: `").append(config.getSubscriptionCount()).append("`\n");
        builder.append("- thread-uri testate: `")
                .append(formatThreadCounts(config.getBenchmarkThreadCounts()))
                .append("`\n");
        builder.append("- frecvente campuri in subscriptii:\n");
        for (Map.Entry<String, Integer> entry : config.getSubscriptionFieldPercentages().entrySet()) {
            builder.append("  - ")
                    .append(entry.getKey())
                    .append(": `")
                    .append(entry.getValue())
                    .append("%`\n");
        }
        builder.append("- prag minim pentru operatorul `=` pe `company`: `")
                .append(config.getCompanyEqualityPercentage())
                .append("%`\n\n");

        builder.append("## Benchmark\n\n");
        builder.append("| Threads | Publicatii ms | Subscriptii ms | Scriere ms | Total ms | Speedup |\n");
        builder.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");

        for (BenchmarkResult result : results) {
            double speedup = baseline.getTotalMillis() == 0
                    ? 1.0d
                    : (double) baseline.getTotalMillis() / (double) result.getTotalMillis();
            builder.append("| ")
                    .append(result.getThreadCount())
                    .append(" | ")
                    .append(result.getPublicationGenerationMillis())
                    .append(" | ")
                    .append(result.getSubscriptionGenerationMillis())
                    .append(" | ")
                    .append(result.getWritingMillis())
                    .append(" | ")
                    .append(result.getTotalMillis())
                    .append(" | ")
                    .append(formatPercent(speedup))
                    .append("x |\n");
        }

        builder.append('\n');
        builder.append("## Verificare distributii\n\n");
        builder.append("Valorile de mai jos provin din rularea cu `")
                .append(referenceResult.getThreadCount())
                .append("` thread-uri.\n\n");
        builder.append("| Camp | Tinta | Obtinut |\n");
        builder.append("| --- | ---: | ---: |\n");

        for (Map.Entry<String, Integer> entry : referenceResult.getFieldCounts().entrySet()) {
            int count = entry.getValue().intValue();
            double actualPercent = 100.0d * count / referenceResult.getSubscriptionCount();
            builder.append("| ")
                    .append(entry.getKey())
                    .append(" | ")
                    .append(config.getSubscriptionFieldPercentages().get(entry.getKey()))
                    .append("% | ")
                    .append(count)
                    .append(" (")
                    .append(formatPercent(actualPercent))
                    .append("%) |\n");
        }

        double equalsPercent = referenceResult.getCompanyPresentCount() == 0
                ? 0.0d
                : 100.0d * referenceResult.getCompanyEqualsCount() / referenceResult.getCompanyPresentCount();
        builder.append('\n');
        builder.append("- `company` cu operator `=`: `")
                .append(referenceResult.getCompanyEqualsCount())
                .append(" / ")
                .append(referenceResult.getCompanyPresentCount())
                .append("` = `")
                .append(formatPercent(equalsPercent))
                .append("%`\n\n");

        builder.append("## Specificatii masina\n\n");
        builder.append("- CPU: `").append(systemInfo.getCpuModel()).append("`\n");
        builder.append("- logical cores: `").append(systemInfo.getLogicalCores()).append("`\n");
        builder.append("- OS: `").append(systemInfo.getOsName()).append("`\n");
        builder.append("- Java: `").append(systemInfo.getJavaVersion()).append("`\n");
        builder.append("- raport generat la: `")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("`\n\n");

        builder.append("## Fisiere generate\n\n");
        for (BenchmarkResult result : results) {
            builder.append("- run `")
                    .append(result.getThreadCount())
                    .append("` thread-uri:\n");
            builder.append("  - `")
                    .append(result.getPublicationFile().toString())
                    .append("`\n");
            builder.append("  - `")
                    .append(result.getSubscriptionFile().toString())
                    .append("`\n");
            builder.append("  - `")
                    .append(result.getSummaryFile().toString())
                    .append("`\n");
        }

        builder.append('\n');
        builder.append("## Rulare\n\n");
        builder.append("Din terminal, din folderul `Homework`:\n\n");
        builder.append("```bash\n");
        builder.append("find src -name '*.java' -print0 | xargs -0 javac -d bin\n");
        builder.append("java -cp bin homework.HomeworkApp\n");
        builder.append("```\n");

        builder.append('\n');
        builder.append("Exemplu pentru testare rapida cu parametri custom:\n\n");
        builder.append("```bash\n");
        builder.append("java -cp bin homework.HomeworkApp --publications=1000 --subscriptions=1000 --threads=1,4 --output=output/test-small\n");
        builder.append("```\n");

        Files.writeString(readmeFile, builder.toString());
    }

    private static long millisBetween(long start, long end) {
        return (end - start) / 1_000_000L;
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
