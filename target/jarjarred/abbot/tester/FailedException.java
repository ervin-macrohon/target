package abbot.tester;

import java.awt.Component;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

import java.util.ArrayList;
import java.util.List;

/**
 * An exception to note that a process has failed in Abbot and we want to presever
 * the system state.
 */
public class FailedException
   extends RuntimeException
{

    public FailedException(Throwable cause) {
        super(cause);
        systemState = dumpSystemState();
    }
   
    public FailedException(String msg, Throwable cause) {
        super(msg, cause);
        if (cause instanceof FailedException) {
            // Don't make a copy of the system state use the one in cause
            // as that will be closer to where it happened
            systemState = ((FailedException)cause).getSystemState();            
        }
        else {
            systemState = dumpSystemState();
        }
    }

    public FailedException(String msg) {
        super(msg);
        systemState = dumpSystemState();
    }
  
   /**
    * @return The a string which represents the state of the system at the time of the failure
    * in particular thread conditions and the current swing tree.
    */
   public String getSystemState()
   {
      return systemState;
   }

   @Override
   public void printStackTrace(PrintWriter s)
   {
      super.printStackTrace(s);
      s.print(systemState);
   }

   @Override
   public void printStackTrace(PrintStream s)
   {
      super.printStackTrace(s);
      s.print(systemState);
   }

   /**
    * Dump the stacks at the point of failure
    */
   private static String dumpSystemState()
   {

      StringWriter sw = new StringWriter();
      PrintWriter ps = new PrintWriter(sw);

      ps.println("\n\n[TOC Focus, AWT Tree, Stack Traces] \n\n");


      // Stack traces

      ps.println("\n\n[[[Dumping Focus]]] \n\n");

      KeyboardFocusManager currentManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

      Component next = currentManager.getFocusOwner();
      List<Component> tree = new ArrayList<Component>();
      
      while (next!=null) {
          tree.add(0, next);
          next = next.getParent();
      }
      
      ps.printf("Focus owner %s\nFocus root %s\n", tree, currentManager.getCurrentFocusCycleRoot());


      ps.println("\n\n[[[Dumping Windows]]] \n\n");

      for (Frame frame: Frame.getFrames())
      {
         ps.println("Dumping frame " + frame);
         frame.list(ps, 2);
      }


      ps.println("\n\n[[[Dumping Stack Traces]]] \n\n");

      ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
      for (ThreadInfo ti: threadMXBean.dumpAllThreads(true, true))
      {
         ps.print(ti);

         // ThreadInfo only prints out the first n lines, so make sure
         // we write out the rest
         StackTraceElement ste[] = ti.getStackTrace();
         if (ste.length > 8)
         {
            ps.println("[Extra stack]");
            for (int element = 8; element < ste.length; element++)
            {
               ps.println("\tat " + ste[element]);
               for (MonitorInfo mi : ti.getLockedMonitors()) {
                   if (mi.getLockedStackDepth() == element) {
                       ps.append("\t- locked " + mi);
                       ps.append('\n');
                   }
               }
            }
            ps.println("[Extra stack]");
         }

         ps.println();
      }


      long deadlock[] = threadMXBean.findDeadlockedThreads();
      if (deadlock != null && deadlock.length > 0)
      {
         ps.println("Deadlocked threads : ");
         for (long d: deadlock)
         {
            ps.println(threadMXBean.getThreadInfo(d));
         }

         ps.println();
      }

      long monitorLocked[] = threadMXBean.findMonitorDeadlockedThreads();
      if (monitorLocked != null && monitorLocked.length > 0)
      {
         ps.println("Monitor locked threads : ");
         for (long d: monitorLocked)
         {
            ps.println(threadMXBean.getThreadInfo(d));
         }

         ps.println();
      }


      return sw.toString();
   }
   protected static String systemState;
}
