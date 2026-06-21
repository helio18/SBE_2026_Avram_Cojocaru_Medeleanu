package project.transport;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public final class OutboundConnections {

    private final ConcurrentHashMap<String, Sender> senders = new ConcurrentHashMap<>();

    public boolean sendLine(String host, int port, String line) {
        Sender sender = senders.computeIfAbsent(host + ":" + port, key -> new Sender(host, port));
        return sender.send(line);
    }

    public void closeAll() {
        for (Sender sender : senders.values()) {
            sender.close();
        }
    }

    private static final class Sender {

        private final String host;
        private final int port;
        private Socket socket;
        private BufferedWriter writer;
        private final Object lock = new Object();

        Sender(String host, int port) {
            this.host = host;
            this.port = port;
        }

        boolean send(String line) {
            synchronized (lock) {
                if (writer == null) {
                    if (!tryOpen()) {
                        return false;
                    }
                }
                try {
                    writer.write(line);
                    writer.write('\n');
                    writer.flush();
                    return true;
                } catch (IOException firstFailure) {
                    closeQuietly();
                    if (!tryOpen()) {
                        return false;
                    }
                    try {
                        writer.write(line);
                        writer.write('\n');
                        writer.flush();
                        return true;
                    } catch (IOException secondFailure) {
                        System.err.println("[outbound] failed to send to " + host + ":" + port
                                + ": " + secondFailure.getMessage());
                        closeQuietly();
                        return false;
                    }
                }
            }
        }

        private boolean tryOpen() {
            try {
                socket = new Socket(host, port);
                writer = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                return true;
            } catch (IOException openFailure) {
                System.err.println("[outbound] failed to open " + host + ":" + port
                        + ": " + openFailure.getMessage());
                closeQuietly();
                return false;
            }
        }

        void close() {
            synchronized (lock) {
                closeQuietly();
            }
        }

        private void closeQuietly() {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
                writer = null;
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
            }
        }
    }
}
