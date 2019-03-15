// $Id: Log.java 2866 2014-08-14 08:37:05Z gdavison $
// Copyright (c) Oculus Technologies Corporation, all rights reserved
// ----------------------------------------------------------------------------
package abbot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import abbot.util.Tee;

/**
   Various logging, assertion, and debug routines.  Typical usage is to
   include the following code
   <blockquote><code><pre>
   public static void main(String[] args) {
&nbsp; args = Log.{@link Log#init(String[]) init}(args)
&nbsp; ...
   }
   </pre></code></blockquote>
   at an application's main entry point.  This way the Log class can remove its
   options from the full set passed into the application.  See the
   {@link #init(String[]) Log.init} method for initialization options. <p>

   General usage notes on public functions:<p>
   
   <ul>
   <li>{@link #warn(String)}<br>
   Programmer warnings; things that you think shouldn't be happening or
   indicate something might be wrong.  Warnings typically mean "Something
   happened that I didn't expect would happen".<p>
   <li>{@link #log(String)}<br>
   Important information that might be needed for later reference; things the
   user or debugger might be interested in.  By default, all messages go
   here.  Logs are made available so that the customer may provide us with an 
   accurate record of software activity.<br>
   All warnings and failed assertions are written to the log.  Debug
   statements are also written to log in non-release code.<p>
   <li>{@link #debug(String)}<br>
   Any messages which might be useful for debugging.
   </ul>
   <p>
   
   Per-class stack trace depth can be specified when adding a class, e.g.
   classname[:stack-depth].<p>
   
   Extraction of the stack trace and logging to file are performed on a 
   separate thread to minimize performance impact due to filesystem delays.
   Use {@link #setSynchronous(boolean) setSynchronous(false)} if you want
   the output to be asynchronous with program execution.<p>

   @author      twall@users.sf.net
*/
public class Log {
    /** No instantiations. */
    protected Log() { }
    private static final int LOG = 0x0001;
    private static final int WARN = 0x0002;
    private static final int DEBUG = 0x0004;
    private static final class StdErrTee extends Tee {
        private StdErrTee(PrintStream p1) {
            super(STDERR, p1);
        }

        public String toString() {
            return "<stderr> and log";
        }
    }
    private static final class StdOutTee extends Tee {
        private StdOutTee(PrintStream p1) {
            super(STDOUT, p1);
        }
        public String toString() {
            return "<stdout> and log";
        }
    }
    private static class Context extends Throwable { 
        public Throwable thrown;
        public int type;
        public Context(int type) { this.type = type; }
        public Context(int type, Throwable t) { 
            this(type);
            this.thrown = t; 
        }
    }
    private static final String NL = System.getProperty("line.separator");
    
    /** Mnemonic to print all lines of a stack trace. */
    public static final int FULL_STACK = 0;
    /** No stack, just a message. */
    public static final int NO_STACK = -1;
    /** Mnemonic to print the default number of lines of stack trace. */
    private static final int CLASS_STACK_DEPTH = -2;
    private static final String STDOUT_NAME = "<stdout>";
    /** Basic warning categories.  FIXME use these. 
    public static final int ERROR   = 0x0001;
    public static final int WARNING = 0x0002;
    public static final int DEBUG   = 0x0004;
    public static final int INFO    = 0x0008;*/

    /** Whether log output is synchronous */
    private static boolean synchronous = true;
    /** Whether to log messages.  Default on so that we capture output until
     * the log file has been set or not set in <code>#init()</code>.
     */
    private static boolean logMessages = true;
    /** Whether to send anything to the console. */
    private static boolean echoToConsole = true;
    private static final PrintStream STDOUT = System.out;
    private static final PrintStream STDERR = System.err;
    /** Whether to show threads in debug output. */
    private static boolean showThreads = false;
    /** Default number of lines of stack trace to print. */
    private static int debugStackDepth;
    /** Default number of lines of stack trace to log. */
    private static int logStackDepth;
    /** Default number of lines of exception stack trace to print. */
    private static int excStackDepth;
    /** Show timestamps in the log? */
    private static boolean showTimestamp = true;
    private static java.text.DateFormat timestampFormat =
        new java.text.SimpleDateFormat("yyMMdd HH:mm:ss:SSS");

    /** Strip this out of output, since it doesn't add information to see it
        repeatedly. */
    private static final String COMMON_PREFIX = "com.oculustech.DOME.client";
    private static final boolean ECLIPSE = 
        System.getProperty("java.class.path").indexOf("eclipse") != -1;
    /** Store which class names we want to see debug info for.  */
    private static Map debugged = new HashMap();
    /** Store which class names we don't want to see debug info for */
    private static Set notdebugged = new HashSet();
    /** Debug all classes? */
    private static boolean debugAll;
    /** Treat inner/anonymous classes as outer class? */
    private static boolean debugInner = true;

    private static final String DEFAULT_LOGFILE_NAME = "co-log.txt";
    private static ByteArrayOutputStream preInitLog =
        new ByteArrayOutputStream();
    private static PrintStream logStream;
    /** Stream which ensures a copy goes to stdout */
    private static PrintStream debugStream;
    /** Stream which ensures a copy goes to stderr */
    private static PrintStream warnStream;
    private static LogThread logThread;
    private static String logFilename;
    private static boolean showWarnings = true;
    private static PrintStream BUFFER = new PrintStream(preInitLog);
    //private static PrintStream STDOUT_AND_BUFFER = new Tee(STDOUT, BUFFER);

    static {
        setDestination(BUFFER);
        logThread = new LogThread();
        logThread.start();

        debugStackDepth = Integer.
            getInteger("co.debug_stack_depth", 1).intValue();
        logStackDepth = Integer.
            getInteger("co.log_stack_depth", NO_STACK).intValue();
        excStackDepth = Integer.
            getInteger("co.exception_stack_depth", FULL_STACK).intValue();

        // Make sure the log gets closed on System.exit
        Runtime.getRuntime().addShutdownHook(new Thread("Log shutdown hook") {
            public void run() {
                close();
            }
        });
    }

    /** Debug/log initialization, presumably from the command line. 
        <br>Recognized options:
        <pre>
        --debug all | className[:depth] | *.partialClassName[:depth]
        --no-debug className | *.partialClassName
        --log <log file name>
        --no-timestamp
        --enable-warnings
        --show-threads
        --stack-depth <depth>
        --exception-depth <depth>
        </pre>
     */
    public static String[] init(String[] args){
        logMessages = false;
        ArrayList newArgs = new ArrayList();
        for (int i=0;i < args.length;i++){
            if (args[i].equals("--enable-warnings")){
                showWarnings  = true;
                setEchoToConsole(true);
            }
            else if (args[i].equals("--no-timestamp")) {
                showTimestamp = false;
            }
            else if (args[i].equals("--show-threads")){
                showThreads = true;
            }
            else if (args[i].equals("--keep-console")){
                setEchoToConsole(true);
            }
            else if (args[i].equals("--stack-depth")) {
                if (++i < args.length) {
                    try {
                        debugStackDepth = Integer.parseInt(args[i]);
                    }
                    catch(Exception exc) {
                    }
                }
                else {
                    warn("Ignoring --stack-depth with no argument");
                }
            }
            else if (args[i].equals("--exception-depth")) {
                if (++i < args.length) {
                    try {
                        excStackDepth = Integer.parseInt(args[i]);
                    }
                    catch(Exception exc) {
                    }
                }
                else {
                    warn("Ignoring --exception-depth with no argument");
                }
            }
            else if (args[i].equals("--debug")
                     || args[i].equals("--no-debug")) {
                if (++i < args.length) {
                    boolean exclude = args[i].startsWith("--no");
                    if (exclude)
                        removeDebugClass(args[i]);
                    else {
                        addDebugClass(args[i]);
                        setEchoToConsole(true);
                    }
                }
                else {
                    warn("Ignoring " + args[i-1] + " with no argument");
                }
            }
            else if (args[i].equals("--log")){
                String filename = DEFAULT_LOGFILE_NAME;
                if (++i < args.length) {
                    filename = args[i];
                }
                initLogging(filename);
            }
            else {
                newArgs.add(args[i]);
            }
        }

        return (String[])newArgs.toArray(new String[newArgs.size()]);
    }

    /** Is log output enabled? */
    public static boolean loggingInitialized() { 
        return logMessages && logStream != null; 
    }


    private static String hostname = null;
    private static final String DEFAULT_HOSTNAME = "unknown";
    public static String getHostName() {
        if(hostname == null) {
            try { 
                hostname = java.net.InetAddress.getLocalHost().getHostName(); 
            }
            catch(java.net.UnknownHostException e) {
                hostname = DEFAULT_HOSTNAME;
                warn("Cannot get hostname, using " + hostname);
            }
        }
        return hostname;
    }

    public static String getLogFilename() {
        return logFilename;
    }
    
    public static PrintStream getLog() {
        return logStream;
    }

    /** Enable log output to the given file.  A filename of "-" means stdout. */
    public static void initLogging(String filename) {
        PrintStream ps = STDOUT;
        logFilename = STDOUT_NAME;
        if (!"-".equals(filename) && filename != null) {
            try {
                ps = new PrintStream(new FileOutputStream(filename), true);
                logFilename = filename;
            }
            catch(IOException e) {
                STDERR.println("Unable to write to " + filename);
                STDERR.println("Output will go to the console");
            }
        }

        setDestination(ps);

        log("Log started on " + getHostName()
            + " (directed to " + logFilename + ")");
        // Always insert the system information
        Log.log(getSystemInfo());
    }
    
    /** Enable log output to the given {@link PrintStream}. */
    public static void setDestination(PrintStream ps) {
        logMessages = true;
        logStream = ps;
        // If there's a log file, redirect stdout/stderr there
        if (logStream == STDOUT) {
            debugStream = logStream;
            warnStream = STDERR;
        }
        else if (logStream == BUFFER) {
            debugStream = new StdOutTee(BUFFER);
            warnStream = new StdErrTee(BUFFER);
        }
        else {
            if (preInitLog.size() > 0) {
                ps.print(preInitLog.toString());
                preInitLog.reset(); 
            }
            debugStream = new StdOutTee(logStream);
            warnStream = new StdErrTee(logStream);
            System.setErr(warnStream);
            System.setOut(debugStream);
            if (logFilename != null) {
                File file = new File(logFilename);
                STDOUT.println("Output also captured in the log file at "
                                  + file.getAbsolutePath());
            }
        }
        setEchoToConsole(logStream != STDOUT);
    }
    
    public static String getSystemInfo() {
        Locale loc = Locale.getDefault();
        return "System Details:" + NL
        + "     java: " + System.getProperty("java.vm.name")
        +           " " + System.getProperty("java.vm.version") + NL
        + "       os: " + System.getProperty("os.name") 
        +           " " + System.getProperty("os.version")
        +           " " + System.getProperty("os.arch") + NL
        + " user.dir: " + System.getProperty("user.dir") + NL
        + "   locale: " + loc.getDisplayName()
        + " " + "[" + loc.getLanguage() + " " + loc.getCountry() + "]" + NL
        + "classpath: " + System.getProperty("java.class.path");
    }

    /** Sets the debug stack depth to the given amount */
    public static void setDebugStackDepth(int depth) {
        debugStackDepth = depth;
    }

    /** Resets the lists of classes to debug and not debug to be
        empty, and turns debugAll off.  
    */
    public static void clearDebugClasses() {
        debugged.clear();
        debugAll = false;
        notdebugged.clear();
    }

    /** Indicate that the given class should NOT be debugged 
        (assuming --debug all) */
    public static void removeDebugClass(String className) {
        setClassDebugEnabled(className, false);
    }

    /** Indicate that debug messages should be output for the given class. */
    public static void addDebugClass(Class class1) {
        addDebugClass(class1.getName());
    }

    /** Indicate that debug messages should no longer be output for the given 
     * class. 
     */
    public static void removeDebugClass(Class class1) {
        removeDebugClass(class1.getName());
    }

    /** Indicate the class name[:depth] to add to debug output. */
    public static void addDebugClass(String className) {
        if (className.indexOf(":") == -1)
            addDebugClass(className, CLASS_STACK_DEPTH);
        else
            setClassDebugEnabled(className, true);
    }

    /** Indicate that debug messages should be output for the given class. */
    public static void addDebugClass(String className, int depth){
        setClassDebugEnabled(className + ":" + depth, true);
    }

    /** Parse the given string, which may should be of the format
        "class[:depth]" */
    private static void setClassDebugEnabled(String id, boolean enable) {
        int colon = id.indexOf(":");
        String className = colon == -1 ? id : id.substring(0, colon);
        if ("all".equals(className)) {
            debugAll = enable;
            if (enable) {
                notdebugged.clear();
            }
            else {
                debugged.clear();
            }
        }
        else {
            className = getFullClassName(className);
            int depth = CLASS_STACK_DEPTH;
            try {
                depth = colon == -1 
                    ? debugStackDepth 
                    : Integer.parseInt(id.substring(colon+1));
            }
            catch (NumberFormatException nfe) {
            }
            if (enable) {
                debugged.put(className, new Integer(depth));
                notdebugged.remove(className);
                debug("Debugging enabled for " + className + " (" + depth + ")");
            }
            else {
                notdebugged.add(className);
                debugged.remove(className);
                debug("Debugging disabled for " + className);
            }
        }
    }

    /** Returns class from given name/descriptor.  Descriptor can be either a
        fully qualified classname, or a classname beginning with *. with
        client-specific package and classname following.
    */
    private static String getFullClassName(String className) {
        if (COMMON_PREFIX != null && className.startsWith("*.")) {
            className = COMMON_PREFIX + className.substring(1);
        }
        return className;
    }

    /** Return the requested number of levels of stack trace, not including
        this call.   Returns the full stack trace if LINES is FULL_STACK.
        Skip the first POP frames of the trace, which is for excluding the
        innermost stack frames when debug functions make nested calls.
        The outermost call of getStackTrace itself is always removed from the
        trace.  */ 
    private static String getStackTrace(int pop, int lines) {
        return getStackTrace(pop, lines, new Throwable("--debug--"));
    }

    /** Return the requested number of levels of stack trace, not including
        this call.   Returns the full stack trace if LINES is FULL_STACK.
        Skip the first POP frames of the trace, which is for excluding the
        innermost stack frames when debug functions make nested calls.
        The outermost call of getStackTrace itself is always removed from the
        trace.   Provide an exception to use for the stack trace,
        rather than using the current program location.
    */
    private static String getStackTrace(int pop, int lines, Throwable thr) {
        if (lines != NO_STACK) {
            String stack = getStackTrace(pop, thr);
            if (lines == FULL_STACK) 
                return stack;
            return trimStackTrace(stack, lines);
        }
        return "";
    }

    /** Return the stack trace contained in the given Throwable.
        Skip the first POP frames of the trace, which is for excluding the
        innermost stack frames when debug functions make nested calls.
        The outermost call of getStackTrace itself is always removed from the
        trace.   
    */
    private static String getStackTrace(int pop, Throwable thr){
        OutputStream os = new ByteArrayOutputStream();
        PrintStream newStream = new PrintStream(os, true);
        // OUCH! this is a serious performance hit!
        thr.printStackTrace(newStream);
        String trace = os.toString();

        // Pop off getStackTrace itself
        // Skip over any calls to getStackTrace; the JIT sometimes puts a
        // spurious entry, so don't just stop at the first one.
        int getLoc = trace.lastIndexOf("getStackTrace");
        int at = trace.indexOf("\tat ", getLoc);
        if (at != -1) 
            trace = trace.substring(at + 3);
        while (pop-- > 0){
            // pop off the calling function
            at = trace.indexOf("\tat ");
            if (at != -1)
                trace = trace.substring(at + 3);
        }

        return trace.trim();
    }

    /** Trim the given trace to LINES levels. */
    private static String trimStackTrace(String trace, int lines) {
        // Keep just as many lines as were requested
        int end = trace.indexOf(")") + 1;
        boolean all = (lines == FULL_STACK);
        while (all || --lines > 0) {
            int index = trace.indexOf("\tat ", end);
            if (index < 0)
                break;
            end = trace.indexOf(")", index) + 1;
        } 
        return trace.substring(0, end);
    }

    /** Return the class corresponding to the first line in the give stack
        trace.  Treat inner/anonymous classes as the enclosing class. */
    // FIXME with JIT enabled, stack trace sometimes has spurious junk on the
    // stack, which will indicate the wrong class...
    private static String extractClass(String trace){
        int paren = trace.indexOf("(");
        String tmp = paren == -1 ? trace : trace.substring(0, paren);
        int mstart = tmp.lastIndexOf(".");
        String cname = mstart == -1 ? tmp : tmp.substring(0, mstart);
        cname = cname.trim();
        if (debugInner) {
            int sub = cname.indexOf("$");
            if (sub != -1)
                cname = cname.substring(0, sub).trim();
        }

        return cname;
    }

    public static boolean isClassDebugEnabled(Class cls) {
        return isClassDebugEnabled(cls.getName());
    }
    
    public static boolean isClassDebugEnabled(String className) {
        return (debugAll || debugged.containsKey(className))
            && !notdebugged.contains(className);
    }

    static int getClassStackDepth(String cname) {
        Integer depth = (Integer)debugged.get(cname);
        if (depth != null && depth.intValue() != CLASS_STACK_DEPTH)
            return depth.intValue();
        return debugStackDepth;
    }

    /** Print a debug message. */
    public static void debug(String event){
        internalDebug(event, new Context(DEBUG));
    }

    /** Print a debug message with the given number of stack lines. */
    public static void debug(String event, int lines){
        internalDebug(event, new Context(DEBUG), lines);
    }

    /** Use this to display debug output for expected or common exceptions. */
    public static void debug(Throwable thr) {
        internalDebug("", new Context(DEBUG, thr));
    }

    /** Issue a debug statement regarding the given {@link Throwable}. */
    public static void debug(String m, Throwable e) {
        internalDebug(m, new Context(DEBUG, e));
    }

    private static void internalDebug(String msg, Context context) {
        internalDebug(msg, context, CLASS_STACK_DEPTH);
    }
    
    private static void internalDebug(String msg, Context context, int lines) {
        if (debugged.size() > 0 || debugAll) {
            internalLog(msg, context, lines, 1, echoToConsole ? debugStream : logStream);
        }
    }

    /** Replace all occurrences of a given expresion with a different
        string. */ 
    private static String abbreviate(String msg, String expr, String sub) {
        // Eclipse uses the full classs name for navigation, so don't hide it
        if (ECLIPSE) return msg;
        StringBuffer sb = new StringBuffer(msg);
        int index = msg.indexOf(expr);
        int len = expr.length();
        while (index >= 0){
            sb.replace(index, index + len, sub);
            index = sb.toString().indexOf(expr);
        }
        return sb.toString();
    }

    /** Strip out stuff we don't want showing in the message.  */
    private static String abbreviate(String msg){
        if (COMMON_PREFIX != null)
            msg = abbreviate(msg, COMMON_PREFIX, "*");
        return msg;
    }

    /** Issue a warning.  All warnings go to the log file and the error
        stream. */ 
    private static void internalWarn(String message, Context context,
                                     int depth, int pop){
        internalLog(message, context, depth, pop, 
                    showWarnings ? warnStream : logStream);
    }

    /** Retrieve the given number of lines of the current stack, as a
        string. */ 
    public static String getStack(int lines){
        return getStackTrace(1, lines);
    }

    /** Retrieve the full stack from the given Throwable, as a string. */ 
    public static String getStack(Throwable t) {
        return getStackTrace(Log.FULL_STACK, t);
    }

    /** Retrieve the given number of lines of stack from the given Throwable,
        as a string. */ 
    public static String getStack(int lines, Throwable thr){
        return getStackTrace(1, lines, thr);
    }

    /** Issue a programmer warning, which will include the source line of the 
        warning. */ 
    public static void warn(String message){
        internalWarn(message, new Context(WARN), debugStackDepth, 1);
    }

    /** Issue a programmer warning, which will include the source line of the 
        warning. */ 
    public static void warn(String message, Throwable e){
        internalWarn(message, new Context(WARN, e), debugStackDepth, 1);
    }

    /** Issue a programmer warning, which will include the source line of the 
        warning, and a stack trace with up to the given number of lines. */ 
    public static void warn(String message, int lines){
        internalWarn(message, new Context(WARN), lines, 1);
    }

    /** Issue a programmer warning, which will include the source line of the 
        original thrown object. */ 
    public static void warn(Throwable thr) {
        internalWarn("", new Context(WARN, thr), debugStackDepth, 1);
    }

    /** Log an exception. */
    public static void log(Throwable thr) {
        internalLog("", new Context(LOG, thr), excStackDepth, 1);
    }

    /** Log an exception with a description. */
    public static void log(String message, Throwable thr) {
        internalLog(message, new Context(LOG, thr), excStackDepth, 1);
    }

    /** Log a message. */
    public static void log(String message){
        internalLog(message, new Context(LOG), logStackDepth, 1);
    }

    private static void internalLog(String event, Context context, 
                                    int depth, int pop) {
        internalLog(event, context, depth, pop, logStream);
    }

    private static void internalLog(String event, Context context, 
                                    int depth, int pop, 
                                    PrintStream stream){
        String thread = Thread.currentThread().getName();
        if (synchronous) {
            logMessage(event, new Date(), context, 
                       depth, pop, stream, thread);
        }
        else if (logThread != null) {
            logThread.post(event, thread, new Date(), 
                           context, depth, pop, stream);
        }
        else {
            STDERR.println("Message posted after close: " + event);
        }
    }

    static void flush() {
        while (logThread.queue.size() > 0) {
            synchronized(logThread.queue) {
                logThread.queue.notifyAll();
            }
            try { Thread.sleep(10); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        debugStream.flush();
        warnStream.flush();
        logStream.flush();
    }
    
    public static void close() {
        flush();
        log("Log closed");
        logStream.close();
        logThread.terminate();
        logThread = null;
    }

    private static class LogThread extends Thread {
        private boolean terminate;
        private Vector queue = new Vector();
        public LogThread() {
            super("Logging thread");
            setDaemon(true);
        }
        public void terminate() {
            synchronized(queue) {
                terminate = true;
                queue.notifyAll();
            }
        }
        public void post(String msg, String threadName,
                         Date date, Context throwable, int depth,
                         int pop, PrintStream output) {
            synchronized(queue) {
                if (!terminate) {
                    queue.add(new Object[]{ msg, date, throwable, 
                        new int[] { depth, pop }, output, threadName });
                    queue.notifyAll();
                }
                else {
                    STDERR.println("discarded: " + msg);
                }
            }
        }
        public void run() {
            setName("Logging thread (to " + logStream + ")");
            while (!terminate) {
                try {
                    while (queue.size() > 0) {
                        Object[] list = (Object[])queue.get(0);
                        int[] args = (int[])list[3];
                        logMessage((String)list[0], (Date)list[1],
                                   (Context)list[2], args[0], args[1], 
                                   (PrintStream)list[4], (String)list[5]);
                        queue.remove(0);
                    }
                    synchronized(queue) {
                        if (queue.size() == 0) {
                            queue.wait();
                        }
                    }
                }
                catch(InterruptedException e) {
                    break;
                }
                catch(Throwable e) {
                    STDERR.println("Error in logging thread: " + e);
                    e.printStackTrace();
                }
            }
        }
    }

    private static String lastMessage = null;
    private static int lastMessageRepeatCount = 0;
    private static String lastMessageTimestamp = null;
    private static PrintStream lastMessageStream = null;
    private static void logMessage(String msg, Date date, Context context,
                                   int depth, int pop, PrintStream stream,
                                   String threadName) {
        boolean debug = context.type == DEBUG;
        String trace;
        if (debug) {
            trace = getStackTrace(pop, Log.FULL_STACK, context);
            String cname = extractClass(trace);
            if (!isClassDebugEnabled(cname)) {
                return;
            }
            if (depth == CLASS_STACK_DEPTH) {
                trace = trimStackTrace(trace, getClassStackDepth(cname));
            }
        }
        else {
            trace = getStackTrace(pop, depth, context);
        }
        if (context.thrown != null) {
            Throwable e = context.thrown;
            String where = getStackTrace(0, excStackDepth, e);
            String type = e instanceof Error ? "Error" : "Exception thrown";
            trace = type + " at " + where + ": " + e + NL
                + "\t(caught at " + trace + ")";
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException)e).getTargetException();
                where = getStackTrace(0, excStackDepth, e);
                trace += NL + "Target exception was " + e + " at " + where;
            }
            else if (e instanceof UndeclaredThrowableException) {
                e = ((UndeclaredThrowableException)e).getUndeclaredThrowable();
                where = getStackTrace(0, excStackDepth, e);
                trace += NL + "Undeclared exception was " + e + " at " + where;
            }
            else if (e instanceof ExceptionInInitializerError) {
                e = ((ExceptionInInitializerError)e).getException();
                where = getStackTrace(0, excStackDepth, e);
                trace += NL + "Exception was " + e + " at " + where;
            }
        }
        trace = abbreviate(trace);
        if (showThreads) {
            trace = "[" + threadName + "] " + trace;
        }
        String timestamp = timestampFormat.format(date);
        if (showTimestamp) {
            trace = timestamp + " " + trace;
        }
        String output = trace.trim(); 
        if (msg != null && !"".equals(msg)) {
            output += ":\n\t" + msg;
        }
        if (stream == lastMessageStream
            && (msg == lastMessage 
                || (msg != null && msg.equals(lastMessage)))) {
            ++lastMessageRepeatCount;
            lastMessageTimestamp = timestamp;
        }
        else {
            if (lastMessageRepeatCount > 0) {
                lastMessageStream.println(lastMessageTimestamp
                                          + ": Last message repeated " 
                                          + lastMessageRepeatCount + " times");
                lastMessageStream.flush();
            }
            stream.println(output);
            lastMessage = msg;
            lastMessageStream = stream;
            lastMessageRepeatCount = 0;
            lastMessageTimestamp = timestamp;
        }
    }
    /** Set whether log output is synchronous with program execution. */
    public static void setSynchronous(boolean b) {
        synchronous = b;
    }
    /** Set whether to display the current thread of execution. */
    public static void setShowThreads(boolean b) {
        showThreads = b;
    }
    /** Set whether messages are echoed to the console in addition to the log. 
     */
    public static void setEchoToConsole(boolean b) {
        echoToConsole = b;
    }
}
