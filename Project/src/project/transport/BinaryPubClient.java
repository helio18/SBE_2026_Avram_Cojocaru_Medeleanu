package project.transport;

import project.proto.Pubsub;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public final class BinaryPubClient {

    private static final int DEFAULT_ACK_TIMEOUT_MS = 30_000;
    private static final int ACK = 0x06;

    private final ConcurrentHashMap<String, Sender> senders = new ConcurrentHashMap<>();
    private final int ackTimeoutMs;

    public BinaryPubClient() {
        this(DEFAULT_ACK_TIMEOUT_MS);
    }

    public BinaryPubClient(int ackTimeoutMs) {
        this.ackTimeoutMs = ackTimeoutMs;
    }

    public boolean send(String host, int port, Pubsub.Publication message) {
        Sender sender = senders.computeIfAbsent(host + ":" + port,
                key -> new Sender(host, port, ackTimeoutMs));
        return sender.send(message);
    }

    public void closeAll() {
        for (Sender sender : senders.values()) {
            sender.close();
        }
    }

    private static final class Sender {

        private final String host;
        private final int port;
        private final int ackTimeoutMs;
        private Socket socket;
        private OutputStream output;
        private InputStream input;
        private final Object lock = new Object();

        Sender(String host, int port, int ackTimeoutMs) {
            this.host = host;
            this.port = port;
            this.ackTimeoutMs = ackTimeoutMs;
        }

        boolean send(Pubsub.Publication message) {
            synchronized (lock) {
                if (output == null) {
                    if (!tryOpen()) {
                        return false;
                    }
                }
                try {
                    return sendAndWaitForAck(message);
                } catch (IOException firstFailure) {
                    closeQuietly();
                    if (!tryOpen()) {
                        return false;
                    }
                    try {
                        return sendAndWaitForAck(message);
                    } catch (IOException secondFailure) {
                        System.err.println("[binout] failed to send to " + host + ":" + port
                                + ": " + secondFailure.getMessage());
                        closeQuietly();
                        return false;
                    }
                }
            }
        }

        private boolean sendAndWaitForAck(Pubsub.Publication message) throws IOException {
            message.writeDelimitedTo(output);
            output.flush();

            int response = input.read();
            if (response == ACK) {
                return true;
            }
            closeQuietly();
            return false;
        }

        private boolean tryOpen() {
            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(ackTimeoutMs);
                output = new BufferedOutputStream(socket.getOutputStream());
                input = new BufferedInputStream(socket.getInputStream());
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
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
                input = null;
            }
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
