# IT Glue Mock API

A mock implementation of a subset of the [IT Glue API](https://api.itglue.com/developer/) built with Spring Boot. It is designed so you can run local proof-of-concept integrations and later swap to the real IT Glue service without changing request signatures.

## Features

- JSON:API compliant endpoints for frequently used IT Glue resources (`/configurations`, `/organizations`, `/locations`, etc.).
- `/search` endpoint that mirrors IT Glue's search parameters, including pagination, filtering by resource type and organization, and JSON:API pagination metadata.
- Relationship endpoints such as `/configurations/{id}/relationships/organization` and `/configurations/{id}/organization`.
- Data loaded from editable JSON fixtures located in `src/main/resources/data`. Update these files to change the mock responses or add new records.

## Getting started

### Prerequisites

- Java 21+
- Maven 3.9+

### Run the mock API

```bash
mvn spring-boot:run
```

The application starts on port `8080`. Example requests:

- `GET http://localhost:8080/search?query=acme&filter[resource_type]=configurations`
- `GET http://localhost:8080/configurations/100?include=organization,location,primary-contact`
- `GET http://localhost:8080/configurations/100/relationships/passwords`
- `GET http://localhost:8080/passwords/1`

### Modifying mock data

Mock data is defined with JSON:API compliant documents in `src/main/resources/data`. To add another workstation, append a new resource to `configurations.json` and reference any related records using the `relationships` node. Restart the Spring Boot application to reload the data.

### Tests

Run the unit tests with:

```bash
mvn test
```

## Project structure

- `src/main/java/com/example/itgluemock` – Spring Boot application and controllers.
- `src/main/resources/data` – JSON fixtures for each IT Glue resource type.

## Notes

- Authentication and rate limiting are not enforced, allowing fast iterations during local development.
- Error responses follow the JSON:API `errors` format to make the mock behaviour align with the production service.
