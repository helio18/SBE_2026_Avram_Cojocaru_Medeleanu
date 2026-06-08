package project.transport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Args {

    private Args() {
    }

    public static Map<String, String> parse(String[] arguments) {
        Map<String, String> parsed = new HashMap<>();
        for (String argument : arguments) {
            if (!argument.startsWith("--")) {
                continue;
            }
            int equalsIndex = argument.indexOf('=');
            if (equalsIndex < 0) {
                parsed.put(argument.substring(2), "true");
            } else {
                parsed.put(argument.substring(2, equalsIndex), argument.substring(equalsIndex + 1));
            }
        }
        return parsed;
    }

    public static void waitForStopFile(Path stopFile, long pollIntervalMillis) {
        while (true) {
            if (Files.exists(stopFile)) {
                return;
            }
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
