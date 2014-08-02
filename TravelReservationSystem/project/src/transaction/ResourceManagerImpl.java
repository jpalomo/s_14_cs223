package transaction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lockmgr.DeadlockException;
import lockmgr.LockManager;
import static transaction.ResourceManager.RMINameCars;
import static transaction.ResourceManager.RMINameCustomers;
import static transaction.ResourceManager.RMINameFlights;
import static transaction.ResourceManager.RMINameRooms;

/** 
 * Resource Manager for the Distributed Travel Reservation System.
 * 
 * Description: toy implementation of the RM, for initial testing
 */

public class ResourceManagerImpl extends UnicastRemoteObject implements ResourceManager {
	public static final String DATA_DIR;
	private static LockManager lm = new LockManager(); //lock manager used for all transactions

    protected String myRMIName = null; // Used to distinguish this RM from other RMs
    protected TransactionManager tm = null;

	private Integer semaphore = 1;
	private boolean isLocal = false;

	
	//Tables
	private Map<String, Flight> flights; 
	private Map<String, Customer> customers; 
	private Map<String, Car> cars; 
	private Map<String, Hotel> rooms; 

	public static final String FLIGHTS = "flights";
	public static final String CARS = "cars";
	public static final String RESERVATIONS = "reservations";
	public static final String HOTELS = "hotels";
	public static final String CUSTOMERS = "customers";
	public static final String DB_NAME = "travel.db"; //version pointer file for db
	public static final String INVALID_CUST_NAME = "Invalid customer name";
	public static final String INVALID_CAR_LOCATION =  "Invalid car location";
	public static final String INVALID_HOTEL_LOCATION =  "Invalid hotel location";
	public static final String INVALID_FLIGHT_NUM =  "Invalid flight number";

    private Integer xidCounter; //transaction numbering, this will be used to assigned xids

	private Map<Integer, Transaction> activeTrans;  //list of all active transactions
	private Map<Integer, Transaction> abortedTrans; //list of all aborted transactions

	private static final int NO_PRICE_CHANGE = -1;

	public boolean dieBefore = false; 
	public boolean dieAfter = false;

	public boolean dieAfterEnlist = false; 
	public boolean dieRMBeforeAbort = false;
    public boolean dieRMBeforeCommit = false; 
    public boolean dieRMAfterPrepare = false; 
	public boolean dieRMBeforePrepare = false; 

	static {
		//build the path to the data dir
		StringBuilder sb = new StringBuilder(System.getProperty("user.dir"));
		
		if(sb.toString().contains("NetBeansProjects") || sb.toString().contains("eclipse") || sb.toString().contains("Eclipse")){
			sb.append(File.separator).append("project").append(File.separator); //project dir
			sb.append("test.part1").append(File.separator).append("data").append(File.separator); //test.part1/data/
		}
		else {
			sb.append(File.separator).append("data").append(File.separator);
		}
	
		//initialize the global final string
		DATA_DIR = sb.toString();	
	}

    public static void main(String args[]) {
		System.setSecurityManager(new RMISecurityManager());

		String rmiName = System.getProperty("rmiName");
		if (rmiName == null || rmiName.equals("")) {
	   		System.err.println("No RMI name given");
	    	System.exit(1);
		}

		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
	   		rmiPort = "";
		} else if (!rmiPort.equals("")) {
	    	rmiPort = "//:" + rmiPort + "/";
		}

		try {
	   		ResourceManagerImpl obj = new ResourceManagerImpl(rmiName);
	    	Naming.rebind(rmiPort + rmiName, obj);
	    	System.out.println(rmiName + " bound");
		} 
		catch (Exception e) {
	    	System.err.println(rmiName + " not bound:" + e);
	    	System.exit(1);
		}
    }
    
	public ResourceManagerImpl() throws RemoteException{
		this("all");
	}
	
    public ResourceManagerImpl(String resourceName) throws RemoteException {
		this.myRMIName = resourceName;
		File dataDir = new File(DATA_DIR);

		//UNCOMMENT!!
		while (!isLocal && !reconnect()) {
////  		 // would be better to sleep a while
		}	 
		
		//create the output directory for the database files and pointer files if it doesnt exist
		if (!dataDir.exists()) {
			boolean createDataDir = dataDir.mkdir();
			if(!createDataDir){ //failed to create the data dir
				throw new RemoteException("Could not initialize resource manager.");
			}
		}

		//create the empty active and aborted transactions lists
		activeTrans = new HashMap<Integer, Transaction>();
		abortedTrans = new HashMap<Integer, Transaction>();

		//initialize the database
		boolean successfulRecover = recover();

		if(!successfulRecover){
			System.err.println("Error trying to recover the tables.");
			System.exit(-1);
		}
		xidCounter = 1;
	}

    // BEGIN: TRANSACTION INTERFACE
	@Override
    public int start() throws RemoteException {
		synchronized(xidCounter) {
			int xid = tm.start();
			Transaction transaction = new Transaction(xid);
			activeTrans.put(xidCounter, transaction);
			return xidCounter++;
		}
    }

	@Override
    public boolean commit(int xid) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		if(dieRMBeforeCommit){
			dieNow();
		}
		
		System.err.println("Realeasing locks for: " + xid);
		lm.unlockAll(xid);
		System.out.println("Committing xid: " + xid);
		validateTransaction(xid);
		System.out.println("After validate in commit");
		commit(activeTrans.get(xid));
		activeTrans.remove(xid);
		return  true;
    }

	@Override
    public void abort(int xid) throws RemoteException, InvalidTransactionException {
		if(dieRMBeforeAbort){
			dieNow();
		}
		Transaction trans = activeTrans.remove(xid);
		abortedTrans.put(xid, trans);
		lm.unlockAll(xid);

    }
    // END: TRANSACTION INTERFACE

	private void validateTransaction(int xid) throws TransactionAbortedException, InvalidTransactionException, RemoteException {
		if(tm.isValidXID(xid)){
			if(!activeTrans.containsKey(xid)) {
				System.err.println("Adding transaction to list: " + xid + " for " + myRMIName);
				Transaction transaction = new Transaction(xid);
				activeTrans.put(xid, transaction);			
			}	
			
			if(abortedTrans.containsKey(xid)){
				throw new TransactionAbortedException(xid, "Transaction was previously aborted.");
			}

//			if(!activeTrans.containsKey(xid)) {
//				throw new InvalidTransactionException(xid, "Invalid transaction identifier.");
//			}		
		}
		else{
			throw new InvalidTransactionException(xid, "Invalid transaction identifier."); 
		}
	}


    // BEGIN: ADMINISTRATIVE INTERFACE
	@Override
    public boolean addFlight(int xid, String flightNum, int numSeats, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		
		//simple validations
		validate(xid, flightNum, INVALID_FLIGHT_NUM);
		enlistWithTM(xid, myRMIName);
		
		try {

			String flightKey = FLIGHTS + flightNum;

			//lock the object
			boolean lockAcquired = lm.lock(xid, flightKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}	
			
			//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);

			//previously deleted flight, need to remove from deletes list since were readding it
			if(trans.deletes_list.containsKey(flightKey)){
				trans.deletes_list.remove(flightKey);
			}

			Flight flight = null;  
			if(trans.updates_list.containsKey(flightKey)){
				flight = (Flight) trans.updates_list.get(flightKey);
			}
			else{
				//Flight f1 = (Flight) database.query(Database.FLIGHTS, flightNum);	
				Flight f1 = flights.get(flightNum);
				if(f1 != null){
					flight = new Flight(f1.getFlightNum(), f1.getNumSeats(), f1.getNumAvail(), f1.getPrice());
				}
			}

			if(flight == null){ //wasnt in update list or database, must be new flight
				flight = new Flight(flightNum);
			}
	
			flight.setNumSeats(flight.getNumSeats() + numSeats);

			if(flight.getNumAvail() + numSeats < 0){
				return false;
			}

			flight.setNumAvail(flight.getNumAvail() + numSeats);

			if(price > 0) {
				flight.setPrice(price);
			}
				
			trans.updates_list.put(flightKey, flight);
			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
	/**
	 * Checked
	 */
    public boolean deleteFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {

		validate(xid, flightNum, INVALID_FLIGHT_NUM);
		enlistWithTM(xid, RMINameFlights);

		boolean reservationExists = isReservationExist(xid, flightNum);

		if(reservationExists){
			return false;
		}

		try {
			//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);
			String flightKey = FLIGHTS + flightNum;

			boolean lockAcquired = lm.lock(xid, flightKey, LockManager.WRITE);

			if(!lockAcquired){
				return false;
			}

			if(trans.updates_list.containsKey(flightKey)){
				trans.updates_list.remove(flightKey);
			}

			//trans.addDelete(new Flight(flightNum));
			trans.deletes_list.put(flightKey, new Flight(flightNum));

			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }
		
	/**
	 * Checked
	 * @param xid
	 * @param resKey
	 * @return 
	 */
	@Override
	public boolean isReservationExist(int xid, String resKey) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		validateTransaction(xid);
		enlistWithTM(xid, RMINameCustomers);
		
		Transaction trans = activeTrans.get(xid);
		boolean reservationExists;	

		Collection<TableRow> currentUpdates = trans.updates_list.values();

		for(TableRow row: currentUpdates) {
			if(row instanceof Customer) {
				Customer customer = (Customer) row;
				reservationExists = checkReservations(customer, resKey);

				if(reservationExists){
					return true;
				}
			}
		}

		for(Customer customer: customers.values()){
			reservationExists = checkReservations(customer, resKey);
			if(reservationExists){
				return true;
			}
		}

		return false;  //no reservations exist
	}

	private boolean checkReservations(Customer customer, String resKey){
		List<Reservation> customerReservations = customer.getReservations();

		if(customerReservations != null && customerReservations.size() > 0) {
			for(Reservation r: customerReservations){
				if(r.getResvKey().equals(resKey)){
					return true;
				}	
			}
		}	
		return false;  //no reservation matches the reskey
	}
	
	@Override
    public boolean addRooms(int xid, String location, int numRooms, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {

		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameRooms);
	
		try {
			//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);
			String roomsKey = HOTELS + location;

			boolean lockAcquired = lm.lock(xid, roomsKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}

			if(trans.deletes_list.containsKey(roomsKey) && numRooms > 0){
				trans.deletes_list.remove(roomsKey);
			}

			Hotel hotel = null;  
			if(trans.updates_list.containsKey(roomsKey)){
				hotel = (Hotel) trans.updates_list.get(roomsKey);
			}
			else{
				Hotel h1 = rooms.get(location);	
				if(h1 != null){
					hotel = new Hotel(location, h1.getPrice(), h1.getNumRooms(), h1.getNumAvail());
				}
			}

			//wasnt in update list or database, must be new hotel
			if(hotel == null){
				hotel = new Hotel(location);
			}
	
			hotel.setNumAvail(hotel.getNumAvail() + numRooms);
			hotel.setNumRooms(hotel.getNumRooms() + numRooms);
	
			if(price > 0) {
				hotel.setPrice(price);
			}
				
			trans.updates_list.put(roomsKey, hotel);

			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean deleteRooms(int xid, String location, int numRooms) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameRooms);
	
		try {
			//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);
			String roomsKey = HOTELS + location;

			boolean lockAcquired = lm.lock(xid, roomsKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}

			Hotel hotel = null;  
			if(trans.updates_list.containsKey(roomsKey)){
				hotel = (Hotel) trans.updates_list.get(roomsKey);
			}
			else{
				Hotel h1 = rooms.get(location);	
				if(h1 != null){
					hotel = new Hotel(location, h1.getPrice(), h1.getNumRooms(), h1.getNumAvail());
				}
			}				

			//wasnt in update list or database, must be new hotel
			if(hotel == null){
				return false;	
			}
	
			if(hotel.getNumAvail() - numRooms < 0){
				return false;
			}

			hotel.setNumAvail(hotel.getNumAvail() - numRooms);
			trans.updates_list.put(roomsKey, hotel);

			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean addCars(int xid, String location, int numCars, int price) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
			
		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameCars);

		try {
			//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);
			String carsKey = CARS + location;

			boolean lockAcquired = lm.lock(xid, carsKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}

			if(trans.deletes_list.containsKey(carsKey) && numCars > 0){
				trans.deletes_list.remove(carsKey);
			}

			Car car = null;  
			if(trans.updates_list.containsKey(carsKey)){
				car = (Car) trans.updates_list.get(carsKey);
			}
			else{
				Car c1 = cars.get(location);	
				if(c1 != null){
					car = new Car(location, c1.getPrice(), c1.getNumCars(), c1.getNumAvail());
				}
			}

			//wasnt in update list or database, must be new flight
			if(car == null){
				car = new Car(location);
			}

			car.setNumCars(car.getNumCars() + numCars);
			car.setNumAvail(car.getNumAvail() + numCars);

			if(price > 0) {
				car.setPrice(price);
			}
			
			trans.updates_list.put(carsKey, car);
			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean deleteCars(int xid, String location, int numCars) throws RemoteException, TransactionAbortedException, InvalidTransactionException {

		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameCars);
	
		try {
				//get the current transaction object to see if we have changed it
			Transaction trans = activeTrans.get(xid);
			String carsKey = CARS + location;

			boolean lockAcquired = lm.lock(xid, carsKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}

			Car car = null;  
			if(trans.updates_list.containsKey(carsKey)){
				car = (Car) trans.updates_list.get(carsKey);
			}
			else{
				Car c1 = cars.get(location);	
				if(c1 != null){
					car = new Car(location, c1.getPrice(), c1.getNumCars(), c1.getNumAvail());
				}
			}

			//wasnt in update list or database, must be new hotel
			if(car == null){
				return false;	
			}
	
			if(car.getNumAvail() - numCars < 0){
				return false;
			}

			car.setNumAvail(car.getNumAvail() - numCars);
			car.setNumCars(car.getNumCars() - numCars);
			
			trans.updates_list.put(carsKey, car);
			
			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean newCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, custName, INVALID_CUST_NAME);
		enlistWithTM(xid, RMINameCustomers);

		try {
			String custKey = CUSTOMERS + custName;
			boolean lockAcquired = lm.lock(xid, custKey, LockManager.WRITE);

			if(!lockAcquired) {
				return false;
			}

			if(customers.containsKey(custName)){
				return true;
			}

			Transaction trans = activeTrans.get(xid);

			Customer customer = new Customer(custName);
			trans.updates_list.put(custKey, customer);
			return true;
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean deleteCustomer(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, custName, INVALID_CUST_NAME);
		enlistWithTM(xid, RMINameCustomers);

		Transaction trans = activeTrans.get(xid);
		try {	
			String custKey = CUSTOMERS + custName;
			boolean custLockAcquired = lm.lock(xid, custKey, LockManager.WRITE);

			if(!custLockAcquired) {
				return false;
			}

			Customer customer = customers.get(custName);
			List<Reservation> customerReservations = customer.getReservations();
		
			//delete all the reservations for the customer => makes seats, rooms, cars available
			if(customerReservations != null && customerReservations.size() > 0) {
				for (Reservation r: customerReservations) {
					int resType = r.getResvType();
					switch (resType){
						case 1:
							//unreserve a flight seat
							addFlight(xid, r.getResvKey(), 1, NO_PRICE_CHANGE);
							break;
						case 2:
							//unreserve a hotel room
							addRooms(xid, r.getResvKey(), 1, NO_PRICE_CHANGE);
							break;
						case 3:
							//unreserve a car 
							addCars(xid, r.getResvKey(), 1, NO_PRICE_CHANGE);
							break;
						default:
							return false;
					}
				}	
			}
			trans.deletes_list.put(custKey, new Customer(custName));
			return true;	
		} catch (DeadlockException e) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }
    // END: ADMINISTRATIVE INTERFACE


    // QUERY INTERFACE
	@Override
    public int queryFlight(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, flightNum, INVALID_FLIGHT_NUM);
		enlistWithTM(xid, RMINameFlights);

		try{
			String flightKey = FLIGHTS + flightNum;
			boolean lockAcquired = lm.lock(xid, flightKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Flight flight = null;

			System.err.println(activeTrans);
			Transaction trans = activeTrans.get(xid);

			System.err.println(trans.deletes_list.size());
			System.err.println("before dletes");
			if(trans.deletes_list.containsKey(flightKey)){
				return 0;
			}

			System.err.println("before updates");
			//check local copy first
			if(trans.updates_list.containsKey(flightKey)) {
				flight = (Flight) trans.updates_list.get(flightKey);
				return flight.getNumAvail();
			}
		
			System.err.println(flights);
			flight = flights.get(flightNum);
			if(flight != null) {
				return flight.getNumAvail();
			}
			return 0;
		} catch(DeadlockException de){
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		} 
    }

	@Override
    public int queryFlightPrice(int xid, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, flightNum, INVALID_FLIGHT_NUM);
		enlistWithTM(xid, RMINameFlights);

		try{
			String flightKey = FLIGHTS + flightNum;
			boolean lockAcquired = lm.lock(xid, flightKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Flight flight = null;
			Transaction trans = activeTrans.get(xid);

			if(trans.deletes_list.containsKey(flightKey)){
				return 0;
			}

			//check local copy first
			if(trans.updates_list.containsKey(flightKey)) {
				flight = (Flight) trans.updates_list.get(flightKey);
				return flight.getPrice();
			}
		
			flight = flights.get(flightNum);
			if(flight != null) {
				return flight.getPrice();
			}
		} catch(DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
		return 0;
    }

	@Override
    public int queryRooms(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameRooms);
		
		try{
			String roomsKey = HOTELS + location;
			boolean lockAcquired = lm.lock(xid, roomsKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Hotel hotel = null;
			Transaction trans = activeTrans.get(xid);

			if(trans.deletes_list.containsKey(roomsKey)){
				return 0;
			}

			if(trans.updates_list.containsKey(roomsKey)){
				hotel = (Hotel)trans.updates_list.get(roomsKey);
				return hotel.getNumAvail();
			}

			hotel = rooms.get(location);
			if(hotel != null) {
				return hotel.getNumAvail();
			}
		} catch (DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		} 
		return 0;
    }

	@Override
    public int queryRoomsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, location, INVALID_HOTEL_LOCATION);
		enlistWithTM(xid, RMINameRooms);

		try {
			String roomsKey = HOTELS + location;
			boolean lockAcquired = lm.lock(xid, roomsKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Hotel hotel = null;

			Transaction trans = activeTrans.get(xid);

			if(trans.deletes_list.containsKey(roomsKey)) {
				return 0;
			}

			if(trans.updates_list.containsKey(roomsKey)){
				hotel = (Hotel) trans.updates_list.get(roomsKey);
				return hotel.getPrice();
			}

			hotel = rooms.get(location);
			if(hotel != null) {
				return hotel.getPrice();
			}
		} catch(DeadlockException de){
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		} 
		return 0;	
    }

	@Override
    public int queryCars(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, location, INVALID_CAR_LOCATION);
		enlistWithTM(xid, RMINameCars);

		try {
			String carsKey = CARS + location;
			boolean lockAcquired = lm.lock(xid, carsKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Car car = null;
			Transaction trans = activeTrans.get(xid);

			if(trans.deletes_list.containsKey(carsKey)){
				return 0;
			}

			if(trans.updates_list.containsKey(carsKey)){
				car = (Car) trans.updates_list.get(carsKey);
				return car.getNumAvail();
			}

			car = cars.get(location);
			if(car != null) {
				return car.getNumAvail();
			}
		} catch (DeadlockException de){
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
		return 0;
    }

	@Override
    public int queryCarsPrice(int xid, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, location, INVALID_CAR_LOCATION);
		enlistWithTM(xid, RMINameCars);

		try {
			String carsKey = CARS + location;
			boolean lockAcquired = lm.lock(xid, carsKey, LockManager.READ);

			if(!lockAcquired) {
				return 0;
			}

			Car car = null;

			Transaction trans = activeTrans.get(xid);

			if(trans.deletes_list.containsKey(carsKey)){
				return 0;
			}
			
			if(trans.updates_list.containsKey(carsKey)){
				car = (Car) trans.updates_list.get(carsKey);
				return car.getPrice();
			}

			car = cars.get(location);
			if(car != null) {
				return car.getPrice();
			}
		} catch (DeadlockException de){
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
		return 0;
    }
		
	@Override
    public int queryCustomerBill(int xid, String custName) throws RemoteException, TransactionAbortedException, InvalidTransactionException { 
		validate(xid, custName, INVALID_CUST_NAME);
		enlistWithTM(xid, RMINameCustomers);

		try { 
			String custKey = CUSTOMERS + custName;
			boolean custLockAcquired = lm.lock(xid, custKey, LockManager.READ);

			if(!custLockAcquired) { 
				return 0;
			}

			Customer customer = customers.get(custName);

			if(customer == null){
				return 0;
			}

			List<Reservation> customerReservations = customer.getReservations();
			
			int billTotal = 0;

			if(customerReservations == null || customerReservations.size() < 1) {
				return billTotal; //customer doesnt exist and/or no reservations for customer;
			}

			for(Reservation r: customerReservations) {
				int resType = r.getResvType();
				switch (resType){
					case 1:
						billTotal += queryFlightPrice(xid, r.getResvKey());
						break;
					case 2:
						billTotal += queryRoomsPrice(xid, r.getResvKey());
						break;
					case 3:
						billTotal += queryCarsPrice(xid, r.getResvKey());
						break;
					default:
						return 0;
				}
			}
			return billTotal;
		} catch (DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

    // RESERVATION INTERFACE
	@Override
    public boolean reserveFlight(int xid, String custName, String flightNum) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, flightNum, INVALID_FLIGHT_NUM);
		try {
			Transaction trans = activeTrans.get(xid);

			if(myRMIName.equals(RMINameFlights)){
				enlistWithTM(xid, RMINameFlights);
				String flightKey = FLIGHTS + flightNum;
				boolean flightLockAcquired = lm.lock(xid, flightKey, LockManager.WRITE);
			
				if(!flightLockAcquired){  //if we couldnt get the flight lock
					return false;
				} 
				
				if(trans.deletes_list.containsKey(flightKey)){
					return false;
				}

				Flight flight = null;  
				if(trans.updates_list.containsKey(flightKey)){
					flight = (Flight) trans.updates_list.get(flightKey);
				}
				else{
					Flight f1 = flights.get(flightNum);	
					if(f1 != null){
						flight = new Flight(flightNum, f1.getNumSeats(), f1.getNumAvail(), f1.getPrice());
					}
				}
	
				//wasnt in update list or database, must be new flight
				if(flight == null){
					return false;
				}

				if(flight.getNumAvail() - 1 < 0){
					return false;
				}

				flight.setNumAvail(flight.getNumAvail() - 1);
					
				trans.updates_list.put(flightKey, flight);
			} 
			else if(myRMIName.equals(RMINameCustomers)) {
				enlistWithTM(xid, RMINameCustomers);

				String custKey = CUSTOMERS + custName;
				boolean customerLockAcquired = lm.lock(xid, custKey, LockManager.WRITE);
				if(!customerLockAcquired) {
					return false;
				}
				Customer customer = null;
				if(trans.updates_list.containsKey(custKey)){
					customer = (Customer) trans.updates_list.get(custKey);
				}
				else{
					Customer c = customers.get(custName); 
					if(c != null){
						customer = new Customer(c.getCustName()); 
					}
				}
	
				if(customer == null){
					customer = new Customer(custName);
				}

				customer.addReservation(new Reservation(Reservation.FLIGHT, flightNum));
					
				trans.updates_list.put(custKey, customer);
			}
			return true;
		} catch (DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }
 
	
	@Override
    public boolean reserveCar(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
			
		validate(xid, location, INVALID_CAR_LOCATION);
		
		try {
			Transaction trans = activeTrans.get(xid);

			if(myRMIName.equals(RMINameCars)){
				enlistWithTM(xid, RMINameCars);
				String carKey = CARS + location;
				boolean carLockAcquired = lm.lock(xid, carKey , LockManager.WRITE);

				if(!carLockAcquired){  //if we couldnt get the car lock
					return false;
				}
				Car car = null;  
				if(trans.updates_list.containsKey(carKey)){
					car = (Car) trans.updates_list.get(carKey);
				}
				else{
					Car c1 = (Car) cars.get(location);	
					if(c1 != null){
						car = new Car(location, c1.getPrice(), c1.getNumCars(), c1.getNumAvail());
					}
				}

				//wasnt in update list or database, must be new flight
				if(car == null){
					return false;
				}

				if(car.getNumAvail() - 1 < 0){
					return false;
				}

				car.setNumAvail(car.getNumAvail() - 1);
				trans.updates_list.put(carKey, car);
			}

			else if(myRMIName.equals(RMINameCustomers)){
				enlistWithTM(xid, RMINameCustomers);

				String custKey = CUSTOMERS + custName;
				boolean customerLockAcquired = lm.lock(xid, custKey, LockManager.WRITE);

				if(!customerLockAcquired) {
					return false;
				}

				Customer customer = null;
				if(trans.updates_list.containsKey(custKey)){
					customer = (Customer) trans.updates_list.get(custKey);
				}
				else{
					Customer c = customers.get(custName); 
					if(c != null){
						customer = new Customer(c.getCustName()); 
					}
				}

				if(customer == null){
					customer = new Customer(custName);
				}

				customer.addReservation(new Reservation(Reservation.CAR, location));
				
				trans.updates_list.put(custKey, customer);	
			}
			return true;
		} catch (DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

	@Override
    public boolean reserveRoom(int xid, String custName, String location) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validate(xid, custName, INVALID_CUST_NAME);
	
		try {
			Transaction trans = activeTrans.get(xid);

			if(myRMIName.equals(RMINameRooms)){
				enlistWithTM(xid, RMINameRooms);
				String hotelKey = HOTELS + location;
				boolean hotelLockAcquired = lm.lock(xid, hotelKey, LockManager.WRITE);

				if(!hotelLockAcquired){  //if we couldnt get the flight lock
					return false;
				}
				Hotel hotel = null;  
				if(trans.updates_list.containsKey(hotelKey)){
					hotel = (Hotel) trans.updates_list.get(hotelKey);
				}
				else{
					Hotel h1 = rooms.get(location);	
					if(h1 != null){
						hotel = new Hotel(location, h1.getPrice(), h1.getNumRooms(), h1.getNumAvail());
					}				
				}
	
				//wasnt in update list or database, must be new flight
				if(hotel == null){
					return false;
				}

				if(hotel.getNumAvail() - 1 < 0){
					return false;
				}

				hotel.setNumAvail(hotel.getNumAvail() - 1);
				trans.updates_list.put(hotelKey, hotel);
			}
	
			else if(myRMIName.equals(RMINameCustomers)){
				enlistWithTM(xid, RMINameCustomers);

				String custKey = CUSTOMERS + custName;
				boolean customerLockAcquired = lm.lock(xid, custKey, LockManager.WRITE);
				if(!customerLockAcquired){
					return false;	
				}
				
				Customer customer = null;
				if(trans.updates_list.containsKey(custKey)){
					customer = (Customer) trans.updates_list.get(custKey);
				}
				else{
					Customer c = customers.get(custName); 
					if(c != null){
						customer = new Customer(c.getCustName()); 
					}
				}

				if(customer == null){
					customer = new Customer(custName);
				}

				customer.addReservation(new Reservation(Reservation.ROOM, location));

				trans.updates_list.put(custKey, customer);
				}
				return true;
		} catch (DeadlockException de) {
			lm.unlockAll(xid);
			abortedTrans.put(xid, activeTrans.remove(xid));
			throw new TransactionAbortedException(xid, "xid: " + "Transaction resulted in a deadlock , Aborting...");
		}
    }

    public boolean reconnect() throws RemoteException {
		String rmiPort = System.getProperty("rmiPort");
		if (rmiPort == null) {
	    	rmiPort = "";
		} else if (!rmiPort.equals("")) {
	    	rmiPort = "//:" + rmiPort + "/";
		}
		try {
	    	tm = (TransactionManager)Naming.lookup(rmiPort + TransactionManager.RMIName);
	    	System.out.println(myRMIName + " bound to TM");
		} 
		catch (Exception e) {
	    	System.err.println(myRMIName + " cannot bind to TM:" + e);
	    	return false;
		}
		return true;
    }

    // TECHNICAL/TESTING INTERFACE
	@Override
    public boolean shutdown() throws RemoteException {
		//flush all contents in the shadow copies to disk
		System.exit(0);
		return true;
    }

	@Override
    public boolean dieNow() throws RemoteException {
		System.exit(1);
		// We won't ever get here since we exited above; but we still need it to please the compiler.
		return true; 
    }

	@Override
    public boolean dieBeforePointerSwitch() throws RemoteException {
		dieBefore = true;
		return true;
    }

	@Override
    public boolean dieAfterPointerSwitch() throws RemoteException {
		dieAfter = true;
		return true;
    }

	public void validate(int xid, String key, String message) throws InvalidTransactionException, TransactionAbortedException, RemoteException{
		validateTransaction(xid);
		if(key == null || key.length() < 1) {
			throw new InvalidTransactionException(xid, message + ": key");
		}
	}

	public boolean getDieAfter()  throws RemoteException{
		return dieAfter;
	}

	public boolean getDieBefore() throws RemoteException {
		return dieBefore;
	}

	public boolean recover()throws RemoteException{
		File file = new File(DATA_DIR + DB_NAME);

		try{
			//pointer file exists, recover all the tables and init the db
			if(!file.exists()){
				return createInitialDatabaseTable();	
			}
		} catch(IOException ioe) {
			return false;
		}

		return recoverTables();
	}

	private Object getObjectFromInput(String tableName){
		StringBuilder tableNameSB = new StringBuilder(DATA_DIR);
		tableNameSB.append(tableName).append(Table.tableExt);

		File file = new File(tableNameSB.toString());
		
		Object tableObj = null;
		if(!file.exists()){
			System.err.println("File did not exist: " + tableName.toString());
			return tableObj;
		}

		try{
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(tableNameSB.toString()));
			tableObj = in.readObject();
			in.close();
		} catch(IOException ioe) {
			return false;
		} catch (ClassNotFoundException cnf){
			System.err.println("error in getObjectFromInput method: " + tableName);
			return false;
		}
		return tableObj;
	}

	private boolean recoverTables(){
		Object mapObject = null;

		if(myRMIName.equals(RMINameFlights) || myRMIName.equals("all")){
			flights = new HashMap<String, Flight>();	
			mapObject = getObjectFromInput(FLIGHTS);
			if(mapObject != null){
				flights = (Map<String, Flight>) mapObject;	
			} 
		}
		else if(myRMIName.equals(RMINameCars) || myRMIName.equals("all")){
			cars = new HashMap<String, Car>();	
			mapObject = getObjectFromInput(CARS);
			if(mapObject != null){
				cars = (Map<String, Car>) mapObject;	
			}
		}
		else if(myRMIName.equals(RMINameCustomers) || myRMIName.equals("all")){
			customers = new HashMap<String, Customer>();
			mapObject = getObjectFromInput(CUSTOMERS);
			if(mapObject != null){
				customers = (Map<String, Customer>) mapObject;	
			} 
		}
		else if(myRMIName.equals(RMINameRooms) || myRMIName.equals("all")){
			rooms = new HashMap<String, Hotel>();	
			System.err.println("Recovering rooms from disk...");
			mapObject = getObjectFromInput(HOTELS);
			if(mapObject != null){
				rooms = (Map<String, Hotel>) mapObject;	
				System.err.println(rooms); 
			}
		}
		
		return true;
	}

	private boolean createInitialDatabaseTable() throws IOException {
		//if any table doesnt exists, the file need to be created
		if(myRMIName.equals("all")){
			flights = new HashMap<String, Flight>();
			rooms = new HashMap<String, Hotel>();
			customers = new HashMap<String, Customer>();
			cars = new HashMap<String, Car>();
		}
		else if(myRMIName.equals(RMINameFlights)){
			flights = new HashMap<String, Flight>();	
		}
		else if(myRMIName.equals(RMINameCars)){
			cars = new HashMap<String, Car>();	
		}
		else if(myRMIName.equals(RMINameCustomers)){
			customers = new HashMap<String, Customer>();
		}
		else if(myRMIName.equals(RMINameRooms)){
			rooms = new HashMap<String, Hotel>();	
		}
		
		try {
			File travelDBFile = new File(DATA_DIR + "travel.db");
			boolean createDBFile = travelDBFile.createNewFile();

			if(createDBFile){
				FileWriter fw = new FileWriter(travelDBFile);
				fw.close();
			}
		} catch (IOException ioe) {
			System.err.println("Could not initialize the database");
			throw new IOException();
		} 
		return true;
	}

	public boolean commit(Transaction transaction) throws RemoteException, TransactionAbortedException {
		System.out.println("begin: commit function"); 
		synchronized(semaphore){
			System.out.println("begin: flights copy");
			Map<String, Flight> flightsCopy = null;
			Map<String, Customer> customersCopy = null;
			Map<String, Car> carsCopy = null;
			Map<String, Hotel> roomsCopy = null;
			
			if(myRMIName.equals(RMINameFlights)){
				System.out.println("Flights obj: " + flights);
				flightsCopy = new HashMap<String, Flight>();
				if(flights != null){
					flightsCopy.putAll(flights);
				}
				System.out.println("end: flights copy");
			}
		
			else if(myRMIName.equals(RMINameCustomers)){
				System.out.println("begin: customer copy");
				customersCopy = new HashMap<String, Customer>(); 
				if(customers != null){
					customersCopy.putAll(customers);
				}
				System.out.println("end: customer copy");
			}
	
			else if(myRMIName.equals(RMINameCars)){
				System.out.println("begin: cars copy");
				carsCopy = new HashMap<String, Car>();
				if(cars != null){
					carsCopy.putAll(cars);
				}
				System.out.println("end: cars copy");
			}

			 if(myRMIName.equals(RMINameRooms)){
				System.out.println("begin: rooms copy");
				roomsCopy = new HashMap<String, Hotel>();
				if(rooms != null){
					roomsCopy.putAll(rooms);
				}
				System.out.println("end: rooms copy");
			 }
	
			System.out.println("before updates");
			boolean hadChanges = false;
			List<TableRow> updates = transaction.getUpdates();
			if(updates != null && updates.size() > 0) {
				hadChanges = true;
				for(TableRow update: updates) {
					if(update instanceof Flight){
						Flight f = (Flight) update;
						flightsCopy.put(f.getFlightNum(), f);
					}
					else if(update instanceof Car) {
						Car c = (Car) update;
						carsCopy.put(c.getLocation(), c);
					}
					else if(update instanceof Customer){
						Customer c = (Customer) update;
						customersCopy.put(c.getCustName(), c);
					}
					else if(update instanceof Hotel){
						Hotel h = (Hotel) update;
						roomsCopy.put(h.getLocation(), h);
					}
				}
			} 
	
			System.out.println("before deletes");
			List<TableRow> deletes = transaction.getDeletes();
			if(deletes != null && deletes.size() > 0) {
				hadChanges = true;
				for(TableRow update: deletes) {
					if(update instanceof Flight){
						Flight f = (Flight) update;
						flightsCopy.remove(f.getFlightNum());
					}
					else if(update instanceof Customer){
						Customer c = (Customer) update; 
						customers.remove(c.getCustName());
					}
				}
			}
			System.out.println("after deletes");
	
			if(dieBefore){
				System.out.println("Die before pointer switch now executing.");	
				System.exit(-1);
			}
	
			if(!hadChanges){
				return true;
			}

			try{
				if(myRMIName.equals(RMINameCustomers)){
					customers.putAll(customersCopy);			
					Table.writeTable(customers, getTableFileName(CUSTOMERS));
					System.err.println("Customers to be committed :" + customers);
				}
				else if (myRMIName.equals(RMINameCars)){
					cars.putAll(carsCopy);
					Table.writeTable(cars, getTableFileName(CARS));
					System.err.println("Cars to be committed :" + cars);
				}
				else if(myRMIName.equals(RMINameFlights)){
					flights.putAll(flightsCopy);
					Table.writeTable(flights, getTableFileName(FLIGHTS));
					System.err.println("Flights to be committed :" + flights); 
				}	
				else if(myRMIName.equals(RMINameRooms)) {
					rooms.putAll(roomsCopy);
					Table.writeTable(rooms, getTableFileName(HOTELS));
					System.err.println("Rooms to be committed :" + rooms);
				}
			} catch(IOException ioe){
				System.err.println("Error persisting tables during commit");
			}
	
			if(dieAfter){
				System.out.println("Die after pointer switch now executing.");	
				System.exit(-1);
			}
			System.out.println("end: commit function"); 
			lm.unlockAll(xidCounter);
			return true;
		}
	}

	private static String getTableFileName(String tableName) {
		StringBuilder tableNameSB = new StringBuilder(DATA_DIR);
		tableNameSB.append(tableName).append(Table.tableExt);
		return tableNameSB.toString();
	}

	/**
	 * DEBUGGING UTILITIES BELOW THIS COMMENT, NOT PART OF PROJECT
	 */

	public static void clearTables() throws RemoteException{
		Table.removeTableFile(getTableFileName(FLIGHTS));
		Table.removeTableFile(getTableFileName(CARS));
		Table.removeTableFile(getTableFileName(CUSTOMERS));
		Table.removeTableFile(getTableFileName(HOTELS));
		Table.removeTableFile(DATA_DIR + "travel.db");
	}

	public void printTables() throws RemoteException, IOException{
		System.out.print("\f");
		System.out.println("########################### Printing Table: " + FLIGHTS + " ###########################");
		printFlights();
		System.out.println("\n\n\n");
		System.out.println("########################### Printing Table: " + CUSTOMERS + " ###########################");
		printCustomers();
		System.out.println("\n\n\n");
		System.out.println("########################### Printing Table: " + CARS + " ###########################");
		printCars();
		System.out.println("\n\n\n");
		System.out.println("########################### Printing Table: " + HOTELS + " ###########################");
		printRooms();
		System.out.println("\n\n\n");
	}	

	public void printFlights() throws RemoteException{
		if(flights == null || flights.size() <= 0 || flights.keySet().size() <= 0){
			System.out.println("No flights found in the table.");
			return;
		} 
		for(String flightNum : flights.keySet()) {
			Flight flight = flights.get(flightNum);
			System.out.print("Flight Num : " + flight.getFlightNum());
			System.out.printf("\t\t%s: %s", "Num Seats: ", flight.getNumSeats());
			System.out.printf("\t\t%s: %s", "Avail Seats: ", flight.getNumAvail());
			System.out.printf("\t\t%s: %s\n", "Price p/Seat: ", flight.getPrice());
		}
	}

	public void printCustomers() throws RemoteException {
		if(customers == null || customers.size() <= 0){
			System.out.println("No customers found in the table.");
			return;
		}
		for(String custName : customers.keySet()) {
			Customer customer = customers.get(custName);
			List<Reservation> reservations = customer.getReservations();
			System.out.println("Customer: " + custName + " has " + reservations.size() + " reservations");
			if(reservations != null && reservations.size() >  0){
				for(Reservation r: reservations){
					System.out.printf("\t\t%s: %s", "ResvType: ", r.getResvType());
					System.out.printf("\t\t%s: %s\n", "ResvKey: ", r.getResvKey());				
				} 
			}
			else{
				System.out.println("\t\tCustomer has no reservations.");
			}
		}
	}

	public void printCars() throws RemoteException{
		if(cars == null || cars.size() <= 0 || cars.keySet().size() <= 0){
			System.out.println("No cars found in the table.");
			return;
		}
		for(String location : cars.keySet()) {
			Car car = cars.get(location);
			System.out.print("Car Location: " + car.getLocation());
			System.out.printf("\t\t%s: %s", "Num Cars: ", car.getNumCars());
			System.out.printf("\t\t%s: %s", "Avail Cars: ", car.getNumAvail());
			System.out.printf("\t\t%s: %s\n", "Price p/Car: ", car.getPrice());
		}
	}

	public void printRooms() throws RemoteException{
		if(rooms == null || rooms.size() <= 0 || rooms.keySet().size() <= 0){
			System.out.println("No hotels found in the table.");
			return;
		}
		for(String location : rooms.keySet()) {
			Hotel hotel = rooms.get(location);
			System.out.print("Hotel Location: " + hotel.getLocation());
			System.out.printf("\t\t%s: %s", "Num Rooms: ", hotel.getNumRooms());
			System.out.printf("\t\t%s: %s", "Avail Rooms: ", hotel.getNumAvail());
			System.out.printf("\t\t%s: %s\n", "Price p/Room: ", hotel.getPrice());
		} 
	}

	@Override
	public List<String> getCustomerFlightReservations(int xid, String custName)throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validateTransaction(xid);
		enlistWithTM(xid, RMINameCustomers);

		List<String> flightNumList = new ArrayList<String>();
	
		Customer customer = customers.get(custName);
		if(customer == null){
			return flightNumList;
		}

		List<Reservation> custReservations = customer.getReservations();
		for(Reservation res : custReservations){
			if(res.getResvType() == Reservation.FLIGHT){
				flightNumList.add(res.getResvKey());
			}
		}
		return flightNumList;
	}

	@Override
	public List<String> getCustomerCarReservations(int xid, String custName)throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validateTransaction(xid);
		enlistWithTM(xid, RMINameCustomers);

		List<String> carLocationList = new ArrayList<String>();
	
		Customer customer = customers.get(custName);
		if(customer == null){
			return carLocationList;
		}

		List<Reservation> custReservations = customer.getReservations();
		for(Reservation res : custReservations){
			if(res.getResvType() == Reservation.CAR){
				carLocationList.add(res.getResvKey());
			}
		}
		return carLocationList;
	}

	@Override
	public List<String> getCustomerRoomReservations(int xid, String custName)throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		validateTransaction(xid);
		enlistWithTM(xid, RMINameCustomers);

		List<String> roomLocationList = new ArrayList<String>();
	
		Customer customer = customers.get(custName);
		if(customer == null){
			return roomLocationList;
		}

		List<Reservation> custReservations = customer.getReservations();
		for(Reservation res : custReservations){
			if(res.getResvType() == Reservation.ROOM){
				roomLocationList.add(res.getResvKey());
			}
		}
		return roomLocationList;
	}

	public void setTm(TransactionManager tm) throws RemoteException {
		this.tm = tm;
	} 

	@Override
	public boolean unreserveFlights(List<String> flightNumList, String custName, int xid) throws RemoteException{
		if(flightNumList == null || flightNumList.size() < 1){
			Transaction trans = activeTrans.get(xid);
			for(String flightNum:flightNumList){
				if(trans.updates_list.containsKey(FLIGHTS + flightNum)){
					trans.updates_list.remove(FLIGHTS + flightNum);
				}
			}

			HashSet<String> flightsSet = new HashSet<String>();
			flightsSet.addAll(flightNumList);
			if(trans.updates_list.containsKey(CUSTOMERS + custName)){
				Customer customer = (Customer) trans.updates_list.get(CUSTOMERS + custName);
			
				List<Reservation> custReservations = customer.getReservations();
				int i = 0;
				for(Reservation r : customer.getReservations()){
					if(r.getResvType() == 3 && flightsSet.contains(r.getResvKey())){
						System.err.println("Removing customer flight reservation: " + r.getResvKey());
						custReservations.remove(i);
					}
					i++;
				}
			}
		} 
		return true;
	}

	public boolean unreserveCar(String location, String custName, int xid) throws RemoteException{
		Transaction trans = activeTrans.get(xid);

		if(trans.updates_list.containsKey(CUSTOMERS + custName)){
			Customer customer = (Customer) trans.updates_list.get(CUSTOMERS + custName);

			List<Reservation> custReservations = customer.getReservations();
			int i = 0;
			for(Reservation r : custReservations){
				if(r.getResvType() == 3 && r.getResvKey().equals(location)){
					System.err.println("Removing customer car reservation: " + r.getResvKey()); 
					custReservations.remove(i);
				}
				i++;
			}
		}	
		return true;
	}

	private void enlistWithTM(int xid, String who) throws RemoteException{
		tm.enlist(xid, who);
		if(dieAfterEnlist){
			dieNow();
		}
	}

	@Override
	public boolean setDieAfterEnlist() throws RemoteException{
		dieAfterEnlist = true;
		return dieAfterEnlist;
	}	
	@Override
	public boolean clearLocks(int xid) throws RemoteException {
		System.err.println("Releasing all locks for: " + xid);
		boolean ret = lm.unlockAll(xid);
		System.err.println("Return status for released locks: " + ret);
		return ret;

	}

	@Override
	public boolean setDieRMBeforeAbort() throws RemoteException {
		System.err.println("Set diebeforeabort for " + myRMIName);
		dieRMBeforeAbort = true;
		return true;
	}

	@Override
	public boolean setDieRMBeforeCommit() throws RemoteException {
		System.err.println("Set dieafterecommit for " + myRMIName);
		dieRMBeforeCommit = true;
		return true;
	}

	@Override
	public boolean setDieRMAfterPrepare() throws RemoteException {
		System.err.println("Set dieaftereprepare for " + myRMIName);
		dieRMAfterPrepare = true;
		return true;
	}

	@Override
	public boolean setDieRMBeforePrepare() throws RemoteException {
		System.err.println("Set diebeforeprepare for " + myRMIName);
		dieRMBeforePrepare = true;
		return true;
	}

	@Override
	public boolean prepareToCommit(int xid) throws RemoteException {
		if(dieRMBeforePrepare){
			dieNow();
		}
		return true;
	}

	public boolean isDieBefore()throws RemoteException {
		return dieBefore;
	}

	public boolean isDieAfter() throws RemoteException{
		return dieAfter;
	}

	public boolean isDieAfterEnlist() throws RemoteException{
		return dieAfterEnlist;
	}

	public boolean isDieRMBeforeAbort() throws RemoteException{
		return dieRMBeforeAbort;
	}

	public boolean isDieRMBeforeCommit()throws RemoteException {
		return dieRMBeforeCommit;
	}

	public boolean isDieRMAfterPrepare()throws RemoteException {
		return dieRMAfterPrepare;
	}

	public boolean isDieRMBeforePrepare() throws RemoteException{
		return dieRMBeforePrepare;
	}

}