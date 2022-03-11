package bio.overture.songsearch.model;

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
    if (fileAccess != null
        && file.getFileAccess() != null
        && !file.getFileAccess().equalsIgnoreCase(fileAccess)) {
      return false;
    }
    if (fileType != null
        && file.getFileType() != null
        && !file.getFileType().equalsIgnoreCase(fileType)) {
      return false;
    }
    if (dataType != null
        && file.getDataType() != null
        && !file.getDataType().equalsIgnoreCase(dataType)) {
      return false;
    }
    if (analysisTools != null
        && file.getAnalysisTools() != null
        && !file.getAnalysisTools().containsAll(analysisTools)) {
      return false;
    }
    return true;
  }
}
