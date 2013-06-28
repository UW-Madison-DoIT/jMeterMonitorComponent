/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter.dao;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import edu.wisc.jmeter.HostStatus;
import edu.wisc.jmeter.Notification;
import edu.wisc.jmeter.Status;

/**
 * @author Eric Dalquist
 * @version $Revision: 1.8 $
 */
public class JdbcMonitorDao implements InitializingBean, DisposableBean, MonitorDao {
    private static final Logger log = LoggingManager.getLoggerForClass();
    
    private static final Map<String, String> TABLE_CONFIG;
    
    static {
        final Map<String, String> tableConfigBuilder = new HashMap<String, String>();
        
        
        tableConfigBuilder.put("MONITOR_HOST_STATUS", 
                "CREATE TABLE MONITOR_HOST_STATUS (\n" + 
                "    HOST_NAME VARCHAR2(500),\n" + 
                "    STATUS VARCHAR2(50) NOT NULL,\n" + 
                "    FAILURE_COUNT NUMBER,\n" + 
                "    MESSAGE_COUNT NUMBER,\n" + 
                "    LAST_NOTIFICATION TIMESTAMP,\n" + 
                "    LAST_UPDATED TIMESTAMP,\n" + 
                "    CONSTRAINT PK_MONITOR_HOST_STATUS PRIMARY KEY (HOST_NAME)\n" + 
                ")");
        
        tableConfigBuilder.put("MONITOR_LOG", 
                "CREATE TABLE MONITOR_LOG (\n" + 
                "    HOST_NAME VARCHAR2(500),\n" + 
                "    LABEL VARCHAR2(2000),\n" + 
                "    LAST_SAMPLE TIMESTAMP,\n" + 
                "    DURATION NUMBER,\n" +
                "    SUCCESS VARCHAR2(10),\n" + 
                "    CONSTRAINT PK_MONITOR_LOG PRIMARY KEY (HOST_NAME, LABEL)\n" + 
                ")");
        
        tableConfigBuilder.put("MONITOR_ERRORS", 
                "CREATE TABLE MONITOR_ERRORS (\n" + 
                "    HOST_NAME VARCHAR2(500),\n" + 
                "    LABEL VARCHAR2(2000),\n" + 
                "    FAILURE_DATE TIMESTAMP,\n" + 
                "    STATUS VARCHAR2(50),\n" + 
                "    EMAIL_SUBJECT VARCHAR2(1000),\n" + 
                "    EMAIL_BODY VARCHAR2(4000),\n" + 
                "    EMAIL_SENT VARCHAR2(10)\n" + 
                ")");
        
        TABLE_CONFIG = Collections.unmodifiableMap(tableConfigBuilder);
    }
    
    private final ConcurrentMap<String, Object> hostMutexMap = new ConcurrentHashMap<String, Object>();
    private final Map<String, HostStatus> hostStatusCache = new ConcurrentHashMap<String, HostStatus>();
    private Timer purgingTimer;
    
    //Purge times are in milliseconds
    private final long purgeStatusCache = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
    private final long purgeOldFailure;
    private final long purgeOldStatus;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    
    
    public JdbcMonitorDao(DataSource dataSource, int purgeOldFailures, int purgeOldStatus) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        
        final DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(dataSource);
        dataSourceTransactionManager.afterPropertiesSet();
        
        this.transactionTemplate = new TransactionTemplate(dataSourceTransactionManager);
        this.transactionTemplate.afterPropertiesSet();
        
        this.purgeOldFailure = TimeUnit.MILLISECONDS.convert(purgeOldFailures, TimeUnit.MINUTES);
        this.purgeOldStatus = TimeUnit.MILLISECONDS.convert(purgeOldStatus, TimeUnit.MINUTES);
    }
    
    public JdbcMonitorDao(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, int purgeOldFailures, int purgeOldStatus) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        
        this.purgeOldFailure = TimeUnit.MILLISECONDS.convert(purgeOldFailures, TimeUnit.MINUTES);
        this.purgeOldStatus = TimeUnit.MILLISECONDS.convert(purgeOldStatus, TimeUnit.MINUTES);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setupTables();
        
        this.purgingTimer = new Timer("JdbcMonitorDao_PurgingTimer", true);
        this.purgingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                purgeFailureLog(new Date(System.currentTimeMillis() - purgeOldFailure));
                purgeRequestLog(new Date(System.currentTimeMillis() - purgeOldStatus));
                purgeStatusCache(new Date(System.currentTimeMillis() - purgeStatusCache)); //HostStatus objects can be rebuilt, don't hold stuff older than 5 minutes
            }
        }, 
        1000 * 60, //Run 1 minute after starting 
        1000 * 60 * 5); //Repeat every 5 minutes
    }

    private void setupTables() {
        final JdbcOperations jdbcOperations = this.jdbcTemplate.getJdbcOperations();
        
        for (final Map.Entry<String, String> tableConfigEntry : TABLE_CONFIG.entrySet()) {
            jdbcOperations.execute(new ConnectionCallback<Object>() {
                @Override
                public Object doInConnection(Connection con) throws SQLException, DataAccessException {
                    final DatabaseMetaData metaData = con.getMetaData();
                    
                    final String tableName = tableConfigEntry.getKey();
                    final ResultSet tables = metaData.getTables(null, null, tableName, null);
                    try {
                        if (!tables.next()) {
                            log.warn("'" + tableName + "' table does not exist, creating.");
                            jdbcOperations.update(tableConfigEntry.getValue());
                        }
                        else {
                            log.info("'" + tableName + "' table already exists, skipping.");
                        }
                    }
                    finally {
                        tables.close();
                    }
                    
                    return null;
                }
            });
        }
    }
    
    @Override
    public void destroy() throws Exception {
        this.purgingTimer.cancel();
        this.purgingTimer = null;
    }
    
    @Override
    public void purgeStatusCache(final Date before) {
        int removedStatuses = 0;
        for (final Iterator<Map.Entry<String, HostStatus>> hostStatusIterator = hostStatusCache.entrySet().iterator(); hostStatusIterator.hasNext();) {
            final Entry<String, HostStatus> hostStatusEntry = hostStatusIterator.next();
            final HostStatus hostStatus = hostStatusEntry.getValue();
            if (hostStatus.getLastUpdated().before(before)) {
                hostMutexMap.remove(hostStatusEntry.getKey());
                hostStatusIterator.remove();
                removedStatuses++;
            }
        }
        if (removedStatuses > 0) {
            log.info("Purged " + removedStatuses + " HostStatus objects older than " + before + " from memory");
        }
    }

    @Override
    public void purgeRequestLog(final String host, final Date before) {
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("before", before);
        params.put("host", host);
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                final int purgedRequests = jdbcTemplate.update(
                        "DELETE FROM MONITOR_LOG " +
                        "WHERE HOST_NAME = :host AND LAST_SAMPLE < :before",
                        params);
                if (purgedRequests > 0) {
                    log.info("Purged " + purgedRequests + " requests for " + host + " older than " + before + " from database");
                }
            }
        });
    }

    @Override
    public void purgeRequestLog(final Date before) {
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("before", before);
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                final int purgedRequests = jdbcTemplate.update(
                        "DELETE FROM MONITOR_LOG " +
                        "WHERE LAST_SAMPLE < :before",
                        params);
                if (purgedRequests > 0) {
                    log.info("Purged " + purgedRequests + " requests older than " + before + " from database");
                }
                
                final int purgedStatuses = jdbcTemplate.update(
                        "DELETE FROM MONITOR_HOST_STATUS " +
                        "WHERE LAST_UPDATED < :before",
                        params);
                if (purgedStatuses > 0) {
                    log.info("Purged " + purgedStatuses + " statuses older than " + before + " from database");
                }
            }
        });
    }
    
    @Override
    public void purgeFailureLog(final Date before) {
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("before", before);
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                final int purged = jdbcTemplate.update(
                        "DELETE FROM MONITOR_ERRORS " +
                        "WHERE FAILURE_DATE < :before",
                        params);
                
                if (purged > 0) {
                    log.info("Purged " + purged + " failures older than " + before + " from database");
                }
            }
        });
    }

    @Override
    public HostStatus getHostStatus(final String hostName) {
        final Object lock = this.getHostLock(hostName);

        synchronized (lock) {
            HostStatus hostStatus = this.hostStatusCache.get(hostName);
            if (hostStatus != null) {
                return hostStatus;
            }
            
            final Map<String, Object> params = new LinkedHashMap<String, Object>();
            params.put("hostName", hostName);
            
            try {
                hostStatus = this.transactionTemplate.execute(new TransactionCallback<HostStatus>() {
                    @Override
                    public HostStatus doInTransaction(TransactionStatus transactionStatus) {
                        final List<HostStatus> results = jdbcTemplate.query(
                                "SELECT STATUS, FAILURE_COUNT, MESSAGE_COUNT, LAST_NOTIFICATION, LAST_UPDATED " +
                                "FROM MONITOR_HOST_STATUS " +
                                "WHERE HOST_NAME = :hostName", 
                                params,
                                new RowMapper<HostStatus>() {
                                    @Override
                                    public HostStatus mapRow(ResultSet rs, int row) throws SQLException {
                                        final HostStatus hostStatus = new HostStatus();
                                        
                                        hostStatus.setHost(hostName);
                                        hostStatus.setStatus(Status.valueOf(rs.getString("STATUS")));
                                        hostStatus.setFailureCount(rs.getInt("FAILURE_COUNT"));
                                        hostStatus.setMessageCount(rs.getInt("MESSAGE_COUNT"));
                                        hostStatus.setLastMessageSent(rs.getTimestamp("LAST_NOTIFICATION"));
                                        hostStatus.setLastUpdated(rs.getTimestamp("LAST_UPDATED"));
                                        
                                        return hostStatus;
                                    }
                                });
                        
                        HostStatus hostStatus = DataAccessUtils.singleResult(results);
                        if (hostStatus != null) {
                            return hostStatus;
                        }
                        
                        hostStatus = new HostStatus();
                        hostStatus.setHost(hostName);
                        hostStatus.setLastUpdated(new Date());
                        
                        params.put("status", hostStatus.getStatus().toString());
                        params.put("failureCount", hostStatus.getFailureCount());
                        params.put("messageCount", hostStatus.getMessageCount());
                        params.put("lastNotification", hostStatus.getLastMessageSent());
                        params.put("lastUpdated", hostStatus.getLastUpdated());
                        
                        jdbcTemplate.update(
                                "INSERT INTO MONITOR_HOST_STATUS (HOST_NAME, STATUS, FAILURE_COUNT, MESSAGE_COUNT, LAST_NOTIFICATION, LAST_UPDATED) " +
                        		"VALUES (:hostName, :status, :failureCount, :messageCount, :lastNotification, :lastUpdated)", params);
    
                        return hostStatus;
                    }
                });
            }
            catch (RuntimeException re) {
                //Want things to still work if the database is broken so create an empty HostStatus to work with in memory only
                if (hostStatus == null) {
                    hostStatus = new HostStatus();
                    hostStatus.setHost(hostName);
                    hostStatus.setLastUpdated(new Date());
                }
                
                log.warn("Failed to retrieve/create HostStatus via database, using memory storage only", re);
            }
            
            this.hostStatusCache.put(hostName, hostStatus);
            return hostStatus;
        }
    }
    
    @Override
    public void storeHostStatus(HostStatus hostStatus) {
        hostStatus.setLastUpdated(new Date());
        
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("hostName", hostStatus.getHost());
        params.put("status", hostStatus.getStatus().toString());
        params.put("failureCount", hostStatus.getFailureCount());
        params.put("messageCount", hostStatus.getMessageCount());
        params.put("lastNotification", hostStatus.getLastMessageSent());
        params.put("lastUpdated", hostStatus.getLastUpdated());
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                jdbcTemplate.update(
                        "UPDATE MONITOR_HOST_STATUS " +
                        "SET " +
                            "STATUS = :status, " +
                            "FAILURE_COUNT = :failureCount, " +
                            "MESSAGE_COUNT = :messageCount, " +
                            "LAST_NOTIFICATION = :lastNotification," +
                            "LAST_UPDATED =  :lastUpdated " +
                        "WHERE HOST_NAME = :hostName",
                        params);
            }
        });
    }
    
    @Override
    public void logFailure(String hostName, String label, Date requestTimestamp, Status status, String subject, String body, Notification sentEmail) {
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("hostName", hostName);
        params.put("label", label);
        params.put("failureDate", requestTimestamp);
        params.put("status", status.toString());
        params.put("emailSubject", subject);
        params.put("emailBody", body);
        params.put("emailSent", sentEmail.toString());
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                jdbcTemplate.update(
                        "INSERT INTO MONITOR_ERRORS (HOST_NAME, LABEL, FAILURE_DATE, STATUS, EMAIL_SUBJECT, EMAIL_BODY, EMAIL_SENT) " +
                        "VALUES (:hostName, :label, :failureDate, :status, :emailSubject, :emailBody, :emailSent)", params);
            }
        });
    }

    @Override
    public void logRequest(String hostName, String label, Date requestTimestamp, long duration, boolean successful) {
        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("hostName", hostName);
        params.put("label", label);
        params.put("lastSample", requestTimestamp);
        params.put("successful", Boolean.toString(successful));
        params.put("duration", duration);
        
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                final int affected = jdbcTemplate.update(
                        "UPDATE MONITOR_LOG " +
                        "SET " +
                            "LAST_SAMPLE = :lastSample, " +
                            "DURATION = :duration, " +
                            "SUCCESS = :successful " +
                        "WHERE HOST_NAME = :hostName AND LABEL = :label",
                        params);
                
                if (affected == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO MONITOR_LOG (HOST_NAME, LABEL, LAST_SAMPLE, DURATION, SUCCESS) " +
                            "VALUES (:hostName, :label, :lastSample, :duration, :successful)", params);
                }
            }
        });
    }
    
    @Override
    public void logRequestAndStatus(final HostStatus hostStatus, final String label, final Date requestTimestamp, final long duration, final boolean successful) {
        this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                storeHostStatus(hostStatus);
                logRequest(hostStatus.getHost(), label, requestTimestamp, duration, successful);
            }
        });
    }
    
    @Override
    public void logFailureAndStatus(final HostStatus hostStatus, final String label, final Date requestTimestamp, final Status status, final String subject, final String body, final Notification sentEmail) {
        try {
            this.transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus transactionStatus) {
                    storeHostStatus(hostStatus);
                    logFailure(hostStatus.getHost(), label, requestTimestamp, status, subject, body, sentEmail);
                }
            });
        }
        catch (RuntimeException re) {
            log.warn("Failed to log failure and status to database", re);
        }
    }

    protected Object getHostLock(String hostName) {
        Object lock = this.hostMutexMap.get(hostName);
        if (lock == null) {
            lock = new Object();
            final Object existingLock = this.hostMutexMap.putIfAbsent(hostName, lock);
            if (existingLock != null) {
                //Another thread created the lock before us, use the _one_ instance from the Map
                return existingLock;
            }
        }
        return lock;
    }

    protected final void clearHostStatusCache() {
        this.hostStatusCache.clear();
    }
}
