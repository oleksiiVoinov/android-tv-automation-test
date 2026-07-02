package configs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;

public class Port {
    public String findAvailablePort() {
        int port = 4732;
        while (!isPortReallyAvailable(port)) {
            port++;
        }
        return String.valueOf(port);
    }

    private boolean isPortReallyAvailable(int port) {
        return isPortFreeByLsof(port) && isPortFreeByServerSocket(port);
    }

    private boolean isPortFreeByLsof(int port) {
        try {
            Process process = new ProcessBuilder("lsof", "-i", ":" + port).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine() == null;
            }
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isPortFreeByServerSocket(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
