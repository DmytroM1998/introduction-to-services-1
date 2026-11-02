# Testing Strategy

## Purpose and scope

This strategy uses a small, risk-based set of tests to catch regressions in the service logic and the important boundaries between services. It is intended to provide useful confidence without aiming for exhaustive coverage: the project is expected to change, so the tests should focus on core behavior and high-risk interactions rather than a fixed coverage percentage.

The test suite should follow a practical testing pyramid: many focused unit tests, fewer component and integration tests, a small number of contract checks for service-to-service interfaces, and one or two end-to-end smoke tests. **100% unit, integration, or line coverage is not a goal.** Add tests for meaningful behavior and likely failure points; avoid duplicating the same scenarios at every level.

## Unit tests

Unit tests exercise a class or small piece of logic in isolation, using mocks for its collaborators. Prioritize:

- `SongsService`, `ResourcesService`, `S3BucketService`, and `RabbitMQService`.
- `ResourceMetadataService`, `CommunicationService`, and `SongDtoMapper`.
- Validation, parsing, mapping, and success/error branches where behavior is owned by the class under test.

Cover representative normal cases and important edge cases, such as invalid or missing IDs, malformed input, dependency failures, and partial delete results. Keep tests fast and deterministic; do not start Spring or external infrastructure for these tests.

## Component tests

Component tests exercise one service through its public boundary while replacing dependencies outside that service with mocks or stubs. They verify that the service's own controllers, request handling, validation, exception handling, and wiring behave together correctly.

- For `resource-service` and `song-service`, use Spring MVC tests (for example, MockMvc) to check representative endpoint success and error responses. Mock service-layer or external collaborators as appropriate; do not mock the controller when testing the HTTP boundary.
- `resource-service`: cover upload, fetch, and delete behavior, including representative invalid-input/not-found and dependency-error responses.
- `song-service`: cover create, fetch, and delete behavior, including validation and not-found/error responses.
- `resource-processor` has no public REST endpoint for its main workflow. Test its message-consumer component with a controlled message and stubbed HTTP collaborators, verifying that it fetches the resource, processes the bytes, and submits song metadata. Keep metadata parsing itself covered by unit tests.

These tests should cover a few representative paths rather than replay the entire Postman collection. In particular, include error cases where the API contract promises a specific status or response body.

## Integration tests

Integration tests verify that a service's code works with the real technologies it directly depends on, rather than testing all microservices together. Use disposable local infrastructure (for example, Testcontainers or the project's Docker Compose services) for a small number of high-value checks:

- Each service's persistence adapter with its PostgreSQL database.
- `resource-service` storage and messaging adapters with LocalStack S3 and RabbitMQ.
- `resource-processor` message consumption and its HTTP calls to the resource and song services, using real local services or controlled HTTP stubs where appropriate.

Focus on the important adapter operations and the upload-to-processing handoff. Keep these tests isolated, repeatable, and responsible for cleaning up their data. They complement component tests: the component tests mock external dependencies; integration tests verify selected real dependency connections and configuration.

## Contract tests

Contract tests check that a consumer and provider agree on the shape and meaning of an interface. They are most useful at boundaries that can change independently:

- `resource-processor` consuming the resource-upload message published by `resource-service` (including the message payload and relevant routing/configuration).
- `resource-processor` calling `GET /resources/{id}` and `POST /songs`, including request/response fields, content types, and expected status codes.
- The externally used `resource-service` and `song-service` REST endpoints, if their request or response formats change.

Validate only the interfaces used by these flows, especially the newer asynchronous processing flow, rather than writing a contract test for every database operation or for LocalStack. Database and S3 behavior belongs in integration tests. Contract tests can use lightweight fixtures or provider verification and do not need a full production-like deployment.

## End-to-end tests

End-to-end tests start the services and required infrastructure together and verify a complete user-visible flow through the public API. Keep this suite very small because it is slower and more sensitive to environment setup.

The primary smoke test should upload a valid MP3 through `POST /resources`, wait for the asynchronous processor to finish, and confirm the resulting song metadata through `GET /songs/{id}` (and, if useful, confirm the uploaded bytes through `GET /resources/{id}`). Clean up created resources and songs afterward. A failure should identify the overall flow that broke; detailed error cases should be covered at lower levels.

Do not run the entire Postman collection as the end-to-end suite. Use it as a source of representative scenarios, while keeping only the critical happy path (and optionally one essential failure path) in the full-stack tests.

## How the levels work together

Unit tests provide fast feedback on business logic; component tests check each service's API and local request handling; integration tests exercise selected real database, storage, and broker connections; contract tests catch incompatible changes between independently developed services; and end-to-end smoke tests confirm that the critical flow works when deployed together. This gives balanced confidence without requiring 100% coverage or repeating every scenario at every level.