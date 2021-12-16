package nachos.threads;

import nachos.machine.*;
import java.util.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	// Store thread and wake time information
	private class ThreadInQueue {
		private KThread thread;
		private long wakeTime;

		public ThreadInQueue(KThread tiq, long time) {
			thread = tiq;
			wakeTime = time;
		}

		public KThread getThread() {
			return thread;
		}

		public long getWakeTime() {
			return wakeTime;
		}
	}

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		long currentTime = Machine.timer().getTime();

		for (int i = 0; i < alarmQueue.size(); i++) {
			if (currentTime >= alarmQueue.get(i).getWakeTime()) {
				alarmQueue.get(i).getThread().ready();
				alarmQueue.remove(i);
			}
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		boolean intStatus = Machine.interrupt().disable();
		if (x > 0) {
			long wakeTime = Machine.timer().getTime() + x;
			alarmQueue.add(new ThreadInQueue(KThread.currentThread(), wakeTime));
			KThread.currentThread().sleep();
		}
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
    public boolean cancel(KThread thread) {
		boolean threadFound = false;
		boolean intStatus = Machine.interrupt().disable();

		for (int i = 0; i < alarmQueue.size(); i++) {
			// thread found
			if (alarmQueue.get(i).getThread() == thread) {
				threadFound = true;
				alarmQueue.get(i).getThread().ready();
				alarmQueue.remove(i);
				break;
			}
		}

		Machine.interrupt().restore(intStatus);
		return threadFound;
	}

	public static void selfTest() {
		alarmTest1();
		alarmTest2();
	}

	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 1000*1000};
		long t0, t1;
		for (int d : durations) {    
			t0 = Machine.timer().getTime();    
			ThreadedKernel.alarm.waitUntil (d);    
			t1 = Machine.timer().getTime();    
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}    
	}

	public static void alarmTest2() {
		int durations[] = {0, 10, -1, 100, 510};
		long t0, t1;
		for (int d : durations) {    
			t0 = Machine.timer().getTime();    
			ThreadedKernel.alarm.waitUntil (d);    
			t1 = Machine.timer().getTime();    
			System.out.println ("alarmTest2: waited for " + (t1 - t0) + " ticks");
		}    
	}

	private ArrayList <ThreadInQueue> alarmQueue = new ArrayList <ThreadInQueue>();

}

