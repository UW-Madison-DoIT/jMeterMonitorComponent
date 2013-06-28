/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.engine.event.LoopIterationEvent;
import org.apache.jmeter.gui.UnsharedComponent;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestListener;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.visualizers.Visualizer;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import edu.wisc.jmeter.dao.ErrorHandlingMonitorDao;
import edu.wisc.jmeter.dao.JdbcMonitorDao;
import edu.wisc.jmeter.dao.MonitorDao;

/**
 * @author Eric Dalquist
 * @version $Revision: 1.11 $
 */
public class MonitorListener extends AbstractTestElement implements SampleListener, TestListener, TestBean, Visualizer, UnsharedComponent {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggingManager.getLoggerForClass();
    
    public static final int DEFAULT_PURGE_OLD_FAILURE = 60 * 24 * 7; //default to 1 week
    public static final int DEFAULT_PURGE_OLD_STATUS  = 60 * 24; //default to 1 day
    
    private final SimpleDateFormat RESPONSE_FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd.HHmmss.SSS");
    
    private String statusVar; //Name of the variable used to communicate server status
    private Pattern statusSamplePattern; //Regex pattern used to identifiy samples of server status flags
    private Pattern monitoredSamplePattern; //Regex pattern used to identifiy samples to be monitored
    
    private String logLocation; //Failed responses are saved here
    
    //Email Notification Settings
    private String notificationVar; //Name of the variable used to communicate if notification should be performed
    private int failureThreshold; //Number of failures needed to mark a machine as down
    private int backoffDuration; //Minutes for spacing between notifications, exponential backoff is used
    private String smtpHost;
    private String emailTo;
    private String emailFrom;
    
    // Database logging settings
    private String jdbcDriver;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPass;
    
    // Purging times in minutes
    private int purgeOldFailure = DEFAULT_PURGE_OLD_FAILURE;
    private int purgeOldStatus = DEFAULT_PURGE_OLD_STATUS;

    private DataSource connectionPool;
    private JdbcMonitorDao jdbcMonitorDao;
    private MonitorDao monitorDao;
    private JavaMailSender javaMailSender;
    
    public MonitorListener() {
        log.info("Created MonitorListener");
    }
    
    @Override
    public Object clone() {
        log.info("Cloned MonitorListener");
        final MonitorListener clone = (MonitorListener)super.clone();
        
        clone.connectionPool = connectionPool;
        clone.monitorDao = monitorDao;
        clone.javaMailSender = javaMailSender;
        
        return clone;
    }
    public String getStatusSamplePattern() {
        if (statusSamplePattern == null) {
            return null;
        }
        return statusSamplePattern.pattern();
    }
    public void setStatusSamplePattern(String statusSamplePattern) {
        if (statusSamplePattern == null) {
            this.statusSamplePattern = null;
        }
        this.statusSamplePattern = Pattern.compile(statusSamplePattern);
    }
    public String getMonitoredSamplePattern() {
        if (monitoredSamplePattern == null) {
            return null;
        }
        return monitoredSamplePattern.pattern();
    }
    public void setMonitoredSamplePattern(String monitoredSamplePattern) {
        if (monitoredSamplePattern == null) {
            this.monitoredSamplePattern = null;
        }
        this.monitoredSamplePattern = Pattern.compile(monitoredSamplePattern);
    }
    public String getStatusVar() {
        return statusVar;
    }
    public void setStatusVar(String statusVar) {
        this.statusVar = statusVar;
    }
    public String getLogLocation() {
        return logLocation;
    }
    public void setLogLocation(String logLocation) {
        this.logLocation = logLocation;
    }
    public String getNotificationVar() {
        return notificationVar;
    }
    public void setNotificationVar(String notificationVar) {
        this.notificationVar = notificationVar;
    }
    public int getFailureThreshold() {
        return failureThreshold;
    }
    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }
    public int getBackoffDuration() {
        return backoffDuration;
    }
    public void setBackoffDuration(int backoffDuration) {
        this.backoffDuration = backoffDuration;
    }
    public String getSmtpHost() {
        return smtpHost;
    }
    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }
    public String getEmailTo() {
        return emailTo;
    }
    public void setEmailTo(String emailTo) {
        this.emailTo = emailTo;
    }
    public String getEmailFrom() {
        return emailFrom;
    }
    public void setEmailFrom(String emailFrom) {
        this.emailFrom = emailFrom;
    }
    public String getJdbcDriver() {
        return jdbcDriver;
    }
    public void setJdbcDriver(String jdbcDriver) {
        this.jdbcDriver = jdbcDriver;
    }
    public String getJdbcUrl() {
        return jdbcUrl;
    }
    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }
    public String getJdbcUser() {
        return jdbcUser;
    }
    public void setJdbcUser(String jdbcUser) {
        this.jdbcUser = jdbcUser;
    }
    public String getJdbcPass() {
        return jdbcPass;
    }
    public void setJdbcPass(String jdbcPass) {
        this.jdbcPass = jdbcPass;
    }
    public int getPurgeOldFailure() {
        return purgeOldFailure;
    }
    public void setPurgeOldFailure(int purgeOldFailures) {
        this.purgeOldFailure = purgeOldFailures;
    }
    public int getPurgeOldStatus() {
        return purgeOldStatus;
    }
    public void setPurgeOldStatus(int purgeOldStatus) {
        this.purgeOldStatus = purgeOldStatus;
    }

    @Override
    public void testStarted() {
        this.connectionPool = new DataSource();
        this.connectionPool.setDriverClassName(this.jdbcDriver);
        this.connectionPool.setUrl(this.jdbcUrl);
        this.connectionPool.setUsername(this.jdbcUser);
        this.connectionPool.setPassword(this.jdbcPass);
        this.connectionPool.setValidationQuery("SELECT 1 FROM DUAL");
        this.connectionPool.setTestOnBorrow(true);
        this.connectionPool.setTestWhileIdle(true);
        this.connectionPool.setJdbcInterceptors("ConnectionState(useEquals=true);ResetAbandonedTimer");
        
        log.info("Created DB pool for: {" + this.jdbcDriver + ", " + this.jdbcUrl + ", " + this.jdbcUser + "}");
        
        this.jdbcMonitorDao = new JdbcMonitorDao(this.connectionPool, this.purgeOldFailure, this.purgeOldStatus);
        try {
            this.jdbcMonitorDao.afterPropertiesSet();
            this.monitorDao = new ErrorHandlingMonitorDao(this.jdbcMonitorDao);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to initialize JdbcMonitorDao", e);
        }
        log.info("Created JdbcMonitorDao");
        
        final JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(this.smtpHost);
        this.javaMailSender = mailSender;
        log.info("Created JavaMailSender for: {" + this.smtpHost + "}");
    }
    
    @Override
    public void testIterationStart(LoopIterationEvent event) {
        final JMeterContext jmctx = JMeterContextService.getContext();
        final JMeterVariables vars = jmctx.getVariables();
        
        IterationInfo iterationInfo = (IterationInfo)vars.getObject(IterationInfo.class.getName());
        if (iterationInfo == null) {
            iterationInfo = new IterationInfo();
            vars.putObject(IterationInfo.class.getName(), iterationInfo);
        }
        
        final Date previousStart = iterationInfo.getPreviousStart();
        if (previousStart != null) {
            for (final String host : iterationInfo.getPreviousVisitedHosts()) {
                this.monitorDao.purgeRequestLog(host, previousStart);
            }
        }
        
        iterationInfo.iterate();
    }

    @Override
    public void sampleOccurred(SampleEvent e) {
        final JMeterContext jmctx = JMeterContextService.getContext();
        final JMeterVariables vars = jmctx.getVariables();

        final SampleResult result = e.getResult();
        
        final String statusStr = vars.get(this.statusVar);
        final Status checkStatus;
        if (statusStr != null) {
            checkStatus = Status.valueOf(statusStr);
        }
        else {
            checkStatus = Status.UNKOWN;
        }
        
        switch (checkStatus) {
            case UP: {
                this.checkLastSample(result, vars, checkStatus);
            } break;
            case OUT_UP: {
                this.checkLastSample(result, vars, checkStatus);
            } break;
            case OUT_DOWN: {
                final String sampleLabel = result.getSampleLabel();
                if (this.statusSamplePattern != null && !this.statusSamplePattern.matcher(sampleLabel).matches()) {
                    //Request is not status sample, ignore it
                    return;
                }

                //Out of cluster and tomcat down, set the status if not already set
                final String hostName = this.getSampleTargetHost(result);
                final HostStatus hostStatus = this.monitorDao.getHostStatus(hostName);
                if (Status.OUT_DOWN != hostStatus.getStatus()) {
                    hostStatus.setStatus(Status.OUT_DOWN);
                    this.monitorDao.storeHostStatus(hostStatus);
                }
            } break;
            default: {
                this.checkLastSample(result, vars, checkStatus);
            }
        }
    }

    @Override
    public void sampleStarted(SampleEvent e) {
    }
    @Override
    public void sampleStopped(SampleEvent e) {
    }
    @Override
    public void add(SampleResult sample) {
    }
    @Override
    public boolean isStats() {
        return false;
    }
    
    @Override
    public void testEnded() {
        final DataSource pool = this.connectionPool;
        this.connectionPool = null;
        if (pool != null) {
            pool.close();
            log.info("Closed data pool");
        }
        
        try {
            this.jdbcMonitorDao.destroy();
        }
        catch (Exception e) {
            log.info("Failed to close monitor dao", e);
        }
        this.monitorDao = null;
        
        this.javaMailSender = null;
    }
    
    

    @Override
    public void testStarted(String host) {
        this.testStarted();
    }

    @Override
    public void testEnded(String host) {
        this.testEnded();
    }
    
    
    
    private void checkLastSample(SampleResult result, JMeterVariables vars, Status checkStatus) {
        final String sampleLabel = result.getSampleLabel();
        if (this.monitoredSamplePattern != null && !this.monitoredSamplePattern.matcher(sampleLabel).matches()) {
            //Request is not monitored, ignore it
            return;
        }
        
        final Date sampleEndTime = new Date(result.getEndTime());
        final boolean lastSampleOk = result.isSuccessful();
        
        final String hostName = this.getSampleTargetHost(result);
        this.trackHost(vars, hostName);
        final HostStatus hostStatus = this.monitorDao.getHostStatus(hostName);
        
        int messageCount = hostStatus.getMessageCount();
        if (lastSampleOk) {
            if (hostStatus.getFailureCount() > 0) {
                final String messageSubject = buildMessageSubject(hostName, Status.UP, 0, 0);
                final String messageBody    = buildMessageBody(sampleEndTime, hostName, sampleLabel, Status.UP, 0, 0, null);
                Notification sentEmail = Notification.FALSE;

                //Only send up message if down message has been sent
                if (messageCount > 0) {
                    hostStatus.setLastMessageSent(new Date());
                    if (notifyForHost(vars)) {
                        sendEmail(sampleEndTime, messageSubject, messageBody, hostName, Status.UP);
                        sentEmail = Notification.TRUE;
                    }
                    else {
                        sentEmail = Notification.DISABLED;
                    }
                }
                
                hostStatus.setFailureCount(0);
                hostStatus.setMessageCount(0);
                hostStatus.setStatus(checkStatus.isOut() ? Status.OUT_UP : Status.UP);

                //Log the clearing of the failure to the DB
                this.monitorDao.logFailureAndStatus(hostStatus, sampleLabel, sampleEndTime, hostStatus.getStatus(), messageSubject, messageBody, sentEmail);
            }
            else if ((checkStatus.isOut() && hostStatus.getStatus() != Status.OUT_UP) ||
                     (!checkStatus.isOut() && hostStatus.getStatus() != Status.UP)) {
                
                final Status oldStatus = hostStatus.getStatus();
                
                //Update the HostStatus with the correct status
                hostStatus.setStatus(checkStatus.isOut() ? Status.OUT_UP : Status.UP);
                this.monitorDao.storeHostStatus(hostStatus);
                
                log.info("Switching HostStatus.status from " + oldStatus + " to " + hostStatus.getStatus() + " for " + sampleLabel);
            }
        }
        else {
            //Failed request, increment the counter
            final int failureCount = hostStatus.incrementFailureCount();
            
            //Handle the server being checked while still coming up
            hostStatus.setStatus(checkStatus.isOut() ? Status.OUT_DOWN : Status.DOWN);

            final String errorMessages = getErrorMessages(result);

            //Setup default messages
            String messageSubject = buildMessageSubject(hostName, hostStatus.getStatus(), messageCount, failureCount);
            String messageBody = buildMessageBody(sampleEndTime, hostName, sampleLabel, hostStatus.getStatus(), messageCount, failureCount, errorMessages);
            Notification sentEmail = Notification.FALSE;

            //Don't email when server is out
            //Ignore single failure counts to avoid making noise about transient failures
            if (!checkStatus.isOut() && failureCount >= failureThreshold) {
                //If not the first message use an exponential roll off send messages after a certain ammount of time
                Date refDate = sampleEndTime;
                if (messageCount > 0) {
                    int minutesToMessage = (int)Math.pow(2, Math.max(messageCount - 1, 0)) * backoffDuration;
                    Calendar refCal = Calendar.getInstance();
                    refCal.setTime(hostStatus.getLastMessageSent());
                    refCal.add(Calendar.MINUTE, minutesToMessage);
                    refDate = refCal.getTime();
                }

                //If a message hasn't been sent yet or if enough time has passed since the last failure message
                if (messageCount == 0 || sampleEndTime.after(refDate)) {
                    //Count last-sent and count even if message sending is disabled to keep both paths of behavior very similar
                    hostStatus.setLastMessageSent(new Date());
                    messageCount = hostStatus.incrementMessageCount();
                    
                    if (notifyForHost(vars)) {
                        //Update messages with new messageCount
                        messageSubject = buildMessageSubject(hostName, hostStatus.getStatus(), messageCount, failureCount);
                        messageBody = buildMessageBody(sampleEndTime, hostName, sampleLabel, hostStatus.getStatus(), messageCount, failureCount, errorMessages);
    
                        sendEmail(sampleEndTime, messageSubject, messageBody, hostName, hostStatus.getStatus());
                        sentEmail = Notification.TRUE;
                    }
                    else {
                        sentEmail = Notification.DISABLED;
                    }
                }
            }
            
            //Log the failure to the DB
            this.monitorDao.logFailureAndStatus(hostStatus, sampleLabel, sampleEndTime, hostStatus.getStatus(), messageSubject, messageBody, sentEmail);

            //Save the data for every failure, post processing so we get updated counts
            final String userId = vars.get("userId");
            saveResponseToFile(result, sampleEndTime, userId, hostName, errorMessages, failureCount, messageCount);
        }
        
        this.monitorDao.logRequestAndStatus(hostStatus, sampleLabel, sampleEndTime, result.getTime(), lastSampleOk);
    }

    /**
     * Generate the email subject string
     */
    private String buildMessageSubject(String hostName, Status status, int messageCount, int failureCount) {
        final StringBuilder subject = new StringBuilder("myUwMonitor: ");
        
        subject.append(hostName).append(" ").append(status);
        
        if (Status.DOWN.equals(status)) {
            subject.append(" (fc=").append(failureCount).append(", mc=").append(messageCount).append(")");
        }
        
        return subject.toString();
    }
    
    /**
     * Generate the email body string
     */
    private String buildMessageBody(Date sampleEndTime, String hostName, String label, Status status, int messageCount, int failureCount, String errorMessages) {
        final StringBuilder body = new StringBuilder();
        body.append(sampleEndTime).append(": myUwMonitor: ").append(hostName)
            .append(" (").append(label).append(" )");
        
        
        if (status.isUp()) {
            body.append(" ").append(status);
        }
        else {
            body.append(" (failureCount=").append(failureCount).append(", messageCount=").append(messageCount).append(")\n")
                .append(errorMessages);
        }
        
        return body.toString();
    }

    /**
     * Executes a shell script to send an email.
     */
    private void sendEmail(Date now, String subject, String body, String host, Status status) {
        log("Sending email (" + status + "): " + subject + " - " + body);

        final SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailTo);
        message.setFrom(emailFrom);
        message.setSubject(subject);
        message.setText(body);

        try {
            this.javaMailSender.send(message);
        }
        catch (MailException me) {
            log("Failed to send email", me);
        }
    }
    
    /**
     * Builds a String of messages from the errors that occured on the request.
     */
    private String getErrorMessages(SampleResult sampleResult) {
        final StringBuilder failureMessage = new StringBuilder();

        if (!sampleResult.getResponseCode().equals("200")) {
            failureMessage.append("Response code was '");
            failureMessage.append(sampleResult.getResponseCode());
            failureMessage.append("' - '");
            failureMessage.append(sampleResult.getResponseMessage());
            failureMessage.append("', ");
        }
        else {
            final AssertionResult[] assertionResults = sampleResult.getAssertionResults();
            for (final AssertionResult assertionResult : assertionResults) {
                if (assertionResult.isError() || assertionResult.isFailure()) {
                    failureMessage.append("Assertion ");
                    if (assertionResult.isError()) {
                        failureMessage.append("error");
                    }
                    else {
                        failureMessage.append("failure");
                    }
                    failureMessage.append(": '");

                    failureMessage.append(assertionResult.getFailureMessage());
                    failureMessage.append("', ");
                }
            }
        }

        //Remove the trailing , if the string is long enough
        int messageLength = failureMessage.length();
        if (messageLength > 2) {
            failureMessage.delete(messageLength - 2, messageLength);
        }

        return failureMessage.toString();
    }
    
    /**
     * Saves data from the last response to a file
     */
    private void saveResponseToFile(SampleResult sampleResult, Date now, String userId, String hostName, String errorMessages, int errorCount, int messageCount) {
        final String formatedDate;
        synchronized (RESPONSE_FILE_DATE_FORMAT) {
            formatedDate = RESPONSE_FILE_DATE_FORMAT.format(now);
        }
        final File responseFile = new File(logLocation, formatedDate + "." + hostName + ".response");

        PrintStream ps = null;
        try {
            ps = new PrintStream(responseFile);

            final String respHeaders = sampleResult.getResponseHeaders();
            final String respData = sampleResult.getResponseDataAsString();

            ps.println("Sampler Label: " + sampleResult.getSampleLabel());
            ps.println("Portal User: " + userId);
            ps.println("Consecutive Error Count: " + errorCount);
            ps.println("Sent Message Count: " + messageCount);
            ps.println("Error Messages: " + errorMessages);
            ps.println("--------------------------------------------------------------------------------");
            ps.print(respHeaders);
            ps.println("--------------------------------------------------------------------------------");
            ps.print(respData);
            ps.flush();

            log("Saved response to: " + responseFile);
        }
        catch (FileNotFoundException fnfe) {
            log("Failed to save response headers and body to file", fnfe);
        }
        finally {
            IOUtils.closeQuietly(ps);
        }
    }


    
    /**
     * Gets the host targeted by the sampler
     */
    private String getSampleTargetHost(SampleResult result) {
        return result.getURL().getHost();
    }
    
    private boolean notifyForHost(JMeterVariables vars) {
        final String notificationStr = vars.get(this.notificationVar);
        return Boolean.parseBoolean(notificationStr);
    }
    
    private void trackHost(JMeterVariables vars, String hostname) {
        final IterationInfo iterationInfo = (IterationInfo)vars.getObject(IterationInfo.class.getName());
        iterationInfo.addHost(hostname);
    }
    
    private void log(String msg) {
        System.out.println(msg);
        log.error(msg);
    }
    
    private void log(String msg, Throwable t) {
        System.out.println(msg + t);
        log.error(msg, t);
    }

}
