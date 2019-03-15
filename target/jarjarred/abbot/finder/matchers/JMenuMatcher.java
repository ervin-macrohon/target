package abbot.finder.matchers;

import abbot.finder.Matcher;

import abbot.util.ExtendedComparator;

import java.awt.Component;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Extension of JMenuItemMatcher that only matches JMenu rather than
 * all JMenuItem
 *
 * @author gdavison
 * @version $Id: JMenuMatcher.java 1419 2005-01-05 18:34:48 +0000 (Wed, 05 Jan 2005) twall $
 */
public class JMenuMatcher extends JMenuItemMatcher {

    public JMenuMatcher(String label) {
        super(label);
    }



    public boolean matches(Component c) {
        if (c instanceof JMenu) {
            return super.matches(c);
        }
        return false;
    }

}
