/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package bio.overture.songsearch.graphql;

import static bio.overture.songsearch.config.constants.SearchFields.ANALYSIS_ID;
import static bio.overture.songsearch.config.constants.SearchFields.RUN_ID;
import static bio.overture.songsearch.utils.CommonUtils.asImmutableMap;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

import bio.overture.songsearch.config.SongSearchProperties;
import bio.overture.songsearch.model.Analysis;
import bio.overture.songsearch.model.Run;
import bio.overture.songsearch.service.AnalysisService;
import bio.overture.songsearch.service.FileService;
import com.apollographql.federation.graphqljava._Entity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EntityDataFetcher {
  public static final String ANALYSIS_ENTITY = "Analysis";
  public static final String FILE_ENTITY = "File";
  public static final String RUN_ENTITY = "Run";

  private final AnalysisService analysisService;
  private final FileService fileService;
  private final SongSearchProperties songSearchProperties;

  @Autowired
  public EntityDataFetcher(
      AnalysisService analysisService,
      FileService fileService,
      SongSearchProperties songSearchProperties) {
    this.analysisService = analysisService;
    this.fileService = fileService;
    this.songSearchProperties = songSearchProperties;
  }

  public DataFetcher getDataFetcher() {
    return environment ->
        environment.<List<Map<String, Object>>>getArgument(_Entity.argumentName).stream()
            .map(
                values -> {
                  if (ANALYSIS_ENTITY.equals(values.get("__typename"))) {
                    final Object analysisId = values.get("analysisId");
                    if (analysisId instanceof String) {
                      return analysisService.getAnalysisById((String) analysisId);
                    }
                  }
                  if (FILE_ENTITY.equals(values.get("__typename"))) {
                    final Object fileObjectId = values.get("objectId");
                    if (fileObjectId instanceof String) {
                      return fileService.getFileByObjectId((String) fileObjectId);
                    }
                  }
                  if (RUN_ENTITY.equals(values.get("__typename"))) {
                    final Object runId = values.get("runId");
                    val ids = getRelevantAnalysisIdsFromRunParameters(values.get("parameters"));

                    if (runId instanceof String) {
                      return new Run(
                          (String) runId,
                          producedAnalysesResolver((String) runId),
                          inputAnalysesResolver(ids));
                    }
                  }
                  return null;
                })
            .collect(toList());
  }

  private DataFetcher<List<Analysis>> inputAnalysesResolver(List<String> inputAnalysisIds) {
    return environment -> {
      ImmutableMap<String, Object> filter = asImmutableMap(environment.getArgument("filter"));
      val filerAnalysisId = filter.get(ANALYSIS_ID);

      val multipleMergedFilters =
          inputAnalysisIds.stream()
              .map(
                  id -> {
                    Map<String, Object> map = new HashMap<>(filter);
                    map.put(ANALYSIS_ID, id);
                    return map;
                  })
              .filter(f -> filerAnalysisId == null || f.get(ANALYSIS_ID).equals(filerAnalysisId))
              .collect(toList());

      // short circuit here, otherwise will get all analysis
      if (multipleMergedFilters.size() < 1) {
        return List.of();
      }

      return analysisService.getAnalyses(multipleMergedFilters);
    };
  }

  private DataFetcher<List<Analysis>> producedAnalysesResolver(String runId) {
    return environment -> {
      ImmutableMap<String, Object> filter = asImmutableMap(environment.getArgument("filter"));
      val filterRunId = filter.getOrDefault(RUN_ID, runId);

      // short circuit here since can't find produced analysis for invalid runId
      if (isNullOrEmpty(runId) || !runId.equals(filterRunId)) {
        return List.of();
      }

      Map<String, Object> mergedFilter = new HashMap<>(filter);
      mergedFilter.put(RUN_ID, runId);

      return analysisService.getAnalyses(mergedFilter, null);
    };
  }

  private List<String> getRelevantAnalysisIdsFromRunParameters(Object parametersObj) {
    ImmutableMap<String, Object> parameters = asImmutableMap(parametersObj);

    ImmutableList<String> analysisIdKeysToLookFor =
        songSearchProperties.getWorkflowRunParameterKeys().getAnalysisId();

    return analysisIdKeysToLookFor.stream()
        .map(parameters::get)
        .filter(Objects::nonNull)
        .map(Objects::toString)
        .collect(toList());
  }
}
