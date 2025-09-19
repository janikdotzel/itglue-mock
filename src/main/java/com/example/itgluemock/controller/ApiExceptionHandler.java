package com.example.itgluemock.controller;

import com.example.itgluemock.exception.BadRequestException;
import com.example.itgluemock.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ObjectMapper objectMapper;

    public ApiExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ObjectNode> handleNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ObjectNode> handleBadRequest(BadRequestException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> handleUnexpected(Exception ex) {
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred");
    }

    private ResponseEntity<ObjectNode> buildErrorResponse(HttpStatus status, String title, String detail) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("status", Integer.toString(status.value()));
        error.put("title", title);
        error.put("detail", detail);

        ArrayNode errors = objectMapper.createArrayNode();
        errors.add(error);

        ObjectNode document = objectMapper.createObjectNode();
        document.set("errors", errors);

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(document);
    }
}
