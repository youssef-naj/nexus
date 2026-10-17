package com.l2c.nexus.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DelegatingDataSource;

@TestConfiguration(proxyBeanMethods = false)
public class DatabaseOutageTestConfiguration {

    private static final AtomicBoolean DOWN = new AtomicBoolean();

    public static void databaseDown() {
        DOWN.set(true);
    }

    public static void databaseUp() {
        DOWN.set(false);
    }

    /** Wraps the application's DataSource so every connection request can be made to fail. */
    @Bean
    static BeanPostProcessor outageSimulator() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource dataSource
                                && !(bean instanceof SwitchableDataSource)
                        ? new SwitchableDataSource(dataSource)
                        : bean;
            }
        };
    }

    private static final class SwitchableDataSource extends DelegatingDataSource
            implements AutoCloseable {

        SwitchableDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            failIfDown();
            return super.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            failIfDown();
            return super.getConnection(username, password);
        }

        private static void failIfDown() throws SQLException {
            if (DOWN.get()) {
                throw new SQLTransientConnectionException(
                        "Connection is not available, request timed out after 5000ms.", "08001");
            }
        }

        /** Closes the real pool when the application context shuts down. */
        @Override
        public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
