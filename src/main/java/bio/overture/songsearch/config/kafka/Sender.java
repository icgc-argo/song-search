package bio.overture.songsearch.config.kafka;

public interface Sender {
  void send(String payload, String key);
}
