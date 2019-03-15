package abbot.util;

import java.awt.EventQueue;

import javax.swing.SwingUtilities;

import abbot.Log;

import java.lang.reflect.InvocationTargetException;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 
 * Handler for uncaught exceptions on any event dispatch thread.<p>
 * It now uses the Thread.UncaughtExceptionHandler class which introduces
 * a minor issue in that when this is called the current event queue object
 * has already been replaced by a new instance. (See the source code for
 * {@link java.awt.EventDispatchThread#run} for more information). To work around
 * this a shared synchronization object has been added, although a read/write lock
 * would also have been a possible design and should be considered if a time out
 * ends up being required.
 */
public class EventDispatchExceptionHandler {

    private static boolean installed = false;
    private static boolean canInstall = true;
    /**
     * Allows sychronization between the current queue and the exception handler
     * queue of which the only thing we know for sure is that it is definitely not
     * the AWT event queue, if it ever was.
     */
    private static CountDownLatch instalLatch = new CountDownLatch(1);
    
    /**
     * We need to make sure that we can't query the state of the hander while we are dealing with an exception
     */
    protected static Object synchronizedBlock = new Object() {};
    
    private static Thread.UncaughtExceptionHandler existingHandler;

    public void install() {

        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Handler must not be installed from the event dispatch thread");
        }

    
        if (!canInstall) {
            Log.warn("Can't install event dispatch exception handler");
        }
        else if (!installed)
        {
            existingHandler = Thread.getDefaultUncaughtExceptionHandler();


            final Thread.UncaughtExceptionHandler swingOnly = new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {

                    synchronized (synchronizedBlock)
                    {
                        // So at this point the original thread has died
                        // so we would need to be able to add another exception listener to the thread
                        // that doesn't necessarily exist as yet, so we need to test to see if t is an intance
                        // of EventDispatchThread
                        // and make sure that the existing handler is check for the current thread
                        //
                        
                        boolean isWasEDT = false;
                        
                        Class $parent = t.getClass();
                        while ($parent!=null && !isWasEDT) {
                            String name = $parent.getName();
                            if ("java.awt.EventDispatchThread".equals(name)) {
                                isWasEDT = true;
                            }
                            $parent = $parent.getSuperclass();
                        }
                        
                        
                        if (!isWasEDT) {
                            if (existingHandler!=null)
                            {
                                existingHandler.uncaughtException(t,e);
                            }
                            else {
                                e.printStackTrace();
                            }
                        }
                        
                        
                        
    
                        // The exception may be created by a different class loader
                        // so compare by name only
                        if (e instanceof DummyException) {
                            // Install succeeded
                            Log.debug("Installation succeeded");
                            installed = true;
                            instalLatch.countDown();
                            return;
                        }
    
                        
                        // We are assuming that the current thread is the EDT, so delegate to
                        // the existing handler is required
                        
                        if (existingHandler!=null) {
                            existingHandler.uncaughtException(t, e);
                        }
                        else  {
                            handle(e);
                        }
                    }
                }
            };

            try {

                Thread.currentThread().setDefaultUncaughtExceptionHandler(swingOnly);
                
                // Check we can catch the dummy exception
                //
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                        throw new DummyException();
                    }
                });
                // Does nothing but wait for the previous invocation to finish
                AWT.invokeAndWait(new Runnable() { public void run() { } });
                
                if (instalLatch.await(30, TimeUnit.SECONDS) && 
                    installed) {
                    // Horrah we are good to go
                    int i = 9;
                }
                else {
                    //
                    canInstall = false;
                }
                
            } catch (Exception e)  {
                installed = false;
                canInstall = false;

                String msg = "The handler for event "
                    + "dispatch thread exceptions could not be installed";
                Log.warn(msg, e);
                throw new RuntimeException(msg, e);

            }
        }
        else {
            // We are fine, don't install again
            //

            Log.debug("Exception handler class already installed");
        }
        
        
    }
    
    /** Define this to handle the exception as needed.  
     * Default prints a warning to System.err.
     */
    protected void exceptionCaught(Throwable thrown) {
        System.err.println("Exception caught on event dispatch thread: " + thrown);
    }

    /** Handle exceptions thrown on the event dispatch thread. */
    public void handle(Throwable thrown) {
        Log.debug("Handling event dispatch exception: " + thrown);

        {
            Log.debug("Handling exception on event dispatch thread: "
                      + thrown + " with " + getClass());
            Log.debug(thrown);
            exceptionCaught(thrown);
        }
        Log.debug("Handling done");
    }

    public static boolean isInstalled() {
        return installed;
    }

    private static class DummyException extends RuntimeException {
        public String toString() {
            return super.toString() + " " + getClass().getClassLoader();
        }
    }
}

