package project.transport;

import project.proto.Pubsub;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

// Client TCP pentru publicatii serializate cu Protocol Buffers.
// Foloseste o conexiune persistenta per (host, port), cu o singura
// reincercare daca scrierea esueaza (acelasi pattern ca OutboundConnections).
public final class BinaryPubClient {

    private final ConcurrentHashMap<String, Sender> senders = new ConcurrentHashMap<>();

    public void send(String host, int port, Pubsub.Publication message) {
        Sender sender = senders.computeIfAbsent(host + ":" + port, key -> new Sender(host, port));
        sender.send(message);
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
        private OutputStream output;
        private final Object lock = new Object();

        Sender(String host, int port) {
            this.host = host;
            this.port = port;
        }

        void send(Pubsub.Publication message) {
            synchronized (lock) {
                if (output == null) {
                    if (!tryOpen()) {
                        return;
                    }
                }
                try {
                    message.writeDelimitedTo(output);
                    output.flush();
                } catch (IOException firstFailure) {
                    closeQuietly();
                    if (!tryOpen()) {
                        return;
                    }
                    try {
                        message.writeDelimitedTo(output);
                        output.flush();
                    } catch (IOException secondFailure) {
                        System.err.println("[binout] failed to send to " + host + ":" + port
                                + ": " + secondFailure.getMessage());
                        closeQuietly();
                    }
                }
            }
        }

        private boolean tryOpen() {
            try {
                socket = new Socket(host, port);
                output = new BufferedOutputStream(socket.getOutputStream());
                return true;
            } catch (IOException openFailure) {
                System.err.println("[binout] failed to open " + host + ":" + port
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
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
                output = null;
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
