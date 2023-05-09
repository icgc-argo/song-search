package bio.overture.songsearch.model;

import lombok.Value;

@Value
public class AutomationMutationResponse {

  private Analysis analysis;
  private String message;
}
