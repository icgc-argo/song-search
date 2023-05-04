package bio.overture.songsearch.config.kafka;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultSender implements Sender{

  public void send(String payload, String key) {
    log.debug("key: "+key);
  }
}
