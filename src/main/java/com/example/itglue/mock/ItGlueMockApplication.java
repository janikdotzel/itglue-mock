package com.example.itglue.mock;

import com.example.itglue.mock.server.JsonApiHandler;
import com.example.itglue.mock.store.MockDataStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point that boots a lightweight HTTP server exposing the mocked IT Glue API.
 */
public final class ItGlueMockApplication {
    private ItGlueMockApplication() {
    }

    public static void main(String[] args) throws IOException {
        MockDataStore store = createDataStore();
        int port = resolvePort();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new JsonApiHandler(store));
        ExecutorService executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();

        System.out.printf("Mock IT Glue API available at http://localhost:%d/%n", port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
        }));
    }

    private static int resolvePort() {
        return Optional.ofNullable(System.getProperty("server.port"))
                .or(() -> Optional.ofNullable(System.getenv("PORT")))
                .map(Integer::parseInt)
                .orElse(8080);
    }

    private static MockDataStore createDataStore() throws IOException {
        String override = Optional.ofNullable(System.getProperty("mock.data.dir"))
                .orElseGet(() -> System.getenv("MOCK_DATA_DIR"));
        if (override != null && !override.isBlank()) {
            return MockDataStore.fromDirectory(Path.of(override));
        }
        return MockDataStore.fromClasspath();
    }
}
