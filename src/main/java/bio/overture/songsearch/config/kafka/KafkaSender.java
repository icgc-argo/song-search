package bio.overture.songsearch.config.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;

@Slf4j
@Profile("kafka")
public class KafkaSender implements Sender {
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;

  public void send(String payload, String key) {
    log.debug("sending payload='{}' to topic='{}'", payload, kafkaTemplate.getDefaultTopic());
    kafkaTemplate.send(kafkaTemplate.getDefaultTopic(), key, payload);
  }
}
