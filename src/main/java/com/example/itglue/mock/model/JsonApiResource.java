package com.example.itglue.mock.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonApiResource {

    private final String id;
    private final String type;
    private final ObjectNode attributes;
    private final ObjectNode relationships;

    public JsonApiResource(String id, String type, ObjectNode attributes, ObjectNode relationships) {
        this.id = id;
        this.type = type;
        this.attributes = attributes;
        this.relationships = relationships;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public ObjectNode getAttributes() {
        return attributes;
    }

    public ObjectNode getRelationships() {
        return relationships;
    }

    public JsonNode getRelationship(String name) {
        if (relationships == null) {
            return null;
        }
        return relationships.get(name);
    }

    public JsonNode getAttribute(String name) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    public ObjectNode toResponse(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        if (attributes != null) {
            node.set("attributes", attributes.deepCopy());
        } else {
            node.putObject("attributes");
        }
        if (relationships != null) {
            node.set("relationships", relationships.deepCopy());
        }
        return node;
    }

    public JsonApiResource withAttributes(ObjectNode newAttributes) {
        return new JsonApiResource(id, type, newAttributes, relationships);
    }

    public JsonApiResource withRelationships(ObjectNode newRelationships) {
        return new JsonApiResource(id, type, attributes, newRelationships);
    }
}
