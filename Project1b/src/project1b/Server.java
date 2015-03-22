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

public class Server {

	private static final int portProj1bRPC = 5300;
	private static final int operationSESSIONREAD = 6;
	private static final int operationSESSIONWRITE = 7;
	private static final int operationSESSIONEXCHANGEVIEWS = 8;
	private static final int maxSizePacket = 512;


	public static void main(String[] args){
		DatagramSocket rpcSocket = new DatagramSocket(portProj1bRPC);
		while(true) {
			byte[] inBuf = new byte[maxSizePacket];
			DatagramPacket recvPkt = new DatagramPacket(inBuf, inBuf.length);
			rpcSocket.receive(recvPkt);
			InetAddress returnAddr = recvPkt.getAddress();
			int returnPort = recvPkt.getPort();
			if()
			// here inBuf contains the callID and operationCode
			int operationCode = recvPkt.getData()[32]; // get requested operationCode
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
					outBuf = SessionRead();
					break;
				
				case operationSESSIONWRITE:
					outBuf = ResponseToSessionWrite(recvPkt.getdata(), recvPkt.getLength());
					break;
				
				case operationSESSIONEXCHANGEVIEWS:
			}
			// here outBuf should contain the callID and results of the call
			DatagramPacket sendPkt = new DatagramPacket(outBuf, outBuf.length,
					returnAddr, returnPort);
			rpcSocket.send(sendPkt);
		}
	}

	// can only get this from cookie
	public DatagramPacket SessionRead(String sessionID) throws IOException{
		//should return <found_version, data>
		//generate unique id for call
		DatagramSocket rpcSocket = new DatagramSocket();
		byte[] outBuf = new byte[maxSizePacket];

		//		fill outBuf with [ callID, operationSESSIONREAD, sessionID ]
		String callID = makeUniqueId();
		String outBufferInfo = callID + operationSESSIONREAD + sessionID;
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
		byte[] recvPktCallID = new byte[callID.length()];
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				byte[] recvPktData = recvPkt.getData();
				for(int callIDIndex = 0; callIDIndex < callID.length(); callIDIndex++){
					recvPktCallID[callIDIndex] = recvPktData[callIDIndex];
				}
			} while(Arrays.equals(callID.getBytes(), recvPktCallID));
		} catch(SocketTimeoutException stoe) {
			// timeout 
			recvPkt = null;
		} catch(IOException ioe) {
			// other error 
		}
		rpcSocket.close();
		return recvPkt;
	}

	public DatagramPacket SessionWrite(String sessionID, int version, int expirationTime, String data) throws IOException {
		//should return <found_version, data>
		//generate unique id for call
		DatagramSocket rpcSocket = new DatagramSocket();
		byte[] outBuf = new byte[maxSizePacket];

		//fill outBuf with [ callID, operationSESSIONWRITE, sessionID ]
		String callID = makeUniqueId();
		String outBufferInfo = callID + operationSESSIONWRITE + sessionID + version + expirationTime + data;
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
		byte[] recvPktCallID = new byte[callID.length()];
		try {
			do {
				recvPkt.setLength(inBuf.length);
				rpcSocket.receive(recvPkt);
				byte[] recvPktData = recvPkt.getData();
				for(int callIDIndex = 0; callIDIndex < callID.length(); callIDIndex++){
					recvPktCallID[callIDIndex] = recvPktData[callIDIndex];
				}
			} while(Arrays.equals(callID.getBytes(), recvPktCallID));
		} catch(SocketTimeoutException stoe) {
			// timeout 
			recvPkt = null;
		} catch(IOException ioe) {
			// other error 
		}
		rpcSocket.close();
		return recvPkt;
	}

	public void ExchangeViews(HashMap<String, String> v) {

	}

	public static String makeUniqueId() {
		return new BigInteger(130, new SecureRandom()).toString(32);
	}
}
