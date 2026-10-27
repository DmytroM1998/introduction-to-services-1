Feature: Resource Upload, Retrieval and Lifecycle Management
  As a media platform client
  I want to upload MP3 audio files, retrieve them, and delete them by ID
  So that audio content can be stored, accessed, and managed reliably

  Background:
    Given the resource service is running

  Scenario: Successfully upload a valid MP3 file and retrieve it
    Given a valid MP3 audio file
    When the client uploads the MP3 file with content type "audio/mpeg"
    Then the response status is 200 OK
    And the response body contains a positive numeric resource id

    When the client requests the resource using the returned id
    Then the response status is 200 OK
    And the response body contains the same bytes as the uploaded MP3 file

  Scenario Outline: Uploading a file with an unsupported content type is rejected
    Given a plain text file masquerading as audio
    When the client uploads the file with content type "<contentType>"
    Then the response status is 400 Bad Request
    And the invalid format response matches the Postman specification for "<contentType>"

    Examples:
      | contentType      |
      | application/json |
      | text/plain       |

  Scenario: A database failure compensates the S3 upload without publishing
    Given a valid MP3 audio file
    And the database rejects the resource record
    When the client uploads the MP3 file with content type "audio/mpeg"
    Then the response status is 500 Internal Server Error
    And the upload returns the generic server error without a resource id
    And no uploaded object or resource record remains
    And no resource notification is published

  Scenario: A publication failure compensates both persistence operations
    Given a valid MP3 audio file
    And the message publisher rejects the upload notification
    When the client uploads the MP3 file with content type "audio/mpeg"
    Then the response status is 500 Internal Server Error
    And the upload returns the generic server error without a resource id
    And no uploaded object or resource record remains

  Scenario: Requesting a resource that does not exist returns 404
    Given no resource exists with id 999999
    When the client requests the resource with id 999999
    Then the response status is 404 Not Found
    And the response body contains an errorMessage explaining the resource was not found

  Scenario: Delete a previously uploaded resource
    Given a valid MP3 audio file has been uploaded and the id was recorded
    When the client deletes the recorded resource
    Then the response status is 200 OK
    And the response body contains an ids list including the deleted id

    When the client requests the deleted resource
    Then the response status is 404 Not Found
