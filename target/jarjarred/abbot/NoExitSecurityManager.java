package abbot;

public abstract class NoExitSecurityManager extends SecurityManager {
    private Exception creation;
    public NoExitSecurityManager() {
        class CreationLocationException extends Exception { }
        creation = new CreationLocationException();
    }

    public void checkPermission(java.security.Permission perm, 
                                Object context) {
        // allow everything
    }
    public void checkPermission(java.security.Permission perm) {
        // allow everything
    }
    
    /**
     * Returns true if the exit has been invoked through a call
     * of Runtime.exit or Runtime.halt .
     * 
     * @return   true <=> An exit has been invoked through a call of Runtime.exit / Runtime.halt .
     */
    public boolean exitInvoked() {
        // We only want to disallow Runtime.halt/Runtime.exit 
        // Anything else is ok (e.g. System.runFinalizersOnExit; some VMs do a
        // check there as well -- 1.3 and prior, I think)
        String stack = Log.getStack(Log.FULL_STACK);
        return(
        	(stack.indexOf("java.lang.Runtime.exit") != -1) ||
            (stack.indexOf("java.lang.Runtime.halt") != -1)
        );
    }
    public void checkExit(int status) {
        if (exitInvoked()) {
            exitCalled(status);
            String msg = "Application exit denied";
            Log.log(msg + " from security manager "
                    + "created at " + Log.getStack(Log.FULL_STACK, creation));
            throw new ExitException(msg, status);
        }
    }
    /** Implement this method to do any context-specific cleanup.  This
        hook is provided since it may not always be possible to catch the
        ExitException explicitly (like when it's caught by someone else, or
        thrown from the event dispatch thread).
    */
    protected abstract void exitCalled(int status);
}
