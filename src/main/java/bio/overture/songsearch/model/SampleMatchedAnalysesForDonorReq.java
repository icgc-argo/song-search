package bio.overture.songsearch.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
public class SampleMatchedAnalysesForDonorReq {
  @NonNull private String donorId;
  private String studyId;
  private String analysisType;
}
