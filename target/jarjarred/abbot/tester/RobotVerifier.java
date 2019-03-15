package abbot.tester;

import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;

import abbot.util.Bugs;

/** Provides methods to verify that the robot on the current platform works
 * properly.
 */
public class RobotVerifier {
    // No instantiations
    private RobotVerifier() { }

    private static final String WINDOW_NAME = "Abbot Robot Verification";

    /** Auto-detect whether the robot actually works. 
        Use this to tell whether we're in w32 service mode without access
        to the desktop.
        @return false if the robot fails.
    */
    public static boolean verify(java.awt.Robot robot) {
        if (!Bugs.needsRobotVerification())
            return true;

        final int SIZE = 4;
        Frame f = new Frame(WINDOW_NAME);
        f.setName(WINDOW_NAME);
        
        try
        {
           Window w = new Window(f);
           Color color = new Color(0, 1, 2);
           w.setBackground(color);
           w.setName(WINDOW_NAME);
           w.pack();
   
           // Need try/finally after a pack as component is now allocated.
           
           try {
   
               w.setSize(SIZE, SIZE);
               w.setLocation(100, 100);
               w.setVisible(true);
           
               robot.waitForIdle();
               WindowTracker tracker = WindowTracker.getTracker();
               while (!tracker.isWindowReady(w)) {
                   robot.delay(20);
               }
   
               Color sample = robot.getPixelColor(100, 100);
               
               return sample.equals(color);
           }
           finally {
               w.setVisible(false);
               w.dispose();
           }
        }
        finally {
           f.dispose();
        }
    }

    /** Quick printout of whether the robot works. */
    public static void main(String[] args) {
        try {
            boolean works = verify(new java.awt.Robot());
            System.out.println("Robot is " + (works ? "" : "not ") + "functional");
        }
        catch (AWTException e) {
            System.out.println("Robot is not available");
        }
        System.exit(0);
    }
}
