package rm.tests;

import transaction.*;
import java.rmi.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * exit(0): test passed
 *     (1): internal test error (incl. any uncaught exceptions)
 *     (2): test failed
 *
 * Test script syntax:
 *  #threads
 *  line*
 *
 * line: xid launch
 *           call func param*
 *           return val?
 *           except exctype?
 *           sleep millisec
 *           exit
 */

public class Client {
    private static final long TESTTIMEOUT = 180000; // 3 minutes
    private static final long LAUNCHSLEEP = 5000; // 5 seconds
    private static final long BCNEXTOPDELAY = 1000; // 1 second
    private static final long BCFINISHDELAY = 500; // 1/2 second

    private static final String DELAYMARKER = "_DLMKR_";

    private static final String LOGDIR = "results/";
    private static final String LOGSUFFIX = ".log";

    private static ResourceManager rm = null;
    private static String currentLine = null;

    public static void main(String args[]) throws FileNotFoundException, IOException
	{

    	BufferedReader scriptReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File("/Users/Palomo/tmp/project1_orig/project/test.part1/MASTER.xml"))));
		launch();

		currentLine = scriptReader.readLine();
		
		int numThreads = Integer.parseInt(currentLine);
		readNextLine();
		System.out.println("[M] Launching " + numThreads + " threads.");
		for (int i=1; i<=numThreads; i++) {
		    new myThread(String.valueOf(i)).start();
		}
	
		try {
		    Thread.sleep(TESTTIMEOUT);
		} catch (InterruptedException e) {
		    System.err.println("[M] Sleep interrupted.");
		    System.exit(1);
		}
		System.err.println("[M] Test timed out.");
		cleanUpExit(2);
    }

    private static void readNextLine()
	{
    }
    
    private static void launch()
	{
		try {
	    	rm = new ResourceManagerImpl();
		} catch (Exception e) {
	    	cleanUpExit(2);
		}
    }

    private static void cleanUpExit(int status) {
	try {
	    rm.dieNow();
	} catch (Exception e) {}
	System.exit(status);
    }

    private static class myThread extends Thread {
		private String id = null;
	
		public myThread (String myid) {
	    	id = myid;
		}
	
		@Override
		public void run() {
	    	System.out.println("[" + id + "] Thread running.");

	    	String myLine = null;

			//the transaction ID of the current thread
			Integer xid = null;

			//introducing any delay into the call
		    boolean bCallDelay = false;
			
			//MAIN LOOP for the thread
			while (true) {
					
			//make sure the following code is the only accessed by one thread at a time
			synchronized (System.in) {
				
				//make sure the current line starts with the current id of the thread
				while (currentLine != null && !currentLine.startsWith(id)) {
					try {
						System.in.wait();
					} catch (InterruptedException e) {
						System.err.println("[" + id + "] Wait interrupted.");
						System.exit(1);
					}
				}

				//no tests to run from the input (current line)
				if (currentLine == null) {
					return;
				}

				myLine = currentLine;
				
				//read the next line in the script reader
				readNextLine();

				if (myLine.startsWith(id+" call ") && !currentLine.startsWith(id)) {
					// This is a blocked call.  Next op should be delayed.
					currentLine += DELAYMARKER;

					// Also, our return should be delayed.
					bCallDelay = true;
				}
				
				if (myLine.endsWith(DELAYMARKER)) {
					try {
						Thread.sleep(BCNEXTOPDELAY);
					} catch (InterruptedException e) {
						System.err.println("[" + id + "] Sleep interrupted.");
						System.exit(1);
					}
					myLine = myLine.substring(0, myLine.length()-DELAYMARKER.length());
				}

				System.in.notifyAll();
			}  //end synchronized
		
			System.out.println("[" + id + "] \tLINE--" + myLine);
			StringTokenizer st = new StringTokenizer(myLine);
			if (!st.nextToken().equals(id)) {
		    	System.err.println("[" + id + "] Bad: line corrupted?");
		    	System.exit(1);
			}

			String action = st.nextToken();
			if (action.equals("launch")) {
		    	launch();
			} 
			else if (action.equals("sleep")) {
		    	try {
					Thread.sleep(Long.parseLong(st.nextToken()));
		    	} catch (InterruptedException e) {
					System.err.println("[" + id + "] Sleep interrupted.");
					System.exit(1);
		    	}
			} 
			else if (action.equals("exit")) {
		   		System.out.println("[" + id + "] Test exiting.");
		    	cleanUpExit(0);
			} 
			else if (action.equals("call")) {
		    	String methodName = st.nextToken();
		    	Method method = findMethod(methodName);
		    	if (method == null) {
					System.err.println("[" + id + "] Method not found: " + methodName);
					System.exit(1);
		    	}
				
                Object[] params = new Object[st.countTokens()];

		    	for (int i=0; i<params.length; i++) {
					String param = st.nextToken();
					if (param.startsWith("\"")) {
			    		params[i] = param.substring(1, param.length()-1);
					}
					else if (param.equals("true") || param.equals("false")) {
			    		params[i] = new Boolean(param);
					}
					else if (param.equals("xid")) {
			    		params[i] = xid;
					} 
					else {
			    		params[i] = new Integer(param);
					}
		    	}

		    	System.out.println("[" + id + "] Calling " + methodName);
		    	Object retVal = null; 
		    	Throwable retExc = null;
		    	try {
					retVal = method.invoke(rm, params);
					System.out.println("[" + id + "] " + methodName + " returned: " + retVal);
		    	} catch (IllegalAccessException e) {
					System.err.println("[" + id + "] " + methodName + " got IllegalAccessException: " + e);
					System.exit(1);
		    	} catch (IllegalArgumentException e) {
					System.err.println("[" + id + "] " + methodName + " got IllegalArgumentException: " + e); 
                    System.exit(1);
		    	} catch (InvocationTargetException e) {
					retExc = e.getTargetException();
					System.out.println("[" + id + "] " + methodName + " exceptioned: " + retExc.getClass().getName());
		    	}

		    	if (methodName.equals("start") && retVal != null) {
					xid = (Integer)retVal;
					System.out.println("[" + id + "] xid set to " + xid.intValue());
		    	}

		    	// For a blocking call, delay seeking the finish
		    	// line to give the other call (which unblocked
		    	// us) a chance to finish first.
		    	if (bCallDelay) {
					bCallDelay = false;
					try {
			    		Thread.sleep(BCFINISHDELAY);
					} catch (InterruptedException e) {
			    		System.err.println("[" + id + "] Sleep interrupted.");
			    		System.exit(1);
					}
		    	}

		    	synchronized (System.in) {
					if (currentLine == null || !currentLine.startsWith(id)) {
			    		System.err.println("[" + id + "] Call finish unexpected: " + currentLine);
			    		cleanUpExit(2);
					}
	              	myLine = currentLine;
					readNextLine();
					if (myLine.endsWith(DELAYMARKER)) {
			    		// Only possibility: deadlock test
			    		myLine = myLine.substring(0,
					    myLine.length()-DELAYMARKER.length());
					}
					System.in.notifyAll();
		    	}  //end synchronized

		    	System.out.println("[" + id + "] \tLINE--" + myLine);
		    	st = new StringTokenizer(myLine);

		    	if (!st.nextToken().equals(id)) {
					System.err.println("[" + id + "] Bad: line corrupted?");
					System.exit(1);
		   		 }

		    	action = st.nextToken();
		    	if (action.equals("return")) {
					// retVal can be null if func returns void
					if (retExc != null) {
			    		System.err.println("[" + id + "] Exception rather than return: " + retExc);
			    		cleanUpExit(2);
					}

					if (st.hasMoreTokens()) {
				    	String expVal = st.nextToken();
				    	if (!retVal.toString().equals(expVal)) {
							System.err.println("[" + id + "] Return value mismatch: expecting " + expVal + "; got " + retVal.toString());
							cleanUpExit(2);
				    	}
					}
		    	}
				else if (action.equals("except")) {
					if (retExc == null) {
			    		System.err.println("[" + id + "] Return rather than exception: " + retVal);
			    		cleanUpExit(2);
					}
			
					if (st.hasMoreTokens()) {
			    		String expExc = st.nextToken();
			    		try {
							if (!Class.forName(expExc).isInstance(retExc)) {
				    			System.err.println("[" + id + "] Exception mismatch: expecting " + expExc + "; got " + retExc);
				    			cleanUpExit(2);
							}
			    		} catch (ClassNotFoundException e) {
							System.err.println("[" + id + "] Cannot find class " + expExc + ": " + e);
							System.exit(1);
			    		}
					}
		    	}
				else {
					System.err.println("[" + id + "] Unknown or unexpected action: " + action);
					System.exit(1);
		    	}
			} else {
		    	System.err.println("[" + id + "] Unknown or unexpected action: " + action);
		    	System.exit(1);
 			}
	    }
	}
	
	private Method findMethod(String methodName) {
	    Method[] allMethods = rm.getClass().getMethods();
	    for (int i=0; i<allMethods.length; i++) {
			if (allMethods[i].getName().equals(methodName)) {
		   		return allMethods[i];
			}
	    }
	    return null;
	}
}
}
