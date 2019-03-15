package abbot.tester;

import java.awt.*;
import java.util.ArrayList;

import javax.swing.JTabbedPane;
import javax.swing.plaf.TabbedPaneUI;

import abbot.tester.JTabbedPaneLocation.TabNotVisibleException;

import java.util.concurrent.Callable;

/** Provides user actions on a JTabbedPane. */
public class JTabbedPaneTester extends JComponentTester {

    /** Return an array of strings that represent the tabs in the pane.
     */
    public String[] getTabs(final Component comp) {
        return Robot.callAndWait(comp, new Callable<String[]>() {

            @Override
            public String[] call() {
                JTabbedPane tp = (JTabbedPane)comp;
                int count = tp.getTabCount();
                ArrayList list = new ArrayList(count);
                for (int i=0;i < count;i++) {
                    list.add(tp.getTitleAt(i));
                }
                return (String[])list.toArray(new String[count]);
            }
        });
    }

    public void actionSelectTab(final Component comp, final JTabbedPaneLocation loc) {
        Point pt;
        try {
            // Fix EDT violation, access this point information on the event queue
            //
            pt = Robot.callAndWait(comp, new Callable<Point>() {

                @Override
                public Point call() {
                    return loc.getPoint(comp);
                }
            });

            actionClick(comp, new ComponentLocation(pt));
        }
        catch(final TabNotVisibleException e) {
            // Set the tab directly
            invokeAndWait(new Runnable() { public void run() { 
                ((JTabbedPane)comp).setSelectedIndex(e.index);
            }});
        }
        
        waitForIdle();
    }

    /** Parse the String representation of a JTableLocation into the actual
        JTableLocation object.
    */
    public ComponentLocation parseLocation(String encoded) {
        return new JTabbedPaneLocation().parse(encoded);
    }

    /** Return (in order of preference) the location corresponding to value,
     * cell, or coordinate.
     */
    public ComponentLocation getLocation(final Component c, final Point p) {
        final JTabbedPane tabs = (JTabbedPane)c;
        final TabbedPaneUI ui = tabs.getUI();
        return Robot.callAndWait(c, new Callable<JTabbedPaneLocation>() {
            public JTabbedPaneLocation call() {
                int index = ui.tabForCoordinate(tabs, p.x, p.y);
                if (index != -1) {
                    String name = tabs.getTitleAt(index);
                    return new JTabbedPaneLocation(name);
                }
                return new JTabbedPaneLocation(p);

            }
        });
    }
}
