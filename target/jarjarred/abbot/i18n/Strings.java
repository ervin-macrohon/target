package abbot.i18n;

import java.util.List;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import abbot.Log;
import abbot.editor.widgets.TextFormat;

/** Provides support for loading localized strings.  Bundles may be added
    by specifying the full resource name or by a simple name located in
    com.oculustech.i18n.<p>
    Bundles are searched from most- to least-recently added.
*/
public class Strings {

    /** Resources whose name ends with this suffix (".tip") will automatically
     * be formatted by the tooltip formatter.
     * @see TextFormat#tooltip
     */
    public static final String TOOLTIP_SUFFIX = ".tip";
    /** Resources whose name ends with this suffix (".dlg") will automatically
     * be formatted by the dialog formatter.
     * @see TextFormat#dialog
     */
    public static final String DIALOG_SUFFIX = ".dlg";
    
    private static final String PREFIX = 
        Strings.class.getPackage().getName() + ".";
    private static final String CORE_BUNDLE = "abbot";
    private static List bundles = new ArrayList();

    static {
        // Load the default bundle
        try {
            addBundle(CORE_BUNDLE);
        }
        catch(MissingResourceException e) {
            String msg = "No resource bundle found in " + CORE_BUNDLE;
            if (System.getProperty("java.class.path").indexOf("junit") != -1) 
                Log.warn(msg);
            else
                throw new Error(msg);
        }
    }

    /** Add the given bundle to the list searched. */
    public static ResourceBundle addBundle(String name)
        throws MissingResourceException {
        return addBundle(name, null);
    }

    /** Add the given bundle to the list searched, loading from the given
        class loader.  The bundle will be weakly referenced.
    */
    public static ResourceBundle addBundle(String name, ClassLoader cl) 
        throws MissingResourceException {
        boolean wrap = cl != null;
        if (cl == null) {
            cl = Strings.class.getClassLoader();
        }
        Locale locale = Locale.getDefault();
        ResourceBundle b;
        try {
            b = ResourceBundle.getBundle(name, locale, cl);
            Log.debug("Added resource bundle " + name);
        }
        catch(MissingResourceException e) {
            try {
                b = ResourceBundle.getBundle(PREFIX + name, locale, cl);
                Log.debug("Added resource bundle " + PREFIX + name);
            }
            catch(MissingResourceException e2) {
                throw e;
            }
        }
        synchronized(bundles) {
            bundles.add(0, wrap ? new WeakReference(b) : (Object)b);
        }
        return b;
    }

    /** Add the given {@link ResourceBundle} as one to be searched. */
    public static void addBundle(ResourceBundle b) {
        synchronized(bundles) {
            bundles.add(0, b);
        }
    }
    
    private Strings() { }

    /** Returns the localized String for the given key, or the key surrounded
        by '#' if no corresponding localized string is found.
    */
    public static String get(String key) {
        return get(key, false);
    }

    /** Returns the localized string for the given key.  If optional is true,
        return null, otherwise returns the key surrounded by '#' if no
        corresponding localized string is found. 
    */
    public static String get(String key, boolean optional) {
        String defaultValue = "#" + key + "#";
        String value = null;
        synchronized(bundles) {
            ListIterator iter = bundles.listIterator(bundles.size());
            while (iter.hasPrevious()) {
                Object bundle = iter.previous();
                if (bundle instanceof WeakReference) {
                    bundle = ((WeakReference)bundle).get();
                    if (bundle == null) {
                        iter.remove();
                        continue;
                    }
                }
                try {
                    value = ((ResourceBundle)bundle).getString(key);
                }
                catch(MissingResourceException mre) {
                }
            }
        }
        if (value == null) {
            if (!optional) {
                Log.log("Missing resource '" + key + "'");
                value = defaultValue;
            }
        }
        else {
            if (key.endsWith(TOOLTIP_SUFFIX)) {
                value = TextFormat.tooltip(value);
            }
            else if (key.endsWith(DIALOG_SUFFIX)) {
                value = TextFormat.dialog(value);
            }
        }
        return value;
    }

    /** Returns a formatted localized string for the given key and arguments,
        or the key if no corresponding localized string is found.  Use
        {@link java.text.MessageFormat} syntax for the format string and 
        arguments.
    */
    public static String get(String key, Object... args) {
        return new MessageFormat(get(key)).format(args);
    }
    


}
