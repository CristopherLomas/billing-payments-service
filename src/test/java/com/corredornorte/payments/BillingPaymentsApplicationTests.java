package com.corredornorte.payments;

import com.corredornorte.payments.integration.ExternalBookingGateway;
import com.corredornorte.payments.integration.HttpExternalBookingGateway;
import com.corredornorte.payments.integration.MockExternalBookingGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class BillingPaymentsApplicationTests {

    @Nested
    @SpringBootTest
    @DisplayName("Pruebas en modo Mock (por defecto)")
    class MockModeTests {

        @Autowired
        private ExternalBookingGateway externalBookingGateway;

        @Test
        @DisplayName("Debe inyectar MockExternalBookingGateway cuando integration.mock es true")
        void contextLoadsAndInjectsMockGateway() {
            assertThat(externalBookingGateway)
                    .isNotNull()
                    .isInstanceOf(MockExternalBookingGateway.class);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "integration.mock=false")
    @DisplayName("Pruebas en modo HTTP Real")
    class HttpModeTests {

        @Autowired
        private ExternalBookingGateway externalBookingGateway;

        @Test
        @DisplayName("Debe inyectar HttpExternalBookingGateway cuando integration.mock es false")
        void contextLoadsAndInjectsHttpGateway() {
            assertThat(externalBookingGateway)
                    .isNotNull()
                    .isInstanceOf(HttpExternalBookingGateway.class);
        }
    }
}
