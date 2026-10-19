package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import com.epam.resource_processor.mapper.SongDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class ResourceMetadataService {

    public static final String SOMETHING_WENT_WRONG = "Something went wrong";
    private final Tika tika = new Tika();

    public SongDto processAndPrintResourceMetadata(Long resourceId, byte[] mp3file) {
        log.debug("mp3file size: {}", mp3file.length);
        Metadata metadata = extractMetadata(mp3file);
        SongDto parsedSongDto = SongDtoMapper.toDto(resourceId, metadata);

        log.info("Parsed Song Metadata: {}", parsedSongDto);
        return parsedSongDto;
    }

    private Metadata extractMetadata(byte[] mp3file) {
        Metadata metadata = new Metadata();
        try (InputStream mp3FileData = new ByteArrayInputStream(mp3file)) {
            tika.parse(mp3FileData, metadata);
        } catch (IOException e) {
            log.error("Failed to extract metadata from resource: {}", e.getMessage());
            throw new RuntimeException(SOMETHING_WENT_WRONG, e);
        }
        return metadata;
    }
}
