### Storage Service

According to Task 6, Storage manages storage **types**. The design assumes only pre-created buckets may be used, so creating a custom S3 bucket, if one is missing, is optional.

STAGING and PERMANENT types are the only ones the logic in Resource Service on creating a new resource is built around, which makes additional storage types optional too. For this reason, enum values are exposed in Storage and Resource services, instead of plain String values.

To provide Storage more sense, I made Resource Service to follow the provided StorageDetails.path, so the PERMANENT bucket may store files under different paths.


### Installation

## LocalStack token
LocalStack requires a Personal Auth Token for usage.

1. Create .localstack.env file out of .localstack.env.example in the root of the project.
2. Sign up at https://app.localstack.cloud, choose AWS and non-commercial usage and copy Personal Auth Token at "Getting Started" page, or at Settings > Auth tokens > Personal Auth Token.
3. Insert the actual token in there and save it.

### Local deployment

To deploy resource-service since Module 4, add the following environment variables to your IDE run configuration or to your shell environment:
SPRING_RABBITMQ_HOST=localhost; SPRING_RABBITMQ_PORT=5672; SPRING_RABBITMQ_USERNAME=app; SPRING_RABBITMQ_PASSWORD=app

### Full Docker deployment