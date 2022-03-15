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

import static bio.overture.songsearch.utils.JacksonUtils.convertValue;
import static java.util.stream.Collectors.toUnmodifiableList;

import bio.overture.songsearch.model.*;
import bio.overture.songsearch.service.AnalysisService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphql.schema.DataFetcher;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalysisDataFetcher {

  private final AnalysisService analysisService;

  @Autowired
  public AnalysisDataFetcher(AnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @SuppressWarnings("unchecked")
  public DataFetcher<SearchResult<Analysis>> getAnalysesDataFetcher() {
    return environment -> {
      val args = environment.getArguments();

      val filter = ImmutableMap.<String, Object>builder();
      val page = ImmutableMap.<String, Integer>builder();
      val sorts = ImmutableList.<Sort>builder();

      if (args != null) {
        if (args.get("filter") != null) filter.putAll((Map<String, Object>) args.get("filter"));
        if (args.get("page") != null) page.putAll((Map<String, Integer>) args.get("page"));
        if (args.get("sorts") != null) {
          val rawSorts = (List<Object>) args.get("sorts");
          sorts.addAll(
              rawSorts.stream()
                  .map(sort -> convertValue(sort, Sort.class))
                  .collect(toUnmodifiableList()));
        }
      }
      return analysisService.searchAnalyses(filter.build(), page.build(), sorts.build());
    };
  }

  @SuppressWarnings("unchecked")
  public DataFetcher<AggregationResult> getAggregateAnalysesDataFetcher() {
    return environment -> {
      val args = environment.getArguments();

      val filter = ImmutableMap.<String, Object>builder();

      if (args != null) {
        if (args.get("filter") != null) filter.putAll((Map<String, Object>) args.get("filter"));
      }
      return analysisService.aggregateAnalyses(filter.build());
    };
  }

  public DataFetcher<List<SampleMatchedAnalysisPair>> getSampleMatchedAnalysisPairsFetcher() {
    return env -> {
      val analysisId = env.getArguments().get("analysisId").toString();
      return analysisService.getSampleMatchedAnalysisPairs(analysisId);
    };
  }

  public DataFetcher<List<SampleMatchedAnalysisPair>> getNewFetcher() {
    return env -> {
      val donorId = env.getArguments().get("donorId").toString();
      val analysisType = env.getArguments().get("analysisType").toString();
      return analysisService.getNew(donorId, analysisType);
    };
  }
}
