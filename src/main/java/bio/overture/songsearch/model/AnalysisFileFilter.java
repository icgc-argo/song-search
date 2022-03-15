package bio.overture.songsearch.model;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisFileFilter {
  private List<String> analysisTools;
  private String fileType;
  private String fileAccess;
  private String dataType;

  public boolean test(AnalysisFile file) {
    if (fileAccess != null && fileAccess.equalsIgnoreCase(file.getFileAccess())) {
      return false;
    }
    if (fileType != null && !fileType.equalsIgnoreCase(file.getFileType())) {
      return false;
    }
    if (dataType != null && !dataType.equalsIgnoreCase(file.getDataType())) {
      return false;
    }
    if (analysisTools != null
        && !ofNullable(file.getAnalysisTools())
            .orElse(emptyList())
            .containsAll(analysisTools)) {
      return false;
    }
    return true;
  }
}
