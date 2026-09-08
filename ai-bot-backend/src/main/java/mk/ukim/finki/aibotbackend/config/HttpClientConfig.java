package mk.ukim.finki.aibotbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/** Timeouts shared by the LLM and Vezilka clients, bound from {@code http.client.*}. */
@Configuration
public class HttpClientConfig {
    @Bean
    public ClientHttpRequestFactory outboundRequestFactory(
        @Value("${http.client.connect-timeout-ms:10000}") int connectTimeoutMillis,
        @Value("${http.client.read-timeout-ms:60000}") int readTimeoutMillis) {
        return requestFactory(connectTimeoutMillis, readTimeoutMillis);
    }

    public static ClientHttpRequestFactory requestFactory(int connectTimeoutMillis, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        return requestFactory;
    }
}
