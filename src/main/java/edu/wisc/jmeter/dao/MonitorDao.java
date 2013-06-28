package edu.wisc.jmeter.dao;

import java.util.Date;

import edu.wisc.jmeter.HostStatus;
import edu.wisc.jmeter.Notification;
import edu.wisc.jmeter.Status;

public interface MonitorDao {

    void purgeStatusCache(Date before);

    void purgeRequestLog(String host, Date before);

    void purgeRequestLog(Date before);

    void purgeFailureLog(Date before);

    HostStatus getHostStatus(String hostName);

    void storeHostStatus(HostStatus hostStatus);

    void logFailure(String hostName, String label, Date requestTimestamp, Status status, String subject, String body,
            Notification sentEmail);

    void logRequest(String hostName, String label, Date requestTimestamp, long duration, boolean successful);

    void logRequestAndStatus(HostStatus hostStatus, String label, Date requestTimestamp, long duration,
            boolean successful);

    void logFailureAndStatus(HostStatus hostStatus, String label, Date requestTimestamp, Status status, String subject,
            String body, Notification sentEmail);

}