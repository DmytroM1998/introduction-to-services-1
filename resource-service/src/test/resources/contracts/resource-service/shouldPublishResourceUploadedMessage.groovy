package contracts.resource_service

import org.springframework.cloud.contract.spec.Contract

/**
 * Spring Cloud Contract — Messaging — resource-service
 *
 * Contract: resource-service publishes a resource.uploaded message to RabbitMQ
 * exchange "resource.uploaded" with routing key "resource.uploaded" whenever
 * a new MP3 resource is successfully stored.
 */
Contract.make {
    name 'successfulUpload_publishesJsonResourceId'
    description """
        After a successful MP3 upload, resource-service sends a message to the
        resource.uploaded exchange with the new resource id as the body.
    """

    label 'resource_uploaded'

    input {
        triggeredBy('triggerResourceUpload()')
    }

    outputMessage {
        sentTo 'resource.uploaded'
        headers {
            header('contentType', 'application/json')
        }
        body(
            $(consumer(~/\d+/), producer('42'))
        )
    }
}
