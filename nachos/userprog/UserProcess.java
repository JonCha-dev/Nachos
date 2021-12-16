package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		UserKernel.pidLock.acquire();
		processPID = UserKernel.pid;
		UserKernel.pid++;
		UserKernel.pidLock.release();

		UserKernel.pcLock.acquire();
		UserKernel.processCount++;
		UserKernel.pcLock.release();

		fileTable = new OpenFile[16];
		fileTable[0] = UserKernel.console.openForReading();
		Lib.debug(dbgProcess, "stdin in table: " + fileTable[0]);
		fileTable[1] = UserKernel.console.openForWriting();
		Lib.debug(dbgProcess, "stdout in table: " + fileTable[1]);

		statusLock = new Lock();
		joinQueue = new Condition(statusLock);
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		// get addresses
		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOffset = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + vpnOffset;
		
		byte[] memory = Machine.processor().getMemory();

		int bytesRead = 0;
		int bytesToRead = length;
		int dataOffset = offset;
		int dataLength = 0;
		boolean firstRead = true;

		while(bytesRead < length) {
			// read first + full pages
			if((bytesToRead + vpnOffset) > pageSize) {
				// invalid entry
				if(!pageTable[vpn].valid) {
					Lib.debug(dbgProcess, "attempt to access invalid vpn in readvirtualmemory.");
					return bytesRead;
				}

				dataLength = pageSize - vpnOffset;

				System.arraycopy(memory, paddr, data, dataOffset, dataLength);	
				pageTable[vpn].used = true;

				bytesRead += dataLength;
				dataOffset += dataLength;
				bytesToRead -= dataLength;
				vpn++;

				// check if invalid index
				if(vpn >= pageTable.length) {
					Lib.debug(dbgProcess, "invalid index in readvirtualmemory.");
					return bytesRead;
				}

				// remove offset for reading full pages 
				if(firstRead) {
					firstRead = false;
					vpnOffset = 0;
				}

				ppn = pageTable[vpn].ppn;
				paddr = pageSize * ppn;

			// read rest of bytes
			} else {
				System.arraycopy(memory, paddr, data, dataOffset, bytesToRead);
				pageTable[vpn].used = true;

				bytesRead += bytesToRead;
			}
		}

		return bytesRead;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOffset = Processor.offsetFromAddress(vaddr);
		int ppn = pageTable[vpn].ppn;
		int paddr = pageSize * ppn + vpnOffset;

		byte[] memory = Machine.processor().getMemory();

		int bytesWritten = 0;
		int bytesToWrite = length;
		int dataOffset = offset;
		int dataLength = 0;
		boolean firstWrite = true;

		while(bytesWritten < length) {
			// read first + full pages
			if((bytesToWrite + vpnOffset) > pageSize) {
				// invalid entry
				if(!pageTable[vpn].valid) {
					Lib.debug(dbgProcess, "attempt to access invalid vpn in writevirtualmemory.");
					return bytesWritten;
				// read-only
				} else if(pageTable[vpn].readOnly) {
					Lib.debug(dbgProcess, "attempt to write in readonly page.");
					return bytesWritten;
				}

				dataLength = pageSize - vpnOffset;
				System.arraycopy(data, dataOffset, memory, paddr, dataLength);	
				pageTable[vpn].used = true;
				pageTable[vpn].dirty = true;

				bytesWritten += dataLength;
				dataOffset += dataLength;
				bytesToWrite -= dataLength;
				vpn++;
				
				// check if invalid index
				if(vpn >= pageTable.length) {
					Lib.debug(dbgProcess, "invalid index in writevirtualmemory.");
					return bytesWritten;
				}

				// remove offset for reading full pages 
				if(firstWrite) {
					firstWrite = false;
					vpnOffset = 0;
				}

				ppn = pageTable[vpn].ppn;
				paddr = pageSize * ppn;

			// read rest of bytes
			} else {
				System.arraycopy(data, dataOffset, memory, paddr, bytesToWrite);
				pageTable[vpn].used = true;
				pageTable[vpn].dirty = true;

				bytesWritten += bytesToWrite;
			}
		}

		return bytesWritten;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		int pagesDone = 0;
		pageTable = new TranslationEntry[numPages];

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				UserKernel.pagesLock.acquire();
				// not enough free phys pages
				if(UserKernel.freePages.size() == 0) {
					Lib.debug(dbgProcess, "not enough physical memory.");
					UserKernel.pagesLock.release();
					unloadSections();
					return false;
				}
				// get free phys page  
				int freePageIdx = UserKernel.freePages.removeFirst();
				UserKernel.pagesLock.release();

				int vpn = section.getFirstVPN() + i;
				pageTable[vpn] = new TranslationEntry(i, freePageIdx, true, section.isReadOnly(), false, false);

				section.loadPage(i, freePageIdx);
				pagesDone++;
			}
		}
		// load stack and arg pages
		for(int i=pagesDone; i < numPages; i++) {
			UserKernel.pagesLock.acquire();
			// not enough free phys pages
			if(UserKernel.freePages.size() == 0) {
				Lib.debug(dbgProcess, "not enough physical memory.");
				UserKernel.pagesLock.release();
				unloadSections();
				return false;
			}
			// get free phys page  
			int freePageIdx = UserKernel.freePages.removeFirst();
			UserKernel.pagesLock.release();

			pageTable[i] = new TranslationEntry(i, freePageIdx, true, false, false, false);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		for(int i=0; i < pageTable.length; i++) {
			if(pageTable[i].valid) {
				UserKernel.pagesLock.acquire();
				UserKernel.freePages.add(pageTable[i].ppn);
				UserKernel.pagesLock.release();
			}
		}
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(processPID != 0) {
			Lib.debug(dbgProcess, "halt called by nonroot process");
			return -1;
		} else {
			Machine.halt();
		}

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
	        // Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		
		// close files
		for(int i=0; i<fileTable.length; i++) {
			if(fileTable[i] != null) {
				fileTable[i].close();
			}
		}

		// free up memory
		unloadSections();

		// close the program
		coff.close();

		if(parentProcess != null) {
			statusLock.acquire();
			exitStatus = status;
			statusLock.release();

			parentProcess.statusLock.acquire();
			parentProcess.joinQueue.wakeAll();
			parentProcess.statusLock.release();
		}

		// check if last process
		UserKernel.pcLock.acquire();
		UserKernel.processCount--;
		if(UserKernel.processCount == 0) {
			Kernel.kernel.terminate();
		}
		UserKernel.pcLock.release();

		KThread.finish();
		return 0;
	}

	private int handleExec(int vaFile, int argc, int vaArgv) { 
		// invalid arg check
		if(vaFile <= 0 || vaFile > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid vafile in exec");
			return -1;
		} else if(argc < 0) {
			Lib.debug(dbgProcess, "invalid argc in exec");
			return -1;
		} else if(vaArgv < 0 || vaArgv > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid vaargv in exec");
			return -1;
		}

		String fileName = null;
		fileName = readVirtualMemoryString(vaFile, 256);
		String[] args = new String[argc];
		byte[] arg = new byte[4];

		if(fileName == null) {
			Lib.debug(dbgProcess,"invalid filename in exec");
			return -1;
		}

		// get argv
		for(int i=0; i < argc; i++) {
			int bytesRead = readVirtualMemory(vaArgv+(arg.length*i), arg);

			int addr = Lib.bytesToInt(arg, 0);
			args[i] = readVirtualMemoryString(addr, 256);

			if(args[i] == null) {
				Lib.debug(dbgProcess,"invalid arg read");
				return -1;
			}
		}

		// create new child, assign parent/child
		UserProcess child = newUserProcess();
		boolean childExe = child.execute(fileName, args);
		childProcesses.add(child);
		child.parentProcess = this;

		if(childExe) {
			return child.processPID;
		} else {
			Lib.debug(dbgProcess, "execute failed in handleexec");
			return -1;
		}
	}

	private int handleJoin(int pidOfChild, int vaStatus) { 
		// arg check
		if(pidOfChild < 0) {
			Lib.debug(dbgProcess, "invalid pid in handlejoin");
			return 0;
		} else if(vaStatus < 0 || vaStatus > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid va in handlejoin");
			return 0;
		}

		UserProcess child = null;

		// find child in parent
		for(int i=0; i < childProcesses.size(); i++){
			if(childProcesses.get(i).processPID == pidOfChild) {
				child = childProcesses.get(i);
			}
		}

		// child not found
		if(child == null) {
			Lib.debug(dbgProcess, "pid is not a child of current process");
			return -1;
		}

		childProcesses.remove(child);
		child.parentProcess = null;
		
		// child has not ended
		child.statusLock.acquire();
		if(child.exitStatus == null) {
			child.statusLock.release();

			this.statusLock.acquire();
			joinQueue.sleep();
			this.statusLock.release();
		} else {
			child.statusLock.release();
		}

		// get exitstatus of child
		child.statusLock.acquire();
		int childExitStatus = child.exitStatus.intValue();
		boolean abnormalChildExit = child.abnormalExit;
		child.statusLock.release();

		if(childExitStatus >= 0 && vaStatus != 0 && !abnormalChildExit) {
			// write back to status pointer if not null
			byte[] exitBytes = Lib.bytesFromInt(childExitStatus);
			writeVirtualMemory(vaStatus, exitBytes);
			return 1;
		} else {
			return 0;
		}
	}

	private int handleCreate(int vaName) {
		int fd = -1;
		String fileName = null;

		if(vaName > 0 && vaName <= (pageTable.length * pageSize)) {
			fileName = readVirtualMemoryString(vaName, 256);
		} else {
			Lib.debug(dbgProcess, "invalid va in create");
			return fd;
		}

		// check if name exists 
		if(fileName != null) {
			OpenFile executable = ThreadedKernel.fileSystem.open(fileName, true);
			if(executable != null) {

				for(int i=0; i<fileTable.length; i++) {
					if(fileTable[i] == null) {
						fileTable[i] = executable;
						fd = i;
						Lib.debug(dbgProcess, "file at fd: " + fd);
					}
				} 

				if(fd == -1) {
					Lib.debug(dbgProcess, "fileTable is full (create)");
					return fd;
				}

			} else {
				Lib.debug(dbgProcess, "open failed in handleCreate");
				return fd;
			}
	    } else {
			Lib.debug(dbgProcess, "invalid filename");
			return fd;
		}
		return fd;
	}

	private int handleOpen(int vaName) {
		int fd = -1;
		String fileName = null;

		if(vaName > 0 && vaName <= (pageTable.length * pageSize)) {
			fileName = readVirtualMemoryString(vaName, 256);
		} else {
			Lib.debug(dbgProcess, "invalid va in open");
			return fd;
		}
		
		// check if name exists 
		if(fileName != null) {
			OpenFile executable = ThreadedKernel.fileSystem.open(fileName, false);
			if(executable != null) {
	
				for(int i=0; i<fileTable.length; i++) {
					if(fileTable[i] == null) {
						fileTable[i] = executable;
						fd = i;
						Lib.debug(dbgProcess, "file at fd: " + fd);
					}
				}

				if(fd == -1) {
					Lib.debug(dbgProcess, "fileTable is full (create)");
					return fd;
				}

			} else {
				Lib.debug(dbgProcess, "open failed in handleOpen");
				return fd;
			}
	    } else {
			Lib.debug(dbgProcess, "invalid filename");
			return fd;
		}
		return fd;
	}

	private int handleRead(int fd, int vaBuffer, int count) {
		// arg checks
		if(fd > 15 || fd < 0) {
			Lib.debug(dbgProcess, "invalid fd in read");
			return -1;
		} else if(fileTable[fd] == null) {
			Lib.debug(dbgProcess, "cannot access file at fd (read)");
			return -1;
		} else if(count <= 0 || count > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid count in read");
			return -1;
		} else if(vaBuffer <= 0 || vaBuffer > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid va in read");
			return -1;
		}

		int bytesRead = 0;
		int bufferOffset = vaBuffer;
		int bytesDone = 0;
		int bytesToRead = count;
		int checkRead = 0;
		byte[] buf = null;
		
		// read entire pages
		while(bytesToRead > pageSize) {
			buf = new byte[pageSize];
			// read from file
			checkRead = fileTable[fd].read(buf, 0, pageSize);
			// nothing to read
			if(checkRead == 0) {
				return bytesRead;
			}
			else if(checkRead != -1) {
				Lib.debug(dbgProcess, "bytes read from file: " + checkRead);
				bytesDone = writeVirtualMemory(bufferOffset, buf, 0, checkRead);

				// check if exact write
				if(bytesDone != checkRead) {
					Lib.debug(dbgProcess, "invalid write: " + checkRead + " | " + bytesDone);
					return -1;
				}
			} else {
				Lib.debug(dbgProcess, "read failed. bytes read: " + buf);
				return -1;
			}

			bytesRead += bytesDone;
			bufferOffset += bytesDone;
			bytesToRead -= bytesDone;
			buf = null;
		}

		// read less than one page
		buf = new byte[pageSize];
		checkRead = fileTable[fd].read(buf, 0, bytesToRead);
		if(checkRead != -1) {
			Lib.debug(dbgProcess, "bytes read from file: " + checkRead);
			bytesDone = writeVirtualMemory(bufferOffset, buf, 0, checkRead);

			// check if exact write
			if(bytesDone != checkRead) {
				Lib.debug(dbgProcess, "invalid write: " + checkRead + " | " + bytesDone);
				return -1;
			}
			bytesRead += bytesDone;
		} else {
			Lib.debug(dbgProcess, "read failed. bytes read: " + buf);
			return -1;
		}

		//Lib.debug(dbgProcess, "read successful. bytes read: " + bytesRead);
		return bytesRead;
	}

	private int handleWrite(int fd, int vaBuffer, int count) {

		// arg checks
		if(fd > 15 || fd < 0) {
			Lib.debug(dbgProcess, "invalid fd in write");
			return -1;
		} else if(fileTable[fd] == null) {
			Lib.debug(dbgProcess, "cannot access file at fd (write)");
			return -1;
		} else if(count < 0 || count > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid count in write");
			return -1;
		} else if(vaBuffer <= 0 || vaBuffer > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid va in write");
			return -1;
		}

		int bytesWritten = 0;
		int bufferOffset = vaBuffer;
		int bytesDone = 0;
		int bytesToWrite = count;
		int checkRead = 0;
		byte[] buf = null;

		// write multiple pages
		while(bytesToWrite > pageSize) {
			buf = new byte[pageSize];
			// read into local buffer
			checkRead = readVirtualMemory(bufferOffset, buf);
			// write to file
			bytesDone = fileTable[fd].write(buf, 0, checkRead);

			// nothing to write
			if(bytesDone == 0) {
				return bytesWritten;
			} else if(bytesDone == -1) {
				Lib.debug(dbgProcess, "write failed. bytes written: " + buf);
				return -1;
			}
				
			bytesWritten += bytesDone;
			bufferOffset += bytesDone;
			bytesToWrite -= bytesDone;
			buf = null;
		}

		// write less than one page
		buf = new byte[pageSize];
		checkRead = readVirtualMemory(bufferOffset, buf, 0, bytesToWrite);
		bytesDone = fileTable[fd].write(buf, 0, checkRead);

		if(bytesDone == -1) {
			Lib.debug(dbgProcess, "write failed. bytes written: " + buf);
			return -1;
		}
		
		bytesWritten += bytesDone;
		return bytesWritten;
	}

	private int handleClose(int fd) {
		if(fd > 15 || fd < 0) {
			Lib.debug(dbgProcess, "invalid fd in close");
			return -1;
		} else if(fileTable[fd] == null) {
			Lib.debug(dbgProcess, "cannot access file at fd (close)");
			return -1;
		} else {
			fileTable[fd].close();
			fileTable[fd] = null;
			Lib.debug(dbgProcess, "file closed");
			return 0;
		}
	}

	private int handleUnlink(int vaName) {
		String fileName = null;
		
		if(vaName <= 0 || vaName > (pageTable.length * pageSize)) {
			Lib.debug(dbgProcess, "invalid va in unlink");
			return -1;
		}

		fileName = readVirtualMemoryString(vaName, 256);
		if(fileName != null) {
			// remove file
			if(ThreadedKernel.fileSystem.remove(fileName)) {
				Lib.debug(dbgProcess, "file removed in unlink");
				return 0;
			} else {
				Lib.debug(dbgProcess, "could not remove file in unlink");
				return -1;
			}
		} else {
			Lib.debug(dbgProcess, "invalid filename in unlink");
			return -1;
		}
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0,a1,a2);
		case syscallJoin:
			return handleJoin(a0,a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);

		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);

			abnormalExit = true;
			handleExit(0);

			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
        protected UThread thread;
    
	private int processPID;

	private int initialPC, initialSP;

	private int argc, argv;

	private Integer exitStatus = null;
	private Lock statusLock;
	private Condition joinQueue;

	private boolean abnormalExit = false;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private LinkedList<UserProcess> childProcesses = new LinkedList<UserProcess>();
	private UserProcess parentProcess;
	private OpenFile[] fileTable;
}
