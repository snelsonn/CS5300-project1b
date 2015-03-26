package project1b;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/project1a")
public class Project1a extends HttpServlet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Server server;

	public Project1a(){
		server = new Server();
		server.start();
		
		(new GarbageCollector()).start();
		
		try {
			// change later to not local address
			servletName = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}
	
	public String servletName;
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
	  boolean showErrorPage = false;
	  
	  // find cookie if it exists
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
	  //new user or user logged out
	  //need to change servletName to be ServerIDprimary, and ServerIDbackup
	  if(request.getParameter("Logout") != null || repeatVisitor == null){
		  String sessionID = makeSessionId();
		  String cookieValue = sessionID + "%" + defaultVersionNumber + "%" + servletName;
		  Cookie newVisitor = new Cookie(cookieName, cookieValue);
		  Session newSession =  new Session(sessionID,
					 						defaultVersionNumber,
					 						defaultMessage,
					 						(int)(System.currentTimeMillis()/1000) + defaultExpirationTime);
		  addToConcurrentHashMap(sessionTable, newSession);
		  
		  System.out.println("new user");
		  //send to backup server, will need to include some random known serverID from view that is up
		  //make sure to include in sessionwrite
		  DatagramPacket writeResponse;
//		  for(String serverID : views.getServerIDs){
			  writeResponse = server.SessionWrite(sessionID, newSession.version, newSession.expirationTimeStamp, newSession.message);
			  System.out.println("finished new");
//			  if(writeResponse != null){
//				  break;
//			  }
//		  }
//		  if(writeResponse == null){
//			  //no server for backup, write as null
//		  }
		  
		  
		  
		  
		  
		  
		  
		  
		  
		  newVisitor.setMaxAge(defaultExpirationTime); //1 min
		  response.addCookie(newVisitor);
	  }
	  // returning user
	  else{
		  System.out.println("returning user");
		  String[] sessionMessage = repeatVisitor.getValue().split("%");
		  
		  //check if there is a new message
		  String newMessage = request.getParameter("newMessage");
		  //use only safe characters
		  if(newMessage != null){
			  newMessage = newMessage.replaceAll("[^A-Za-z0-9.-_ ?]", "");
		  }
		  
		  
		  
		  
		  
		  
		  
		  Session m;
		  //check if this is primary or backup serverid
//		  if(servletName.equals(sessionMessage[2]) || servletName.equals(sessionMessage[3])){
//			  m = sessionTable.get(sessionMessage[0]);
//			  if(m == null){
//				  //Session Timedout
//			  }
//		  } else{
//			  boolean readBackup = true;
//			  //read the session data from primary
			  DatagramPacket readResponse = server.SessionRead(sessionMessage[0]);
//			  //null means socket failed
			  if(readResponse != null){
				  String[] readResponseData = new String(readResponse.getData()).trim().split("_");
				  //not found means session Timedout
				  // TODO Update views table with status DOWN
				  if(readResponseData[1].equals("found")){
					  //check that the version numbers are the same, if not session error
					  if(readResponseData[2].equals(sessionMessage[1])){
						  message = readResponseData[3];
//						  readBackup = false;
					  }
				  }
			  }
//			  if(readBackup && !sessionMessage[3].equals("null")){
//				  //read the session data from backup
//				  DatagramPacket readResponseBackup = server.SessionRead(sessionMessage[0]);
//				  if(readResponseBackup != null){
//					  String[] readResponseData = new String(readResponseBackup.getData()).trim().split("_");
//					  //not found means session Timedout
//					  if(readResponseData[1].equals("found")){
//						  //check that the version numbers are the same, if not session error
//						  if(readResponseData[2].equals(Integer.parseInt(sessionMessage[1]))){
//							  message = readResponseData[3];
//						  }
//					  }
//				  }
//			  }
//		  }
		  
		  //TODO change output if there is a session timeout or failure!!!
			  
		  
		  //determine if message exists and is below 512 char
		  //TODO NOTE, will have to change to below 512, because of addition package format, which includes callID, operationCode, sessionID, etc
		  //if not use the old message
		  if(newMessage != null && !newMessage.equals("") && newMessage.length() <= 512){
			  message = newMessage;
		  }
//		  else if(m != null){
//			  message = m.message;
//		  } 
		  
		  //if no errors do this
		  Session newSession =  new Session(sessionMessage[0],
					 						Integer.parseInt(sessionMessage[1]) + 1,
					 						message,
					 						(int)(System.currentTimeMillis()/1000) + defaultExpirationTime);
		  addToConcurrentHashMap(sessionTable, newSession);
		  //send to backup server, will need to include some random known serverID from view that is up
		  //make sure to include in sessionwrite
		  DatagramPacket writeResponse;
//		  for(String serverID : views.getServerIDs){
			  writeResponse = server.SessionWrite(newSession.sessionID, newSession.version, newSession.expirationTimeStamp, newSession.message);
//			  if(writeResponse != null){
//				  break;
//			  }
//		  }
//		  if(writeResponse == null){
//			  //no server for backup, write as null
//		  }
		  //Cookie value is sessionID, version number, serverNamePrimary, serverNameBackup
//		  String cookieValue = sessionMessage[0] + "%" + (Integer.parseInt(sessionMessage[1]) + 1) + "%" + servletName + "%" + backupServletName;
		  String cookieValue = sessionMessage[0] + "%" + (Integer.parseInt(sessionMessage[1]) + 1) + "%" + servletName;
		  Cookie newCookieForRepeatVisitor = new Cookie(cookieName, cookieValue);
		  newCookieForRepeatVisitor.setMaxAge(defaultExpirationTime); //1 min
		  response.addCookie(newCookieForRepeatVisitor);
	  }
	  
	  response.setContentType("text/html");
	  PrintWriter out = response.getWriter();
	  out.println
	      ("<!DOCTYPE html>\n" +
	       "<html>\n" +
	       "<head><title>CS 5300: Project 1a</title></head>\n" +
	       "<body bgcolor=\"#fdf5e6\">\n" +
	       "<h1>Message: " + message + "</h1>\n" +
	       "<h2>Expiration Time (in secs): " + defaultExpirationTime + "<h2>" +
	       "<h2>Servlet Identity: " + servletName + "</h2>" + 
	       "<p>" +
	       "<form action='project1a' method='POST'>" +
       	   "<input type='submit' value='Replace'><input type='text' name='newMessage'/><br/><br/>" +
	       "<input type='submit' value='Refresh'><br/><br/>" +
	       "<input type='submit' value='Logout' name='Logout'>" +
	       "</form>" +
	       "</p>\n" +
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
				  // TODO Auto-generated catch block
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


