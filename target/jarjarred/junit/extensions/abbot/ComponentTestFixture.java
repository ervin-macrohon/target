package junit.extensions.abbot;

import abbot.tester.Robot;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Iterator;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import junit.framework.TestCase;
import abbot.*;
import abbot.finder.*;
import abbot.finder.matchers.*;
import abbot.tester.*;
import abbot.tester.Robot;
import abbot.util.*;

import java.util.concurrent.Callable;

import junit.framework.AssertionFailedError;

import org.junit.Assert;

/** Fixture for testing AWT and/or JFC/Swing components under JUnit.  Ensures
 * proper setup and cleanup for a GUI environment.  Provides methods for
 * automatically placing a GUI component within a frame and properly handling
 * Window showing/hiding (including modal dialogs).  Catches exceptions thrown
 * on the event dispatch thread and rethrows them as test failures.<p>
 * Use {@link #showFrame(Component)}
 * when testing individual components, or
 * {@link #showWindow(Window)}
 * when testing a {@link Frame}, {@link Dialog}, or {@link Window}.<p>
 * Any member fields you define which are classes derived from any of the
 * classes in {@link #DISPOSE_CLASSES} will be automatically set to null after
 * the test is run.<p>
 * <bold>WARNING:</bold> Any tests which use significant or scarce resources
 * and reference them in member fields should explicitly null those fields in
 * the tearDown method if those classes are not included or derived from those
 * in {@link #DISPOSE_CLASSES}.  Otherwise the resources will not be subject
 * to GC until the {@link TestCase} itself and any containing
 * {@link junit.framework.TestSuite} is
 * disposed (which, in the case of the standard JUnit test runners, is
 * <i>never</i>).
 */
public abstract class ComponentTestFixture extends TestCase {

    public class EventDispatchException extends InvocationTargetException {
        private EventDispatchException(Throwable t) {
            super(t, "An exception was thrown on the event dispatch thread: "
                  + t.toString());
        }
        public void printStackTrace() {
            getTargetException().printStackTrace();
        }
        public void printStackTrace(PrintStream p) {
            getTargetException().printStackTrace(p);
        }
        public void printStackTrace(PrintWriter p) {
            getTargetException().printStackTrace(p);
        }
    }

    /** Simple matcher that may be used to verify that a specific component is
        found by a given ComponentFinder.
    */    
    protected class ComponentMatcher implements Matcher {
        private Component component;
        public ComponentMatcher(Component c) {
            component = c;
        }
        public boolean matches(Component c) {
            return c == component;
        }
    }

    /** Typical delay to wait for a robot event to be translated into a Java
        event. */
    public static final int EVENT_GENERATION_DELAY = AWTFixtureHelper.EVENT_GENERATION_DELAY;
    public static final int WINDOW_DELAY = AWTFixtureHelper.WINDOW_DELAY;
    public static final int POPUP_DELAY = AWTFixtureHelper.POPUP_DELAY;

    /** Any member data derived from these classes will be automatically set
        to <code>null</code> after the test has run.  This enables GC of said
        classes without GC of the test itself (the default JUnit runners never
        release their references to the tests) or requiring explicit
        <code>null</code>-setting in the {@link TestCase#tearDown()} method.
    */
    protected static final Class[] DISPOSE_CLASSES = {
        Component.class,
        ComponentTester.class
    };

    private AWTFixtureHelper fixtureHelper;
    private Throwable edtException;
    private long edtExceptionTime;
    private ComponentFinder finder;
    private Hierarchy hierarchy;

    /** Return an Abbot {@link abbot.tester.Robot} for basic event generation.
     */ 
    protected Robot getRobot() { return fixtureHelper.getRobot(); }
    /** Return a WindowTracker instance. */
    protected WindowTracker getWindowTracker() { return fixtureHelper.getWindowTracker(); }



    /**
     * Waits until a value equals to the value produced by the closure
     * @param message The message is display if we fail
     * @param expectedValue The expected value
     * @param eventualValue The closure that fetched the value we are testing against
     */
    public static <T> void assertEqualsEventually(final String message, final T expectedValue, final Callable<T> eventualValue) {
        try {
            abbot.tester.Robot.wait(
                new abbot.tester.Robot.ConditionEDTDecorator(null,
                    new Condition()
                    {
                        @Override
                        public boolean test() {
                            try {
                                Assert.assertEquals(
                                    message,
                                    expectedValue,
                                    eventualValue.call());
                                return true;
                            } catch (Throwable e) {
                                return false;
                            }
                        }
                    }));
        } 
        catch (WaitTimedOutException wtoe) {
            try {
                Assert.assertEquals(
                    message,
                    expectedValue,
                    eventualValue.call());
            } catch (Exception e) {
                AssertionFailedError ae = new AssertionFailedError("Failure to process assertion");
                ae.initCause(e);
                throw ae;
            }
        }
    }


    /**
     * Waits until a value produce by the closure is true 
     * @param message The message is display if we fail
     * @param eventualValue The expected value, true of false
     */
    public static void assertTrueEventually(final String message,  final Callable<Boolean> eventualValue) {
        assertEqualsEventually(message, Boolean.TRUE, eventualValue);
    }

    /** Convenience method to sleep for a UI interval 
     * (same as getRobot().sleep()). 
     */
    protected void sleep() {
        getRobot().sleep();
    }
    
    /** This method should be invoked to display the component under test.
     * The frame's size will be its preferred size.  This method will return
     * with the enclosing {@link Frame} is showing and ready for input.
     */
    protected Frame showFrame(Component comp) {
        return fixtureHelper.showFrame(comp, null, getName());
    }

    /** This method should be invoked to display the component under test,
     * when a specific size of frame is desired.  The method will return when
     * the enclosing {@link Frame} is showing and ready for input.  
     * @param comp
     * @param size Desired size of the enclosing frame, or <code>null</code>
     * to make no explicit adjustments to its size.
     */
    protected Frame showFrame(Component comp, Dimension size) {
        return fixtureHelper.showFrame(comp, size, getName());
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the {@link Window} is showing and ready for input.
     */
    protected void showWindow(Window w) {
        showWindow(w, null, true);
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the {@link Window} is showing and ready for input.
     */
    protected void showWindow(final Window w, final Dimension size) {
        showWindow(w, size, true);
    }

    /** Safely display a window with proper EDT synchronization.   This method
     * blocks until the window is showing.  This method will return even when
     * the window is a modal dialog, since the show method is called on the
     * event dispatch thread.  The window will be packed if the pack flag is
     * set, and set to the given size if it is non-<code>null</code>.<p>
     * Modal dialogs may be shown with this method without blocking.
     */
    protected void showWindow(final Window w, final Dimension size,
                              final boolean pack) {
        fixtureHelper.showWindow(w, size, pack);
    }

    /** Return when the window is ready for input or times out waiting.
     * @param w
     */
    protected void waitForWindow(Window w, boolean visible) {
        fixtureHelper.waitForWindow(w, visible);
    }
    
    /** Synchronous, safe hide of a window.  The window is ensured to be
     * hidden ({@link java.awt.event.ComponentEvent#COMPONENT_HIDDEN} or
     * equivalent has been posted) when this method returns.  Note that this
     * will <em>not</em> trigger a 
     * {@link java.awt.event.WindowEvent#WINDOW_CLOSING} event; use
     * {@link abbot.tester.WindowTester#actionClose(Component)}
     * if a window manager window close operation is required. 
     */
    protected void hideWindow(final Window w) {
        fixtureHelper.hideWindow(w);
    }

    /** Synchronous, safe dispose of a window.  The window is ensured to be
     * disposed ({@link java.awt.event.WindowEvent#WINDOW_CLOSED} has been
     * posted) when this method returns. 
     */
    protected void disposeWindow(Window w) {
        fixtureHelper.disposeWindow(w);
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
    protected void setModifiers(int modifiers, boolean pressed) {
        fixtureHelper.setModifiers(modifiers, pressed);
    }
    
    
    /** Convenience for <code>getRobot().invokeAndWait(Runnable)</code>. */
    protected void invokeAndWait(Runnable runnable) {
        fixtureHelper.invokeAndWait(runnable);
    }

    /** Convenience for <code>getRobot().invokeLater(Runnable)</code>. */
    protected void invokeLater(Runnable runnable) {
        fixtureHelper.invokeLater(runnable);
    }

    /** Install the given popup on the given component.  Takes care of
     * installing the appropriate mouse handler to activate the popup.
     */
    protected void installPopup(Component invoker, final JPopupMenu popup) {
        fixtureHelper.installPopup(invoker, popup);
    }

    /** Safely install and display a popup in the center of the given
     * component, returning when it is visible.  Does not install any mouse
     * handlers not generate any mouse events. 
     */
    protected void showPopup(final JPopupMenu popup, final Component invoker) {
        showPopup(popup, invoker, invoker.getWidth()/2, invoker.getHeight()/2);
    }

    /** Safely install and display a popup, returning when it is visible.
        Does not install any mouse handlers not generate any mouse events.
     */
    protected void showPopup(final JPopupMenu popup, final Component invoker,
                             final int x, final int y) {
        fixtureHelper.showPopup(popup, invoker, x, y);
    }

    /** Display a modal dialog and wait for it to show.  Useful for things
     * like {@link JFileChooser#showOpenDialog(Component)} or
     * {@link JOptionPane#showInputDialog(Component,Object)}, or any
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
    protected Dialog showModalDialog(final Runnable showAction) throws ComponentSearchException {
        return fixtureHelper.showModalDialog(showAction, getFinder());
    }

    /** Similar to {@link #showModalDialog(Runnable)},
     * but provides for the case where some of the {@link Dialog}'s contents
     * are known beforehand.<p>
     * @deprecated Use {@link #showModalDialog(Runnable)} instead.
     */
    protected Dialog showModalDialog(Runnable showAction, Component contents)
        throws Exception {
        return showModalDialog(showAction);
    }

    /** Returns whether a Component is showing.  The ID may be the component
     * name or, in the case of a Frame or Dialog, the title.  Regular
     * expressions may be used, but must be delimited by slashes, e.g. /expr/.
     * Returns if one or more matches is found.
     */
    protected boolean isShowing(String id) {
        try {
            getFinder().find(new WindowMatcher(id, true));
        }
        catch(ComponentNotFoundException e) {
            return false;
        }
        catch(MultipleComponentsFoundException m) {
            // Might not be the one you want, but that's what the docs say
        }
        return true;
    }

    /** Construct a test case with the given name.  */
    public ComponentTestFixture(String name) {
        super(name);
    }

    /** Default Constructor.  The name will be automatically set from the
        selected test method.
    */ 
    public ComponentTestFixture() { }

    /** Ensure proper test harness setup and teardown that won't
     * be inadvertently overridden by a derived class. 
     */
    protected void fixtureSetUp() throws Throwable {
        hierarchy = createHierarchy();

        finder = new BasicFinder(hierarchy);

        fixtureHelper = new AWTFixtureHelper(hierarchy) {
            // backward-compatibility
            protected void disposeAll() {
                ComponentTestFixture.this.disposeAll();
            }
        };
    }
    
    /** Handles restoration of system state.  Automatically disposes of any
        Components used in the test.
    */
    protected void fixtureTearDown() throws Throwable {
        edtExceptionTime = fixtureHelper.getEventDispatchErrorTime();
        edtException = fixtureHelper.getEventDispatchError();
        fixtureHelper.dispose();
        fixtureHelper = null;
        clearTestFields();
        // Explicitly set these null, since the test fixture instance may
        // be kept around by the test runner
        hierarchy = null;
        finder = null;
    }

    /** Dispose of all extant windows.
     * @deprecated This functionality is now deferred to AWTFixtureHelper 
     */
    protected void disposeAll() {
        Iterator iter = hierarchy.getRoots().iterator();
        while (iter.hasNext()) {
            hierarchy.dispose((Window)iter.next());
        }
    }

    /** Clears all non-static {@link TestCase} fields which are instances of
     * any class found in {@link #DISPOSE_CLASSES}.
     */
    private void clearTestFields() {
        try {
            Field[] fields = getClass().getDeclaredFields();
            for (int i=0;i < fields.length;i++) {
                if ((fields[i].getModifiers() & Modifier.STATIC) == 0) {
                    fields[i].setAccessible(true);
                    for (int c=0;c < DISPOSE_CLASSES.length;c++) {
                        Class cls = DISPOSE_CLASSES[c];
                        if (cls.isAssignableFrom(fields[i].getType())) {
                            fields[i].set(this, null);
                        }
                    }
                }
            }
        }
        catch(Exception e) {
            Log.warn(e);
        }
    }

    /** Override the default <code>junit.framework.TestCase#RunBare()</code>
     * to ensure proper test harness setup and teardown that won't
     * likely be accidentally overridden by a derived class.
     * <p> 
     * If any exceptions are thrown on the event dispatch thread, they count
     * as errors.  They will not, however supersede any failures/errors
     * thrown by the test itself unless thrown prior to the main test
     * failure. 
     */
    public void runBare() throws Throwable {
        if (Boolean.getBoolean("abbot.skip_ui_tests")) {
            return;
        }
        
        Throwable exception = null;
        long exceptionTime = -1;
        try {
            try {
                fixtureSetUp();
                super.runBare();
            }
            catch(Throwable e) {
                exception = e;
            }
            finally {
                Log.log("tearing down fixture: " + getName());
                try {
                    fixtureTearDown();
                }
                catch(Throwable tearingDown) {
                    if (exception == null)
                        exception = tearingDown;
                }
            }
            if (exception != null)
                throw exception;
        }
        catch(Throwable e) {
            exceptionTime = System.currentTimeMillis();
            exception = e;
        }
        finally {
            // Cf. StepRunner.runStep()
            // Any EDT exception which occurred *prior* to when the
            // exception on the main thread was thrown should be used
            // instead.
            if (edtException != null
                && (exception == null
                    || edtExceptionTime < exceptionTime)) {
                exception = new EventDispatchException(edtException);
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
    /** Provide for derived classes to provide their own Hierarchy. */
    protected Hierarchy createHierarchy() {
        return new TestHierarchy();
    }
    /** Obtain a component finder to look up components. */
    protected ComponentFinder getFinder() { return finder; }
    /** Obtain a consistent hierarchy. */
    protected Hierarchy getHierarchy() { return hierarchy; }
}
