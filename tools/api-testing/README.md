# BlueCollar local API testing

This folder adds a local API-testing workflow without changing the Spring Boot runtime or Maven dependencies.

- **Schemathesis**: open-source, OpenAPI-driven test generation and property-based API checks.
- **Keploy**: AI-assisted test-suite generation from OpenAPI / cURL / Postman inputs. The AI test generator is provided through Keploy Console; the CLI and local-agent workflow have separate capabilities and account requirements.

## 0. Safety first

Schemathesis sends generated requests to your API. It can call write operations (POST, PUT, PATCH, DELETE), so do not point it at production, Render, or a database containing data you care about.

Create and use a dedicated local PostgreSQL database for API testing, for example `bluecollar_api_test`. Start the backend with that database configured through the existing `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` environment variables. Do not reuse your development database if it contains important data.

The runner refuses non-loopback API URLs and requires an explicit database-safety confirmation.

## 1. Start BlueCollar locally

From the repository root, configure the environment to use your disposable test database and start the application:

```bash
export DB_URL='jdbc:postgresql://127.0.0.1:5432/bluecollar_api_test'
export DB_USERNAME='YOUR_LOCAL_DB_USER'
export DB_PASSWORD='YOUR_LOCAL_DB_PASSWORD'
./mvnw spring-boot:run
```

Wait for startup and verify the OpenAPI document is reachable:

```bash
curl --fail http://127.0.0.1:8080/v3/api-docs -o /tmp/bluecollar-openapi.json
```

If this returns 401/403, check the Spring Security rules for the SpringDoc endpoints before testing. Do not disable authentication globally just to make the test tool work.

## 2. Install and run Schemathesis

On macOS, install `uv` (which provides `uvx`) if needed:

```bash
brew install uv
```

Run the guarded local suite from the repository root:

```bash
BLUECOLLAR_TEST_DB_CONFIRMED=YES ./tools/api-testing/run-schemathesis.sh
```

For authenticated operations, first obtain a test-user JWT using the existing BlueCollar auth API, then pass it without committing it:

```bash
export API_TOKEN='YOUR_LOCAL_TEST_JWT'
BLUECOLLAR_TEST_DB_CONFIRMED=YES ./tools/api-testing/run-schemathesis.sh
```

The initial run is intentionally constrained: one worker, at most 10 generated examples per operation, a 90-second time budget, and the examples/fuzzing phases only. Stateful multi-request workflows are excluded from this first pass; enable them only after the test database and cleanup behavior are understood.

Schemathesis reports failures with reproducible requests. Treat 401/403 responses on protected operations as expected when no token is supplied; investigate 5xx responses and undocumented status/schema violations. Some failures may indicate incomplete OpenAPI response documentation rather than a runtime defect.

## 3. Generate AI-assisted tests with Keploy

1. Open the [Keploy Console](https://app.keploy.io/) and choose its API Test Generator.
2. Use the OpenAPI document exported in step 1 (`/tmp/bluecollar-openapi.json`) as the input schema. Review it before uploading; OpenAPI documents can reveal endpoint names, request shapes, and examples.
3. Configure the generator to target your local BlueCollar API using Keploy's Local Agent workflow for private/local APIs. Keep the target at `http://127.0.0.1:8080`.
4. Review generated requests and assertions before running them. Use test-only accounts and test-only records.
5. Export or save the generated suite in Keploy's supported format and keep generated secrets/tokens out of Git.

Keploy documents generation from OpenAPI, Postman, and cURL, as well as a Local Agent for APIs behind a firewall. The AI generator is a Console feature; do not assume every AI capability is part of the fully self-hosted open-source CLI.

### macOS + PostgreSQL limitation

Keploy's native Apple Silicon mode can intercept HTTP/HTTPS, but its installation documentation notes that PostgreSQL traffic is not captured for reliable replay in native macOS mode. Therefore, for this BlueCollar setup, use Keploy primarily for AI-generated API test cases against the local service and use Schemathesis for repeatable OpenAPI fuzzing. Do not rely on native Keploy record/replay to reproduce database-backed workflows unless you have configured a supported container-based capture environment.

## 4. Suggested local workflow

1. Start PostgreSQL using the dedicated test database.
2. Start BlueCollar on port 8080.
3. Run Schemathesis and review reproducible failures.
4. Generate/review Keploy tests from the OpenAPI document.
5. Fix defects and rerun both tools.
6. Only after local results are stable, decide which deterministic tests belong in the existing Maven/GitHub Actions pipeline.

## Current scope

This integration does not add a Maven dependency, alter application security, change database migrations, or modify deployment configuration. It is an external local test harness around the existing SpringDoc endpoint.
