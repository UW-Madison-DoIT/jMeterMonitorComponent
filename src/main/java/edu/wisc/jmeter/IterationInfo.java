/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package edu.wisc.jmeter;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

class IterationInfo {
    private final Set<String> visitedHosts = new LinkedHashSet<String>();
    private Date start;
    
    private final Set<String> previousVisitedHosts = new LinkedHashSet<String>();
    
    public void addHost(String host) {
        this.visitedHosts.add(host);
    }
    
    public void iterate() {
        this.previousVisitedHosts.clear();
        this.previousVisitedHosts.addAll(this.visitedHosts);
        this.start = new Date();
    }

    public Set<String> getPreviousVisitedHosts() {
        return Collections.unmodifiableSet(previousVisitedHosts);
    }

    public Date getPreviousStart() {
        return start;
    }
}