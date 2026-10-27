package contracts.resource_service

import org.springframework.cloud.contract.spec.Contract

/**
 * Spring Cloud Contract — HTTP — resource-service
 *
 * Contract: GET /resources/{id}
 *
 * When resource-processor (or any consumer) calls GET /resources/1,
 * the resource-service MUST respond with:
 *   - HTTP 200 OK
 *   - Content-Type: audio/mpeg
 *   - A non-empty binary body
 */
Contract.make {
    name 'existingResource_returnsExactMp3Bytes'
    description """
        GET /resources/1 returns the binary MP3 content for the resource with id=1.
        The response MUST have Content-Type audio/mpeg and a non-empty body.
    """

    request {
        method GET()
        url '/resources/1'
        headers {
            accept('audio/mpeg')
        }
    }

    response {
        status OK()
        headers {
            contentType('audio/mpeg')
        }
        body(fileAsBytes('valid-sample.mp3'))
    }
}
