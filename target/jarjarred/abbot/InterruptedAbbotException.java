package abbot;

import abbot.tester.FailedException;

/**
 * Record the case where the current thread has been interrupted,
 * a proxy for the normal 
 */
public class InterruptedAbbotException extends FailedException {
//    public WaitTimedOutError() {  }
    public InterruptedAbbotException(String msg) { super(msg); }
}
