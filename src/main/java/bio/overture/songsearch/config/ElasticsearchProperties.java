package bio.overture.songsearch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "elastic")
public class ElasticsearchProperties {
  String host;
  Integer port;
  Boolean useHttps;
  Boolean useAuthentication;
  String username;
  String password;
  String analysisCentricIndex;
  String fileCentricIndex;
}
