package project.publisher;

import com.google.protobuf.CodedOutputStream;
import homework.DatasetGenerator;
import homework.GeneratorConfig;
import homework.Publication;
import project.proto.Pubsub;
import project.transport.Args;
import project.transport.BinaryPubClient;
import project.transport.MessageCodec;
import project.transport.OutboundConnections;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PublisherMain {

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = Args.parse(arguments);
        String publisherId = options.getOrDefault("id", "P1");
        String brokerListSpec = options.get("brokers");
        if (brokerListSpec == null) {
            throw new IllegalArgumentException("--brokers is required");
        }
        int publicationCount = Integer.parseInt(options.getOrDefault("publications", "1000"));
        int rate = Integer.parseInt(options.getOrDefault("rate", "50"));
        int durationSeconds = Integer.parseInt(options.getOrDefault("duration-seconds", "180"));
        long seed = Long.parseLong(options.getOrDefault("seed", "20260608"));
        int threads = Integer.parseInt(options.getOrDefault("threads", "1"));
        String transport = options.getOrDefault("transport", "protobuf");
        boolean useProtobuf = !transport.equalsIgnoreCase("text");
        String pubIdPrefix = options.getOrDefault("pub-id-prefix", publisherId);
        Path stopFile = options.containsKey("stop-file") ? Path.of(options.get("stop-file")) : null;
        Path statsFile = options.containsKey("stats-file") ? Path.of(options.get("stats-file")) : null;

        List<Endpoint> brokers = parseBrokers(brokerListSpec);

        GeneratorConfig config = GeneratorConfig.defaultConfig()
                .withPublicationCount(publicationCount)
                .withBaseSeed(seed);
        DatasetGenerator generator = new DatasetGenerator(config);
        Publication[] publications = generator.generatePublications(threads);

        BinaryPubClient binaryOutbound = useProtobuf ? new BinaryPubClient() : null;
        OutboundConnections textOutbound = useProtobuf ? null : new OutboundConnections();

        System.out.println("[" + publisherId + "] generated " + publications.length
                + " publications, emitting at " + rate + "/s for up to " + durationSeconds + "s"
                + " (" + (useProtobuf ? "protobuf binary" : "text") + " serialization)");

        long startMillis = System.currentTimeMillis();
        long endMillis = startMillis + durationSeconds * 1000L;
        long publicationsSent = 0L;
        long bytesSent = 0L;

        while (System.currentTimeMillis() < endMillis) {
            if (stopFile != null && Files.exists(stopFile)) {
                break;
            }

            Publication publication = publications[(int) (publicationsSent % publications.length)];
            long emitTimestamp = System.currentTimeMillis();
            String publicationId = pubIdPrefix + "-" + (publicationsSent + 1L);
            Endpoint broker = brokers.get((int) (publicationsSent % brokers.size()));

            if (useProtobuf) {
                Pubsub.Publication message = Pubsub.Publication.newBuilder()
                        .setPubId(publicationId)
                        .setEmitTimestampMs(emitTimestamp)
                        .setHopCount(1)
                        .setCompany(publication.getCompany())
                        .setValue(publication.getValue())
                        .setDrop(publication.getDrop())
                        .setVariation(publication.getVariation())
                        .setDate(publication.getDate())
                        .build();
                int serializedSize = message.getSerializedSize();
                bytesSent += serializedSize + CodedOutputStream.computeUInt32SizeNoTag(serializedSize);
                binaryOutbound.send(broker.host, broker.port, message);
            } else {
                String line = MessageCodec.buildPublication(publicationId, emitTimestamp, 1, publication);
                bytesSent += line.getBytes(StandardCharsets.UTF_8).length + 1;
                textOutbound.sendLine(broker.host, broker.port, line);
            }
            publicationsSent++;

            long expectedElapsed = (publicationsSent * 1000L) / Math.max(1, rate);
            long actualElapsed = System.currentTimeMillis() - startMillis;
            long sleepMillis = expectedElapsed - actualElapsed;
            if (sleepMillis > 0L) {
                Thread.sleep(sleepMillis);
            }
        }

        long runtimeMillis = System.currentTimeMillis() - startMillis;
        if (useProtobuf) {
            binaryOutbound.closeAll();
        } else {
            textOutbound.closeAll();
        }

        StringBuilder statsBuilder = new StringBuilder();
        statsBuilder.append("publisherId=").append(publisherId).append('\n');
        statsBuilder.append("transport=").append(useProtobuf ? "protobuf" : "text").append('\n');
        statsBuilder.append("publicationsSent=").append(publicationsSent).append('\n');
        statsBuilder.append("bytesSent=").append(bytesSent).append('\n');
        double avgBytes = publicationsSent > 0 ? (bytesSent * 1.0 / publicationsSent) : 0.0;
        statsBuilder.append("avgBytesPerPublication=")
                .append(String.format(Locale.US, "%.2f", avgBytes)).append('\n');
        statsBuilder.append("runtimeMs=").append(runtimeMillis).append('\n');
        double effectiveRate = runtimeMillis > 0 ? (publicationsSent * 1000.0 / runtimeMillis) : 0.0;
        statsBuilder.append("effectiveRatePerSec=")
                .append(String.format(Locale.US, "%.2f", effectiveRate)).append('\n');

        String stats = statsBuilder.toString();
        System.out.println("[" + publisherId + "] stats:\n" + stats);

        if (statsFile != null) {
            if (statsFile.getParent() != null) {
                Files.createDirectories(statsFile.getParent());
            }
            Files.writeString(statsFile, stats);
        }
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
}
