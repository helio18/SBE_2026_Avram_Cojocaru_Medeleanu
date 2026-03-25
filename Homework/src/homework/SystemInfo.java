package homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SystemInfo {

    private final String cpuModel;
    private final int logicalCores;
    private final String osName;
    private final String javaVersion;

    public SystemInfo(String cpuModel, int logicalCores, String osName, String javaVersion) {
        this.cpuModel = cpuModel;
        this.logicalCores = logicalCores;
        this.osName = osName;
        this.javaVersion = javaVersion;
    }

    public static SystemInfo read() {
        String cpuModel = "Unknown CPU";
        Path cpuInfoFile = Path.of("/proc/cpuinfo");

        if (Files.exists(cpuInfoFile)) {
            try {
                List<String> lines = Files.readAllLines(cpuInfoFile);
                for (String line : lines) {
                    if (line.startsWith("model name")) {
                        int separatorIndex = line.indexOf(':');
                        if (separatorIndex >= 0) {
                            cpuModel = line.substring(separatorIndex + 1).trim();
                            break;
                        }
                    }
                }
            } catch (IOException exception) {
                cpuModel = "Unknown CPU";
            }
        }

        return new SystemInfo(
                cpuModel,
                Runtime.getRuntime().availableProcessors(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("java.version"));
    }

    public String getCpuModel() {
        return cpuModel;
    }

    public int getLogicalCores() {
        return logicalCores;
    }

    public String getOsName() {
        return osName;
    }

    public String getJavaVersion() {
        return javaVersion;
    }
}
