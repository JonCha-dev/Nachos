Group members: 
- Jon Chang


Coding:
-----------
Alarm:
- For Alarm, I used an Array list of "ThreadInQueue" objects to serve as a 
queue for the threads. The "ThreadInQueue" is a private class I implemented to 
associate the thread with its assigned wake-time. waitUntil() inserts the thread and its assigned wake-time into the queue and sleeps the current thread. timerInterrupt() will iterate through the queue and sets any threads at or past its wake-time to ready state.

Join:
- For Join, I defined two new member variables: one that checks if the thread has already been joined, and one that points to the parent thread. join() checks whether the child thread has finished, and either blocks or doesn't block the parent thread accordingly. finish() was also modified such that if a child thread had a parent that is still in blocked state, it would be set to ready.

Condition2:
- For Condition2, I followed a similar format to how Condition was designed, but instead using a linkedlist of KThreads as the queue. sleep() releases the held lock, then sleeps the current thread after putting it into the waitqueue, and will reacquire the lock once it is woken up. wake() removes the first element of the queue (if any) and sets it to ready state. wakeAll() calls wake() until the queue is empty.

sleepFor:
- For sleepFor, I defined cancel() in Alarm class to forcibly remove and ready threads that were waiting for a certain time, and modified wake() to only call ready() if cancel() does not find the thread in the Alarm queue. sleepFor() functions similar to sleep(), but instead utilizes Alarm's waitUntil to set a wait-time for the thread, and also removes the thread from the queue if it times out so ready() is not called twice. 

GameMatch:
- For GameMatch, I defined int counters for all three ranks and the match #, along with their corresponding locks. Three conditions were also defined per rank. play() has similar code for all three ranks, where it acquires its corresponding lock, increments the count, and sleeps the thread if the player cap is not reached. However, if it is reached, wakeAll is called on the correct condition and match count is safely incremented. Finally, if the thread contains a valid argument, then it will safely get and return the match count.  


Testing:
-----------
For testing, the given tests were run on each part of the assignment as it was completed, as well as some variant tests that were based on the guidelines in the writeup. I used nachos -d t to display thread information in the debugging process as well. 


How well it worked:
------------
Good enough. Probably. I hope. 


Contributions:
------------
Me. I did everything. I don't know what to put here. I mean, it'd be pretty weird if I put someone else's name here despite being a solo group. 
