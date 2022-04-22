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

package bio.overture.songsearch.repository;

import static bio.overture.songsearch.config.constants.SearchFields.*;
import static bio.overture.songsearch.utils.ElasticsearchQueryUtils.queryFromArgs;
import static bio.overture.songsearch.utils.ElasticsearchQueryUtils.sortsToEsSortBuilders;
import static java.util.Collections.emptyList;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.search.sort.SortOrder.ASC;

import bio.overture.songsearch.config.ElasticsearchProperties;
import bio.overture.songsearch.model.Sort;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnalysisRepository {
  private static final Map<String, Function<Object, AbstractQueryBuilder<?>>> QUERY_RESOLVER =
      argumentPathMap();

  private static final Map<String, FieldSortBuilder> SORT_BUILDER_RESOLVER = sortPathMap();

  private final RestHighLevelClient client;
  private final String analysisCentricIndex;

  @Autowired
  public AnalysisRepository(
      @NonNull RestHighLevelClient client,
      @NonNull ElasticsearchProperties elasticSearchProperties) {
    this.client = client;
    this.analysisCentricIndex = elasticSearchProperties.getAnalysisCentricIndex();
  }

  private static Map<String, Function<Object, AbstractQueryBuilder<?>>> argumentPathMap() {
    return ImmutableMap.<String, Function<Object, AbstractQueryBuilder<?>>>builder()
        .put(ANALYSIS_ID, value -> new TermQueryBuilder("analysis_id", value))
        .put(ANALYSIS_TYPE, value -> new TermQueryBuilder("analysis_type", value))
        .put(ANALYSIS_VERSION, value -> new TermQueryBuilder("analysis_version", value))
        .put(ANALYSIS_STATE, value -> new TermQueryBuilder("analysis_state", value))
        .put(STUDY_ID, value -> new TermQueryBuilder("study_id", value))
        .put(RUN_ID, value -> new TermQueryBuilder("workflow.run_id", value))
        .put(
            DONOR_ID,
            value ->
                new NestedQueryBuilder(
                    "donors", new TermQueryBuilder("donors.donor_id", value), ScoreMode.None))
        .put(
            SPECIMEN_ID,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens",
                    new TermQueryBuilder("donors.specimens.specimen_id", value),
                    ScoreMode.None))
        .put(
            TUMOUR_NORMAL_DESIGNATION,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens",
                    new TermQueryBuilder("donors.specimens.tumour_normal_designation", value),
                    ScoreMode.None))
        .put(
            SAMPLE_ID,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens.samples",
                    new TermQueryBuilder("donors.specimens.samples.sample_id", value),
                    ScoreMode.None))
        .put(
            MATCHED_NORMAL_SUBMITTER_SAMPLE_ID,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens.samples",
                    new TermQueryBuilder(
                        "donors.specimens.samples.matched_normal_submitter_sample_id", value),
                    ScoreMode.None))
        .put(
            SUBMITTER_SAMPLE_ID,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens.samples",
                    new TermQueryBuilder("donors.specimens.samples.submitter_sample_id", value),
                    ScoreMode.None))
        .put(
            EXPERIMENTAL_STRATEGY,
            value ->  {
              val bool = new BoolQueryBuilder();
              // older analyses might have library_strategy isntead of experimental_strategy
              // so use should (logical OR) to look for either
              bool.should(new TermQueryBuilder("experiment.experimental_strategy", value));
              bool.should(new TermQueryBuilder("experiment.library_strategy", value));
              // make sure at least one of them match
              bool.minimumShouldMatch(1);
              return bool;
            })
        .put(
            SAMPLE_TYPE,
            value ->
                new NestedQueryBuilder(
                    "donors.specimens.samples",
                    new TermQueryBuilder("donors.specimens.samples.sample_type", value),
                    ScoreMode.None))
        .build();
  }

  private static Map<String, FieldSortBuilder> sortPathMap() {
    return ImmutableMap.<String, FieldSortBuilder>builder()
        .put(ANALYSIS_ID, SortBuilders.fieldSort("analysis_id"))
        .put(ANALYSIS_STATE, SortBuilders.fieldSort("analysis_state"))
        .put(PUBLISHED_AT, SortBuilders.fieldSort("published_at"))
        .put(UPDATED_AT, SortBuilders.fieldSort("updated_at"))
        .put(FIRST_PUBLISHED_AT, SortBuilders.fieldSort("first_published_at"))
        .build();
  }

  public SearchResponse getAnalyses(Map<String, Object> filter, Map<String, Integer> page) {
    return getAnalyses(filter, page, emptyList());
  }

  public SearchResponse getAnalyses(
      Map<String, Object> filter, Map<String, Integer> page, List<Sort> sorts) {
    final AbstractQueryBuilder<?> query =
        (filter == null || filter.size() == 0)
            ? matchAllQuery()
            : queryFromArgs(QUERY_RESOLVER, filter);

    val searchSourceBuilder = createSearchSourceBuilder(query, page, sorts);

    return execute(searchSourceBuilder);
  }

  public MultiSearchResponse getAnalyses(
      List<Map<String, Object>> multipleFilters, Map<String, Integer> page) {
    List<SearchSourceBuilder> searchSourceBuilders =
        multipleFilters.stream()
            .filter(f -> f != null && f.size() != 0)
            .map(f -> createSearchSourceBuilder(queryFromArgs(QUERY_RESOLVER, f), page))
            .collect(Collectors.toList());

    if (searchSourceBuilders.isEmpty()) {
      searchSourceBuilders.add(createSearchSourceBuilder(matchAllQuery(), page));
    }

    return execute(searchSourceBuilders);
  }

  private SearchSourceBuilder createSearchSourceBuilder(
      AbstractQueryBuilder<?> query, Map<String, Integer> page) {
    return createSearchSourceBuilder(query, page, emptyList());
  }

  private SearchSourceBuilder createSearchSourceBuilder(
      AbstractQueryBuilder<?> query, Map<String, Integer> page, List<Sort> sorts) {
    val searchSourceBuilder = new SearchSourceBuilder();

    if (sorts.isEmpty()) {
      searchSourceBuilder.sort(SORT_BUILDER_RESOLVER.get(UPDATED_AT).order(ASC));
    } else {
      val sortBuilders = sortsToEsSortBuilders(SORT_BUILDER_RESOLVER, sorts);
      sortBuilders.forEach(searchSourceBuilder::sort);
    }

    searchSourceBuilder.query(query);

    if (page != null && page.size() != 0) {
      searchSourceBuilder.size(page.get("size"));
      searchSourceBuilder.from(page.get("from"));
    }

    // es 7.0+ by default caps total hits up to 10,000 if not explicitly told to track all hits
    // more info:
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/breaking-changes-7.0.html#track-total-hits-10000-default
    searchSourceBuilder.trackTotalHits(true);

    return searchSourceBuilder;
  }

  @SneakyThrows
  private SearchResponse execute(@NonNull SearchSourceBuilder builder) {
    val searchRequest = new SearchRequest(analysisCentricIndex);
    searchRequest.source(builder);
    return client.search(searchRequest, RequestOptions.DEFAULT);
  }

  @SneakyThrows
  private MultiSearchResponse execute(@NonNull List<SearchSourceBuilder> builders) {
    MultiSearchRequest mSearchRequest = new MultiSearchRequest();
    builders.forEach(b -> mSearchRequest.add(new SearchRequest(analysisCentricIndex).source(b)));
    return client.msearch(mSearchRequest, RequestOptions.DEFAULT);
  }
}
