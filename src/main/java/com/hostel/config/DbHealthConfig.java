package com.hostel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DbHealthConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DbHealthConfig.class);
    private final DataSource dataSource;

    public DbHealthConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            if (!rs.next()) {
                throw new IllegalStateException("Database health probe returned no result");
            }
            log.info("Startup DB health check PASSED (URL={}, timezone=UTC)", conn.getMetaData().getURL());
        } catch (Exception ex) {
            String msg = "FATAL: Database unreachable during startup health check: " + ex.getMessage();
            log.error(msg, ex);
            throw new IllegalStateException(msg, ex);
        }
    }
}

