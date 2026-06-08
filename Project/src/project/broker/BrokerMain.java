package project.broker;

import project.transport.Args;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrokerMain {

    public static void main(String[] arguments) throws Exception {
        Map<String, String> options = Args.parse(arguments);
        String brokerId = options.getOrDefault("id", "B1");
        int port = Integer.parseInt(options.getOrDefault("port", "5001"));
        String peersSpec = options.getOrDefault("peers", "");
        String stopFilePath = options.get("stop-file");
        String statsFilePath = options.get("stats-file");

        Map<String, Broker.Peer> peers = new LinkedHashMap<>();
        if (!peersSpec.isEmpty()) {
            for (String specification : peersSpec.split(",")) {
                int atIndex = specification.indexOf('@');
                int colonIndex = specification.lastIndexOf(':');
                String peerId = specification.substring(0, atIndex);
                String host = specification.substring(atIndex + 1, colonIndex);
                int peerPort = Integer.parseInt(specification.substring(colonIndex + 1));
                peers.put(peerId, new Broker.Peer(host, peerPort));
            }
        }

        Broker broker = new Broker(brokerId, port, peers);
        broker.start();

        if (stopFilePath != null) {
            Args.waitForStopFile(Path.of(stopFilePath), 250L);
        } else {
            while (true) {
                Thread.sleep(60_000L);
            }
        }

        broker.stop(statsFilePath == null ? null : Path.of(statsFilePath));
    }
}
