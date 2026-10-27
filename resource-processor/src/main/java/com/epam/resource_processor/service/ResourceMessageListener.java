package com.epam.resource_processor.service;

import com.epam.resource_processor.dto.SongDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@Slf4j
@AllArgsConstructor
public class ResourceMessageListener implements Consumer<Long> {
  
  private final ResourceMetadataService resourceMetadataService;
  private final CommunicationService communicationService;

  @Override
  public void accept(Long resourceId) {
    log.info("Received resource ID {} for processing", resourceId);
    
    byte[] resourceData = communicationService.requestResourceBinary(resourceId);
    SongDto songMetadata = resourceMetadataService.processResourceMetadata(resourceId, resourceData);
    communicationService.saveSongMetadata(resourceId, songMetadata);
    
    log.info("Completed processing for resource ID {}", resourceId);
  }
}
