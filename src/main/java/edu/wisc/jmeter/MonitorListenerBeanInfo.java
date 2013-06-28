/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter;

import java.beans.PropertyDescriptor;

import org.apache.jmeter.testbeans.BeanInfoSupport;

/**
 * @author Eric Dalquist
 * @version $Revision: 1.3 $
 */
public class MonitorListenerBeanInfo extends BeanInfoSupport {

    public MonitorListenerBeanInfo() {
        super(MonitorListener.class);
        PropertyDescriptor p;

        p = property("statusVar");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("statusSamplePattern");
        p.setValue(NOT_UNDEFINED, Boolean.FALSE);
        p.setValue(DEFAULT, "");
        
        p = property("monitoredSamplePattern");
        p.setValue(NOT_UNDEFINED, Boolean.FALSE);
        p.setValue(DEFAULT, "");
        
        createPropertyGroup("statusVarGroup", new String[] { "statusVar", "statusSamplePattern", "monitoredSamplePattern" });

        p = property("logLocation");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        createPropertyGroup("logLocationGroup", new String[] { "logLocation" });



        p = property("notificationVar");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("failureThreshold");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "2");
        
        p = property("backoffDuration");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "15");
        
        p = property("smtpHost");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("emailTo");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("emailFrom");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");

        createPropertyGroup("notifcationGroup", new String[] { "notificationVar", "failureThreshold", "backoffDuration", "smtpHost", "emailTo", "emailFrom" });
        

        
        p = property("jdbcDriver");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("jdbcUrl");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("jdbcUser");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("jdbcPass");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, "");
        
        p = property("purgeOldFailure");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, Integer.toString(MonitorListener.DEFAULT_PURGE_OLD_FAILURE));
        
        p = property("purgeOldStatus");
        p.setValue(NOT_UNDEFINED, Boolean.TRUE);
        p.setValue(DEFAULT, Integer.toString(MonitorListener.DEFAULT_PURGE_OLD_STATUS));

        createPropertyGroup("databaseGroup", new String[] { "jdbcDriver", "jdbcUrl", "jdbcUser", "jdbcPass", "purgeOldFailure", "purgeOldStatus" });
    }

}
