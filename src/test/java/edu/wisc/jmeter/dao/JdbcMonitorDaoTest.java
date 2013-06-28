/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter.dao;

import java.util.Date;

import javax.sql.DataSource;

import org.hsqldb.jdbcDriver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.jdbc.SimpleJdbcTestUtils;

import edu.wisc.jmeter.HostStatus;
import edu.wisc.jmeter.Notification;
import edu.wisc.jmeter.Status;

/**
 * @author Eric Dalquist
 * @version $Revision: 1.3 $
 */
public class JdbcMonitorDaoTest {
    private JdbcMonitorDao jdbcMonitorDao;
    private DataSource ds;
    private JdbcTemplate jdbcTemplate;
    
    @Before
    public void setup() throws Exception {
        this.ds = new SimpleDriverDataSource(new jdbcDriver(), "jdbc:hsqldb:mem:JdbcMonitorTest", "sa", "");
        this.jdbcTemplate = new JdbcTemplate(this.ds);
        SimpleJdbcTestUtils.executeSqlScript(new SimpleJdbcTemplate(this.jdbcTemplate), new ClassPathResource("/tables_hsql.sql"), false);
        
        
        this.jdbcMonitorDao = new JdbcMonitorDao(this.ds, Integer.MAX_VALUE, Integer.MAX_VALUE);
        this.jdbcMonitorDao.afterPropertiesSet();
    }
    
    @After
    public void tearDown() throws Exception {
        this.jdbcTemplate.execute("SHUTDOWN");
        this.jdbcMonitorDao.destroy();
        this.jdbcMonitorDao = null;
    }
    
    @Test
    public void hostStatusTest() {
        final HostStatus host1Status = this.jdbcMonitorDao.getHostStatus("host1");
        Assert.assertNotNull(host1Status);
        
        final HostStatus host2Status = this.jdbcMonitorDao.getHostStatus("host2");
        Assert.assertNotNull(host2Status);
        
        final HostStatus host1StatusPrime1 = this.jdbcMonitorDao.getHostStatus("host1");
        Assert.assertNotNull(host1StatusPrime1);
        Assert.assertTrue(host1Status == host1StatusPrime1);
        
        this.jdbcMonitorDao.clearHostStatusCache();
        
        final HostStatus host1StatusPrime2 = this.jdbcMonitorDao.getHostStatus("host1");
        Assert.assertNotNull(host1StatusPrime2);
        Assert.assertTrue(host1Status != host1StatusPrime2);
        Assert.assertEquals(host1Status, host1StatusPrime2);
        
        host1Status.incrementFailureCount();
        host1Status.incrementFailureCount();
        host1Status.incrementMessageCount();
        host1Status.setLastMessageSent(new Date());
        host1Status.setStatus(Status.DOWN);
        
        this.jdbcMonitorDao.storeHostStatus(host1Status);
        Assert.assertTrue(host1Status == host1StatusPrime1);
        Assert.assertEquals(host1Status, host1StatusPrime1);
        Assert.assertTrue(host1Status != host1StatusPrime2);
        Assert.assertNotSame(host1Status, host1StatusPrime2);
    }
    
    @Test
    public void logFailureTest() {
        final HostStatus host1Status = this.jdbcMonitorDao.getHostStatus("host1");
        Assert.assertNotNull(host1Status);
        
        host1Status.incrementFailureCount();
        
        this.jdbcMonitorDao.storeHostStatus(host1Status);
        this.jdbcMonitorDao.logFailure(host1Status.getHost(), "label", new Date(), host1Status.getStatus(), "subject1", "body1", Notification.FALSE);
        
        host1Status.incrementFailureCount();
        host1Status.incrementMessageCount();
        host1Status.setLastMessageSent(new Date());
        host1Status.setStatus(Status.DOWN);
        
        this.jdbcMonitorDao.logFailureAndStatus(host1Status, "label", new Date(), host1Status.getStatus(), "subject2", "body2", Notification.TRUE);
        
        this.jdbcMonitorDao.logFailureAndStatus(host1Status, "label", new Date(), host1Status.getStatus(), "subject2", "body2", Notification.DISABLED);
    }
    
    @Test
    public void logRequestTest() {
        final HostStatus host1Status = this.jdbcMonitorDao.getHostStatus("host1");
        Assert.assertNotNull(host1Status);
        
        this.jdbcMonitorDao.storeHostStatus(host1Status);
        this.jdbcMonitorDao.logRequest(host1Status.getHost(), "label", new Date(), 0, true);
        
        host1Status.setLastMessageSent(new Date());
        host1Status.setStatus(Status.UP);
        
        this.jdbcMonitorDao.logRequestAndStatus(host1Status, "label", new Date(), 0, true);
    }
}
