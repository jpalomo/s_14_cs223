package transaction;

import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TransactionManagerImpl extends UnicastRemoteObject implements TransactionManager {
    
	//private Map<Integer, Transaction> activeTrans;  //list of all active transactions
	//private Map<Integer, Transaction> abortedTrans; //list of all aborted transactions
	private Map<Integer, HashSet<String>> enlistRM;  //data structure to hold for each transaction(xid) its RMs
	private int xidcounter = 0;
	
	protected ResourceManager rmFlights = null;
    protected ResourceManager rmRooms = null;
    protected ResourceManager rmCars = null;
    protected ResourceManager rmCustomers = null;
	private boolean isLocal = false;
	private boolean dieBeforeCommit = false;
	private boolean dieAfterCommit = false;

	boolean isInitialized = false;
    
	public static void main(String args[]) {
		System.setSecurityManager(new RMISecurityManager());

		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
	    	rmiPort = "";
		} else if (!rmiPort.equals("")) {
	    	rmiPort = "//:" + rmiPort + "/";
		}

		try {
	    	TransactionManagerImpl obj = new TransactionManagerImpl();
			System.out.println(rmiPort + TransactionManager.RMIName);
	    	Naming.rebind(rmiPort + TransactionManager.RMIName, obj);
	    	System.out.println("TM bound");
		} 
		catch (Exception e) {
			System.out.println(" Error:  " + rmiPort + TransactionManager.RMIName);
			e.printStackTrace();
	    	System.err.println("TM not bound:" + e);
	    	System.exit(1);
		}
    }
   
    public TransactionManagerImpl() throws RemoteException {  
		
		enlistRM = new HashMap<Integer, HashSet<String>>();
		xidcounter++;
    }

    public boolean dieNow() throws RemoteException {
		System.exit(1);
		return true; 
		// We won't ever get here since we exited above;
	    // but we still need it to please the compiler.
    }

	@Override
	public int start() throws RemoteException {
		if(!isInitialized){
			//UNCOMMENT
			while(!isLocal && !reconnect()){
//				;
			}
		}
		enlist(xidcounter, null);
		return xidcounter++;
	}
	
	@Override
	public boolean enlist(int xid, String rmName) throws RemoteException {
		// TODO Auto-generated method stub
		// THe RM is enlisted with the HashMap of the transaction 
		if(rmName == null){
			enlistRM.put(xid, new HashSet<String>());
		}	
		else if(enlistRM.containsKey(xid))
	   	{
			System.err.println("Enlisted xid: " + xid + " for RM: " + rmName);
			HashSet<String> RMlist = enlistRM.get(xid);
		   	RMlist.add(rmName);
		   	enlistRM.put(xid,RMlist);
	   	}
	   	return true;
	}
	
	@Override
	public boolean commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		if(!isValidXID(xid)){
			throw new TransactionAbortedException(xid, "Invalid transaction identifier.");
		}

		if(dieBeforeCommit){
			dieNow();
		}
		
		HashSet<String> resourceManagers = enlistRM.get(xid);
		ResourceManager rm = null;

		for(String rmName: resourceManagers){
			System.err.println("#####Passing resoucrce manager name " + rmName);
			rm = getRMByName(rmName);
			if(rm.prepareToCommit(xid) && !rm.isDieRMBeforePrepare()){
				System.err.println("#####Prepared " + rmName);
			}
			else{
				abort(xid);
				System.err.println("#####Aborting trans prepare " + rmName);

				throw new TransactionAbortedException(xid, "Transaction: " + xid + " is being aborted.");
			} 
		}

		for(String rmName: resourceManagers){
			System.err.println("#####Passing resoucrce manager name " + rmName);
			rm = getRMByName(rmName);
			if(rm.isDieRMAfterPrepare()){
				abort(xid);
				System.err.println("#####Aborting after prepare " + rmName);
				throw new TransactionAbortedException(xid, "Transaction: " + xid + " is being aborted.");
			} 
		}

		for(String rmName: resourceManagers){
			System.err.println("#####Passing resoucrce manager name " + rmName);
			rm = getRMByName(rmName);
			rm.commit(xid);
			System.err.println("#####Committed " + rmName);
	 		rm.clearLocks(xid);
		} 
		System.err.println("Removing xid: " + xid + " from enlistRM");
		enlistRM.remove(xid);
		
		if(dieAfterCommit){
			dieNow();
		}
		
		return true;
	}
	
	@Override
	public void abort(int xid) throws RemoteException, InvalidTransactionException{
		if(!isValidXID(xid)){
				throw new InvalidTransactionException(xid, "Invalid transaction identifier.");
		}
		
		HashSet<String> resourceManagers = enlistRM.get(xid);
		ResourceManager rm;
		for(String rmName: resourceManagers){
			rm = getRMByName(rmName);
			rm.abort(xid);
		} 
	}

	@Override
	public boolean isValidXID(int xid) throws RemoteException {
		if(enlistRM != null || enlistRM.size() > 1){
			for(int transID:  enlistRM.keySet()){
				if (transID == xid)	{
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean reconnect() throws RemoteException {
		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
	   		rmiPort = "";
		} else if (!rmiPort.equals("")) {
	    	rmiPort = "//:" + rmiPort + "/";
		}

		try {
	    	rmFlights = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameFlights);
	    	System.out.println("TM bound to RMFlights");

	    	rmRooms = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameRooms);
	    	System.out.println("TM bound to RMRooms");
			
	    	rmCars = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameCars);
	    	System.out.println("TM bound to RMCars");

	    	rmCustomers = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameCustomers);
	    	System.out.println("TM bound to RMCustomers");
			isInitialized = true;
			return true;
		} 
		catch (Exception e) {
	    	System.err.println("TM cannot bind to some component:" + e);
	    	return false;
		}
    }

	private ResourceManager getRMByName(String rmName){
		if(rmName.equals(ResourceManager.RMINameCars)){
			System.err.println("Returning Cars rm for commit");
			return rmCars;
		}
		else if(rmName.equals(ResourceManager.RMINameCustomers)){
			System.err.println("Returning customers rm for commit");
			return rmCustomers;
		}
		else if(rmName.equals(ResourceManager.RMINameFlights)){
			System.err.println("Returning flights rm for commit");
			return rmFlights;
		}
		else{ 
			System.err.println("Returning rooms rm for commit");
			return rmRooms;
		}
	}

	public void setRM(String name, ResourceManager rm) throws RemoteException{
		if(name.equals(ResourceManager.RMINameCars)){
			rmCars = rm;
		}	
		if(name.equals(ResourceManager.RMINameCustomers)){
			rmCustomers = rm;
		}	
		if(name.equals(ResourceManager.RMINameFlights)){
			rmFlights = rm;
		}	
		if(name.equals(ResourceManager.RMINameRooms)){
			rmRooms = rm;
		}	
	}

	@Override
	public boolean setDieTMBeforeCommit() throws RemoteException {
		dieBeforeCommit = true;
		return dieBeforeCommit;
	}

	@Override
	public boolean setDieTMAfterCommit() throws RemoteException {
		dieAfterCommit = true;
		return dieAfterCommit;
	}
}