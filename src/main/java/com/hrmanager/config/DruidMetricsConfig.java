package com.hrmanager.config;

import com.alibaba.druid.pool.DruidDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DruidMetricsConfig {

    public DruidMetricsConfig(DataSource dataSource, MeterRegistry registry) {
        if (!(dataSource instanceof DruidDataSource)) {
            return;
        }
        DruidDataSource druid = (DruidDataSource) dataSource;
        Gauge.builder("druid.connections.active", druid, DruidDataSource::getActiveCount)
                .description("Active connections").register(registry);
        Gauge.builder("druid.connections.idle", druid, DruidDataSource::getPoolingCount)
                .description("Idle connections").register(registry);
        Gauge.builder("druid.connections.max", druid, DruidDataSource::getMaxActive)
                .description("Max connections").register(registry);
        Gauge.builder("druid.connections.pending", druid, DruidDataSource::getWaitThreadCount)
                .description("Threads waiting for a connection").register(registry);
        Gauge.builder("druid.connections.create", druid, DruidDataSource::getCreateCount)
                .description("Total created connections").register(registry);
        Gauge.builder("druid.connections.destroy", druid, DruidDataSource::getDestroyCount)
                .description("Total destroyed connections").register(registry);
    }
}
