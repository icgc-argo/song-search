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

import static bio.overture.songsearch.config.SearchFields.*;
import static bio.overture.songsearch.model.enums.SpecimenType.NORMAL;
import static bio.overture.songsearch.model.enums.SpecimenType.TUMOUR;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import bio.overture.songsearch.model.Analysis;
import bio.overture.songsearch.model.Sample;
import bio.overture.songsearch.model.SampleMatchedAnalysisPair;
import bio.overture.songsearch.repository.AnalysisRepository;
import com.google.common.collect.ImmutableMap;
import java.util.*;
import lombok.Value;
import lombok.val;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

  private final AnalysisRepository analysisRepository;

  @Autowired
  public AnalysisService(AnalysisRepository analysisRepository) {
    this.analysisRepository = analysisRepository;
  }

  private static Analysis hitToAnalysis(SearchHit hit) {
    val sourceMap = hit.getSourceAsMap();
    return Analysis.parse(sourceMap);
  }

  public List<Analysis> getAnalyses(Map<String, Object> filter, Map<String, Integer> page) {
    val response = analysisRepository.getAnalyses(filter, page);
    val hitStream = Arrays.stream(response.getHits().getHits());
    return hitStream.map(AnalysisService::hitToAnalysis).collect(toUnmodifiableList());
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

  public List<Analysis> getAnalysesById(List<String> analysisIds) {
    val multipleFilters =
        analysisIds.stream().map(id -> Map.of(ANALYSIS_ID, (Object) id)).collect(toList());
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

  public List<SampleMatchedAnalysisPair> getSampleMatchedAnalysisPairs(String analysisId) {
    val analysisFromId = getAnalysisById(analysisId);
    val flattenedSamples = getFlattenedSamplesFromAnalysis(analysisFromId);
    val experimentalStrategy = analysisFromId.getExperiment().get("experimental_strategy");

    // short circuit return if can't find sample matched pairs for analysisFromId
    if (experimentalStrategy == null || flattenedSamples.size() != 1) {
      return emptyList();
    }

    val flattenedSampleOfInterest = flattenedSamples.get(0);
    val tumourNormalDesignation = flattenedSampleOfInterest.getTumourNormalDesignation();

    val filter = ImmutableMap.<String, Object>builder();

    if (flattenedSampleOfInterest
        .getTumourNormalDesignation()
        .equalsIgnoreCase(TUMOUR.toString())) {
      filter.put(
          SUBMITTER_SAMPLE_ID, flattenedSampleOfInterest.getMatchedNormalSubmitterSampleId());
    } else if (flattenedSampleOfInterest
        .getTumourNormalDesignation()
        .equalsIgnoreCase(NORMAL.toString())) {
      filter.put(
          MATCHED_NORMAL_SUBMITTER_SAMPLE_ID, flattenedSampleOfInterest.getSubmitterSampleId());
    }

    filter.put(ANALYSIS_TYPE, analysisFromId.getAnalysisType());
    filter.put("experiment.experimental_strategy", experimentalStrategy);

    return getAnalyses(filter.build(), null).stream()
        .map(
            a ->
                tumourNormalDesignation.equalsIgnoreCase(TUMOUR.toString())
                    ? new SampleMatchedAnalysisPair(a, analysisFromId)
                    : new SampleMatchedAnalysisPair(analysisFromId, a))
        .collect(toUnmodifiableList());
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
                              .map(sam -> new FlatDonorSample(sam, designation));
                        }))
        .collect(toUnmodifiableList());
  }

  @Value
  static class FlatDonorSample {
    String tumourNormalDesignation;
    String submitterSampleId;
    String matchedNormalSubmitterSampleId;

    FlatDonorSample(Sample sample, String tumourNormalDesignation) {
      this.tumourNormalDesignation = tumourNormalDesignation;
      this.submitterSampleId = sample.getSubmitterSampleId();
      this.matchedNormalSubmitterSampleId = sample.getMatchedNormalSubmitterSampleId();
    }
  }
}
