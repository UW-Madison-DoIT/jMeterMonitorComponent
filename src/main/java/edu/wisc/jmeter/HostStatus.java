/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.util.Assert;

/**
 * @author Eric Dalquist
 * @version $Revision: 1.2 $
 */
public class HostStatus {
    private String host;
    private final AtomicInteger messageCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private Date lastMessageSent = null;
    private Date lastUpdated = null;
    private Status status = Status.UNKOWN;
    
    public Status getStatus() {
        return status;
    }
    public void setStatus(Status status) {
        Assert.notNull(status);
        this.status = status;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public int getMessageCount() {
        return messageCount.get();
    }
    public void setMessageCount(int messageCount) {
        this.messageCount.set(messageCount);
    }
    public int incrementMessageCount() {
        return this.messageCount.incrementAndGet();
    }
    public int getFailureCount() {
        return failureCount.get();
    }
    public void setFailureCount(int failureCount) {
        this.failureCount.set(failureCount);
    }
    public int incrementFailureCount() {
        return this.failureCount.incrementAndGet();
    }
    public Date getLastMessageSent() {
        return lastMessageSent;
    }
    public void setLastMessageSent(Date lastMessageSent) {
        this.lastMessageSent = lastMessageSent;
    }
    public Date getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((host == null) ? 0 : host.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof HostStatus)) {
            return false;
        }
        HostStatus other = (HostStatus) obj;
        if (host == null) {
            if (other.host != null) {
                return false;
            }
        }
        else if (!host.equals(other.host)) {
            return false;
        }
        return true;
    }
    @Override
    public String toString() {
        return "HostStatus [host=" + host + ", status=" + status + ", lastUpdated=" + lastUpdated + ", failureCount="
                + failureCount + ", messageCount=" + messageCount + ", lastMessageSent=" + lastMessageSent + "]";
    }
}
