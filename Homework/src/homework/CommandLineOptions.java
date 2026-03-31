package homework;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CommandLineOptions {

    private String mode = "project";
    private Integer publicationCount;
    private Integer subscriptionCount;
    private Integer companyFrequency;
    private Integer valueFrequency;
    private Integer dropFrequency;
    private Integer variationFrequency;
    private Integer dateFrequency;
    private Integer companyEqualityPercentage;
    private Integer brokerCount;
    private Integer publisherCount;
    private Integer subscriberCount;
    private Integer evaluationSeconds;
    private Integer publishIntervalMillis;
    private Long seed;
    private int[] threadCounts;
    private Path outputDir = Path.of("output");
    private boolean helpRequested;

    public static CommandLineOptions parse(String[] args) {
        CommandLineOptions options = new CommandLineOptions();

        for (String arg : args) {
            if (arg.equals("--help")) {
                options.helpRequested = true;
                continue;
            }

            String[] parts = arg.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Argument invalid: " + arg);
            }

            String name = parts[0];
            String value = parts[1];

            switch (name) {
                case "--mode":
                    options.mode = value;
                    break;
                case "--publications":
                    options.publicationCount = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--subscriptions":
                    options.subscriptionCount = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--company-frequency":
                    options.companyFrequency = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--value-frequency":
                    options.valueFrequency = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--drop-frequency":
                    options.dropFrequency = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--variation-frequency":
                    options.variationFrequency = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--date-frequency":
                    options.dateFrequency = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--company-equals":
                    options.companyEqualityPercentage = Integer.valueOf(parsePercentage(name, value));
                    break;
                case "--broker-count":
                    options.brokerCount = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--publisher-count":
                    options.publisherCount = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--subscriber-count":
                    options.subscriberCount = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--evaluation-seconds":
                    options.evaluationSeconds = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--publish-interval-ms":
                    options.publishIntervalMillis = Integer.valueOf(parsePositiveInt(name, value));
                    break;
                case "--seed":
                    options.seed = Long.valueOf(parseLong(name, value));
                    break;
                case "--threads":
                    options.threadCounts = parseThreadCounts(value);
                    break;
                case "--output":
                    options.outputDir = Path.of(value);
                    break;
                default:
                    throw new IllegalArgumentException("Argument necunoscut: " + name);
            }
        }

        return options;
    }

    public GeneratorConfig applyTo(GeneratorConfig baseConfig) {
        GeneratorConfig config = baseConfig;

        if (publicationCount != null) {
            config = config.withPublicationCount(publicationCount.intValue());
        }
        if (subscriptionCount != null) {
            config = config.withSubscriptionCount(subscriptionCount.intValue());
        }
        if (companyFrequency != null) {
            config = config.withFieldPercentage("company", companyFrequency.intValue());
        }
        if (valueFrequency != null) {
            config = config.withFieldPercentage("value", valueFrequency.intValue());
        }
        if (dropFrequency != null) {
            config = config.withFieldPercentage("drop", dropFrequency.intValue());
        }
        if (variationFrequency != null) {
            config = config.withFieldPercentage("variation", variationFrequency.intValue());
        }
        if (dateFrequency != null) {
            config = config.withFieldPercentage("date", dateFrequency.intValue());
        }
        if (companyEqualityPercentage != null) {
            config = config.withCompanyEqualityPercentage(companyEqualityPercentage.intValue());
        }
        if (seed != null) {
            config = config.withBaseSeed(seed.longValue());
        }
        if (threadCounts != null) {
            config = config.withBenchmarkThreadCounts(threadCounts);
        }

        return config;
    }

    public ProjectConfig applyTo(ProjectConfig baseConfig) {
        ProjectConfig config = baseConfig;

        if (publicationCount != null) {
            config = config.withPublicationPoolSize(publicationCount.intValue());
        }
        if (subscriptionCount != null) {
            config = config.withSimpleSubscriptionCount(subscriptionCount.intValue());
        }
        if (brokerCount != null) {
            config = config.withBrokerCount(brokerCount.intValue());
        }
        if (publisherCount != null) {
            config = config.withPublisherCount(publisherCount.intValue());
        }
        if (subscriberCount != null) {
            config = config.withSubscriberCount(subscriberCount.intValue());
        }
        if (evaluationSeconds != null) {
            config = config.withEvaluationSeconds(evaluationSeconds.intValue());
        }
        if (publishIntervalMillis != null) {
            config = config.withPublishIntervalMillis(publishIntervalMillis.intValue());
        }
        if (threadCounts != null && threadCounts.length > 0) {
            config = config.withGenerationThreadCount(threadCounts[0]);
        }

        return config;
    }

    public String getMode() {
        return mode;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

    public static String usageText() {
        return String.join(
                System.lineSeparator(),
                "Usage:",
                "  java -cp bin homework.HomeworkApp [options]",
                "",
                "Optiuni:",
                "  --help",
                "  --mode=project|generator",
                "  --publications=<numar>",
                "  --subscriptions=<numar>",
                "  --threads=<lista separata prin virgula, ex. 1,4>",
                "  --company-frequency=<0..100>",
                "  --value-frequency=<0..100>",
                "  --drop-frequency=<0..100>",
                "  --variation-frequency=<0..100>",
                "  --date-frequency=<0..100>",
                "  --company-equals=<0..100>",
                "  --broker-count=<numar>",
                "  --publisher-count=<numar>",
                "  --subscriber-count=<numar>",
                "  --evaluation-seconds=<numar>",
                "  --publish-interval-ms=<numar>",
                "  --seed=<numar>",
                "  --output=<director>",
                "",
                "Exemple:",
                "  java -cp bin homework.HomeworkApp",
                "  java -cp bin homework.HomeworkApp --mode=project --evaluation-seconds=15 --output=output/project-demo",
                "  java -cp bin homework.HomeworkApp --mode=generator --publications=1000 --subscriptions=1000 --threads=1,4",
                "  java -cp bin homework.HomeworkApp --mode=generator --company-frequency=100 --value-frequency=100 --drop-frequency=0 --variation-frequency=0 --date-frequency=0");
    }

    private static int parsePositiveInt(String name, String value) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " trebuie sa fie > 0.");
        }
        return parsed;
    }

    private static int parsePercentage(String name, String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0 || parsed > 100) {
            throw new IllegalArgumentException(name + " trebuie sa fie intre 0 si 100.");
        }
        return parsed;
    }

    private static long parseLong(String name, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " trebuie sa fie un numar valid.", exception);
        }
    }

    private static int[] parseThreadCounts(String value) {
        String[] parts = value.split(",");
        List<Integer> parsedValues = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int threadCount = parsePositiveInt("--threads", trimmed);
            parsedValues.add(Integer.valueOf(threadCount));
        }

        if (parsedValues.isEmpty()) {
            throw new IllegalArgumentException("--threads trebuie sa contina macar o valoare.");
        }

        int[] result = new int[parsedValues.size()];
        for (int index = 0; index < parsedValues.size(); index++) {
            result[index] = parsedValues.get(index).intValue();
        }
        return result;
    }
}
