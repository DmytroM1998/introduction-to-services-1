## Unit tests

Most of the logic is concentrated in the following classes:
- song-service/src/main/java/com/epam/song/service/SongsService.java
- resource-service/src/main/java/com/epam/resource/service/RabbitMQService.java
- resource-service/src/main/java/com/epam/resource/service/ResourcesService.java
- resource-service/src/main/java/com/epam/resource/service/S3BucketService.java
- resource-processor/src/main/java/com/epam/resource_processor/service/ResourceMetadataService.java
- resource-processor/src/main/java/com/epam/resource_processor/service/CommunicationService.java
- resource-processor/src/main/java/com/epam/resource_processor/mapper/SongDtoMapper.java

#### Approach
Test service methods for possible outcomes in resource-service, resource-processor and song-service with neighboring consumers mocked.

## Integration tests

#### Endpoints
**resource-service**
/resources
- POST: saves data in LocalStack, sends resource ID to RabbitMQ, saves LocalStack object location and resource ID in resource-db database, returns id.
- GET /{id}: gets data from LocalStack, gets entity from resource-db database, returns binary data.
- DELETE /{id}: deletes object in LocalStack, delete entity in resource-db database, returns id

**song-service**
/songs
- POST: saves metadata in song-db database, returns song id
- GET /{id}: gets metadata from song-db database, returns song details
- DELETE /{ids}: delete entity in song-db database, returns all, some of or none of the provided ids

**resource-processor**
- [ResourceMessageListener.java](./resource-processor/src/main/java/com/epam/resource_processor/service/ResourceMessageListener.java): consumes resource id, calls GET /resources, processes a binary file, calls POST /songs.

#### Approach
(Test all paths through microservices of running endpoints? Will mock logic still be present in test containers? What to do with resource-processor?)

## Component tests

#### Endpoints
**resource-service**
/resources
- POST: mocks LocalStack, mocks RabbitMQ, mocks resource-db database, returns id
- GET /{id}: mocks LocalStack, mocks resource-db database, returns some binary data
- DELETE /{id}: mocks LocalStack, mocks resource-db database, returns id

**song-service**
/songs
- POST: mocks song-db database, returns song id
- GET /{id}: mocks song-db database, returns song details
- DELETE /{ids}: mocks song-db database, returns all, some of or none of the provided ids

**resource-processor**
- [ResourceMessageListener.java](./resource-processor/src/main/java/com/epam/resource_processor/service/ResourceMessageListener.java): mocks consumed id, mocks GET /resources, mocks POST /songs.

#### Approach
Have a few Spring MockMvc REST assured tests which mock response from a database, other sources and microservices. (Which scenarios should they test? Probably "Error cases" requests from the Postman collection should be tested, like Controller Advice responses)

## Contract tests

There are 3 microservices involved into business flows, excluding Eureka server.

**Smallest units**:
- resource-service talks to resource-db database (save, get by id, delete).
- resource-service talks to RabbitMQ (send).
- resource-service talks to AWS S3 LocalStack (create, read, delete).
- resource-processor talks to resource-service (GET).
- resource-processor talks to RabbitMQ (consume).
- resource-processor talks to song-service (POST).
- song-service talks to song-db database (save, get by id, delete).

**Contract Stability**:
- REST controllers didn't receive any change to their interfaces for a long time.
- resource-processor was implemented recently.
- RabbitMQ uses only one topic for now. May have more topics/DLQ in the future.

#### Approach
(Validate what hasn't been changed for a while? 
Or validate what is a subject to change?
Or validate all requests and responses between services, databases, RabbitMQ, but exclude LocalStack?
I don't know)


## End-to-End tests

There are services expose REST API to interact with the system:
- resource-service: [ResourcesController.java](./resource-service/src/main/java/com/epam/resource/controller/ResourcesController.java)
- song-service: [SongsController.java](./song-service/src/main/java/com/epam/song/controller/SongsController.java)

Running the whole Postman collection against it, imagining production scale,
is not a productive way: real HTTP requests are expensive, slow and don't cover 
all possible scenarios. As the system grows, fewer scenarios will be covered.

#### Approach
Run "Happy Path" requests only to ensure all endpoints work. This covers all microservices interactions.