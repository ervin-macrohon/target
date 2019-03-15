package abbot.util;

import abbot.InterruptedAbbotException;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.*;
import java.util.Iterator;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import abbot.Log;
import abbot.finder.BasicFinder;
import abbot.finder.ComponentFinder;
import abbot.finder.ComponentNotFoundException;
import abbot.finder.ComponentSearchException;
import abbot.finder.Hierarchy;
import abbot.finder.Matcher;
import abbot.finder.MultipleComponentsFoundException;
import abbot.finder.TestHierarchy;
import abbot.finder.matchers.ClassMatcher;
import abbot.finder.matchers.WindowMatcher;
import abbot.tester.Robot;
import abbot.tester.WindowTracker;

import java.awt.Color;
import java.awt.Toolkit;

/** Provides various facilities for setting up, using, and tearing down
 * a test involving UI components.
 * Handles standardized AWTEvent logging and catching exceptions on the
 * AWT event dispatch thread (EDT).
 * This class should be used at setup and teardown of your chosen fixture.
 * @see junit.extensions.abbot.ComponentTestFixture
 * @see abbot.script.StepRunner
 */

public class AWTFixtureHelper {
    /** Typical delay to wait for a robot event to be translated into a Java
    event. */
    public static final int EVENT_GENERATION_DELAY = 5000;
    public static final int WINDOW_DELAY = 20000; // for slow systems
    public static final int POPUP_DELAY = 10000;

    private AWTEventListener listener = null;
    private SystemState state;
    private Robot robot;
    private WindowTracker tracker;
    private Hierarchy hierarchy;
    // modifiers set during this helper's lifetime (to be cleared on dispose)
    private int modifiers;

    public AWTFixtureHelper() {
        this(new TestHierarchy());
    }
    
    /** Create an instance of AWTFixtureHelper which makes a snapshot of the
        current VM state.
    */
    public AWTFixtureHelper(Hierarchy hierarchy) {
        // Preserve all system properties to restore them later
        state = new SystemState();
        this.hierarchy = hierarchy;

        // Install our own event handler, which will forward events thrown on
        // the event queue
        try {
            new EDTExceptionCatcher().install();
        }
        catch(RuntimeException re) {
            // Not fatal if we can't install, since most tests don't
            // depend on it.  We won't be able to throw errors that were
            // generated on the event dispatch thread, though.
        }
        // Only enable event logging if debug is enabled for this class
        // Facilitate debugging by logging all events
        if (Boolean.getBoolean("abbot.fixture.log_events")) {
            long mask = Properties.getProperty("abbot.fixture.event_mask",
                                               Long.MIN_VALUE,
                                               Long.MAX_VALUE,
                                               abbot.editor.recorder.
                                               EventRecorder.
                                               RECORDING_EVENT_MASK);

            Log.log("Using mask value " + mask);
            listener = new AWTEventListener() {
                public void eventDispatched(AWTEvent event) {
                    if (listener != null)
                        Log.log(Robot.toString(event));
                }
            };
            new WeakAWTEventListener(listener, mask);
        }
        robot = new Robot();
        tracker = WindowTracker.getTracker();


        SystemState.clearLockingKeys();
        robot.reset();
        if (Bugs.hasMultiClickFrameBug())
            robot.delay(500);
    }
    
    public Robot getRobot() { return robot; }
    public WindowTracker getWindowTracker() { return tracker; }
    public Hierarchy getHierarchy() { return hierarchy; }

    /** Returns the last exception thrown on the event dispatch thread, or
        <code>null</code> if no such exception has been thrown.
    */
    public Throwable getEventDispatchError() {
        return EDTExceptionCatcher.getThrowable();
    }

    /** Returns the time of the last exception thrown on the event dispatch
        thread.
    */
    public long getEventDispatchErrorTime() {
        return EDTExceptionCatcher.getThrowableTime();
    }

    /** Convenience method to set key modifiers.  Using this method is 
     * preferred to invoking {@link Robot#setModifiers(int,boolean)} or 
     * {@link Robot#keyPress(int)}, since this method's effects will be 
     * automatically undone at the end of the test.  If you use the 
     * {@link Robot} methods, you must remember to release any keys pressed 
     * during the test.
     * @param modifiers mask indicating which modifier keys to use
     * @param pressed whether the modifiers should be in the pressed state.
     */
    public void setModifiers(int modifiers, boolean pressed) {
        if (pressed)
            this.modifiers |= modifiers;
        else
            this.modifiers &= ~modifiers;
        robot.setModifiers(modifiers, pressed);
        robot.waitForIdle();
    }

    protected void disposeAll() {
        Iterator iter = hierarchy.getRoots().iterator();
        while (iter.hasNext()) {
            hierarchy.dispose((Window)iter.next());
        }
    }
    
    /** Restore the state that was preserved when this object was created.
     */
    public void restore() {
        if (AWT.isAWTPopupMenuBlocking())
            AWT.dismissAWTPopup();
        state.restore();

        // Encourage GC of unused components, which reduces the load on
        // future tests.
        System.gc();
        System.runFinalization();
    }
    
    /** Dispose all windows created during this object's lifetime and restore
     * the previous system/UI state, to the extent possible.
     */
    public void dispose() {
        // WARNING: clear input state prior to disposing windows,
        // otherwise native drag operations may be left stuck
        if (robot != null) {
            if (modifiers != 0) {
                robot.setModifiers(modifiers, false);
                modifiers = 0;
            }
            int buttons = Robot.getState().getButtons();
            if (buttons != 0) {
                Log.debug("release " + AWT.getMouseModifiers(buttons));
                robot.mouseRelease(buttons);
            }
            if (robot.getState().isNativeDragActive()) {
                robot.keyPress(KeyEvent.VK_ESCAPE);
                robot.keyRelease(KeyEvent.VK_ESCAPE);
            }
            // TODO: release *any* keys that were pressed, not just modifiers
        }
        disposeAll();
        restore();
    }
    
    /** This method should be invoked to display the component under test.
     * The frame's size will be its preferred size.  This method will return
     * with the enclosing {@link Frame} is showing and ready for input.
     */
    public Frame showFrame(Component comp) {
        return showFrame(comp, null);
    }

    /** This method should be invoked to display the component under test,
     * when a specific size of frame is desired.  The method will return when
     * the enclosing {@link Frame} is showing and ready for input.  
     * @param comp
     * @param size Desired size of the enclosing frame, or <code>null</code>
     * to make no explicit adjustments to its size.
     */
    public Frame showFrame(Component comp, Dimension size) {
        return showFrame(comp, size, "Test Frame");
    }
    
    /** This method should be invoked to display the component under test,
     * when a specific size of frame is desired.  The method will return when
     * the enclosing {@link Frame} is showing and ready for input.  
     * @param comp
     * @param size Desired size of the enclosing frame, or <code>null</code>
     * to make no explicit adjustments to its size.
     * @param title Title of the wrapping frame
     */
    public Frame showFrame(Component comp, Dimension size, String title) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel pane = (JPanel)frame.getContentPane();
        pane.setBorder(new EmptyBorder(10, 10, 10, 10));
        pane.add(comp);
        showWindow(frame, size, true);
        return frame;
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the {@link Window} is showing and ready for input.
     */
    public void showWindow(Window w) {
        showWindow(w, null, true);
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the {@link Window} is showing and ready for input.
     */
    public void showWindow(final Window w, final Dimension size) {
        showWindow(w, size, true);
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the window is showing.  This method will return even when
     * the window is a modal dialog, since the show method is called on the
     * event dispatch thread.  The window will be packed if the pack flag is
     * set, and set to the given size if it is non-<code>null</code>.<p>
     * Modal dialogs may be shown with this method without blocking.
     */
    public void showWindow(final Window w, final Dimension size,
                              final boolean pack) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                if (pack) {
                    w.pack();
                    // Make sure the window is positioned away from
                    // any toolbars around the display borders
                    w.setLocation(100, 100);
                }
                if (size != null)
                    w.setSize(size.width, size.height);
                w.setVisible(true);
            }
        });
        // Ensure the window is visible before returning
        waitForWindow(w, true);
    }

    /** Return when the window is ready for input or times out waiting.
     * @param w
     */
    public void waitForWindow(Window w, boolean visible) {
        long start = System.currentTimeMillis();
        while ((Robot.getEventMode() == Robot.EM_ROBOT
                && visible && !getWindowTracker().isWindowReady(w))
                || w.isShowing() != visible) {
            long elapsed = System.currentTimeMillis() - start; 
            if (elapsed > WINDOW_DELAY)
                throw new RuntimeException("Timed out waiting for Window to "
                                           + (visible ? "open" : "close")
                                           + " (" + elapsed + "ms)");
            getRobot().sleep();
        }
        
        getRobot().waitForIdle();
    }
    
    /** Synchronous, safe hide of a window.  The window is ensured to be
     * hidden ({@link java.awt.event.ComponentEvent#COMPONENT_HIDDEN} or
     * equivalent has been posted) when this method returns.  Note that this
     * will <em>not</em> trigger a 
     * {@link java.awt.event.WindowEvent#WINDOW_CLOSING} event; use
     * {@link abbot.tester.WindowTester#actionClose(Component)}
     * if a window manager window close operation is required. 
     */
    public void hideWindow(final Window w) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                w.setVisible(false);
            }
        });
        waitForWindow(w, false);
        // Not strictly required, but if a test is depending on a window
        // event listener's actions on window hide/close, better to wait.
        getRobot().waitForIdle();
    }

    /** Synchronous, safe dispose of a window.  The window is ensured to be
     * disposed ({@link java.awt.event.WindowEvent#WINDOW_CLOSED} has been
     * posted) when this method returns. 
     */
    public void disposeWindow(Window w) {
        w.dispose();
        waitForWindow(w, false);
        getRobot().waitForIdle();
    }

    /** Convenience for <code>getRobot().invokeAndWait(Runnable)</code>. */
    public void invokeAndWait(Runnable runnable) {
        getRobot().invokeAndWait(runnable);
    }

    /** Convenience for <code>getRobot().invokeLater(Runnable)</code>. */
    public void invokeLater(Runnable runnable) {
        getRobot().invokeLater(runnable);
    }

    /** Install the given popup on the given component.  Takes care of
     * installing the appropriate mouse handler to activate the popup.
     */
    public void installPopup(Component invoker, final JPopupMenu popup) {
        invoker.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                mouseReleased(e);
            }
            public void mouseClicked(MouseEvent e) {
                mouseReleased(e);
            }
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /** Safely install and display a popup in the center of the given
     * component, returning when it is visible.  Does not install any mouse
     * handlers not generate any mouse events. 
     */
    public void showPopup(final JPopupMenu popup, final Component invoker) {
        showPopup(popup, invoker, invoker.getWidth()/2, invoker.getHeight()/2);
    }

    /** Safely install and display a popup, returning when it is visible.
        Does not install any mouse handlers not generate any mouse events.
     */
    public void showPopup(final JPopupMenu popup, final Component invoker,
                             final int x, final int y) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                popup.show(invoker, x, y);
            }
        });
        long start = System.currentTimeMillis();
        while (!popup.isShowing()) {
            if (System.currentTimeMillis() - start > POPUP_DELAY)
                throw new RuntimeException("Timed out waiting for popup to show");
            robot.sleep();
        }
        waitForWindow(SwingUtilities.getWindowAncestor(popup), true);
    }

    /** Display a modal dialog and wait for it to show.  Useful for things
     * like
     * {@link javax.swing.JFileChooser#showOpenDialog(java.awt.Component)} or
     * {@link javax.swing.JOptionPane#showInputDialog(Object)}, or any
     * other instance where the dialog contents are not predefined and
     * displaying the dialog involves anything more than 
     * {@link Window#setVisible(boolean) show()/setVisible(true} 
     * (if {@link Window#setVisible(boolean) show()/setVisible(true)} is all 
     * that is required, use the {@link #showWindow(Window)} method instead).<p>
     * The given {@link Runnable} should contain the code which will show the
     * modal {@link Dialog} (and thus block); it will be run on the event
     * dispatch thread.<p>
     * This method will return when a {@link Dialog} becomes visible which
     * contains the given component (which may be any component which will
     * appear on the {@link Dialog}), or the standard timeout (10s) is
     * reached, at which point a {@link RuntimeException} will be thrown.<p>
     * For example,<br>
     <pre><code>
     final Frame parent = ...;
     Dialog d = showModalDialog(new Runnable) {
         public void run() {
             JOptionPane.showInputDialog(parent, "Hit me");
         }
     });
     </code></pre> 
     @see #showWindow(java.awt.Window)
     @see #showWindow(java.awt.Window,java.awt.Dimension)
     @see #showWindow(java.awt.Window,java.awt.Dimension,boolean)
     */
    public Dialog showModalDialog(final Runnable showAction) throws ComponentSearchException {
        return showModalDialog(showAction, new BasicFinder(hierarchy));
    }
    
    /** Same as {@link #showModalDialog(Runnable)}, but provides a custom
     * {@link ComponentFinder} to find the dialog.
     */
    public Dialog showModalDialog(final Runnable showAction, ComponentFinder finder) throws ComponentSearchException {
        final boolean[] modalRun = {false};
        final boolean[] invocationFinished = { false };
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                modalRun[0] = true;
                try {
                    showAction.run();
                }
                finally {
                    // Detect premature Runnable return
                    invocationFinished[0] = true;
                }
            }
        });
        while (!modalRun[0]) {
            try { Thread.sleep(10); }
            catch(InterruptedException e) { 
               throw new InterruptedAbbotException("Interrupted when waiting for dialog");
            }
        }
        // Wait for any modal dialog to appear
        Matcher matcher = new ClassMatcher(Dialog.class, true) {
            public boolean matches(Component c) {
                return super.matches(c) 
                    && ((Dialog)c).isModal() && AWT.containsFocus(c);
            }
        };
        long start = System.currentTimeMillis();
        boolean finished = false;
        while (true) {
            try {
                return (Dialog)finder.find(matcher);
            }
            catch(ComponentSearchException e) {
                if (invocationFinished[0]) {
                    // ensure we do one more check to see if the dialog is there
                    if (finished) break;
                    finished = true;
                }
                if (System.currentTimeMillis() - start > 10000)
                    throw new ComponentSearchException("Timed out waiting for dialog to be ready");
                robot.sleep();
            }
        }
        throw new ComponentSearchException("No dialog was displayed (premature return=" + finished + ")");
    }

    /** Returns whether a Component is showing.  The ID may be the component
     * name or, in the case of a Frame or Dialog, the title.  Regular
     * expressions may be used, but must be delimited by slashes, e.g. /expr/.
     * Returns if one or more matches is found.
     */
    public boolean isShowing(String id) {
        return isShowing(id, new BasicFinder(hierarchy));
    }
    
    /** Same as {@link #isShowing(String)}, but uses the given 
     * ComponentFinder to do the lookup.
     */
    public boolean isShowing(String id, ComponentFinder finder) {
        try {
            finder.find(new WindowMatcher(id, true));
        }
        catch(ComponentNotFoundException e) {
            return false;
        }
        catch(MultipleComponentsFoundException m) {
            // Might not be the one you want, but that's what the docs say
        }
        return true;
    }
}

