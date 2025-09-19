package com.example.itglue.mock.store;

import com.example.itglue.mock.json.JsonParser;
import com.example.itglue.mock.json.JsonWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Loads JSON:API fixture data and exposes helpers to render collection, resource, and relationship documents.
 */
public final class MockDataStore {
    private final Map<String, CollectionData> collections;

    private MockDataStore(Map<String, CollectionData> collections) {
        this.collections = new ConcurrentHashMap<>(collections);
    }

    public static MockDataStore fromClasspath() throws IOException {
        ClassLoader classLoader = MockDataStore.class.getClassLoader();
        try (InputStream indexStream = classLoader.getResourceAsStream("mock-data/index.txt")) {
            if (indexStream == null) {
                throw new IOException("Missing mock-data/index.txt on classpath");
            }
            List<String> fileNames = readIndex(indexStream);
            Map<String, CollectionData> loaded = new LinkedHashMap<>();
            for (String fileName : fileNames) {
                String resourcePath = "mock-data/" + fileName;
                try (InputStream dataStream = classLoader.getResourceAsStream(resourcePath)) {
                    if (dataStream == null) {
                        throw new IOException("Missing mock data resource: " + resourcePath);
                    }
                    String json = new String(dataStream.readAllBytes(), StandardCharsets.UTF_8);
                    CollectionData collection = CollectionData.fromJson(extractCollectionName(fileName), json);
                    loaded.put(collection.name(), collection);
                }
            }
            return new MockDataStore(loaded);
        }
    }

    public static MockDataStore fromDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Mock data directory does not exist: " + directory);
        }
        Path indexFile = directory.resolve("index.txt");
        if (!Files.exists(indexFile)) {
            throw new IOException("Mock data directory missing index.txt: " + directory);
        }
        List<String> fileNames;
        try (InputStream indexStream = Files.newInputStream(indexFile)) {
            fileNames = readIndex(indexStream);
        }
        Map<String, CollectionData> loaded = new LinkedHashMap<>();
        for (String fileName : fileNames) {
            Path file = directory.resolve(fileName);
            if (!Files.exists(file)) {
                throw new IOException("Missing mock data file: " + file);
            }
            String json = Files.readString(file);
            CollectionData collection = CollectionData.fromJson(extractCollectionName(fileName), json);
            loaded.put(collection.name(), collection);
        }
        return new MockDataStore(loaded);
    }

    public Optional<String> renderCollection(String collection) {
        CollectionData data = collections.get(collection);
        return Optional.ofNullable(data).map(CollectionData::document);
    }

    public Optional<String> renderResource(String collection, String id) {
        CollectionData data = collections.get(collection);
        if (data == null) {
            return Optional.empty();
        }
        return data.renderResource(id);
    }

    public Optional<String> renderRelationship(String collection, String id, String relationship) {
        CollectionData data = collections.get(collection);
        if (data == null) {
            return Optional.empty();
        }
        return data.renderRelationship(id, relationship);
    }

    private static List<String> readIndex(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());
        }
    }

    private static String extractCollectionName(String fileName) {
        int dot = fileName.indexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private record CollectionData(String name, String document, Map<String, Object> template, Map<String, Map<String, Object>> resources) {
        static CollectionData fromJson(String name, String json) {
            Object parsed = JsonParser.parse(json);
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Root of JSON document must be an object for collection " + name);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> document = (Map<String, Object>) map;
            Object dataNode = document.get("data");
            Map<String, Object> template = document.entrySet().stream()
                    .filter(entry -> !Objects.equals(entry.getKey(), "data"))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
            Map<String, Map<String, Object>> resources = new LinkedHashMap<>();
            if (dataNode instanceof List<?> list) {
                for (Object element : list) {
                    Map<String, Object> resource = asResource(element, name);
                    Object id = resource.get("id");
                    if (!(id instanceof String idString)) {
                        throw new IllegalArgumentException("Resource in collection " + name + " is missing string id");
                    }
                    resources.put(idString, resource);
                }
            } else if (dataNode instanceof Map<?, ?> single) {
                Map<String, Object> resource = asResource(single, name);
                Object id = resource.get("id");
                if (!(id instanceof String idString)) {
                    throw new IllegalArgumentException("Resource in collection " + name + " is missing string id");
                }
                resources.put(idString, resource);
            } else if (dataNode != null) {
                throw new IllegalArgumentException("Unsupported data node for collection " + name);
            }
            return new CollectionData(name, json, Collections.unmodifiableMap(template), Collections.unmodifiableMap(resources));
        }

        Optional<String> renderResource(String id) {
            Map<String, Object> resource = resources.get(id);
            if (resource == null) {
                return Optional.empty();
            }
            Map<String, Object> response = new LinkedHashMap<>(template);
            response.put("data", resource);
            return Optional.of(JsonWriter.toJson(response));
        }

        Optional<String> renderRelationship(String id, String relationshipName) {
            Map<String, Object> resource = resources.get(id);
            if (resource == null) {
                return Optional.empty();
            }
            Object relationshipsNode = resource.get("relationships");
            if (!(relationshipsNode instanceof Map<?, ?> relationships)) {
                return Optional.empty();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> relationshipsMap = (Map<String, Object>) relationships;
            Object relationshipNode = relationshipsMap.get(relationshipName);
            if (relationshipNode == null) {
                return Optional.empty();
            }
            Object data;
            if (relationshipNode instanceof Map<?, ?> relationshipMap && relationshipMap.containsKey("data")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castRelationship = (Map<String, Object>) relationshipMap;
                data = castRelationship.get("data");
            } else {
                data = relationshipNode;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", data);
            return Optional.of(JsonWriter.toJson(response));
        }

        private static Map<String, Object> asResource(Object value, String collection) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Expected resource object in collection " + collection);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) map;
            return resource;
        }
    }
}
