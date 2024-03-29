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

package bio.overture.songsearch.model;

import bio.overture.songsearch.model.enums.AnalysisState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Analysis {
  private static final String EXPERIMENT_KEY_EXPERIMENTAL_STRATEGY = "experimental_strategy";
  private static final String EXPERIMENT_KEY_LIBRARY_STRATEGY = "library_strategy";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private String analysisId;

  private String analysisType;

  private Integer analysisVersion;

  private AnalysisState analysisState;

  private String studyId;

  private List<Donor> donors;

  private List<AnalysisFile> files;

  private List<Repository> repositories;

  private Map<String, Object> experiment;

  private Workflow workflow;

  private String updatedAt;

  private String publishedAt;

  private String firstPublishedAt;

  public List<AnalysisFile> getFiles(DataFetchingEnvironment env) {
    if (env.getArguments().get("filter") == null) {
      return files;
    }
    val fileFilter =
        MAPPER.convertValue(env.getArguments().get("filter"), AnalysisFileFilter.class);
    return files.stream().filter(fileFilter::test).collect(Collectors.toList());
  }

  public Object getExperimentStrategy() {
    // experiment.experimental_strategy and experiment.library_strategy are the SAME fields,
    // currently because of historical reasons, some analyses use experimental_strategy and some
    // use library_strategy, tickets are made to ensure all analyses use experimental_strategy in
    // the future, but for now we must consider both fields.
    return experiment.getOrDefault(
        EXPERIMENT_KEY_EXPERIMENTAL_STRATEGY, experiment.get(EXPERIMENT_KEY_LIBRARY_STRATEGY));
  }

  @SneakyThrows
  public static Analysis parse(@NonNull Map<String, Object> sourceMap) {
    return MAPPER.convertValue(sourceMap, Analysis.class);
  }
}
