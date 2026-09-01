package auth.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthEventPublisher {

    public static final String TOPIC = "auth-events";

    private final KafkaTemplate<String, AuthEvent> kafkaTemplate;

    public AuthEventPublisher(KafkaTemplate<String, AuthEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(AuthEvent event) {
        kafkaTemplate.send(TOPIC, event.key(), event);
    }
}
