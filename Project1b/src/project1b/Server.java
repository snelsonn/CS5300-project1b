package project1b;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;

public class Server extends Thread{

	private static final int portProj1bRPC = 5300;
	private static final int operationSESSIONREAD = 6;
	private static final int operationSESSIONWRITE = 7;
	private static final int operationSESSIONEXCHANGEVIEWS = 8;
	private static final int maxSizePacket = 512;
	
	@Override
	public void run(){
		DatagramSocket rpcSocket = null;
		try {
			rpcSocket = new DatagramSocket(portProj1bRPC);
		} catch (SocketException e1) {
			e1.printStackTrace();
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
					break;
				
				case operationSESSIONWRITE:
					outBuf = ResponseToSessionWrite(recvPkt.getData());
					break;
				
				case operationSESSIONEXCHANGEVIEWS:
					break;
				
				default:
					System.out.println("ERROR occurred, no operation code");
					break;
						
			}
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
		DatagramPacket sendPkt = new DatagramPacket(outBuf, i, InetAddress.getLocalHost(), portProj1bRPC);
		rpcSocket.send(sendPkt);
		byte[] inBuf = new byte[maxSizePacket];
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
			} while(!callID.equals(getCallID(recvPkt.getData())));
		} catch(SocketTimeoutException stoe) {
			// timeout 
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

		//should only need version and message, because expiration time will be reset and sessionID is already known
		String outBufferInfo = callID + "_"
							   + session.version + "_"
							   + session.message;
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
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}

		//send out DatagramPacket to each destAddress or server with sessionID
		//fix destination id: not InetAddress.getLocalHost()
		//but need to look up actual server location from session table which uses sessionID as key
		DatagramPacket sendPkt = new DatagramPacket(outBuf, i, InetAddress.getLocalHost(), portProj1bRPC);
		rpcSocket.send(sendPkt);
		byte[] inBuf = new byte[maxSizePacket];
		DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
			} while(!callID.equals(getCallID(recvPkt.getData())));
		} catch(SocketTimeoutException stoe) {
			// timeout 
			recvPkt = null;
		} catch(IOException ioe) {
			// other error 
		}
		rpcSocket.close();
		return recvPkt;
	}
	
	//send data back in response to sessionWrite
	//callID + Acknowledged
	public byte[] ResponseToSessionWrite(byte[] requestData) {
		//make sure session constructor works with requestData and delimiter "_"
		Session session = new Session(requestData);
		
		//write session to local sessionTable
		Project1a.sessionTable.put(session.sessionID, session);
		
		//return datagramPacket acknowledging the write
		String callID = getCallID(requestData);
		byte[] outBuf = new byte[maxSizePacket];
		//fill outBuf with [ callID, "acknowledged" ]
		String outBufferInfo = callID + "acknowledged";
		int i = 0;
		for(byte c : outBufferInfo.getBytes(Charset.forName("UTF-8"))){
			outBuf[i] = c;
			i++;
		}
		
		return outBuf;
	}

	public void ExchangeViews(HashMap<String, String> v) {

	}

	public static String makeUniqueId() {
		return new BigInteger(130, new SecureRandom()).toString(32);
	}
	
	public static String getCallID(byte[] requestData){
		return new String(requestData).trim().substring(0, 32);
	}
	
	public static int getOperationCode(byte[] requestData){
		String request = new String(requestData).trim();
		return Integer.parseInt(request.split("_")[1]);
	}
	
	public static String getSessionID(byte[] requestData){
		String request = new String(requestData).trim();
		return request.split("_")[2];
	}
}
