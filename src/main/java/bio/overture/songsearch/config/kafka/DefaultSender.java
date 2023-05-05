package bio.overture.songsearch.config.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultSender implements Sender {

  public void send(String payload, String key) {
    log.debug("key: " + key);
  }
}
