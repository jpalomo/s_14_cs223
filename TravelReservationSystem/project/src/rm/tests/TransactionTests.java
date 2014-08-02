/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rm.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;
import transaction.InvalidTransactionException;
import transaction.ResourceManager;
import transaction.ResourceManagerImpl;
import transaction.TransactionAbortedException;
import transaction.WorkflowControllerImpl;
import java.util.ArrayList;
import transaction.TransactionManagerImpl;
/**
 *
 * @author Palomo
 */

public class TransactionTests {
	public static String[] customers = {"John", "Lindsay", "Bob", "Ryan", "Katie", "Tripper"};	

	public static void main(String args[]) throws RemoteException, TransactionAbortedException, InvalidTransactionException, FileNotFoundException, IOException {
		TransactionManagerImpl tm = new TransactionManagerImpl();
		ResourceManagerImpl rmflights = new ResourceManagerImpl(ResourceManager.RMINameFlights);
		rmflights.setTm(tm);

		ResourceManagerImpl rmcustomers = new ResourceManagerImpl(ResourceManager.RMINameCustomers);
		rmcustomers.setTm(tm);

		ResourceManagerImpl rmcars = new ResourceManagerImpl(ResourceManager.RMINameCars);
		rmcars.setTm(tm);

		ResourceManagerImpl rmhotels = new ResourceManagerImpl(ResourceManager.RMINameRooms);
		rmhotels.printRooms();
		rmhotels.setTm(tm);

		
		WorkflowControllerImpl wc = new WorkflowControllerImpl();
		wc.setRM(ResourceManager.RMINameFlights, rmflights); 
		wc.setRM(ResourceManager.RMINameCars, rmcars); 
		wc.setRM(ResourceManager.RMINameCustomers, rmcustomers); 
		wc.setRM(ResourceManager.RMINameRooms, rmhotels); 
		wc.setTM(tm);


		tm.setRM(ResourceManager.RMINameFlights, rmflights); 
		tm.setRM(ResourceManager.RMINameCars, rmcars); 
		tm.setRM(ResourceManager.RMINameCustomers, rmcustomers); 
		tm.setRM(ResourceManager.RMINameRooms, rmhotels); 

//		badd(wc);
//		baddabt(wc);
//		baddabtrd(wc);
//		baddcmtrd(wc);
//		baddcmtrsv(wc);
//		bstcmt(wc);
//		bstabt(wc);
//		bstart(wc);
		Fdieall(wc);
//		FunLock(wc);
//		Lconc(wc);
//		Saddcmtdelcmt(wc);
//		Saddrd(wc);
//		Sbadxid(wc);
//		sbill(wc);
//		Sitiabt(wc);
//		Siticmt(wc);
//		Sitifail(wc);
		System.exit(1);
		}
	
		public static void badd(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			returnValue = wc.newCustomer(xid, "John");	
			assert returnValue == true;
		}

		
		public static void baddabt(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			returnValue = wc.newCustomer(xid, "John");	
			assert returnValue == true;
			wc.abort(xid);
		}
		
		
		public static void baddabtrd(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			returnValue = wc.newCustomer(xid, "John");	
			assert returnValue == true;
			wc.commit(xid);
			
			int xid2 = wc.start();
			returnValue = wc.addFlight(xid, "347", 100, 620);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 300);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 60);
			assert returnValue == true;
			wc.abort(xid2);
			
			int xid3 = wc.start();
			int flight = wc.queryFlight(xid3,"347");
			assert flight == 100;
			int flightPrice = wc.queryFlightPrice(xid3,"347");
			assert flightPrice == 310;
			int rooms = wc.queryRooms(xid3,"SFO");
			assert rooms == 200;
			int roomsPrice = wc.queryRoomsPrice(xid3,"SFO");
			assert roomsPrice == 150;
			int cars = wc.queryCars(xid3,"SFO");
			assert cars == 300;
			int carsprice = wc.queryCarsPrice(xid3, "SFO");
			assert carsprice == 30;
			int customerbill = wc.queryCustomerBill(xid3, "John");
			assert customerbill == 0;
		}
		
		
		public static void baddcmtrd(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			returnValue = wc.newCustomer(xid, "John");	
			assert returnValue == true;
			wc.commit(xid);
			
			int xid2 = wc.start();
			int flight = wc.queryFlight(xid2,"347");
			assert flight == 100;
			int flightPrice = wc.queryFlightPrice(xid2,"347");
			assert flightPrice == 310;
			int rooms = wc.queryRooms(xid2,"SFO");
			assert rooms == 200;
			int roomsPrice = wc.queryRoomsPrice(xid2,"SFO");
			assert roomsPrice == 150;
			int cars = wc.queryCars(xid2,"SFO");
			assert cars == 300;
			int carsprice = wc.queryCarsPrice(xid2, "SFO");
			assert carsprice == 30;
			int customerbill = wc.queryCustomerBill(xid2, "John");
			assert customerbill == 0;
		}
		
		public static void baddcmtrsv(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			returnValue = wc.newCustomer(xid, "John");	
			assert returnValue == true;
			wc.commit(xid);
			
			int xid2 = wc.start();
			int flight = wc.queryFlight(xid2,"347");
			assert flight == 100;
			int flightPrice = wc.queryFlightPrice(xid2,"347");
			assert flightPrice == 310;
			int rooms = wc.queryRooms(xid2,"SFO");
			assert rooms == 200;
			int roomsPrice = wc.queryRoomsPrice(xid2,"SFO");
			assert roomsPrice == 150;
			int cars = wc.queryCars(xid2,"SFO");
			assert cars == 300;
			int carsprice = wc.queryCarsPrice(xid2, "SFO");
			assert carsprice == 30;
			int customerbill = wc.queryCustomerBill(xid2, "John");
			assert customerbill == 0;
		}
		
		public static void bstcmt(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			System.out.println(xid);
				boolean returnValue = wc.commit(xid);
			System.out.println(returnValue);
			}
		
		public static void bstabt(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			System.out.println(xid);
			wc.abort(xid);
			}
		
		public static void bstart(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = wc.start();
			System.out.println(xid);
			}
		
		public static void Fdieb4self(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			
			int xid = rm.start();
			rm.addFlight(xid, "347", 100, 310);
			rm.addRooms(xid, "Stanford", 200, 150);
			rm.addCars(xid, "SFO", 300, 30);
			rm.newCustomer(xid, "John");	
			rm.commit(xid);

			xid = rm.start();
			rm.addFlight(xid, "347", 100, 620);
			rm.addRooms(xid, "Stanford", 200, 300);
			rm.addCars(xid, "SFO", 300, 60);
			rm.dieBeforePointerSwitch();	
			try{

				rm.commit(xid);
			}catch(RemoteException e){
				
				System.out.println("Caught remote exception");
			}
			xid = rm.start();
			int value = rm.queryFlight(xid, "347");
			System.out.println(value);
			value = rm.queryFlightPrice(xid, "347");
			System.out.println(value);
			value = rm.queryRooms(xid, "Stanford");
			System.out.println(value);
			value = rm.queryRoomsPrice(xid, "Stanford");
			System.out.println(value);
		}
	
		
		public static void Fdieall(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
//			int xid = wc.start();
//			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
//			assert returnValue == true;
//			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
//			assert returnValue == true;
//			returnValue = wc.addCars(xid, "SFO", 300, 30);
//			assert returnValue == true;
//			returnValue = wc.newCustomer(xid, "John");	
//			assert returnValue == true;
//			wc.commit(xid);
//			try{
//			wc.dieNow("ALL");
//			} catch(RemoteException e)
//			{  
//		      System.out.println("Caught remote exception");	
//			}
			int xid2 = wc.start();
			int flight = wc.queryFlight(xid2,"347");
			assert flight == 100;
			int flightPrice = wc.queryFlightPrice(xid2,"347");
			assert flightPrice == 310;
			int rooms = wc.queryRooms(xid2,"Stanford");
			assert rooms == 200;
			int roomsPrice = wc.queryRoomsPrice(xid2,"Stanford");
			assert roomsPrice == 150;
			int cars = wc.queryCars(xid2,"SFO");
			assert cars == 300;
			int carsprice = wc.queryCarsPrice(xid2, "SFO");
			assert carsprice == 30;
			int customerbill = wc.queryCustomerBill(xid2, "John");
			assert customerbill == 0;		
		}

		public static void LdeadLock(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = rm.start();
			rm.addFlight(xid, "347", 100, 310);
			rm.addRooms(xid, "Stanford", 200, 150);
			rm.addCars(xid, "SFO", 300, 30);
			rm.newCustomer(xid, "John");	
			rm.commit(xid);

			xid = rm.start();
			int xid2 = rm.start();
			rm.addFlight(xid2, "347", 100, 620);
			rm.addRooms(xid, "Stanford", 200, 300);
			try {
			rm.queryRooms(xid2, "Stanford");
			}catch(TransactionAbortedException e){
				System.out.println("Caught deadlock");
			}
			rm.commit(xid);
			//printTables(rm);

			xid = rm.start();
			int value = rm.queryFlight(xid, "347");
			System.out.println(value);
			value = rm.queryFlightPrice(xid, "347");
			System.out.println(value);
			value = rm.queryRooms(xid, "Stanford");
			System.out.println(value);
			value = rm.queryRoomsPrice(xid, "Stanford");
			System.out.println(value);
		}
		
		public static void FunLock(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
/*			int xid = wc.start();
			wc.addFlight(xid, "347", 100, 310);
			wc.addRooms(xid, "Stanford", 200, 150);
			wc.addCars(xid, "SFO", 300, 30);
			wc.newCustomer(xid, "John");	
			wc.commit(xid);
			xid = wc.start();
			int xid2 = wc.start();
			wc.addFlight(xid2, "347", 100, 620);
			wc.addRooms(xid, "Stanford", 200, 300);
			try {
			wc.queryRooms(xid2, "Stanford");
			}catch(TransactionAbortedException e){
				System.out.println("Caught deadlock");
			}
			wc.commit(xid);
			//printTables(wc);
			xid = wc.start();
			int value = wc.queryFlight(xid, "347");
			System.out.println(value);
			value = wc.queryFlightPrice(xid, "347");
			System.out.println(value);
			value = wc.queryRooms(xid, "Stanford");
			System.out.println(value);
			value = wc.queryRoomsPrice(xid, "Stanford");
			System.out.println(value);
*/		
			int xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			wc.commit(xid);
			xid = wc.start();
			xid = wc.start();
			returnValue = wc.addFlight(xid,"347",100,620);
			assert returnValue == true;
			int flight = wc.queryFlight(xid,"347");
			assert flight == 100;
			returnValue = wc.dieTMAfterCommit();
			assert returnValue == true;
			try{
				wc.commit(xid);
				} catch(RemoteException e)
				{  
			      System.out.println("Caught remote exception");	
				}
		}
		
		public static void Lconc(WorkflowControllerImpl wc)throws RemoteException, TransactionAbortedException, InvalidTransactionException{	
			
			int xid = wc.start();
			xid = wc.start();
			boolean returnValue = wc.addFlight(xid, "347", 100, 310);
			assert returnValue == true;
			returnValue = wc.addRooms(xid, "Stanford", 200, 150);
			assert returnValue == true;
			returnValue = wc.addCars(xid, "SFO", 300, 30);
			assert returnValue == true;
			wc.commit(xid);
			
			int xid2 = wc.start();
			int flight = wc.queryFlight(xid2,"347");
			assert flight == 100;
			int flightPrice = wc.queryFlightPrice(xid2,"347");
			assert flightPrice == 310;
			int rooms = wc.queryRooms(xid2,"SFO");
			assert rooms == 200;
			int roomsPrice = wc.queryRoomsPrice(xid2,"SFO");
			assert roomsPrice == 150;
			int cars = wc.queryCars(xid2,"SFO");
			assert cars == 300;
			int carsprice = wc.queryCarsPrice(xid2, "SFO");
			assert carsprice == 30;
		}
		

		public static void Stoomanyrsv(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
				int xid = rm.start();
			rm.addFlight(xid, "347", 1, 310);
			rm.addRooms(xid, "Stanford", 1, 150);
			rm.addCars(xid, "SFO", 1, 30);
			rm.newCustomer(xid, "John");
			rm.newCustomer(xid, "Bob");
			rm.commit(xid);
			//printTables(rm);	

			xid = rm.start();
			boolean returnVal = rm.reserveFlight(xid,"John","347");
			returnVal = rm.reserveRoom(xid,"John","Stanford");
			returnVal = rm.reserveCar(xid,"John","SFO");
			returnVal = rm.commit(xid);
			//printTables(rm);	

			xid = rm.start();
			returnVal = rm.reserveFlight(xid,"Bob","347");
			returnVal = rm.reserveRoom(xid,"Bob","Stanford");
			returnVal = rm.reserveCar(xid,"Bob","SFO");
			returnVal = rm.commit(xid);
			rm.commit(xid);
			//printTables(rm);	

		}

		
	public static void Saddcmtdelrsv(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
			int xid = rm.start();
			rm.addFlight(xid, "347", 100, 310);
			rm.addRooms(xid, "Stanford", 200, 150);
			rm.addCars(xid, "SFO", 300, 30);
			rm.newCustomer(xid, "John");	
			rm.commit(xid);
			//printTables(rm);	

			xid = rm.start();
			rm.deleteFlight(xid, "347");
			rm.deleteRooms(xid, "Stanford", 200);
			rm.deleteCars(xid, "SFO", 300);

			boolean returnVal = rm.reserveFlight(xid,"John","347");
			returnVal = rm.reserveRoom(xid,"John","Stanford");
			returnVal = rm.reserveCar(xid,"John","SFO");
			returnVal = rm.commit(xid);
			//printTables(rm);	
	}


	public static void Saddcmtdelcmt(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		
		int xid = wc.start();
		xid = wc.start();
		boolean returnValue = wc.addRooms(xid, "Stanford", 200, 150);
		assert returnValue == true;
		returnValue = wc.addCars(xid, "SFO", 300, 30);
		assert returnValue == true;
		wc.commit(xid);
		
		int xid2 = wc.start();
		boolean rooms = wc.deleteRooms(xid2,"Stanford",5);
		assert rooms == true;
		boolean cars = wc.deleteCars(xid2,"Stanford",5);
		assert cars == true;
		wc.commit(xid2);
		
		int xid3 = wc.start();
		int noofrooms = wc.queryRooms(xid3,"Stanford");
		assert noofrooms == 195;
		int noofcars = wc.queryCars(xid3,"Stanford");
		assert noofcars == 195;
		
		
	}
	
	
	public static void Saddrd(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
	
		int xid = wc.start();
		xid = wc.start();
		boolean returnValue = wc.addFlight(xid, "347", 100, 310);
		assert returnValue == true;
		returnValue = wc.addRooms(xid, "Stanford", 200, 150);
		assert returnValue == true;
		returnValue = wc.addCars(xid, "SFO", 300, 30);
		assert returnValue == true;
		
		int flight = wc.queryFlight(xid,"347");
		assert flight == 100;
		int flightPrice = wc.queryFlightPrice(xid,"347");
		assert flightPrice == 310;
		int noofrooms = wc.queryRooms(xid,"Stanford");
		assert noofrooms == 200;
		int roomsPrice = wc.queryRoomsPrice(xid,"Stanford");
		assert roomsPrice == 150;
		int noofcars = wc.queryCars(xid,"SFO");
		assert noofcars == 300;
		int carsprice = wc.queryCarsPrice(xid,"SFO");
		assert carsprice == 30;
		
	}
	
	public static void Sbadxid(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		
		int xid = wc.start();
		xid = wc.start();
		try {wc.addFlight(7123894, "347", 100, 310);
		}
		catch(InvalidTransactionException e) {
			System.out.println("Caught remote exception");
		}
	}
	
	
	
	public static void Sitiabt(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
	
		int xid = wc.start();
		xid = wc.start();
		boolean returnValue = wc.addFlight(xid, "347", 100, 310);
		assert returnValue == true;
	    returnValue = wc.addFlight(xid,"3471",1001,3101);
		assert returnValue == true;
		returnValue = wc.addRooms(xid, "Stanford", 200, 150);
		assert returnValue == true;
		returnValue = wc.addCars(xid, "SFO", 300, 30);
		assert returnValue == true;
		returnValue = wc.newCustomer(xid,"John");
		assert returnValue == true;
		wc.commit(xid);
		
		xid = wc.start();
		List<String> FlightList = new ArrayList<String>();
		FlightList.add("347");
		FlightList.add("3471");
		returnValue = wc.reserveItinerary(xid,"John",FlightList,"Stanford",false,true);
		assert returnValue == true;
		wc.abort(xid);
		
		xid = wc.start();
		int flight = wc.queryFlight(xid,"347");
		assert flight == 100;
		int flightPrice = wc.queryFlightPrice(xid,"347");
		assert flightPrice == 310;
		flight = wc.queryFlight(xid,"3471");
		assert flight == 1001;
		flightPrice = wc.queryFlightPrice(xid,"3471");
		assert flightPrice == 3101;
		int noofrooms = wc.queryRooms(xid,"Stanford");
		assert noofrooms == 200;
		int roomsPrice = wc.queryRoomsPrice(xid,"Stanford");
		assert roomsPrice == 150;
		int noofcars = wc.queryCars(xid,"SFO");
		assert noofcars == 300;
		int carsprice = wc.queryCarsPrice(xid,"SFO");
		assert carsprice == 30;
		int customerbill = wc.queryCustomerBill(xid, "John");
		assert customerbill == 0;		
		
	}
	
	public static void Siticmt(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		
		int xid = wc.start();
		xid = wc.start();
		boolean returnValue = wc.addFlight(xid, "347", 100, 310);
		assert returnValue == true;
	    returnValue = wc.addFlight(xid,"3471",1001,3101);
		assert returnValue == true;
		returnValue = wc.addRooms(xid, "Stanford", 200, 150);
		assert returnValue == true;
		returnValue = wc.addCars(xid, "SFO", 300, 30);
		assert returnValue == true;
		returnValue = wc.newCustomer(xid,"John");
		assert returnValue == true;
		wc.commit(xid);
		
		xid = wc.start();
		List<String> FlightList = new ArrayList<String>();
		FlightList.add("347");
		FlightList.add("3471");
		returnValue = wc.reserveItinerary(xid,"John",FlightList,"Stanford",false,true);
		assert returnValue == true;
		wc.commit(xid);
		
		xid = wc.start();
		int flight = wc.queryFlight(xid,"347");
		assert flight == 99;
		int flightPrice = wc.queryFlightPrice(xid,"347");
		assert flightPrice == 310;
		flight = wc.queryFlight(xid,"3471");
		assert flight == 1000;
		flightPrice = wc.queryFlightPrice(xid,"3471");
		assert flightPrice == 3101;
		int noofrooms = wc.queryRooms(xid,"Stanford");
		assert noofrooms == 199;
		int roomsPrice = wc.queryRoomsPrice(xid,"Stanford");
		assert roomsPrice == 150;
		int noofcars = wc.queryCars(xid,"SFO");
		assert noofcars == 300;
		int carsprice = wc.queryCarsPrice(xid,"SFO");
		assert carsprice == 30;
		int customerbill = wc.queryCustomerBill(xid, "John");
		assert customerbill == 3561;		
		
	}
	
	
	public static void Sitifail(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		
		int xid = wc.start();
		xid = wc.start();
		boolean returnValue = wc.addFlight(xid, "347", 100, 310);
		assert returnValue == true;
	    returnValue = wc.addFlight(xid,"3471",1001,3101);
		assert returnValue == true;
		returnValue = wc.addRooms(xid, "Stanford", 200, 150);
		assert returnValue == true;
		returnValue = wc.addCars(xid, "SFO", 300, 30);
		assert returnValue == true;
		returnValue = wc.newCustomer(xid,"John");
		assert returnValue == true;
		wc.commit(xid);
		
		xid = wc.start();
		returnValue = wc.reserveCar(xid,"John","SFO");
		assert returnValue == true;
		List<String> FlightList = new ArrayList<String>();
		FlightList.add("347");
		FlightList.add("3471");
		returnValue = wc.reserveItinerary(xid,"John",FlightList,"Stanford",true,true);
		assert returnValue == false;
		wc.commit(xid);
		
		xid = wc.start();
		int flight = wc.queryFlight(xid,"347");
		assert flight == 100;
		int flightPrice = wc.queryFlightPrice(xid,"347");
		assert flightPrice == 310;
		flight = wc.queryFlight(xid,"3471");
		assert flight == 1001;
		flightPrice = wc.queryFlightPrice(xid,"3471");
		assert flightPrice == 3101;
		int noofrooms = wc.queryRooms(xid,"Stanford");
		assert noofrooms == 200;
		int roomsPrice = wc.queryRoomsPrice(xid,"Stanford");
		assert roomsPrice == 150;
		int noofcars = wc.queryCars(xid,"SFO");
		assert noofcars == 299;
		int carsprice = wc.queryCarsPrice(xid,"SFO");
		assert carsprice == 30;
		int customerbill = wc.queryCustomerBill(xid, "John");
		assert customerbill == 30;		
		
	}
	
	public static void Baddabtrd(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		
		int xid = rm.start();
		rm.addFlight(xid, "347", 100, 310);
		rm.addRooms(xid, "Stanford", 200, 150);
		rm.addCars(xid, "SFO", 300, 30);
		rm.newCustomer(xid, "John");
		rm.commit(xid);
		//printTables(rm);
		
		xid = rm.start();
		rm.addFlight(xid, "347", 100, 310);
		rm.addRooms(xid, "Stanford", 200, 150);
		rm.addCars(xid, "SFO", 300, 30);
		rm.abort(xid);
		//printTables(rm);
		
		xid = rm.start();
		int avail = rm.queryFlight(xid, "347");
		System.out.println(avail);
		avail = rm.queryFlightPrice(xid, "347");
		System.out.println(avail);
		avail = rm.queryRooms(xid, "Stanford");
		System.out.println(avail);
		avail = rm.queryRoomsPrice(xid, "Stanford");
		System.out.println(avail);
		avail = rm.queryCars(xid, "SFO");
		System.out.println(avail);
		avail = rm.queryCarsPrice(xid, "SFO");
		System.out.println(avail);
		avail = rm.queryCustomerBill(xid, "John");
		System.out.println(avail);
	}

	public static void checkBoolean(boolean got, Scanner scan, String globalString){
		String currentInst = scan.toString();
		StringTokenizer tk2 = new StringTokenizer(scan.nextLine());
		String token = tk2.nextToken();
		token = tk2.nextToken();
		token = tk2.nextToken();
		boolean returnVal = Boolean.valueOf(token);
		if(got != returnVal){
			System.out.println(globalString);
		}
	}

	public static void checkInt(int got, Scanner scan, String globalString){
		String currentInst = scan.toString();
		StringTokenizer tk2 = new StringTokenizer(scan.nextLine());
		String token = tk2.nextToken();
		token = tk2.nextToken();
		token = tk2.nextToken();
		int returnVal = Integer.valueOf(token);
		if(got != returnVal){
			System.out.println(globalString);
		}
	}
		

	public static void Baddcmtrd(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = rm.start();
		rm.addFlight(xid, "347", 100, 310);
		rm.addRooms(xid, "Stanford", 200, 150);
		rm.addCars(xid, "SFO", 300, 30);
		rm.newCustomer(xid, "John");
		rm.commit(xid);
		//printTables(rm);
		
		xid = rm.start();
		int avail = rm.queryFlight(xid, "347");
		System.out.println(avail);
		avail = rm.queryFlightPrice(xid, "347");
		System.out.println(avail);
		avail = rm.queryRooms(xid, "Stanford");
		System.out.println(avail);
		avail = rm.queryRoomsPrice(xid, "Stanford");
		System.out.println(avail);
		avail = rm.queryCars(xid, "SFO");
		System.out.println(avail);
		avail = rm.queryCarsPrice(xid, "SFO");
		System.out.println(avail);
		avail = rm.queryCustomerBill(xid, "John");
		System.out.println(avail);

	}

	public static void sbill(WorkflowControllerImpl wc) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = wc.start();
		boolean returnVal = wc.addFlight(xid, "347", 100, 310);
		returnVal = wc.addRooms(xid, "Stanford", 200, 150);
		returnVal = wc.addCars(xid,"SFO",300,30);
		returnVal = wc.newCustomer(xid,"John");
		wc.commit(xid);
		//printTables(wc);

		xid = wc.start();
		returnVal = wc.reserveFlight(xid,"John","347");
		returnVal = wc.reserveRoom(xid,"John","Stanford");
		returnVal = wc.reserveCar(xid,"John","SFO");
		//returnVal = rm.commit(xid);
		wc.commit(xid);
		//printTables(wc);

		xid = wc.start();
		int bill = wc.queryCustomerBill(xid,"John");
		System.out.println("Bill for john " + bill);
		wc.commit(xid);
	}


	public static void printCustomerBills(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = rm.start();
		for(String cust: customers){
			int bill = rm.queryCustomerBill(xid, cust);
			System.out.println("Bill for " + cust + " : " + bill);
		}	
			int bill = rm.queryCustomerBill(xid, "Joe");
			System.out.println("Bill for " + "Joe" + " : " + bill);
		rm.commit(xid);
	}
	public static void deleteItems(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = rm.start();
		rm.deleteFlight(xid, "4");
		//rm.deleteCustomer(xid, "Joe");
		rm.commit(xid);
	}


/*	public static void printTables(WorkflowControllerImpl wc) {
		wc.printTable(Database.FLIGHTS);
		wc.printTable(Database.CARS);
		wc.printTable(Database.HOTELS);
		wc.printTable(Database.CUSTOMERS);
		wc.printTable(Database.RESERVATIONS);
	}*/

	public static void queryCheck(ResourceManagerImpl rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException {
		int xid = rm.start();
		System.out.println(rm.queryFlight(xid, "1"));
		System.out.println(rm.queryCars(xid, "a"));
		System.out.println(rm.queryRooms(xid, "A"));
	}


	public static void populateCustomers(ResourceManager rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = rm.start();
		for(String cust: customers){
			rm.newCustomer(xid, cust);
		}
		rm.commit(xid);
	}

	public static void makeResverations(ResourceManager rm) throws RemoteException, TransactionAbortedException, InvalidTransactionException{
		int xid = rm.start();
		
		rm.reserveFlight(xid, customers[0], "4");
		rm.reserveCar(xid, customers[1], "a");
		rm.reserveRoom(xid, customers[2], "A");
		rm.reserveRoom(xid, "Joe" , "A");

		rm.commit(xid);
	}
}