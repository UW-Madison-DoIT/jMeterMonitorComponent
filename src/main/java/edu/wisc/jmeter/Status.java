/**
 * Copyright (c) 2000-2009, Jasig, Inc.
 * See license distributed with this file and available online at
 * https://www.ja-sig.org/svn/jasig-parent/tags/rel-10/license-header.txt
 */

package edu.wisc.jmeter;

/**
 * Potential server status
 * 
 * @author Eric Dalquist
 * @version $Revision: 1.2 $
 */
public enum Status {
    UP(true, false),
    OUT_UP(true, true),
    OUT_DOWN(false, true),
    DOWN(false, false),
    UNKOWN(false, false);
    
    private final boolean up;
    private final boolean out;
    
    private Status(boolean up, boolean out) {
        this.up = up;
        this.out = out;
    }

    public boolean isUp() {
        return up;
    }

    public boolean isOut() {
        return out;
    }
}
