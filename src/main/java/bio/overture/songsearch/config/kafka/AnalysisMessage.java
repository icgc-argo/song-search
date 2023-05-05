/*
 * Copyright (c) 2018. Ontario Institute for Cancer Research
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package bio.overture.songsearch.config.kafka;

import static lombok.AccessLevel.PRIVATE;
import bio.overture.songsearch.model.enums.AnalysisState;
import lombok.*;

@Value
// Note: although the AllArgs and NoArgs combination below seems odd,
// it allows Jackson to deserialize to an immutable object without using any additional annotations.
@AllArgsConstructor
@NoArgsConstructor(force = true, access = PRIVATE)
public class AnalysisMessage {

  @NonNull private final String analysisId;
  @NonNull private final String studyId;
  @NonNull private final String state;
  @NonNull private final String songServerId;
  @NonNull private final Analysis analysis;

  public static AnalysisMessage createAnalysisMessage(
      bio.overture.songsearch.model.Analysis analysis, String songServerId) {

    return new AnalysisMessage(
        analysis.getAnalysisId(),
        analysis.getStudyId(),
        analysis.getAnalysisState().toString(),
        analysis.getRepositories().get(0).getCode(),
        new Analysis(
            analysis.getAnalysisId(),
            new AnalysisType(analysis.getAnalysisType()),
            analysis.getAnalysisState(),
            analysis.getStudyId()));
  }

  @Value
  @AllArgsConstructor
  private static class Analysis {
    String analysisId;
    AnalysisType analysisType;
    AnalysisState analysisState;
    String studyId;
  }

  @Data
  @AllArgsConstructor
  public static class AnalysisType {
    String name;
  }
}
