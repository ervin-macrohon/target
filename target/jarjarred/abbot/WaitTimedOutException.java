package abbot;

import abbot.tester.FailedException;

/**
 * Record the case where are have failed to wait for something, used to
 * extend an AssertionError; but now extends FailException so we get the
 * extra diagnostics.
 */
public class WaitTimedOutException extends FailedException {
//    public WaitTimedOutError() {  }
    public WaitTimedOutException(String msg) { super(msg); }
}
