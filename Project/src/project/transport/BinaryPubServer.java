package project.transport;

import project.proto.Pubsub;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public final class BinaryPubServer {

    private final int port;
    private final Consumer<Pubsub.Publication> messageHandler;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public BinaryPubServer(int port, Consumer<Pubsub.Publication> messageHandler) {
        this.port = port;
        this.messageHandler = messageHandler;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "BinaryPubServer-accept-" + port);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket),
                        "BinaryPubServer-client-" + socket.getPort());
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException ioException) {
                if (running) {
                    System.err.println("[binserver] accept error on port " + port + ": "
                            + ioException.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (InputStream input = new BufferedInputStream(socket.getInputStream())) {
            while (true) {
                Pubsub.Publication message = Pubsub.Publication.parseDelimitedFrom(input);
                if (message == null) {
                    break;
                }
                messageHandler.accept(message);
            }
        } catch (IOException ignored) {
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
