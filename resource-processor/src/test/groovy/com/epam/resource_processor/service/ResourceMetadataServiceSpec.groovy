package com.epam.resource_processor.service

import com.epam.resource_processor.dto.SongDto
import spock.lang.Specification
import spock.lang.Subject

/**
 * Spock Integration Test — resource-processor
 *
 * Covers the {@link ResourceMetadataService#processResourceMetadata} integration layer,
 * using Apache Tika to extract ID3 tags from a real MP3 file.
 *
 * This is a focused integration test: real Tika parsers + real MP3 bytes are used,
 * but no Spring context or external services are involved.
 */
class ResourceMetadataServiceSpec extends Specification {

    @Subject
    ResourceMetadataService service = new ResourceMetadataService()

    /**
     * Loads the sample MP3 bundled in src/test/resources.
     * The file contains: Title="Test Title", Artist="Test Artist",
     *                     Album="Test Album", Year="2025".
     */
    def mp3Bytes = ResourceMetadataServiceSpec.class
            .getResourceAsStream("/valid-sample.mp3")
            .readAllBytes()

    // ─── Scenarios ────────────────────────────────────────────────────────────

    def "processResourceMetadata_validMp3_preservesResourceId"() {
        when: "the service processes the MP3 bytes for resource id 42"
            SongDto result = service.processResourceMetadata(42L, mp3Bytes)

        then: "the returned DTO carries the resource id"
            result.id == 42L
    }

    def "processResourceMetadata_validId3Title_extractsTitle"() {
        when: "the service processes the valid MP3"
            SongDto result = service.processResourceMetadata(1L, mp3Bytes)

        then: "the title matches the ID3 tag embedded in the file"
            result.name == "Test Title"
    }

    def "processResourceMetadata_validId3Tags_extractsArtistAlbumAndYear"() {
        when: "the service processes the valid MP3"
            SongDto result = service.processResourceMetadata(1L, mp3Bytes)

        then: "all expected metadata fields are populated"
            result.artist == "Test Artist"
            result.album  == "Test Album"
            result.year   == "2025"
    }

    def "processResourceMetadata_validMp3_formatsDurationAsMinutesAndSeconds"() {
        when: "the service processes the valid MP3"
            SongDto result = service.processResourceMetadata(1L, mp3Bytes)

        then: "duration is formatted as MM:SS (not null, matches pattern)"
            result.duration != null
            result.duration ==~ /\d{2}:\d{2}/
    }

    def "processResourceMetadata_emptyBytes_returnsIdWithNullMetadata"() {
        when: "an empty byte array is passed"
            SongDto result = service.processResourceMetadata(1L, new byte[0])

        then: "a SongDto is returned with the id and null metadata"
            result.id == 1L
            result.name == null
            result.artist == null
            result.album == null
    }

    def "processResourceMetadata_nullBytes_throwsNullPointerException"() {
        when: "null byte array is passed"
            service.processResourceMetadata(1L, null)

        then: "a NullPointerException is thrown"
            thrown(NullPointerException)
    }
}
