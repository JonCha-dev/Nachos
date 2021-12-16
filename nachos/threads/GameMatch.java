package nachos.threads;

import nachos.machine.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    private static int beginnerCount;
    private static int intermediateCount;
    private static int expertCount;
    private static int matchCount;

    private static int maxPlayers;

    private static Lock matchLock;
    private static Lock beginnerLock;
    private static Lock intermediateLock;
    private static Lock expertLock;        

    private static Condition beginnerQ;
    private static Condition intermediateQ;
    private static Condition expertQ;    

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        matchLock = new Lock();
        beginnerLock = new Lock();
        intermediateLock = new Lock();
        expertLock = new Lock();  
 
        beginnerQ = new Condition(beginnerLock);
        intermediateQ = new Condition(intermediateLock);
        expertQ = new Condition(expertLock);   

        maxPlayers = numPlayersInMatch;
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {
        int match = -1;
        boolean validAbility = false;

        // beginner
        if (ability == abilityBeginner) {
            validAbility = true;
            beginnerLock.acquire();
            beginnerCount++;
            
            // full lobby
            if (beginnerCount == maxPlayers) {
                beginnerCount = 0;
                beginnerQ.wakeAll();

                matchLock.acquire();
                matchCount++;
                matchLock.release();

            } else {
                beginnerQ.sleep();
            }
            beginnerLock.release();
        
        // intermediate
        } else if (ability == abilityIntermediate) {
            validAbility = true;
            intermediateLock.acquire();
            intermediateCount++;
            
            // full lobby
            if (intermediateCount == maxPlayers) {
                intermediateCount = 0;
                intermediateQ.wakeAll();
                
                matchLock.acquire();
                matchCount++;
                matchLock.release();

            } else {
                intermediateQ.sleep();
            }
            intermediateLock.release();
        
        // expert
        } else if (ability == abilityExpert) {
            validAbility = true;
            expertLock.acquire();
            expertCount++;
            
            // full lobby
            if (expertCount == maxPlayers) {
                expertCount = 0;
                expertQ.wakeAll();

                matchLock.acquire();
                matchCount++;
                matchLock.release();

            } else {
                expertQ.sleep();
            }
            expertLock.release();
        }

        // get match count
        if (validAbility) {
            matchLock.acquire();
            match = matchCount;
            matchLock.release();
        }

	    return match;
    }

    public static void selfTest() {
        matchTest4();
    }

    public static void matchTest4 () {
        final GameMatch match = new GameMatch(2);
    
        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg1.setName("B1");
    
        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
            });
        beg2.setName("B2");
    
        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
            });
        int1.setName("I1");
    
        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }
            });
        exp1.setName("E1");
    
        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
    
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
    }
}
