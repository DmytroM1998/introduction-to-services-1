package com.epam.resource_processor.mapper;

import com.epam.resource_processor.dto.SongDto;
import org.apache.tika.metadata.Metadata;

public class SongDtoMapper {
  
  private SongDtoMapper() {}
  
  public static SongDto toDto(Long id, Metadata mp3metadata) {
    SongDto songDto = new SongDto();
    songDto.setId(id);
    songDto.setName(mp3metadata.get("dc:title"));
    songDto.setArtist(mp3metadata.get("xmpDM:artist"));
    songDto.setAlbum(mp3metadata.get("xmpDM:album"));
    songDto.setDuration(formatDuration(mp3metadata.get("xmpDM:duration")));
    songDto.setYear(mp3metadata.get("xmpDM:releaseDate"));
    return songDto;
  }

  private static String formatDuration(String duration) {
    if (duration == null) {
      return null;
    }
    double durationSeconds = Double.parseDouble(duration);
    long durationMillis = (long) (durationSeconds * 1000);
    long minutes = (durationMillis / 1000) / 60;
    long seconds = (durationMillis / 1000) % 60;
    return String.format("%02d:%02d", minutes, seconds);
  }
}
