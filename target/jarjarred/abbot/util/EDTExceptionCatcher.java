/*
 * Copyright (c) 2005 Timothy Wall, All Rights Reserved
 */
package abbot.util;

import abbot.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/** Provide access to the most recent exception caught on the event
    dispatch thread.
 */ 
public class EDTExceptionCatcher
    extends EventDispatchExceptionHandler {

    private static Throwable throwable = null;
    private static long when = -1;

    public void install() {
        clear();
        super.install();
    }

    /** Return the most recent exception caught on the dispatch thread, or
        <code>null</code> if none has been thrown or the exception has been
        cleared.  Also clears the exception. 
    */
    public static Throwable getThrowable() {
        
        synchronized (synchronizedBlock)
        {
            Throwable t = throwable;
            clear();
            return t;
        }
    }

    /** Returns when the most recent exception was caught on the dispatch
        thread, or -1 if none has been thrown or the exception has been
        cleared.
    */
    public static long getThrowableTime() {
        synchronized (synchronizedBlock)
        {
            return when;
        }
    }

    public static void clear() {
        synchronized (synchronizedBlock)
        {
            throwable = null;
            when = -1;
        }
    }

    /**
     * Note we are synchronized by synchronizeBlock at this point
     */
    protected void exceptionCaught(Throwable thr) {
        if (!(thr instanceof ExitException)) {
            
            if (thr.getClass().getName().startsWith("sun.awt.SunToolkit")) {
                Log.warn("Ignoring an exception from the sun toolkit as likely an artifact of a realSyncFailure", thr);
                return;
            }
            
            if (System.getProperty("java.class.path").indexOf("eclipse") != -1) {
                Log.warn("An exception was thrown on the EDT: " + thr, thr);
            }
            else {
                Log.log("An exception was thrown on the EDT: " + thr, thr);
            }
            synchronized(EDTExceptionCatcher.class) {
                when = System.currentTimeMillis();
                throwable = thr;
            }
        }
    }
}
