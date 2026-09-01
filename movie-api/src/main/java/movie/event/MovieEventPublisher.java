package movie.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MovieEventPublisher {

    public static final String TOPIC = "movie-events";

    private final KafkaTemplate<String, MovieEvent> kafkaTemplate;

    public MovieEventPublisher(KafkaTemplate<String, MovieEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(MovieEvent event) {
        kafkaTemplate.send(TOPIC, event.key(), event);
    }
}
