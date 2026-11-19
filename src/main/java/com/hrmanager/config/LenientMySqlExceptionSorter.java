package com.hrmanager.config;

import com.alibaba.druid.pool.ExceptionSorter;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;

import java.sql.SQLException;
import java.util.Properties;

public class LenientMySqlExceptionSorter implements ExceptionSorter {

    private final MySqlExceptionSorter delegate = new MySqlExceptionSorter();

    @Override
    public boolean isExceptionFatal(SQLException e) {
        int code = e.getErrorCode();
        if (code == 1142 || code == 1227) {
            return false;
        }
        return delegate.isExceptionFatal(e);
    }

    @Override
    public void configFromProperties(Properties properties) {
        delegate.configFromProperties(properties);
    }
}
