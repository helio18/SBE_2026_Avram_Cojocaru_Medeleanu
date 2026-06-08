package project.transport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class LineServer {

    private final int port;
    private final Consumer<String> lineHandler;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public LineServer(int port, Consumer<String> lineHandler) {
        this.port = port;
        this.lineHandler = lineHandler;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "LineServer-accept-" + port);
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
                        "LineServer-client-" + socket.getPort());
                clientThread.setDaemon(true);
                clientThread.start();
            } catch (IOException ioException) {
                if (running) {
                    System.err.println("[lineserver] accept error on port " + port + ": "
                            + ioException.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineHandler.accept(line);
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
