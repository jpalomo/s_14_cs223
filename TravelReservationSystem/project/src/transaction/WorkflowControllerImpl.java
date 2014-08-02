package transaction;

import java.rmi.*;
import java.util.*;

/** 
 * Workflow Controller for the Distributed Travel Reservation System.
 * 
 * Description: toy implementation of the WC.  In the real
 * implementation, the WC should forward calls to either RM or TM,
 * instead of doing the things itself.
 */

public class WorkflowControllerImpl extends java.rmi.server.UnicastRemoteObject implements WorkflowController {

    protected ResourceManager rmFlights = null;
    protected ResourceManager rmRooms = null;
    protected ResourceManager rmCars = null;
    protected ResourceManager rmCustomers = null;
    protected TransactionManager tm = null;
	private boolean isLocal = false;

    public static void main(String args[]) {
		System.setSecurityManager(new RMISecurityManager());

		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
	    	rmiPort = "";
		} else if (!rmiPort.equals("")) {
	    	rmiPort = "//:" + rmiPort + "/";
		}

		try {
	    	WorkflowControllerImpl obj = new WorkflowControllerImpl();
	    	Naming.rebind(rmiPort + WorkflowController.RMIName, obj);
	    	System.out.println("WC bound");
		}
		catch (Exception e) {
	    	System.err.println("WC not bound:" + e);
	    	System.exit(1);
		}
    }
    
    public WorkflowControllerImpl() throws RemoteException {
		
		//UNCOMMENT!!
		while (!isLocal && !reconnect()) {
////	    // would be better to sleep a while
		} 
    }

    // TRANSACTION INTERFACE
	@Override
    public int start() throws RemoteException {
		return tm.start();
    }

	@Override
    public boolean commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		synchronized(tm) {
			System.out.println("Committing: " + xid);
			return tm.commit(xid);
		}
    }

	@Override
    public void abort(int xid) throws RemoteException, InvalidTransactionException {
		tm.abort(xid); 
    }

    // ADMINISTRATIVE INTERFACE
	@Override
    public boolean addFlight(int xid, String flightNum, int numSeats, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmFlights.addFlight(xid, flightNum, numSeats, price);
    }

	@Override
    public boolean deleteFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		boolean flightResExist = rmCustomers.isReservationExist(xid, flightNum);
		if(flightResExist){
			return false;
		}
		return rmFlights.deleteFlight(xid, flightNum);
    }
		
	@Override
    public boolean addRooms(int xid, String location, int numRooms, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmRooms.addRooms(xid, location, numRooms, price);
    }

	@Override
    public boolean deleteRooms(int xid, String location, int numRooms) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmRooms.deleteRooms(xid, location, numRooms);
    }

	@Override
    public boolean addCars(int xid, String location, int numCars, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCars.addCars(xid, location, numCars, price);
    }

	@Override
    public boolean deleteCars(int xid, String location, int numCars) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCars.deleteCars(xid, location, numCars);
    }

	@Override
    public boolean newCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCustomers.newCustomer(xid, custName);
    }

	@Override
    public boolean deleteCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCustomers.deleteCustomer(xid, custName);
    }

    // QUERY INTERFACE
	@Override
    public int queryFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmFlights.queryFlight(xid, flightNum);
    }

	@Override
    public int queryFlightPrice(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmFlights.queryFlightPrice(xid, flightNum);
    }

	@Override
    public int queryRooms(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmRooms.queryRooms(xid, location);
    }

	@Override
    public int queryRoomsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmRooms.queryRoomsPrice(xid, location);
    }

	@Override
    public int queryCars(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCars.queryCars(xid, location);
    }

	@Override
    public int queryCarsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		return rmCars.queryCarsPrice(xid, location);
    }

	@Override
    public int queryCustomerBill(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		int customerBillTotal = 0;
		
		System.err.println("Calling get FLIGHTs with xid: " + xid);
		List<String> flightNums = rmCustomers.getCustomerFlightReservations(xid, custName);
		if(flightNums != null){
			for(String flight:flightNums){
				customerBillTotal += queryFlightPrice(xid, flight);
			}
		}

		System.err.println("Calling get rooms with xid: " + xid);
		List<String> roomLocs = rmCustomers.getCustomerRoomReservations(xid, custName);
		if(roomLocs != null){
			for(String room:roomLocs){
				customerBillTotal += queryRoomsPrice(xid, room);
			}	
		}

		System.err.println("Calling get rooms with xid: " + xid);
		List<String> carLocs = rmCustomers.getCustomerCarReservations(xid, custName);
		if(carLocs != null){
			for(String car: carLocs){
				customerBillTotal += queryCarsPrice(xid, car);
			}
		}
		return customerBillTotal; 
    }

    // RESERVATION INTERFACE
	@Override
    public boolean reserveFlight(int xid, String custName, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		boolean flightReserved = rmFlights.reserveFlight(xid, custName, flightNum);
		if(flightReserved){
			return rmCustomers.reserveFlight(xid, custName, flightNum);
		}
		return false;
    }
 
	@Override
    public boolean reserveCar(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		boolean carReserved = rmCars.reserveCar(xid, custName, location);
		if(carReserved){
			return rmCustomers.reserveCar(xid, custName, location);
		}
		return false;
    }

	@Override
    public boolean reserveRoom(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		boolean roomReserved = rmRooms.reserveRoom(xid, custName, location);
		if(roomReserved){
			return rmCustomers.reserveRoom(xid, custName, location);
		}
		return false;
    }

	@Override
    public boolean reserveItinerary(int xid, String custName, List flightNumList, String location, boolean needCar, boolean needRoom) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		
		if(flightNumList == null || flightNumList.size() < 1) {
			return false;
		}

		String flightnum;
		boolean flightReserved = false;
		Iterator flightIter = flightNumList.iterator();
		while(flightIter.hasNext()){
			flightnum = (String) flightIter.next();
			flightReserved = reserveFlight(xid, custName, flightnum);

			if(!flightReserved){
				return false;
			}
		}

		boolean carReserved = false;
		if(needCar){
			carReserved = reserveCar(xid, custName, location);
			if(!carReserved){
				return rmCustomers.unreserveFlights(flightNumList, custName, xid);
			}

			
		}

		if(needRoom){
			boolean roomReserved = reserveRoom(xid, custName, location);
			if(!roomReserved){
				rmCustomers.unreserveFlights(flightNumList, custName, xid);
				if(carReserved){
					
				}
				return false;
			}
		}
		return true;
    }

    // TECHNICAL/TESTING INTERFACE
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
	    	System.out.println("WC bound to RMFlights");

	    	rmRooms = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameRooms);
	    	System.out.println("WC bound to RMRooms");
			
	    	rmCars = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameCars);
	    	System.out.println("WC bound to RMCars");

	    	rmCustomers = (ResourceManager)Naming.lookup(rmiPort + ResourceManager.RMINameCustomers);
	    	System.out.println("WC bound to RMCustomers");
	    	
			tm = (TransactionManager)Naming.lookup(rmiPort + TransactionManager.RMIName);
	    	System.out.println("WC bound to TM");
		} 
		catch (Exception e) {
	    	System.err.println("WC cannot bind to some component:" + e);
	    	return false;
		}

		try {
	    	if (rmFlights.reconnect() && rmRooms.reconnect() && rmCars.reconnect() && rmCustomers.reconnect()) {
				return true;
	    	}
		} catch (Exception e) {
	    	System.err.println("Some RM cannot reconnect:" + e);
	    	return false;
		}
	
		return false;
    }
	
	@Override
	public boolean dieNow(String who) throws RemoteException {
		if (who.equals(TransactionManager.RMIName) || who.equals("ALL")) {
	    	try {
				tm.dieNow();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.dieNow();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.dieNow();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				System.out.println("rmi cars is dying now!");
				rmCars.dieNow();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.dieNow();
		    } catch (RemoteException e) {}
		}
		if (who.equals(WorkflowController.RMIName) || who.equals("ALL")) {
	    	System.exit(1);
		}
		return true;
    }

	@Override
    public boolean dieRMAfterEnlist(String who) throws RemoteException {
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.setDieAfterEnlist();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.setDieAfterEnlist();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				rmCars.setDieAfterEnlist();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.setDieAfterEnlist();
		    } catch (RemoteException e) {}
		}
		return true;
    }

	@Override
    public boolean dieRMBeforePrepare(String who) throws RemoteException {
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.setDieRMBeforePrepare();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.setDieRMBeforePrepare();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				rmCars.setDieRMBeforePrepare();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.setDieRMBeforePrepare();
		    } catch (RemoteException e) {}
		}
		return true;
    }
	
	@Override
    public boolean dieRMAfterPrepare(String who) throws RemoteException {
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.setDieRMAfterPrepare();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.setDieRMAfterPrepare();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				rmCars.setDieRMAfterPrepare();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.setDieRMAfterPrepare();
		    } catch (RemoteException e) {}
		}
		return true;
    }

	@Override
    public boolean dieTMBeforeCommit() throws RemoteException {
		return tm.setDieTMBeforeCommit();
    }

	@Override
    public boolean dieTMAfterCommit() throws RemoteException {
		return tm.setDieTMAfterCommit();
    }

	@Override
    public boolean dieRMBeforeCommit(String who) throws RemoteException {
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.setDieRMBeforeCommit();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.setDieRMBeforeCommit();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				rmCars.setDieRMBeforeCommit();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.setDieRMBeforeCommit();
		    } catch (RemoteException e) {}
		}
		return true;
    }

	@Override
    public boolean dieRMBeforeAbort(String who) throws RemoteException {
		if (who.equals(ResourceManager.RMINameFlights) || who.equals("ALL")) {
		    try {
				rmFlights.setDieRMBeforeAbort();
	    	} catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameRooms) || who.equals("ALL")) {
		    try {
				rmRooms.setDieRMBeforeAbort();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCars) || who.equals("ALL")) {
		    try {
				rmCars.setDieRMBeforeAbort();
		    } catch (RemoteException e) {}
		}
		if (who.equals(ResourceManager.RMINameCustomers) || who.equals("ALL")) {
		    try {
				rmCustomers.setDieRMBeforeAbort();
		    } catch (RemoteException e) {}
		}
		return true;
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

	public void setTM(TransactionManager tm){
		this.tm = tm;
	}
}
