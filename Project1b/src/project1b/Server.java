package project1b;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import project1b.Project1a.GarbageCollector;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simpledb.*;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataResult;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import java.util.List;


public class Server extends Thread {

	private static final int portProj1bRPC = 5300;
	private static final int operationSESSIONREAD = 192384;
	private static final int operationSESSIONWRITE = 192385;
	private static final int operationSESSIONEXCHANGEVIEWS = 8;
	private static final int maxSizePacket = 512;
	public static final ConcurrentHashMap<String, View> viewTable = new ConcurrentHashMap<String, View>();
	private static final Random r = new Random();
	private static ViewThread viewThread;
	private static AmazonSimpleDB simpleDB;
	private static String awsAccessId = "AKIAIH4YZPHV7S45LIEQ";
	private static String awsSecretKey = "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";
	private static String ownAddr;
	
	public Server(){
		viewThread = new ViewThread();
		viewThread.start();
	}
	
	@Override
	public void run(){
		DatagramSocket rpcSocket = null;
		try {
			rpcSocket = new DatagramSocket(portProj1bRPC);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		// Create new simpleDB Object
		BasicAWSCredentials basicAWSCredentials = new BasicAWSCredentials(awsAccessId, awsSecretKey);
		simpleDB = new AmazonSimpleDBClient(basicAWSCredentials);
		Region usWest2 = Region.getRegion(Regions.US_WEST_2);
		simpleDB.setRegion(usWest2);
		
		Process p;
		try {
			p = Runtime.getRuntime().exec("/opt/aws/bin/ec2-metadata --public-ipv4");
			int returnCode = p.waitFor();
			if (returnCode == 0) {
				BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String ipStr = r.readLine();
				ownAddr = ipStr.split(":")[1].trim();
			}
		} catch (IOException | InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			String myDomain = "simpleDB";
			simpleDB.createDomain(new CreateDomainRequest(myDomain));
			View simpleDBView = new View("simpleDB");
			viewTable.put("simpleDB", simpleDBView);
		} catch (AmazonServiceException ase) {
			System.out.println("Service Exception");
		} catch (AmazonClientException ace) {
			System.out.println("Client Exception");
		}
		
		while(true) {
			byte[] inBuf = new byte[maxSizePacket];
			DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			try {
				rpcSocket.receive(recvPkt);
			} catch (IOException e) {
				e.printStackTrace();
			}
			InetAddress returnAddr = recvPkt.getAddress();
			
			// update own svrId in viewTable since svr is currently running
			// need to change to not local host later
			if (viewTable.containsKey(ownAddr)) {
				View curr_view = viewTable.get(ownAddr);
				curr_view.updateUPTime();
				viewTable.replace(ownAddr,curr_view);
			} else { // not in own viewTable, create new View object 
				viewTable.put(ownAddr, new View(ownAddr));
			}

			// update return svrId in viewTable since we are interacting with it
			// convert InetAddress to String for viewTable
			String returnAddrStr = returnAddr.toString();
			
			if (viewTable.containsKey(returnAddrStr)) {
				View curr_addr = viewTable.get(returnAddrStr);
				curr_addr.updateUPTime();
				viewTable.replace(returnAddrStr,curr_addr);
			} else {
				viewTable.put(returnAddrStr, new View(ownAddr));
			}

			
			int returnPort = recvPkt.getPort();
			int operationCode = getOperationCode(recvPkt.getData()); // get requested operationCode
			// here inBuf contains the callID and operationCode
			byte[] outBuf = null;
			switch( operationCode ) {
				//1. compare to see if you contain information about current session from cookie, so either primary or backup server
				//if you are either primary or backup server, get information from your local memory
				//update that information in local memory (update version number, expiration time), then send new cookie with updated version
				//if not either primary or backup server,
				//do a SessionRead to the given primary and/or backup server,
				//then you add this information to your local memory, and update necessary information, 
				//do SessionWrite to a a new backup server,
				//then send new cookie with updated version and primary server and backup server
			
			
				case operationSESSIONREAD:
					// SessionRead accepts call args and returns call results 
					outBuf = ResponseToSessionRead(recvPkt.getData());
					sendDatagramPacket(rpcSocket, outBuf, returnAddr, returnPort);
					break;
				
				case operationSESSIONWRITE:
					outBuf = ResponseToSessionWrite(recvPkt.getData());
					sendDatagramPacket(rpcSocket, outBuf, returnAddr, returnPort);
					break;
				
				case operationSESSIONEXCHANGEVIEWS:
					ResponseToExchangeView(recvPkt.getData(), rpcSocket, returnAddr, returnPort);
					break;
				
				default:
					System.out.println("ERROR occurred, no operation code");
					break;
						
			}

		}
	}
	
	public static void sendDatagramPacket(DatagramSocket rpcSocket, byte[] outBuf, InetAddress returnAddr, int returnPort) {
		// here outBuf should contain the callID and results of the call
		DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
				returnAddr, returnPort);
		try {
			rpcSocket.send(sendPkt);
		} catch(IOException ioe) {
			ioe.printStackTrace();
			rpcSocket.close();
		}
	}

	// can only get this from cookie
	public DatagramPacket SessionRead(String sessionID) throws IOException{
		//should return <found_version, data>
		//generate unique id for call
		DatagramSocket rpcSocket = new DatagramSocket();
		byte[] outBuf = new byte[maxSizePacket];

		// fill outBuf with [ callID, operationSESSIONREAD, sessionID ]
		String callID = makeUniqueId();
		String outBufferInfo = callID + "_"
							   + operationSESSIONREAD + "_"
							   + sessionID;
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}

		//send out DatagramPacket to each destAddress or server with sessionID
		//fix destination id: not InetAddress.getLocalHost()
		//but need to look up actual server location from session table which uses sessionID as key
		InetAddress[] destAddress = getDestinationAddr();
		DatagramPacket sendPkt = new DatagramPacket(outBuf, i, destAddress[0], portProj1bRPC);
		rpcSocket.send(sendPkt);
		byte[] inBuf = new byte[maxSizePacket];
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				System.out.println("sessionread finish");
			} while(!callID.equals(getCallID(recvPkt.getData())));
		} catch(SocketTimeoutException stoe) {
			// timeout
			viewTable.replace(destAddress.toString(), viewTable.get(destAddress.toString()).updateDOWNTime());
			recvPkt = null;
		} catch(IOException ioe) {
			// other error 
		}
		rpcSocket.close();
		return recvPkt;
	}
	
	//send data back in response to sessionRead
	//callID + session.version + session.message
	public byte[] ResponseToSessionRead(byte[] requestData){
		String callID = getCallID(requestData);
		String sessionID = getSessionID(requestData);
		
		Session session = Project1a.sessionTable.get(sessionID);
		
		String outBufferInfo;
		if(session == null){
			outBufferInfo = callID + "_"
					        + "not found";
		} else{
			//should only need version and message, because expiration time will be reset and sessionID is already known
			outBufferInfo = callID + "_"
								   + "found" + "_"
								   + session.version + "_"
								   + session.message;
		}
		byte[] outBuf = new byte[maxSizePacket];
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}

		return outBuf;
	}

	public DatagramPacket SessionWrite(String sessionID, int version, int expirationTime, String data) throws IOException {
		//should return <found_version, data>
		//generate unique id for call
		DatagramSocket rpcSocket = new DatagramSocket();
		byte[] outBuf = new byte[maxSizePacket];

		//fill outBuf with [ callID, operationSESSIONWRITE, sessionID, version, expirationTime, data ]
		String callID = makeUniqueId();
		String outBufferInfo = callID + "_"
							   + operationSESSIONWRITE + "_"
							   + sessionID + "_" 
							   + version + "_" 
							   + expirationTime + "_"
							   + data;
		System.out.println("sessioWrite outBufferInfo: " + outBufferInfo);
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}

		//send out DatagramPacket to each destAddress or server with sessionID
		//fix destination id: not InetAddress.getLocalHost()
		//but need to look up actual server location from session table which uses sessionID as key
		InetAddress[] destAddress = getDestinationAddr();
		DatagramPacket sendPkt = new DatagramPacket(outBuf, i, destAddress[0], portProj1bRPC);
		rpcSocket.send(sendPkt);
		byte[] inBuf = new byte[maxSizePacket];
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				System.out.println("got packet back");
			} while(!callID.equals(getCallID(recvPkt.getData())));
		} catch(SocketTimeoutException stoe) {
			// timeout 
			// update view that unsuccessful write (change to DOWN)
			viewTable.replace(destAddress.toString(), viewTable.get(destAddress.toString()).updateDOWNTime());
			recvPkt = null;
		} catch(IOException ioe) {
			// Are we supposed to be resending it??????
			System.out.println("IOException, trying to resend once more");
			try {
				do {
					recvPkt.setLength(inBuf.length);
					rpcSocket.receive(recvPkt);
				} while(!callID.equals(getCallID(recvPkt.getData())));
			} catch(SocketTimeoutException stoe) {
				// timeout 
				viewTable.replace(destAddress.toString(), viewTable.get(destAddress.toString()).updateDOWNTime());
				recvPkt = null;
			}
		}
		rpcSocket.close();
		return recvPkt;
	}
	
	//send data back in response to sessionWrite
	//callID + Acknowledged
	public byte[] ResponseToSessionWrite(byte[] requestData) {
		//make sure session constructor works with requestData and delimiter "_"
		Session session = new Session(requestData);
		
		System.out.println("In ResponseToSessionWrite");
		
		//write session to local sessionTable
		Project1a.sessionTable.put(session.sessionID, session);
		
		//return datagramPacket acknowledging the write
		String callID = getCallID(requestData);
		byte[] outBuf = new byte[maxSizePacket];
		//fill outBuf with [ callID, "acknowledged" ]
		String outBufferInfo = callID;
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}
		
		return outBuf;
	}
	
	
	public void ResponseToExchangeView(byte[] requestData, DatagramSocket rpcSocket, InetAddress returnAddr, int returnPort) {
		
		// send response acknowledging that request was received, 
		// along with telling how many packets will be sent to represent the 
		// view table
		byte[] outBuf = new byte[maxSizePacket];
		String callID = getCallID(requestData);
		String outBufferInfo = callID + "_" + getNumViewPackets();
		
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}
		
		sendDatagramPacket(rpcSocket, outBuf, returnAddr, returnPort);

		// get the number of packets we should be receiving
		String request = new String(requestData).trim();
		int viewTableLen = Integer.parseInt(request.split("_")[2]);
		
		// start receiving all packets
		receiveViewPackets(callID, returnAddr.toString(), viewTableLen, rpcSocket);
		
		// lock view table, make copy of table, unlock view table
		// using copy, send representation of table
		sendViewPackets(callID, rpcSocket, returnAddr, returnPort);
		
	}
	
	
	public static void receiveViewPackets(String callID, String returnAddr, int viewTableLen, DatagramSocket rpcSocket) {
		// start receiving packets
		byte[] inBuf = new byte[maxSizePacket];
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		
		for (; viewTableLen >= 0; viewTableLen--) {
			try {
				do {
					rpcSocket.receive(recvPkt);
					mergeWithTable(recvPkt.getData());
				} while (!callID.equals(getCallID(recvPkt.getData())));
			} catch(SocketTimeoutException stoe) {
				// timeout
				viewTable.replace(returnAddr, viewTable.get(returnAddr).updateDOWNTime());
				recvPkt = null;
			} catch(IOException ioe) {
				System.out.println("IOException!!");
			}
		}
	}
	
	
	public static void mergeWithTable(byte[] otherViewBytes) {
		// viewPackets are of form callID_serverID_status_time_serverID2_status2_time2_serverID3_status3_time3
		String[] viewArray = otherViewBytes.toString().split("_");
		View otherView = null;
		for (int i = 1; i < viewArray.length - 1; i += 3) {
			otherView = new View(viewArray[i], Integer.parseInt(viewArray[i+1]), Long.parseLong(viewArray[i+2]));
			
			if (!viewTable.contains(otherView.svrId)) {
				viewTable.put(otherView.svrId, otherView); 
			} else if (viewTable.get(viewArray[i]).getTime().compareTo(otherView.getTime()) < 0) {
				viewTable.replace(otherView.svrId, otherView);
			}
		}
	}

	
	public static void ExchangeViews() throws IOException {
		DatagramSocket rpcSocket = new DatagramSocket();
		byte[] outBuf = new byte[maxSizePacket];

		// fill outBuf with [ callID, operationSESSIONEXCHANGEVIEWS, data (len of viewTable) ]
		// data = length of viewTable / how many packets will be sending
		String callID = makeUniqueId();
		String outBufferInfo = callID + "_"
							   + operationSESSIONEXCHANGEVIEWS + "_"
							   + getNumViewPackets();
		
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}

		// randomly pick a server from viewTable
		int j = r.nextInt(viewTable.size());
		String destAddressStr =  (String) viewTable.entrySet().toArray()[j];
		
		if (destAddressStr.equals("simpleDB")) {
			//DomainMetadataRequest dmreq = new DomainMetadataRequest().withDomainName("simpleDB");
			//DomainMetadataResult meta = simpleDB.domainMetadata(dmreq);
			GetAttributesRequest gar = new GetAttributesRequest();
			gar.setDomainName("simpleDB");
			List<Attribute> simpleDBAttrs = simpleDB.getAttributes(gar).getAttributes();
			View otherView;
			String[] simpleDBVal;
			
			for (Attribute attr : simpleDBAttrs) {
				simpleDBVal = attr.getValue().split("_");
				otherView = new View(simpleDBVal[0], Integer.parseInt(simpleDBVal[1]), Long.parseLong(simpleDBVal[2]));
				
				if (!viewTable.contains(otherView.svrId)) {
					viewTable.put(otherView.svrId, otherView); 
				} else if (viewTable.get(simpleDBVal[0]).getTime().compareTo(otherView.getTime()) < 0) {
					viewTable.replace(otherView.svrId, otherView);
				}
			}
			
			sendViewPacketsSimpleDB();
			
		} else {
		
			InetAddress destAddress = InetAddress.getByName(destAddressStr);
			
			// formulate packet to send that we want to exchange views
			DatagramPacket sendPkt = new DatagramPacket(outBuf, i, destAddress, portProj1bRPC);
			rpcSocket.send(sendPkt);
			
			// set up receive packet 
			byte[] inBuf = new byte[maxSizePacket];
			DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			int viewTableLen = 0;
		
			// get packet saying how long dest server's viewTable is		
			try {
				do {
					recvPkt.setLength(inBuf.length);
					rpcSocket.receive(recvPkt);
					System.out.println("got packet length back");
						// get the number of packets we should be receiving
						String request = new String(recvPkt.getData()).trim();
						viewTableLen = Integer.parseInt(request.split("_")[1]);
					
				} while(!callID.equals(getCallID(recvPkt.getData())));
			} catch(SocketTimeoutException stoe) {
				// timeout 
				viewTable.replace(destAddress.toString(), viewTable.get(destAddress.toString()).updateDOWNTime());
				recvPkt = null;
			} catch(IOException ioe) {
				System.out.println("IOException, trying to resend once more");
			}
					
			// if packet says how long dest viewTable is
			// that means dest server is responding
			// so first send all your packets 
			// and then keep receiving packets 
			// and merge with curr viewTable
			if (recvPkt != null) {
				sendViewPackets(callID, rpcSocket, destAddress, portProj1bRPC);
				
				// start receiving packets
				receiveViewPackets(callID, destAddress.toString(), viewTableLen, rpcSocket);
			}
		}
		
		rpcSocket.close();
	}
	
	public static void sendViewPackets(String callID, DatagramSocket rpcSocket, InetAddress returnAddr, int returnPort) {
		byte[] outBufSend = new byte[maxSizePacket];
		String outBuf = callID;
		Set<Entry<String, View>> viewTableKeys = viewTable.entrySet();
		int count = 1;
		int numViewsInPacket = (int) Math.floor( ( maxSizePacket - 32 ) / View.getBytes() );

		for (Entry<String, View> entry : viewTableKeys) {

			outBuf += entry.getValue().toString();
			
			if (count % numViewsInPacket == 0 || count == viewTable.size()) {
				int i = 0;
				for(byte c : outBuf.getBytes(Charset.forName("UTF-8"))){
					outBufSend[i] = c;
					i++;
				}
				sendDatagramPacket(rpcSocket, outBuf.getBytes(Charset.forName("UTF-8")), returnAddr, returnPort);
				outBufSend = new byte[maxSizePacket];
				outBuf = callID;
			}
			
			count++;
			
		}
	}
	
	public static void sendViewPacketsSimpleDB() {
		Set<Entry<String, View>> viewTableKeys = viewTable.entrySet();
		List<ReplaceableAttribute> sendInfo = new ArrayList<ReplaceableAttribute>();
		ReplaceableAttribute a;

		for (Entry<String, View> entry : viewTableKeys) {
			a = new ReplaceableAttribute(entry.getKey(), entry.getValue().toString(), true);
			sendInfo.add(a);
		}
		
		PutAttributesRequest par = new PutAttributesRequest();
		par.setDomainName("simpleDB");
		par.setAttributes(sendInfo);
		simpleDB.putAttributes(par);
	}

	public static String makeUniqueId() {
		return new BigInteger(130, new SecureRandom()).toString(32);
	}
	
	public static String getCallID(byte[] requestData){
		String request = new String(requestData).trim();
		return request.split("_")[0];
	}
	
	public static int getOperationCode(byte[] requestData){
		String request = new String(requestData).trim();
		return Integer.parseInt(request.split("_")[1]);
	}
	
	public static String getSessionID(byte[] requestData){
		String request = new String(requestData).trim();
		return request.split("_")[2];
	}
		
	public static int getNumViewPackets() {
		// each packet has callID 32 + 1 + viewData 43 = 76 total bytes
		int numViewsInPacket = (int) Math.floor( ( maxSizePacket - 32 ) / View.getBytes() );
		return (int) Math.ceil(viewTable.size() / numViewsInPacket);
	}
	
	public static InetAddress[] getDestinationAddr() {
		String cookieMetadata = Project1a.repeatVisitor.getValue();
		InetAddress primaryID;
		InetAddress backupID;
		try {
			primaryID = InetAddress.getByName(cookieMetadata.split("_")[3]);
			backupID = InetAddress.getByName(cookieMetadata.split("_")[4]);
			InetAddress[] destAddr = {primaryID, backupID};
			return destAddr;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
		 
	}
}
