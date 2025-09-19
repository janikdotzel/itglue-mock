# IT Glue Mock API

This project provides an offline-friendly mock server that mirrors the structure of the [IT Glue developer API](https://api.itglue.com/developer/). It serves JSON:API compliant fixtures directly from disk so you can develop and test integrations without touching the live service.

## Features

- Serves the same resource names and relationship endpoints documented by IT Glue (e.g. `/public_api/v2/organizations`, `/public_api/v2/organizations/1/relationships/contacts`).
- Responds with the JSON payloads defined in `src/main/resources/mock-data`, making it easy to tailor scenarios by editing flat files.
- Ships without external dependencies; the mock runs anywhere a Java 17 runtime is available.
- Supports overriding the fixture directory via the `mock.data.dir` system property or `MOCK_DATA_DIR` environment variable.

## Getting started

### Prerequisites

- Java 17+
- (Optional) Maven 3.9+

### Build the project

If you have internet access, you can compile with Maven:

```bash
mvn -q compile
```

For completely offline environments, compile directly with `javac`:

```bash
mkdir -p target/classes
javac $(find src/main/java -name '*.java') -d target/classes
```

### Run the mock server

```bash
java -cp target/classes com.example.itglue.mock.ItGlueMockApplication
```

The server listens on port `8080` by default. Visit for example:

```bash
curl -H 'Accept: application/vnd.api+json' http://localhost:8080/public_api/v2/organizations
```

To point the server at an alternative fixture directory:

```bash
java -Dmock.data.dir=/path/to/fixtures -cp target/classes com.example.itglue.mock.ItGlueMockApplication
```

### Modifying mock data

1. Edit or add JSON files inside `src/main/resources/mock-data`. Each file should contain a JSON:API document with a top-level `data` member.
2. Append the new file name to `src/main/resources/mock-data/index.txt` so the loader can discover it.
3. Restart the mock server to pick up your changes.

### Supported endpoints

The mock currently implements `GET` handlers for:

- Collection endpoints such as `/public_api/v2/organizations`.
- Individual resources such as `/public_api/v2/organizations/{id}`.
- Relationship links such as `/public_api/v2/organizations/{id}/relationships/{name}`.

Other HTTP methods return `405 Method Not Allowed`. Query parameters are accepted but ignored.

## Notes

- Authentication and rate limiting are intentionally omitted.
- Responses are served verbatim from the fixture files. Ensure they match the live API contract your client expects.
- Because everything runs from the local JVM, no external network access is required to build or execute the mock.
