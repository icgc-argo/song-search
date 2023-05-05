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

import static graphql.Scalars.GraphQLLong;
import static graphql.scalars.ExtendedScalars.Json;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

import bio.overture.songsearch.config.websecurity.AuthProperties;
import bio.overture.songsearch.graphql.security.VerifyAuthQueryExecutionStrategyDecorator;
import bio.overture.songsearch.model.Analysis;
import bio.overture.songsearch.model.File;
import bio.overture.songsearch.model.Run;
import com.apollographql.federation.graphqljava.Federation;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import graphql.GraphQL;
import graphql.execution.AsyncExecutionStrategy;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import java.io.IOException;
import java.net.URL;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GraphQLProvider {

  private final AnalysisDataFetcher analysisDataFetcher;
  private final FileDataFetcher fileDataFetcher;
  private final EntityDataFetcher entityDataFetcher;

  private final StartAutomationMutation startAutomationMutation;
  private final AuthProperties authProperties;
  private GraphQL graphQL;
  private GraphQLSchema graphQLSchema;

  @Autowired
  public GraphQLProvider(
      AnalysisDataFetcher analysisDataFetcher,
      FileDataFetcher fileDataFetcher,
      EntityDataFetcher entityDataFetcher,
      StartAutomationMutation startAutomationMutation,
      AuthProperties authProperties) {
    this.analysisDataFetcher = analysisDataFetcher;
    this.fileDataFetcher = fileDataFetcher;
    this.entityDataFetcher = entityDataFetcher;
    this.startAutomationMutation = startAutomationMutation;
    this.authProperties = authProperties;
  }

  @Bean
  @Profile("!secure")
  public GraphQL graphQL() {
    return graphQL;
  }

  @Bean
  @Profile("secure")
  public GraphQL secureGraphQL() {
    return graphQL.transform(this::toSecureGraphql);
  }

  private void toSecureGraphql(GraphQL.Builder graphQLBuilder) {
    // For more info on `Execution Strategies` see:
    // https://www.graphql-java.com/documentation/v15/execution/
    graphQLBuilder.queryExecutionStrategy(
        new VerifyAuthQueryExecutionStrategyDecorator(
            new AsyncExecutionStrategy(), queryScopesToCheck()));
  }

  @PostConstruct
  public void init() throws IOException {
    URL url = Resources.getResource("schema.graphql");
    String sdl = Resources.toString(url, Charsets.UTF_8);
    graphQLSchema = buildSchema(sdl);
    this.graphQL = GraphQL.newGraphQL(graphQLSchema).build();
  }

  private GraphQLSchema buildSchema(String sdl) {
    return Federation.transform(sdl, buildWiring())
        .fetchEntities(entityDataFetcher.getDataFetcher())
        .resolveEntityType(
            typeResolutionEnvironment -> {
              final Object src = typeResolutionEnvironment.getObject();
              if (src instanceof Analysis) {
                return typeResolutionEnvironment
                    .getSchema()
                    .getObjectType(EntityDataFetcher.ANALYSIS_ENTITY);
              }
              if (src instanceof File) {
                return typeResolutionEnvironment
                    .getSchema()
                    .getObjectType(EntityDataFetcher.FILE_ENTITY);
              }
              if (src instanceof Run) {
                return typeResolutionEnvironment
                    .getSchema()
                    .getObjectType(EntityDataFetcher.RUN_ENTITY);
              }
              return null;
            })
        .build();
  }

  private RuntimeWiring buildWiring() {
    return RuntimeWiring.newRuntimeWiring()
        .scalar(GraphQLLong)
        .scalar(Json)
        .type(
            newTypeWiring("Query")
                .dataFetcher("analyses", analysisDataFetcher.getAnalysesDataFetcher()))
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "aggregateAnalyses", analysisDataFetcher.getAggregateAnalysesDataFetcher()))
        .type(newTypeWiring("Query").dataFetcher("files", fileDataFetcher.getFilesDataFetcher()))
        .type(
            newTypeWiring("Query")
                .dataFetcher("aggregateFiles", fileDataFetcher.getAggregateFilesDataFetcher()))
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "sampleMatchedAnalysisPairs",
                    analysisDataFetcher.getSampleMatchedAnalysisPairsFetcher()))
        .type(
            newTypeWiring("Query")
                .dataFetcher(
                    "sampleMatchedAnalysesForDonor",
                    analysisDataFetcher.getSampleMatchedAnalysesForDonorFetcher()))
        .type(
            newTypeWiring("Mutation")
                .dataFetcher("startAutomation", startAutomationMutation.startAutomationResolver()))
        .build();
  }

  private ImmutableList<String> queryScopesToCheck() {
    return ImmutableList.copyOf(
        Iterables.concat(
            authProperties.getGraphqlScopes().getQueryOnly(),
            authProperties.getGraphqlScopes().getQueryAndMutation()));
  }
}
