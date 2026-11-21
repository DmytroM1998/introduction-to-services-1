### Storage Service

According to Task 6, Storage manages storage **types**. The design assumes only pre-created buckets may be used, so creating a custom S3 bucket, if one is missing, is optional.

STAGING and PERMANENT types are the only ones the logic in Resource Service on creating a new resource is built around, which makes additional storage types optional too. For this reason, enum values are exposed in Storage and Resource services, instead of plain String values.

To provide Storage more sense, I made Resource Service to follow the provided StorageDetails.path, so the PERMANENT bucket may store files under different paths.

### Monitoring

The following technologies were used for monitoring:
- Elasticsearch, 
- Micrometer, 
- Spring Actuator, 
- Grafana

JVM metrics for Grafana were not filled  manually. Instead, a dashboard was created in Grafana itself. 

1. Use any of the provided Docker command from compose.yaml and deploy Grafana.
2. On the title screen, click "+ Create dashboard" button to get into http://localhost:3000/dashboard/new .
Alternatively, load http://localhost:3000, select Dashboards from the left side panel. In /dashboard page, click "New > New dashboard".
3. Use these queries to see field mappings and documents by a property name. Use these data to configure Queries by criteria.
```bash
curl -s 'http://localhost:9200/metrics-microservices-*/_field_caps?fields=name*,area*,application*,value,@timestamp&pretty'

curl -s -H 'Content-Type: application/json' \
  'http://localhost:9200/metrics-microservices-*/_search?pretty' \
  -d '{"size":10,"query":{"match":{"name":"jvm_memory_used"}}}'
```
4. Once you created a panel, click "{ } Edit as code" at the right side-panel where you can copy or paste a ready dashboard code. I was making each panel manually and copied the code from there into `docker/grafana/provisioning/dashboards/jvm-metrics.json` and configured it to be consumed by Grafana at deployment time.
5. Examine this file to see the panels' data. Here is an example of a Lucene query: `name:"jvm_memory_used" AND area:"heap"`.
6. Do similar actions for `docker/grafana/provisioning/dashboards/api-gateway-performance.json`.


### Installation

#### Places of interest

Eureka status: http://localhost:8761/
Grafana: http://localhost:3000/login

## LocalStack token
LocalStack requires a Personal Auth Token for usage.

1. Create .localstack.env file out of .localstack.env.example in the root of the project.
2. Sign up at https://app.localstack.cloud, choose AWS and non-commercial usage and copy Personal Auth Token at "Getting Started" page, or at Settings > Auth tokens > Personal Auth Token.
3. Insert the actual token in there and save it.

### Local deployment

To deploy resource-service since Module 4, add the following environment variables to your IDE run configuration or to your shell environment:
SPRING_RABBITMQ_HOST=localhost; SPRING_RABBITMQ_PORT=5672; SPRING_RABBITMQ_USERNAME=app; SPRING_RABBITMQ_PASSWORD=app

### Full Docker deployment