package edu.wisc.jmeter.dao;

import java.util.Date;

import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import edu.wisc.jmeter.HostStatus;
import edu.wisc.jmeter.Notification;
import edu.wisc.jmeter.Status;

/**
 * Wraps another {@link MonitorDao} logging all exceptions
 * 
 * @author Eric Dalquist
 */
public class ErrorHandlingMonitorDao implements MonitorDao {
    private static final Logger log = LoggingManager.getLoggerForClass();
    private final MonitorDao monitorDao;

    public ErrorHandlingMonitorDao(MonitorDao monitorDao) {
        this.monitorDao = monitorDao;
    }

    @Override
    public void purgeStatusCache(Date before) {
        try {
            this.monitorDao.purgeStatusCache(before);
        }
        catch (RuntimeException re) {
            log.warn("Failed to purge status cache", re);
        }
    }

    @Override
    public void purgeRequestLog(String host, Date before) {
        try {
            this.monitorDao.purgeRequestLog(host, before);
        }
        catch (RuntimeException re) {
            log.warn("Failed to purge request log database", re);
        }
    }

    @Override
    public void purgeRequestLog(Date before) {
        try {
            this.monitorDao.purgeRequestLog(before);
        }
        catch (RuntimeException re) {
            log.warn("Failed to purge request log database", re);
        }
    }

    @Override
    public void purgeFailureLog(Date before) {
        try {
            this.monitorDao.purgeFailureLog(before);
        }
        catch (RuntimeException re) {
            log.warn("Failed to purge failure log database", re);
        }
    }

    @Override
    public HostStatus getHostStatus(String hostName) {
        try {
            return this.monitorDao.getHostStatus(hostName);
        }
        catch (RuntimeException re) {
            //Want things to still work if the database is broken so create an empty HostStatus to work with in memory only
            final HostStatus hostStatus = new HostStatus();
            hostStatus.setHost(hostName);
            hostStatus.setLastUpdated(new Date());
            
            log.warn("Failed to retrieve/create HostStatus via database, using memory storage only", re);
            
            return hostStatus;
        }
    }

    @Override
    public void storeHostStatus(HostStatus hostStatus) {
        try {
            this.monitorDao.storeHostStatus(hostStatus);
        }
        catch (RuntimeException re) {
            log.warn("Failed to persist HostStatus via database, using memory storage only", re);
        }
    }

    @Override
    public void logFailure(String hostName, String label, Date requestTimestamp, Status status, String subject,
            String body, Notification sentEmail) {
        try {
            this.monitorDao.logFailure(hostName, label, requestTimestamp, status, subject, body, sentEmail);
        }
        catch (RuntimeException re) {
            log.warn("Failed to log failure to database", re);
        }
    }

    @Override
    public void logRequest(String hostName, String label, Date requestTimestamp, long duration, boolean successful) {
        try {
            this.monitorDao.logRequest(hostName, label, requestTimestamp, duration, successful);
        }
        catch (RuntimeException re) {
            log.warn("Failed to log request to database", re);
        }
    }

    @Override
    public void logRequestAndStatus(HostStatus hostStatus, String label, Date requestTimestamp, long duration,
            boolean successful) {
        try {
            this.monitorDao.logRequestAndStatus(hostStatus, label, requestTimestamp, duration, successful);
        }
        catch (RuntimeException re) {
            log.warn("Failed to log request and store status to database", re);
        }
    }

    @Override
    public void logFailureAndStatus(HostStatus hostStatus, String label, Date requestTimestamp, Status status,
            String subject, String body, Notification sentEmail) {
        try {
            this.monitorDao.logFailureAndStatus(hostStatus, label, requestTimestamp, status, subject, body, sentEmail);
        }
        catch (RuntimeException re) {
            log.warn("Failed to log request and store failure status to database", re);
        }
    }

}
