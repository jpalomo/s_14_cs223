package transaction;

import java.rmi.*;

/** 
 * Interface for the Transaction Manager of the Distributed Travel
 * Reservation System.
 * <p>
 * Unlike WorkflowController.java, you are supposed to make changes
 * to this file.
 */

public interface TransactionManager extends Remote {

    public boolean dieNow() throws RemoteException;

    /** The RMI name a TransactionManager binds to. */
    public static final String RMIName = "TM";
	    
    public int start() throws RemoteException; 
    
    public boolean enlist(int xid, String rmName) throws RemoteException;
    	    
    public boolean commit(int xid) throws RemoteException, TransactionAbortedException,InvalidTransactionException;
    	
    public void abort(int xid) throws RemoteException,InvalidTransactionException; 

	public boolean isValidXID(int xid) throws RemoteException;

	public boolean reconnect() throws RemoteException;

    public boolean setDieTMBeforeCommit() throws RemoteException;

	public boolean setDieTMAfterCommit() throws RemoteException; 
}
