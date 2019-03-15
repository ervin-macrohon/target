package abbot.util;

import java.lang.reflect.Array;

/** Utility class to perform comparisons. */
public class ExtendedComparator {
    private ExtendedComparator() { }

    /** Match with a regexp if the pattern contains a ".*" or is bounded by
     * slashes (/regexp/).  Multiline matches are enabled by /(?m)regexp/.
     * Embedded newlines ("\n") in the match string will then match
     * end-of-lines.
     */
    // Requiring exact matches eliminates the need to always include the start
    // "^" and finish "$" symbols. 
    public static boolean stringsMatch(String pattern, String actual) {
        if (pattern.startsWith("/")
            && pattern.substring(1).endsWith("/")) {
            pattern = pattern.substring(1, pattern.length()-1);
            return Regexp.stringMatch(pattern, actual);
        }
        if (pattern.indexOf(".*") != -1) {
            return Regexp.stringMatch(pattern, actual);
        }
        return pattern.equals(actual);
    }

    /** Perform element-by-element comparisons of arrays in addition to
        regular comparisons.  */
    public static boolean equals(Object obj1, Object obj2) {
        boolean result = false;
        if (obj1 == null && obj2 == null) {
            result = true;
        }
        else if (obj1 == null && obj2 != null
                 || obj2 == null && obj1 != null) {
            result = false;
        }
        else if (obj1.equals(obj2)) {
            result = true;
        }
        // If both are strings, check for a regexp match
        else if (obj1 instanceof String && obj2 instanceof String) {
            result = stringsMatch((String)obj1, (String)obj2);
        }
        else if (obj1.getClass().isArray() && obj2.getClass().isArray()) {
            if (Array.getLength(obj1) == Array.getLength(obj2)) {
                result = true;
                for (int i=0;i < Array.getLength(obj1);i++) {
                    if (!equals(Array.get(obj1, i), Array.get(obj2, i))) {
                        result = false;
                        break;
                    }
                }
            }
        }
        return result;
    }
}
