package project.broker;

import homework.Publication;
import homework.Subscription;
import project.matching.MatchingEngine;
import project.proto.Pubsub;
import project.routing.RoutingTable;
import project.transport.BinaryPubServer;
import project.transport.LineServer;
import project.transport.MessageCodec;
import project.transport.OutboundConnections;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Broker {

    private final String brokerId;
    private final int listenPort;
    private final int pubPort;
    private final Map<String, Peer> peers;

    private final OutboundConnections outbound = new OutboundConnections();
    private final RoutingTable routingTable = new RoutingTable();

    private final ConcurrentHashMap<String, LocalSubscription> localSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> seenPublications = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> seenAdvertisements = new ConcurrentHashMap<>();

    private final AtomicLong publicationsReceived = new AtomicLong();
    private final AtomicLong publicationsReceivedBinary = new AtomicLong();
    private final AtomicLong publicationsForwarded = new AtomicLong();
    private final AtomicLong publicationsMatchedLocally = new AtomicLong();
    private final AtomicLong notificationsSent = new AtomicLong();
    private final AtomicLong subscriptionsReceived = new AtomicLong();
    private final AtomicLong advertisementsReceived = new AtomicLong();

    private LineServer server;
    private BinaryPubServer binaryServer;

    public Broker(String brokerId, int listenPort, int pubPort, Map<String, Peer> peers) {
        this.brokerId = brokerId;
        this.listenPort = listenPort;
        this.pubPort = pubPort;
        this.peers = new LinkedHashMap<>(peers);
    }

    public void start() throws IOException {
        server = new LineServer(listenPort, this::handleIncomingLine);
        server.start();
        binaryServer = new BinaryPubServer(pubPort, this::handleBinaryPublication);
        binaryServer.start();
        System.out.println("[" + brokerId + "] listening on port " + listenPort
                + " (text) and " + pubPort + " (protobuf pub), peers=" + peers.keySet());
    }

    public void stop(Path statsFile, Path dumpFile) {
        if (server != null) {
            server.stop();
        }
        if (binaryServer != null) {
            binaryServer.stop();
        }
        outbound.closeAll();
        writeStats(statsFile);
        if (dumpFile != null) {
            dumpStore(dumpFile);
        }
    }

    private void dumpStore(Path dumpFile) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, LocalSubscription> entry : localSubscriptions.entrySet()) {
            LocalSubscription record = entry.getValue();
            builder.append(entry.getKey())
                    .append('|')
                    .append(MessageCodec.encodeConditions(record.subscription))
                    .append('|')
                    .append(record.subscriberHost)
                    .append(':')
                    .append(record.subscriberPort)
                    .append('\n');
        }
        try {
            if (dumpFile.getParent() != null) {
                Files.createDirectories(dumpFile.getParent());
            }
            Files.writeString(dumpFile, builder.toString());
        } catch (IOException ioException) {
            System.err.println("[" + brokerId + "] failed to write store dump: " + ioException);
        }
    }

    private void handleIncomingLine(String line) {
        try {
            String[] parts = line.split("\\|", -1);
            String type = parts[0];
            switch (type) {
                case MessageCodec.TYPE_SUB:
                    handleSubscribe(parts);
                    break;
                case MessageCodec.TYPE_ADV:
                    handleAdvertisement(parts);
                    break;
                case MessageCodec.TYPE_PUB:
                    handlePublication(parts);
                    break;
                default:
                    System.err.println("[" + brokerId + "] unknown message type: " + type);
            }
        } catch (RuntimeException exception) {
            System.err.println("[" + brokerId + "] error processing message: " + exception);
        }
    }

    private void handleSubscribe(String[] parts) {
        String subscriptionId = parts[1];
        String subscriberHost = parts[2];
        int subscriberPort = Integer.parseInt(parts[3]);
        String encodedConditions = parts[4];

        Subscription subscription = new Subscription(MessageCodec.decodeConditions(encodedConditions));
        localSubscriptions.put(subscriptionId,
                new LocalSubscription(subscription, subscriberHost, subscriberPort));
        subscriptionsReceived.incrementAndGet();

        String advertisementKey = subscriptionId + "@" + brokerId;
        seenAdvertisements.putIfAbsent(advertisementKey, Boolean.TRUE);

        String advertisement = MessageCodec.buildAdvertisement(
                subscriptionId, brokerId, 1, encodedConditions);
        for (Peer peer : peers.values()) {
            outbound.sendLine(peer.host, peer.port, advertisement);
        }
    }

    private void handleAdvertisement(String[] parts) {
        String subscriptionId = parts[1];
        String origin = parts[2];
        String encodedConditions = parts[4];

        if (origin.equals(brokerId)) {
            return;
        }
        String advertisementKey = subscriptionId + "@" + origin;
        if (seenAdvertisements.putIfAbsent(advertisementKey, Boolean.TRUE) != null) {
            return;
        }

        Subscription subscription = new Subscription(MessageCodec.decodeConditions(encodedConditions));
        routingTable.addAdvertisement(origin, subscriptionId, subscription);
        advertisementsReceived.incrementAndGet();
    }

    private void handlePublication(String[] parts) {
        String publicationId = parts[1];
        long emitTimestamp = Long.parseLong(parts[2]);
        int hopCount = Integer.parseInt(parts[3]);
        String company = parts[4];
        double value = Double.parseDouble(parts[5]);
        double drop = Double.parseDouble(parts[6]);
        double variation = Double.parseDouble(parts[7]);
        String date = parts[8];

        Publication publication = new Publication(company, value, drop, variation, date);
        processPublication(publicationId, emitTimestamp, hopCount, publication);
    }

    private void handleBinaryPublication(Pubsub.Publication message) {
        publicationsReceivedBinary.incrementAndGet();
        Publication publication = new Publication(
                message.getCompany(),
                message.getValue(),
                message.getDrop(),
                message.getVariation(),
                message.getDate());
        processPublication(
                message.getPubId(),
                message.getEmitTimestampMs(),
                message.getHopCount(),
                publication);
    }

    private void processPublication(
            String publicationId,
            long emitTimestamp,
            int hopCount,
            Publication publication) {
        if (seenPublications.putIfAbsent(publicationId, Boolean.TRUE) != null) {
            return;
        }
        publicationsReceived.incrementAndGet();

        boolean matchedLocally = false;
        for (Map.Entry<String, LocalSubscription> entry : localSubscriptions.entrySet()) {
            LocalSubscription record = entry.getValue();
            if (MatchingEngine.matches(publication, record.subscription)) {
                String notification = MessageCodec.buildNotification(
                        publicationId, emitTimestamp, entry.getKey(), publication);
                if (outbound.sendLine(record.subscriberHost, record.subscriberPort, notification)) {
                    notificationsSent.incrementAndGet();
                }
                matchedLocally = true;
            }
        }
        if (matchedLocally) {
            publicationsMatchedLocally.incrementAndGet();
        }

        int hopLimit = Math.max(1, peers.size());
        if (hopCount < hopLimit + 1) {
            List<String> interested = routingTable.neighborsInterestedIn(publication);
            if (!interested.isEmpty()) {
                String forwarded = MessageCodec.buildPublication(
                        publicationId, emitTimestamp, hopCount + 1, publication);
                for (String neighborId : interested) {
                    Peer peer = peers.get(neighborId);
                    if (peer == null) {
                        continue;
                    }
                    if (outbound.sendLine(peer.host, peer.port, forwarded)) {
                        publicationsForwarded.incrementAndGet();
                    }
                }
            }
        }
    }

    private void writeStats(Path statsFile) {
        StringBuilder builder = new StringBuilder();
        builder.append("brokerId=").append(brokerId).append('\n');
        builder.append("subscriptionsReceived=").append(subscriptionsReceived.get()).append('\n');
        builder.append("advertisementsReceived=").append(advertisementsReceived.get()).append('\n');
        builder.append("publicationsReceived=").append(publicationsReceived.get()).append('\n');
        builder.append("publicationsReceivedBinary=").append(publicationsReceivedBinary.get()).append('\n');
        builder.append("publicationsMatchedLocally=").append(publicationsMatchedLocally.get()).append('\n');
        builder.append("publicationsForwarded=").append(publicationsForwarded.get()).append('\n');
        builder.append("notificationsSent=").append(notificationsSent.get()).append('\n');
        builder.append("routedAdvertisementsInTable=")
                .append(routingTable.totalAdvertisedSubscriptions()).append('\n');

        String stats = builder.toString();
        System.out.println("[" + brokerId + "] stats:\n" + stats);

        if (statsFile != null) {
            try {
                if (statsFile.getParent() != null) {
                    Files.createDirectories(statsFile.getParent());
                }
                Files.writeString(statsFile, stats);
            } catch (IOException ioException) {
                System.err.println("[" + brokerId + "] failed to write stats: " + ioException);
            }
        }
    }

    public static final class Peer {

        public final String host;
        public final int port;

        public Peer(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    private static final class LocalSubscription {

        final Subscription subscription;
        final String subscriberHost;
        final int subscriberPort;

        LocalSubscription(Subscription subscription, String subscriberHost, int subscriberPort) {
            this.subscription = subscription;
            this.subscriberHost = subscriberHost;
            this.subscriberPort = subscriberPort;
        }
    }
}
