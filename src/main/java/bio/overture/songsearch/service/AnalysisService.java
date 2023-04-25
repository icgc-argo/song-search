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

package bio.overture.songsearch.service;

import static bio.overture.songsearch.config.constants.EsDefaults.ES_PAGE_DEFAULT_FROM;
import static bio.overture.songsearch.config.constants.EsDefaults.ES_PAGE_DEFAULT_SIZE;
import static bio.overture.songsearch.config.constants.SearchFields.*;
import static bio.overture.songsearch.config.kafka.AnalysisMessage.createAnalysisMessage;
import static bio.overture.songsearch.model.enums.AnalysisState.PUBLISHED;
import static bio.overture.songsearch.model.enums.SpecimenType.NORMAL;
import static bio.overture.songsearch.model.enums.SpecimenType.TUMOUR;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.empty;

import bio.overture.songsearch.config.kafka.KafkaSender;
import bio.overture.songsearch.model.*;
import bio.overture.songsearch.repository.AnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import java.util.*;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

  private final AnalysisRepository analysisRepository;
  private String songServerId;
  private final KafkaSender sender;


  @Autowired
  public AnalysisService(AnalysisRepository analysisRepository,
                         @NonNull KafkaSender sender) {

    this.analysisRepository = analysisRepository;
    this.sender = sender;
  }


  private static Analysis hitToAnalysis(SearchHit hit) {
    val sourceMap = hit.getSourceAsMap();
    return Analysis.parse(sourceMap);
  }

  public SearchResult<Analysis> searchAnalyses(
      Map<String, Object> filter, Map<String, Integer> page, List<Sort> sorts) {
    val response = analysisRepository.getAnalyses(filter, page, sorts);
    val responseSearchHits = response.getHits();

    val totalHits = responseSearchHits.getTotalHits().value;
    val from = page.getOrDefault("from", ES_PAGE_DEFAULT_FROM);
    val size = page.getOrDefault("size", ES_PAGE_DEFAULT_SIZE);

    val analyses =
        Arrays.stream(responseSearchHits.getHits())
            .map(AnalysisService::hitToAnalysis)
            .collect(toUnmodifiableList());
    val nextFrom = (totalHits - from) / size > 0;
    return new SearchResult<>(analyses, nextFrom, totalHits);
  }

  public AggregationResult aggregateAnalyses(Map<String, Object> filter) {
    val response = analysisRepository.getAnalyses(filter, Map.of(), List.of());
    val responseSearchHits = response.getHits();
    val totalHits = responseSearchHits.getTotalHits().value;
    return new AggregationResult(totalHits);
  }

  public List<Analysis> getAnalyses(Map<String, Object> filter, Map<String, Integer> page) {
    return getAnalysesStream(filter, page).collect(toUnmodifiableList());
  }

  public Stream<Analysis> getAnalysesStream(Map<String, Object> filter, Map<String, Integer> page) {
    val response = analysisRepository.getAnalyses(filter, page);
    val hitStream = Arrays.stream(response.getHits().getHits());
    return hitStream.map(AnalysisService::hitToAnalysis);
  }

  public Analysis getAnalysisById(String analysisId) {
    val response = analysisRepository.getAnalyses(Map.of(ANALYSIS_ID, analysisId), null);
    val runOpt =
        Arrays.stream(response.getHits().getHits()).map(AnalysisService::hitToAnalysis).findFirst();
    return runOpt.orElse(null);
  }

  public List<Analysis> getAnalysesByRunId(String runId) {
    val response = analysisRepository.getAnalyses(Map.of(RUN_ID, runId), null);
    val hitStream = Arrays.stream(response.getHits().getHits());
    return hitStream.map(AnalysisService::hitToAnalysis).collect(toUnmodifiableList());
  }

  public List<Analysis> getAnalyses(List<Map<String, Object>> multipleFilters) {
    val multiSearchResponse = analysisRepository.getAnalyses(multipleFilters, null);
    return Arrays.stream(multiSearchResponse.getResponses())
        .map(MultiSearchResponse.Item::getResponse)
        .map(
            res ->
                Arrays.stream(res.getHits().getHits())
                    .map(AnalysisService::hitToAnalysis)
                    .findFirst())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(toUnmodifiableList());
  }

  @SneakyThrows
  public List<SampleMatchedAnalysisPair> getSampleMatchedAnalysesForDonor(
      @NonNull SampleMatchedAnalysesForDonorReq req) {
    val filter = new HashMap<String, Object>();
    filter.put(DONOR_ID, req.getDonorId());
    filter.put(ANALYSIS_STATE, PUBLISHED);
    // fetch donor normal samples and use those to fetch tumour later
    filter.put(TUMOUR_NORMAL_DESIGNATION, NORMAL);
    if (req.getAnalysisType() != null) {
      filter.put(ANALYSIS_TYPE, req.getAnalysisType());
    }
    if (req.getStudyId() != null) {
      filter.put(STUDY_ID, req.getStudyId());
    }
    if (req.getSampleType() != null) {
      filter.put(SAMPLE_TYPE, req.getSampleType());
    }
    return getAnalysesStream(filter, null)
        .flatMap(this::getSampleMatchedAnalysisPairs)
        .collect(toUnmodifiableList());
  }

  public List<SampleMatchedAnalysisPair> getSampleMatchedAnalysisPairs(String analysisId) {
    return getSampleMatchedAnalysisPairs(getAnalysisById(analysisId)).collect(toUnmodifiableList());
  }

  private Stream<SampleMatchedAnalysisPair> getSampleMatchedAnalysisPairs(Analysis analysis) {
    if (analysis == null || !analysis.getAnalysisState().equals(PUBLISHED)) {
      return empty();
    }

    val flattenedSamples = getFlattenedSamplesFromAnalysis(analysis);
    val experimentStrategy = analysis.getExperimentStrategy();

    // short circuit return if not enough data to find matched pairs for given analysis
    if (experimentStrategy == null || flattenedSamples.size() != 1) {
      return empty();
    }

    val flattenedSampleOfInterest = flattenedSamples.get(0);
    val tumourNormalDesignation = flattenedSampleOfInterest.getTumourNormalDesignation();

    val filter = ImmutableMap.<String, Object>builder();

    if (tumourNormalDesignation.equalsIgnoreCase(TUMOUR.toString())) {
      filter.put(
          SUBMITTER_SAMPLE_ID, flattenedSampleOfInterest.getMatchedNormalSubmitterSampleId());
    } else if (tumourNormalDesignation.equalsIgnoreCase(NORMAL.toString())) {
      filter.put(
          MATCHED_NORMAL_SUBMITTER_SAMPLE_ID, flattenedSampleOfInterest.getSubmitterSampleId());
    }

    filter.put(ANALYSIS_TYPE, analysis.getAnalysisType());
    filter.put(ANALYSIS_STATE, PUBLISHED.toString());
    filter.put(EXPERIMENT_STRATEGY, experimentStrategy);
    filter.put(STUDY_ID, analysis.getStudyId());
    filter.put(DONOR_ID, flattenedSampleOfInterest.getDonorId());
    filter.put(SAMPLE_TYPE, flattenedSampleOfInterest.getSampleType());

    return getAnalyses(filter.build(), null).stream()
        .map(
            a ->
                tumourNormalDesignation.equalsIgnoreCase(TUMOUR.toString())
                    ? new SampleMatchedAnalysisPair(a, analysis)
                    : new SampleMatchedAnalysisPair(analysis, a));
  }

  private List<FlatDonorSample> getFlattenedSamplesFromAnalysis(Analysis analysis) {
    return analysis.getDonors().stream()
        .flatMap(
            d ->
                d.getSpecimens().stream()
                    .flatMap(
                        sp -> {
                          val designation = sp.getTumourNormalDesignation();
                          return sp.getSamples().stream()
                              .map(sam -> new FlatDonorSample(d, sam, designation));
                        }))
        .collect(toUnmodifiableList());
  }

  @Value
  private static class FlatDonorSample {
    String donorId;
    String tumourNormalDesignation;
    String submitterSampleId;
    String matchedNormalSubmitterSampleId;
    String sampleType;

    FlatDonorSample(Donor donor, Sample sample, String tumourNormalDesignation) {
      this.donorId = donor.getDonorId();
      this.tumourNormalDesignation = tumourNormalDesignation;
      this.submitterSampleId = sample.getSubmitterSampleId();
      this.matchedNormalSubmitterSampleId = sample.getMatchedNormalSubmitterSampleId();
      this.sampleType = sample.getSampleType();
    }
  }

  @SneakyThrows
  public void sendAnalysisMessage(Analysis analysis) {
    val message = createAnalysisMessage(analysis, songServerId);
    System.out.println("Message payload: "+message);
    System.out.println("message after mapping: "+new ObjectMapper().writeValueAsString(message));
    sender.send(new ObjectMapper().writeValueAsString(message), message.getAnalysisId());
  }

}
