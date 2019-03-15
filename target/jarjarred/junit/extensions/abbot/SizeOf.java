package junit.extensions.abbot;

import abbot.InterruptedAbbotException;

import java.util.ArrayList;

public class SizeOf {

    /** For use with constructors which require arguments. */
    public static interface ObjectCreator {
        Object create();
    }

    private static class ClassCreator implements ObjectCreator {
        private Class cls;
        public ClassCreator(Class cls) {
            this.cls = cls;
        }
        public Object create() {
            try {
                return cls.newInstance();
            }
            catch(IllegalAccessException e) {
                String msg = "No-argument constructor is "
                    + "private for " + cls;
                throw new IllegalArgumentException(msg);
            }
            catch(InstantiationException e) {
                String msg = "Can't create an instance of " + cls;
                throw new IllegalArgumentException(msg);
            }
            catch(Exception e) {
                e.printStackTrace();
                String msg = "Can't obtain size of " + cls;
                throw new IllegalArgumentException(msg);
            }
        }
    }

    private static final int GCCOUNT = 2;
    
    private static void gc() {
        try {
            System.gc(); Thread.sleep(100);
            System.runFinalization(); Thread.sleep(100);
        }
        catch(InterruptedException e) {
           throw new InterruptedAbbotException("Interrupted when waiting for gc");

        }
    }

    private static void gc(int n) {
        for (int i=0;i < n;i++) { gc(); }
    }

    public static long getMemoryUse() {
        gc(GCCOUNT);
        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        return total - free;
    }

    /** Return the approximate size in bytes of the object returned by the
        given ObjectCreator.
    */ 
    public static long sizeof(ObjectCreator oc) {
        final int SIZE = 1000;
        ArrayList list = new ArrayList(SIZE);
        long before = getMemoryUse();
        for (int i=0;i < SIZE;i++) {
            list.add(oc.create());
        }
        long after = getMemoryUse();
        return (after - before) / SIZE;
    }

    /** Return the approximate size in bytes of an instance of the given
        class.  The class <em>must</em> provide a no-args constructor.
    */ 
    public static long sizeof(Class cls) {
        return sizeof(new ClassCreator(cls));
    }

    /** Display the approximate size (in bytes) of the class given in the
        first argument.
    */
    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                System.err.println("Use with a single class name argument");
                System.exit(1);
            }
            Class cls = Class.forName(args[0]);
            System.out.println("Class " + cls.getName() 
                               + " size is " + sizeof(cls) + " bytes");
        }
        catch(Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
