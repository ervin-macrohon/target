package abbot.finder.matchers;

import java.awt.Component;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import abbot.finder.Matcher;
import abbot.util.ExtendedComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches a {@link JMenuItem} given a simple label or a menu path of the
 * format "menu|submenu|menuitem", for example "File|Open|Can of worms".
 *
 * @author twall
 * @version $Id: JMenuItemMatcher.java 2815 2012-01-26 17:37:22Z gdavison $
 */
public class JMenuItemMatcher implements Matcher {
    private String label;

    public JMenuItemMatcher(String label) {
        this.label = label;
    }

    public static String getPath(JMenuItem item) {
        Component parent = item.getParent();
        if (parent instanceof JPopupMenu) {
            parent = ((JPopupMenu)parent).getInvoker();
        }
        if (parent instanceof JMenuItem) {
            return getPath((JMenuItem)parent) + "|" + item.getText();
        }
        return item.getText();
    }
    
    
    /**
     * @param path A path of the form File|Open|Can of worms
     * @return A list of strings, File, File|Open, File|Open|Can of worms
     */
    
    public static List splitMenuPath(String path) {
        // Split the path
        //
        
        int lastFoundIndex = -1;
        java.util.List selectionPath = new ArrayList();
        while ((lastFoundIndex = path.indexOf('|', lastFoundIndex))!=-1)
        {
            selectionPath.add(path.substring(
                0, lastFoundIndex));
            lastFoundIndex = lastFoundIndex + 1;
        }
        
        selectionPath.add(path);
        return selectionPath;
    }
    

    public boolean matches(Component c) {
        if (c instanceof JMenuItem) {
            JMenuItem mi = (JMenuItem)c;
            String text = mi.getText();
            return ExtendedComparator.stringsMatch(label, text)
                || ExtendedComparator.stringsMatch(label, getPath(mi));
        }
        return false;
    }


    public String toString()
    {
      return getClass().getSimpleName() + "[" +  label +  "]"; 
    }
}
