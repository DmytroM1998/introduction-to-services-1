package com.epam.song.service;

import com.epam.song.dto.SongDto;
import com.epam.song.entity.SongEntity;
import com.epam.song.exception.ErrorCodeException;
import com.epam.song.repository.SongsRepository;
import com.epam.song.util.MessageConstants;
import com.epam.song.util.SongMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class SongsService {

  private static final int IDS_STRING_MAX_LENGTH = 200;
  
  private final SongsRepository songsRepository;
  
  public Map<String, Long> saveNewSong(SongDto songDto) {
    Long providedSongId = songDto.getId();
    if (!songsRepository.existsById(providedSongId)) {
      SongEntity songEntity = SongMapper.toEntity(songDto);
      providedSongId = songsRepository.save(songEntity).getId();
      return Map.of("id", providedSongId);
    } else {
      throw new ErrorCodeException(HttpStatus.CONFLICT.value(), String.format("Song with ID=%d already exists", providedSongId));
    }
  }

  public SongDto getSongById(String id) {
    long parsedId = parseId(id);
    return songsRepository.findById(parsedId)
        .map(SongMapper::toDto)
        .orElseThrow(() -> new ErrorCodeException(HttpStatus.NOT_FOUND.value(), String.format("Song with ID=%d not found", parsedId)));
  }

  private long parseId(String id) {
    try {
      int parsedId = Integer.parseInt(id);
      if (parsedId <= 0) {
        throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
            String.format("Invalid value '%s' for ID. Must be a positive integer", id));
      }
      return parsedId;
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("Invalid value '%s' for ID. Must be a positive integer", id));
    }
  }

  @Transactional
  public Map<String, List<Long>> deleteSongsByIds(String ids) {
    validateSongsIdsLength(ids);
    List<Long> idList = parseSongIds(ids);
    try {
      return deleteExistingSongsByIds(idList);
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new ErrorCodeException(HttpStatus.INTERNAL_SERVER_ERROR.value(), MessageConstants.SOMETHING_WENT_WRONG);
    }
  }

  private Map<String, List<Long>> deleteExistingSongsByIds(List<Long> idList) {
    List<Long> deletedIds = new ArrayList<>();
    for (Long id : idList) {
      if (songsRepository.existsById(id)) {
        songsRepository.deleteById(id);
        deletedIds.add(id);
      }
    }
    return Map.of("ids", deletedIds);
  }

  private List<Long> parseSongIds(String ids) {
    AtomicReference<String> invalidValue = new AtomicReference<>(StringUtils.EMPTY);
    try {
      return Arrays.stream(ids.split(","))
          .map(id -> mapAndReturnInvalidLong(id, invalidValue))
          .toList();
    } catch (NumberFormatException e) {
      log.error(e.getMessage());
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("Invalid ID format: '%s'. Only positive integers are allowed", invalidValue.get()));
    }
  }

  private static long mapAndReturnInvalidLong(String id, AtomicReference<String> invalidValue) {
    try {
      return Long.parseLong(id);
    } catch (NumberFormatException e) {
      invalidValue.set(id);
      throw e;
    }
  }

  private void validateSongsIdsLength(String ids) {
    if (ids.length() >= IDS_STRING_MAX_LENGTH) {
      throw new ErrorCodeException(HttpStatus.BAD_REQUEST.value(),
          String.format("CSV string is too long: received %s characters, maximum allowed is %s", ids.length(), IDS_STRING_MAX_LENGTH));
    }
  }
}
