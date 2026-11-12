package com.stockwise.api.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockwise.shared.api.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "stockwise.auth.refresh-cookie.secure=false",
        "stockwise.auth.refresh-cookie.same-site=Lax"
})
class HealthControllerTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointReportsServiceAsUp() {
        ResponseEntity<HealthResponse> response =
                restTemplate.getForEntity("/api/v1/health", HealthResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().service()).isEqualTo("stockwise-api");
    }
}
