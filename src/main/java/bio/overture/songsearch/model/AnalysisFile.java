package bio.overture.songsearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AnalysisFile {
  private String objectId;
  private String name;
  private Long size;
  private String fileType;
  private String md5Sum;
  private String fileAccess;
  private String dataType;
  private Map<String, Object> metrics;
  private List<String> analysisTools;
}
