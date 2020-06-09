package bio.overture.songsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class File {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private String objectId;

  private String studyId;

  private String dataType;

  private String fileType;

  private String fileAccess;

  private Analysis analysis;

  private FileMeta file;

  private List<Repository> repositories;

  private List<Donor> donors;

  @SneakyThrows
  public static File parse(@NonNull Map<String, Object> sourceMap) {
    return MAPPER.convertValue(sourceMap, File.class);
  }
}
