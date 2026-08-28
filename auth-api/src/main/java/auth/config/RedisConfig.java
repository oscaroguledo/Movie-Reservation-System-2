package auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory, Jackson2ObjectMapperBuilder jacksonBuilder) {
        // A fresh mapper (same modules as the app's, e.g. JavaTimeModule)
        // rather than the shared web ObjectMapper: default typing has to
        // be activated for GenericJackson2JsonRedisSerializer to embed
        // "@class" and reconstruct the right type on read, and doing that
        // on the shared bean would leak "@class" into unrelated JSON
        // (e.g. HTTP responses).
        ObjectMapper redisObjectMapper = jacksonBuilder.build();
        redisObjectMapper.activateDefaultTyping(
                redisObjectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper));
        template.afterPropertiesSet();
        return template;
    }
}
