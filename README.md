# IT Glue Mock API

This project provides a Spring Boot based mock server that mirrors the structure of the [IT Glue developer API](https://api.itglue.com/developer/). It allows you to simulate real requests without depending on the live service and uses JSON files as the backing store for data, so the mock can be extended easily.

## Features

- JSON:API compliant responses with support for `include`, `filter`, `sort`, and `page` query parameters.
- CRUD operations for every resource type shipped with the mock (`GET`, `POST`, `PATCH`, `DELETE`).
- Relationship and related resource endpoints such as `/relationships/{name}` and `/{name}`.
- Data bootstrapped from JSON fixtures located in `src/main/resources/mock-data`. Updating the fixtures changes the API output without recompiling.
- Designed so that the HTTP contract matches the live IT Glue endpoints, allowing easy substitution.

## Getting started

### Prerequisites

- Java 17+
- Maven 3.9+

### Running the mock server

```bash
mvn spring-boot:run
```

The server starts on port `8080`. For example, to list organizations:

```bash
curl -H 'Accept: application/vnd.api+json' http://localhost:8080/public_api/v1/organizations
```

To retrieve a single organization including related contacts:

```bash
curl -H 'Accept: application/vnd.api+json' 'http://localhost:8080/public_api/v1/organizations/1?include=contacts'
```

### Modifying mock data

All seed data is stored as JSON arrays under `src/main/resources/mock-data`. The file name determines the resource type (e.g. `organizations.json` -> `organizations`). Each JSON file follows the JSON:API format. You can edit or add files to customise responses.

### Extending the API

1. Create a new JSON fixture file under `src/main/resources/mock-data` with a `data` array describing the resources.
2. Restart the application. The new type will be exposed automatically.
3. If the real API introduces additional relationships or attributes, mirror them in the fixture files so clients continue to work unchanged.

### Testing

```bash
mvn test
```

## Notes

- The mock does not implement rate limiting or authentication.
- Generated identifiers for POST requests are numeric and based on existing fixture IDs.
- Relationship includes cascade recursively, enabling tests of complex `include` graphs.
