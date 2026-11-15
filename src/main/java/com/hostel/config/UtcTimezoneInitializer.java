package com.hostel.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TimeZone;

/**
 * Ensures JVM default timezone is UTC before DataSource, Flyway, or Hibernate initialize,
 * matching hibernate.jdbc.time_zone=UTC across application boots and integration tests.
 */
public class UtcTimezoneInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}

