Group members: 
- Jon Chang


Coding:
-----------
For all implemented functions, initial validity checks are done on all arguments, returning -1 to indicate any errors.

Part 1:
- handleCreate and handleOpen have similar implementations in that both open a file and assign a FD to it,
with a minor difference in the call to fileSystem.open
- handleRead and handleWrite reads/writes in partitions of page size, storing data in a local buffer and reading/writing it into virtual memory.
A final read/write is implemented to cover the case where the remaining amount to read/write is less than the page size.
- handleClose retrieves the file at the specified FD and closes it, then frees the index in the FD table.
- handleUnlink gets the file name from the given virtual address, and calls fileSystem.remove on the filename.

Part 2:
- UserProcess was modified such that the pageTable is initialized in loadSections. 
- A linkedlist of type Integer is initialized in UserKernel, and contains ints from 0 to # of physical pages, indicating which 
physical pages are free for use. A lock is used to ensure synchronization.
- loadSections was modified to load in section pages, and the stack/arg pages.
- unloadSections was modified to free up physical pages for use in other processes.
- readVirtualMemory and writeVirtualMemory were modified similar to handleRead and handleWrite of part 1, in that read/writes were done
in partitions; the functions first account for the initial offset and read up to (page size - offset), then reads full pages, and finally
reads the remaining bytes less than a page (if any).

Part 3:
- In UserKernel, a pid and process counter was added. Locks are used to ensure synchronization. 
- Member variables created to keep track of parent/children/exit status/abnormal exits. A lock and condition are used for sleeping and waking
parent processes in joining. 
- handleExec gets the filename, and gets each arg from the virtual address of argv and stores them in an array of strings. This filename and array 
is then used to call execute and create a new child process. 
- handleJoin removes the child from the parent process and sleeps the parent process until the child process is done (exit status exists).
The exit status is then written back to the status pointer arg.
- handleExit closes files in FD table, frees up physical memory, closes the program, and wakes any parent processes waiting on a child
process. A check is done to see if the last process called exit, in which case Kernel.kernel.terminate() is called. 
- handleException was modified to flag abnormal exits of child processes.

Testing:
-----------
For testing, I used the provided sample tests and modified them to check various edge cases. Additionally, I placed a debug message after most 
conditional checks to more easily locate bugs.


