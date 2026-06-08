package project.subscriber;

import homework.DatasetGenerator;
import homework.GeneratorConfig;
import homework.Subscription;
import project.transport.Args;
import project.transport.LineServer;
import project.transport.MessageCodec;
import project.transport.OutboundConnections;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class SubscriberMain {

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = Args.parse(arguments);
        String subscriberId = options.getOrDefault("id", "S1");
        int listenPort = Integer.parseInt(options.getOrDefault("listen-port", "6001"));
        String brokerListSpec = options.get("brokers");
        if (brokerListSpec == null) {
            throw new IllegalArgumentException("--brokers is required");
        }
        int subscriptionCount = Integer.parseInt(options.getOrDefault("subscriptions", "100"));
        int companyFrequency = Integer.parseInt(options.getOrDefault("company-frequency", "100"));
        int valueFrequency = Integer.parseInt(options.getOrDefault("value-frequency", "0"));
        int dropFrequency = Integer.parseInt(options.getOrDefault("drop-frequency", "0"));
        int variationFrequency = Integer.parseInt(options.getOrDefault("variation-frequency", "0"));
        int dateFrequency = Integer.parseInt(options.getOrDefault("date-frequency", "0"));
        int companyEquals = Integer.parseInt(options.getOrDefault("company-equals", "100"));
        long seed = Long.parseLong(options.getOrDefault("seed", "20260608"));
        int threads = Integer.parseInt(options.getOrDefault("threads", "1"));
        String subIdPrefix = options.getOrDefault("sub-id-prefix", subscriberId);
        String selfHost = options.getOrDefault("self-host", "localhost");
        Path stopFile = options.containsKey("stop-file") ? Path.of(options.get("stop-file")) : null;
        Path statsFile = options.containsKey("stats-file") ? Path.of(options.get("stats-file")) : null;

        List<Endpoint> brokers = parseBrokers(brokerListSpec);

        Stats stats = new Stats();
        LineServer server = new LineServer(listenPort, line -> stats.recordNotification(line));
        server.start();

        LinkedHashMap<String, Integer> fieldPercentages = new LinkedHashMap<>();
        fieldPercentages.put("company", companyFrequency);
        fieldPercentages.put("value", valueFrequency);
        fieldPercentages.put("drop", dropFrequency);
        fieldPercentages.put("variation", variationFrequency);
        fieldPercentages.put("date", dateFrequency);

        GeneratorConfig configuration = GeneratorConfig.defaultConfig()
                .withPublicationCount(1)
                .withSubscriptionCount(subscriptionCount)
                .withBaseSeed(seed)
                .withCompanyEqualityPercentage(companyEquals);
        for (Map.Entry<String, Integer> entry : fieldPercentages.entrySet()) {
            configuration = configuration.withFieldPercentage(entry.getKey(), entry.getValue());
        }

        DatasetGenerator generator = new DatasetGenerator(configuration);
        Subscription[] subscriptions = generator.generateSubscriptions(threads);

        OutboundConnections outbound = new OutboundConnections();
        long sendStartMillis = System.currentTimeMillis();

        for (int index = 0; index < subscriptions.length; index++) {
            Endpoint broker = brokers.get(index % brokers.size());
            String subscriptionId = subIdPrefix + "-" + (index + 1);
            String message = MessageCodec.buildSubscribe(
                    subscriptionId, selfHost, listenPort, subscriptions[index]);
            outbound.sendLine(broker.host, broker.port, message);
        }
        long sendElapsed = System.currentTimeMillis() - sendStartMillis;
        stats.subscriptionsSent = subscriptions.length;

        System.out.println("[" + subscriberId + "] sent " + subscriptions.length
                + " subscriptions in " + sendElapsed + "ms, listening on port " + listenPort);

        if (stopFile != null) {
            Args.waitForStopFile(stopFile, 250L);
        } else {
            while (true) {
                Thread.sleep(60_000L);
            }
        }

        server.stop();
        outbound.closeAll();
        stats.writeTo(subscriberId, statsFile);
    }

    private static List<Endpoint> parseBrokers(String specification) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (String entry : specification.split(",")) {
            int atIndex = entry.indexOf('@');
            int colonIndex = entry.lastIndexOf(':');
            String id = entry.substring(0, atIndex);
            String host = entry.substring(atIndex + 1, colonIndex);
            int port = Integer.parseInt(entry.substring(colonIndex + 1));
            endpoints.add(new Endpoint(id, host, port));
        }
        return endpoints;
    }

    private static final class Endpoint {

        final String host;
        final int port;

        Endpoint(String id, String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class Stats {

        long subscriptionsSent;
        final AtomicLong notificationsReceived = new AtomicLong();
        final LongAdder totalLatencyMs = new LongAdder();
        final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxLatencyMs = new AtomicLong(Long.MIN_VALUE);
        final long startMillis = System.currentTimeMillis();

        void recordNotification(String line) {
            try {
                int firstPipe = line.indexOf('|');
                int secondPipe = line.indexOf('|', firstPipe + 1);
                int thirdPipe = line.indexOf('|', secondPipe + 1);
                if (firstPipe < 0 || secondPipe < 0 || thirdPipe < 0) {
                    return;
                }
                long emitTimestamp = Long.parseLong(line.substring(secondPipe + 1, thirdPipe));
                long latency = System.currentTimeMillis() - emitTimestamp;
                notificationsReceived.incrementAndGet();
                totalLatencyMs.add(latency);
                minLatencyMs.accumulateAndGet(latency, Math::min);
                maxLatencyMs.accumulateAndGet(latency, Math::max);
            } catch (RuntimeException ignored) {
            }
        }

        void writeTo(String subscriberId, Path file) throws IOException {
            long count = notificationsReceived.get();
            double averageLatency = count > 0 ? (totalLatencyMs.sum() * 1.0 / count) : 0.0;
            long minLatency = count > 0 ? minLatencyMs.get() : 0L;
            long maxLatency = count > 0 ? maxLatencyMs.get() : 0L;
            long runtime = System.currentTimeMillis() - startMillis;

            StringBuilder builder = new StringBuilder();
            builder.append("subscriberId=").append(subscriberId).append('\n');
            builder.append("subscriptionsSent=").append(subscriptionsSent).append('\n');
            builder.append("notificationsReceived=").append(count).append('\n');
            builder.append("averageLatencyMs=")
                    .append(String.format(Locale.US, "%.3f", averageLatency)).append('\n');
            builder.append("minLatencyMs=").append(minLatency).append('\n');
            builder.append("maxLatencyMs=").append(maxLatency).append('\n');
            builder.append("runtimeMs=").append(runtime).append('\n');

            String stats = builder.toString();
            System.out.println("[" + subscriberId + "] stats:\n" + stats);

            if (file != null) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, stats);
            }
        }
    }
}
