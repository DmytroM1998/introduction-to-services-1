package com.epam.resource_processor.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SongDto {
  
  private Long id;
  private String name;
  private String artist;
  private String album;
  private String duration;
  private String year;

  public SongDto(Long id, String name, String artist, String album, String duration, String year) {
    this.id = id;
    this.name = name;
    this.artist = artist;
    this.album = album;
    this.duration = duration;
    this.year = year;
  }

}
