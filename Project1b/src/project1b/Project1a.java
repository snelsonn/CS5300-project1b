package project1b;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/")
public class Project1a extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Server server;
	public static PrintWriter out;

	public Project1a(){
		// Start new server instance
		server = new Server();
		server.start();

		(new GarbageCollector()).start();

		// Get own serverID
		servletName = server.getOwnServerID();
	}

	public String servletName;
	public String backupServlet;
	public static final ConcurrentHashMap<String, Session> sessionTable = new ConcurrentHashMap<String, Session>();
	public static int sessNumber = 0;
	private static final String cookieName = "CS5300PROJ1SESSION";
	private static final String defaultMessage = "Hello User";
	private static final int defaultExpirationTime = 60 * 1; //1 min
	private static final int defaultVersionNumber = 1;
	public static Cookie repeatVisitor;

	@Override
	public void doGet(HttpServletRequest request,
			HttpServletResponse response)
					throws ServletException, IOException {

		out = response.getWriter();
		
		out.println("Views<br/>");
		for(Entry<String,View> s : Server.viewTable.entrySet()) {
			out.println("Server: " + s.getKey() + ", value: " + s.getValue() + "<br/>");
		}

		// Find 5300 cookie if it exists
		Cookie[] cookies = request.getCookies();
		repeatVisitor = null;
		if(cookies != null){
			for(Cookie c : cookies){
				if(c.getName().equals(cookieName)){
					repeatVisitor = c;
					break;
				}
			}
		}

		String message = defaultMessage;
		String serverFound = "not found";
		String serverFoundOn = "";
		String serverPrimaryExpirationTime = "N/A";
		String serverPrimaryDiscardTime = "N/A";
		String serverBackupExpirationTime = "N/A";
		String serverBackupDiscardTime = "N/A";
		
		backupServlet = "null";

		// 5300 cookie not found so must be new user or logged out
		// Create new session and write sessionID to table
		if(request.getParameter("Logout") != null || repeatVisitor == null){

			// First write session to own table
			String sessionID = makeSessionId();
			Session newSession =  new Session(sessionID,
					defaultVersionNumber,
					defaultMessage,
					(int)(System.currentTimeMillis()/1000) + defaultExpirationTime);
			addToConcurrentHashMap(sessionTable, newSession);
			System.out.println("new user");

			// Since this is a new session, need to send to a backup server as well
			// Pick this server at random from viewTable
			// Set up datagramPacket for backup server's write
			DatagramPacket writeResponse = null;
			String destAddressStr = "null";

			// attempt to find a backup server, if possible. If not, set as "null"
			List<String> servers = new ArrayList<String>(Arrays.asList(Server.viewTable.keySet().toArray(new String[0])));
			servers.removeAll(Arrays.asList("simpleDB", servletName));
			for(int i = 0; i < servers.size(); i++){
				int j = new Random().nextInt(servers.size());
				destAddressStr = servers.get(j);
				writeResponse = server.SessionWrite(newSession.sessionID, InetAddress.getByName(destAddressStr), newSession.version, newSession.expirationTimeStamp, newSession.message);
				if(writeResponse != null){
					break;
				} else{
					destAddressStr = "null";				
				}
			}

			out.println("After new user: " + destAddressStr + " ");
			
			backupServlet = destAddressStr;

			// Set expiration times for both primary and backup servers
			serverPrimaryExpirationTime = "" + defaultExpirationTime;
			serverPrimaryDiscardTime = "" + newSession.expirationTimeStamp;
			if(!backupServlet.equals("null")){
				serverBackupExpirationTime = "" + newSession.expirationTimeStamp;
				serverBackupDiscardTime = "" + newSession.expirationTimeStamp;
			}

			// Create cookie to add to response so know this is a returning user next time
			String cookieValue = sessionID + "%" + defaultVersionNumber + "%" + servletName + "%" + backupServlet;
			Cookie newVisitor = new Cookie(cookieName, cookieValue);

			newVisitor.setMaxAge(defaultExpirationTime); //1 min
			response.addCookie(newVisitor);
		}

		// If cookie exists, then this is a returning user
		else {

			// Get the current session message from the cookie
			String[] sessionMessage = repeatVisitor.getValue().split("%");

			// Check if there is a new message in the textbox
			String newMessage = request.getParameter("newMessage");

			// Replace all unsafe characters in message
			if (newMessage != null){
				newMessage = newMessage.replaceAll("[^A-Za-z0-9.-_ ?]", "");
			}

			Session m = null;

			// Check if the current server is primary server specified in cookie
			if (servletName.equals(sessionMessage[2])){ 
				
				// Get session information from sessionTable using sessionID
				m = sessionTable.get(sessionMessage[0]);
				serverFound = "Primary";
				serverFoundOn = sessionMessage[2];
				
				// If sessionID not found in the sessionTable, indicate this session timed out / no longer exists
				if (m == null){
					serverFound = "timedout";
				} else {
					// If it was found, now need to get backup server to make sure
					// that the data corresponds (ie. same version number)
					if (!sessionMessage[3].equals("null")){
						
						// Create datagram to read data from backup server
						DatagramPacket readResponseBackup = server.SessionRead(sessionMessage[0], InetAddress.getByName(sessionMessage[3]));
						
						// If successful read from backup server
						if (readResponseBackup != null){
							String[] readResponseData = new String(readResponseBackup.getData()).trim().split("_");
							
							if (readResponseData[1].equals("found")){
								serverBackupDiscardTime = readResponseData[4];
								serverBackupExpirationTime = "" + (Integer.parseInt(readResponseData[4]) - (int)(System.currentTimeMillis()/1000));
							}
						} // Otherwise, server knows it timed out, but already handled that in viewTable in Server.java
						out.println("After local = primary: " + sessionMessage[3]);
					}
					serverPrimaryDiscardTime = "" + m.expirationTimeStamp;
					serverPrimaryExpirationTime = "" + (m.expirationTimeStamp - (int)(System.currentTimeMillis()/1000));
				}
			} 
			
			// Otherwise, if the server is the backup server
			else if (servletName.equals(sessionMessage[3])){
				m = sessionTable.get(sessionMessage[0]);
				serverFound = "Backup";
				serverFoundOn = sessionMessage[3];
				
				if(m == null){
					// Session timed out
					serverFound = "timedout";
				} else {
					// Read data from primary server
					DatagramPacket readResponse = server.SessionRead(sessionMessage[0], InetAddress.getByName(sessionMessage[2]));
					
					// If read was successful, then process the data
					if (readResponse != null){
						String[] readResponseData = new String(readResponse.getData()).trim().split("_");

						if (readResponseData[1].equals("found")){
							serverPrimaryDiscardTime = readResponseData[4];
							serverPrimaryExpirationTime = "" + (Integer.parseInt(readResponseData[4]) - (int)(System.currentTimeMillis()/1000));
						} 
						
						out.println("After local = backup: " + sessionMessage[2]);
					} 
					serverBackupDiscardTime = "" + m.expirationTimeStamp;
					serverBackupExpirationTime = "" + (m.expirationTimeStamp - (int)(System.currentTimeMillis()/1000));
				}
			} 
			
			// Finally, if the server is neither the primary nor backup,
			// need to perform a read of the data from either primary or backup
			// then write to local table and write to a new backup
			else {
				boolean readBackup = true;
				
				// Read the session data from primary server
				DatagramPacket readResponse = server.SessionRead(sessionMessage[0], InetAddress.getByName(sessionMessage[2]));
				out.print("read response: " + readResponse.getLength());
				
				// If successfully read from the primary server
				if (readResponse != null){
					String[] readResponseData = new String(readResponse.getData()).trim().split("_");
					
					// Not found means session timed out
					if (readResponseData[1].equals("found")){
						serverPrimaryDiscardTime = readResponseData[4];
						serverPrimaryExpirationTime = "" + (Integer.parseInt(readResponseData[4]) - (int)(System.currentTimeMillis()/1000));
						
						// Check that the version numbers are the same
						if (readResponseData[2].equals(sessionMessage[1])){
							message = readResponseData[3];
							readBackup = false;
							serverFound = "Primary";
							serverFoundOn = sessionMessage[2];
						}
					} else {
						serverFound = "timedout";
					}
				} else {
					serverFound = "failed";
				}
				
				out.print("After neither primary nor backup: " + sessionMessage[2]);
				
				// If a backup server was indicated in the cookie
				if (!sessionMessage[3].equals("null")) {
					
					// Read the session data from backup
					DatagramPacket readResponseBackup = server.SessionRead(sessionMessage[0], InetAddress.getByName(sessionMessage[3]));
					
					// If the read was successful
					if(readResponseBackup != null){
						String[] readResponseData = new String(readResponseBackup.getData()).trim().split("_");
						
						if(readResponseData[1].equals("found")){
							serverBackupDiscardTime = readResponseData[4];
							serverBackupExpirationTime = "" + (Integer.parseInt(readResponseData[4]) - (int)(System.currentTimeMillis()/1000));
							
							// Check that the version numbers are the same, if not session error
							if(readBackup && readResponseData[2].equals(Integer.parseInt(sessionMessage[1]))){
								message = readResponseData[3];
								serverFound = "Backup";
								serverFoundOn = sessionMessage[3];
							}
						} else{
							serverFound = "timedout";
						}
					} else {
						serverFound = "failed";
					} 
					
					out.print("After neither primary nor backup 2: " + sessionMessage[3]);
				} 
			}


			// Determine if message exists and is below 512 char
			if (newMessage != null && !newMessage.equals("") && newMessage.length() <= 512) {
				message = newMessage;
			} else if (m != null) {
				message = m.message;
			} 

			// Create new session with same sessionID, but new version and (potentially) message
			Session newSession =  new Session(sessionMessage[0],
					Integer.parseInt(sessionMessage[1]) + 1,
					message,
					(int)(System.currentTimeMillis()/1000) + defaultExpirationTime);
			addToConcurrentHashMap(sessionTable, newSession);
			
			// Since there is no backup server right now, need to pick some random serverID from viewTable
			// and write to its sessionTable
			DatagramPacket writeResponse = null;
			String destAddressStr = "null";
			
			List<String> servers = new ArrayList<String>(Arrays.asList(Server.viewTable.keySet().toArray(new String[0])));
			servers.removeAll(Arrays.asList("simpleDB", servletName));
			for(int i = 0; i < servers.size(); i++){
				int j = new Random().nextInt(servers.size());
				destAddressStr = servers.get(j);
				writeResponse = server.SessionWrite(newSession.sessionID, InetAddress.getByName(destAddressStr), newSession.version, newSession.expirationTimeStamp, newSession.message);
				if(writeResponse != null){
					break;
				} else{
					destAddressStr = "null";				
				}
			}
			out.println("After changing version: " + destAddressStr + " ");
			
			String backupServlet = destAddressStr;

			// Cookie value is sessionID_version number_serverNamePrimary_serverNameBackup
			String cookieValue = sessionMessage[0] + "%" + (Integer.parseInt(sessionMessage[1]) + 1) + "%" + servletName + "%" + backupServlet;
			Cookie newCookieForRepeatVisitor = new Cookie(cookieName, cookieValue);
			newCookieForRepeatVisitor.setMaxAge(defaultExpirationTime); //1 min
			response.addCookie(newCookieForRepeatVisitor);
		}

		response.setContentType("text/html");

		if(serverFound.equals("failed")){
			Cookie killMe = new Cookie(cookieName, null);
			killMe.setMaxAge(0);
			response.addCookie(killMe);
			out.println
			("<!DOCTYPE html>\n" +
					"<html>\n" +
					"<head><title>CS 5300: Project 1a</title></head>\n" +
					"<body bgcolor=\"#fdf5e6\">\n" +
					"<h1>Error Both Primary " + serverFoundOn + " and Backup Server Failed " + backupServlet + " </h1>\n");

		} else if(serverFound.equals("timedout")){
			Cookie killMe = new Cookie(cookieName, null);
			killMe.setMaxAge(0);
			response.addCookie(killMe);
			out.println
			("<!DOCTYPE html>\n" +
					"<html>\n" +
					"<head><title>CS 5300: Project 1a</title></head>\n" +
					"<body bgcolor=\"#fdf5e6\">\n" +
					"<h1>Session Timedout</h1>\n");
		} else {
			out.println
			("<!DOCTYPE html>\n" +
					"<html>\n" +
					"<head><title>CS 5300: Project 1a</title></head>\n" +
					"<body bgcolor=\"#fdf5e6\">\n" +
					"<h1>Message: " + message + "</h1>\n" +
					"<h2>Expiration Time (in secs): " + defaultExpirationTime + "<h2>" +
					"<h2>This Server ID: " + servletName + "</h2>" + 
					"<p>" +
					"<form action='/' method='POST'>" +
					"<input type='submit' value='Replace'><input type='text' name='newMessage'/><br/><br/>" +
					"<input type='submit' value='Refresh'><br/><br/>" +
					"<input type='submit' value='Logout' name='Logout'>" +
					"</form>" +
					"</p>\n");

			out.println(
					"<h2>Session Data found on " + serverFoundOn + ", which was a " + serverFound + "</h2>" + 
							"<h2>Primary Server [" + serverFoundOn +"] Expiration Time: " + serverPrimaryExpirationTime + "</h2>" +  
							"<h2>Primary Server Discard Time: " + serverPrimaryDiscardTime + "</h2>" +  
							"<h2>Backup Server [" + backupServlet +"]  Expiration Time: " + serverBackupExpirationTime + "</h2>" +  
							"<h2>Backup Server Discard Time: " + serverBackupDiscardTime + "</h2>\n"					       	  
					);

		}
		for(Entry<String,View> s : Server.viewTable.entrySet()) {
			out.println(s.getKey() + " " + s.getValue() + "<br/>");
		}
		out.println(
				"</body>" +
				"</html>");

	}

	@Override
	public void doPost(HttpServletRequest request,
			HttpServletResponse response)
					throws ServletException, IOException {
		doGet(request, response);
	}

	static class GarbageCollector extends Thread{

		@Override
		//check all sessions in sessionTable, if they are passed the default ExpirationTime, remove that kv-pair
		public void run(){
			while(true){
				try {
					sleep((long) (defaultExpirationTime * 1.5 * 1000));
					for(Entry<String, Session> session : sessionTable.entrySet()){
						if(session.getValue().expirationTimeStamp < (int)(System.currentTimeMillis()/1000)){
							sessionTable.remove(session.getKey());
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}  
	}

	public String makeSessionId() {
		sessNumber++;
		return sessNumber + "-" + servletName;
	}

	public static void addToConcurrentHashMap(ConcurrentHashMap<String, Session> sessionTable, Session message){
		sessionTable.put(message.sessionID, message);
	}




}


