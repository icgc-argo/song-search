package bio.overture.songsearch.config.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
public class KafkaSender {
  @Autowired
  private KafkaTemplate<String, String> kafkaTemplate;

  public void send(String payload, String key) {
    log.debug("sending payload='{}' to topic='{}'", payload, kafkaTemplate.getDefaultTopic());
    kafkaTemplate.send(kafkaTemplate.getDefaultTopic(), key, payload);
  }
}
