package abbot.util;

import java.awt.AWTException;
import java.awt.Toolkit;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.PrintStream;
import java.util.Properties;

import javax.swing.UIManager;

import abbot.Log;

/** Preserve and restore system state.
    This includes the following:
    <ul>
    <li><code>System.out/err</code> streams
    <li><code>System</code> properties
    <li>Security manager
    </ul>
 */
public class SystemState {

    public static final int LOCKING_CODES[] = {
        KeyEvent.VK_CAPS_LOCK,
        KeyEvent.VK_NUM_LOCK,
        KeyEvent.VK_SCROLL_LOCK,
        KeyEvent.VK_KANA_LOCK
    };
    private Properties oldProps;
    private PrintStream oldOut;
    private PrintStream oldErr;
    private SecurityManager oldsm;
    private String oldLookAndFeel;
    private boolean lockingKeys[];
    private static Robot robot = null;

    static {
        try {
            robot = new Robot();
        }
        catch(AWTException e) {
        }
    }

    /** Take a snapshot of the current System state for later restoration. */
    public SystemState() {
        lockingKeys = new boolean[LOCKING_CODES.length];
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (int i=0;i < LOCKING_CODES.length;i++) {
            try {
                lockingKeys[i] = toolkit.getLockingKeyState(LOCKING_CODES[i]);
            }
            catch(UnsupportedOperationException e) {
                // Nothing much we can do
            }
        }
        oldLookAndFeel = UIManager.getLookAndFeel().getClass().getName();
        oldOut = System.out;
        oldErr = System.err;
        System.setOut(new ProtectedStream(oldOut));
        System.setErr(new ProtectedStream(oldErr));
        oldProps = (Properties)System.getProperties().clone();
        oldsm = System.getSecurityManager();
    }

    /** Restore the state captured in the ctor. */
    public void restore() {

        // As per issue it is not always possible to reset the security manager
        // in this case when JDeveloper is running under the Netbeans window manager
        // so we can first of all check that the manager has not been changes
        // and then catch the exception an ignore it if this has been blocked
        SecurityManager currentsm = System.getSecurityManager();
        if (oldsm != currentsm) {
            try { System.setSecurityManager(oldsm); }
            catch (SecurityException se) { Log.warn("Couldn't reset security manager " + se); }
        }
        
        System.setProperties(oldProps); 
        System.setOut(oldOut); 
        System.setErr(oldErr); 
        try { UIManager.setLookAndFeel(oldLookAndFeel); }
        catch(Exception e) { Log.warn("Could not restore LAF: " + e); }
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (int i=0;i < LOCKING_CODES.length;i++) {
            try {
                boolean state = toolkit.getLockingKeyState(LOCKING_CODES[i]);
                if (state != lockingKeys[i]) {
                    try {
                        toolkit.setLockingKeyState(LOCKING_CODES[i], lockingKeys[i]);
                    }
                    catch(UnsupportedOperationException e) {
                        if (robot != null) {
                            try {
                                robot.keyPress(LOCKING_CODES[i]);
                                robot.keyRelease(LOCKING_CODES[i]);
                            }
                            catch(IllegalArgumentException ex) {
                                // ignore
                            }
                        }
                    }
                }
            }
            catch(UnsupportedOperationException e) {
                // Oh, well
            }
        }
    }

    /** Provide a wrapper that prevents the original stream from being
        closed.
    */
    private class ProtectedStream extends PrintStream {
        private boolean closed = false;
        public ProtectedStream(PrintStream original) {
            super(original);
        }
        public void flush() {
            if (!closed)
                super.flush();
        }
        public void close() {
            closed = true;
        }
        public void write(int b) {
            if (!closed)
                super.write(b);
        }
        public void write(byte[] buf, int off, int len) {
            if (!closed)
                super.write(buf, off, len);
        }
    }

    /** Clear all locking keys. */
    public static void clearLockingKeys() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (int i=0;i < LOCKING_CODES.length;i++) {
            try {
                if (toolkit.getLockingKeyState(LOCKING_CODES[i])) {
                    try {
                        toolkit.setLockingKeyState(LOCKING_CODES[i], false);
                    }
                    catch(UnsupportedOperationException e) {
                        if (robot != null) {
                            try {
                                robot.keyPress(LOCKING_CODES[i]);
                                robot.keyRelease(LOCKING_CODES[i]);
                            }
                            catch(IllegalArgumentException ex) {
                                // ignore
                            }
                        }
                    }
                }
            }
            catch(UnsupportedOperationException e) { 
                // can't read locking keys state, so do nothing
            }
        }
    }
}

