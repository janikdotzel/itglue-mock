package com.example.itglue.mock.server;

import com.example.itglue.mock.store.MockDataStore;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Basic HTTP handler that serves JSON:API responses backed by {@link MockDataStore}.
 */
public final class JsonApiHandler implements HttpHandler {
    private static final String JSON_API_MEDIA_TYPE = "application/vnd.api+json";
    private final MockDataStore store;

    public JsonApiHandler(MockDataStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().add("Allow", "GET");
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String[] rawSegments = Arrays.stream(exchange.getRequestURI().getPath().split("/"))
                .filter(segment -> !segment.isEmpty())
                .toArray(String[]::new);
        String[] segments = normalizeSegments(rawSegments);
        if (segments.length == 0) {
            sendResponse(exchange, 404, "Not Found");
            return;
        }

        String responseBody = null;
        if (segments.length == 1) {
            responseBody = store.renderCollection(segments[0]).orElse(null);
        } else if (segments.length == 2) {
            responseBody = store.renderResource(segments[0], segments[1]).orElse(null);
        } else if (segments.length == 4 && "relationships".equals(segments[2])) {
            responseBody = store.renderRelationship(segments[0], segments[1], segments[3]).orElse(null);
        }

        if (responseBody == null) {
            sendResponse(exchange, 404, "Not Found");
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", JSON_API_MEDIA_TYPE);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String[] normalizeSegments(String[] segments) {
        if (segments.length >= 1 && "public_api".equals(segments[0])) {
            if (segments.length >= 2 && segments[1].matches("v\\d+")) {
                return Arrays.copyOfRange(segments, 2, segments.length);
            }
            return Arrays.copyOfRange(segments, 1, segments.length);
        }
        return segments;
    }

    private static void sendResponse(HttpExchange exchange, int status, String message) throws IOException {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }
}
