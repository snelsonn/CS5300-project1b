package project1b;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.SecureRandom;
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
	
	public Project1a(){
		(new Server()).start();
	}
	
	public static final ConcurrentHashMap<String, Session> sessionTable = new ConcurrentHashMap<String, Session>();
	private static final String cookieName = "CS5300PROJ1SESSION";
	private static final String defaultMessage = "Hello User";
	private static final int defaultExpirationTime = 60 * 1; //1 min
	private static final int defaultVersionNumber = 1;

  @Override
  public void doGet(HttpServletRequest request,
                    HttpServletResponse response)
      throws ServletException, IOException {
	  // find cookie if it exists
	  Cookie[] cookies = request.getCookies();
	  Cookie repeatVisitor = null;
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
	  String servletName = getServletName();
	  if(request.getParameter("Logout") != null || repeatVisitor == null){
		  String sessionID = makeUniqueId();
		  String cookieValue = sessionID + "%" + defaultVersionNumber + "%" + servletName;
		  Cookie newVisitor = new Cookie(cookieName, cookieValue);
		  addToConcurrentHashMap(sessionTable, new Session(sessionID,
				  								 defaultVersionNumber,
				  								 defaultMessage,
				  								(int)(System.currentTimeMillis()/1000) + defaultExpirationTime));
		  newVisitor.setMaxAge(defaultExpirationTime); //1 min
		  response.addCookie(newVisitor);
	  }
	  // returning user
	  else{
		  String[] sessionMessage = repeatVisitor.getValue().split("%");
		  //Cookie value is sessionID, version number, expiration-timestamp
		  String cookieValue = sessionMessage[0] + "%" + (Integer.parseInt(sessionMessage[1]) + 1) + "%" + sessionMessage[2];
		  
		  //check if there is a new message
		  String newMessage = request.getParameter("newMessage");
		  //use only safe characters
		  if(newMessage != null){
			  newMessage = newMessage.replaceAll("[^A-Za-z0-9.-_ ?]", "");
		  }
		  Session m = sessionTable.get(sessionMessage[0]);
		  //determine if message exists and is below 512 char
		  //if not use the old message
		  if(newMessage != null && !newMessage.equals("") && newMessage.length() <= 512){
			  message = newMessage;
		  } else if(m != null){
			  message = m.message;
		  } 
		  
		  Cookie newCookieForRepeatVisitor = new Cookie(cookieName, cookieValue);
		  newCookieForRepeatVisitor.setMaxAge(defaultExpirationTime); //1 min
		  addToConcurrentHashMap(sessionTable, new Session(sessionMessage[0],
				  								 Integer.parseInt(sessionMessage[1]) + 1,
					 							 message,
					 							 (int)(System.currentTimeMillis()/1000) + defaultExpirationTime));
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
	  
	  garbageCollect(sessionTable);
  }
  
  @Override
  public void doPost(HttpServletRequest request,
          HttpServletResponse response)
        		  throws ServletException, IOException {
	  doGet(request, response);
  }
  
  //check all sessions in sessionTable, if they are passed the default ExpirationTime, remove that kv-pair
  public static void garbageCollect(ConcurrentHashMap<String, Session> sessionTable){
	  for(Entry<String, Session> session : sessionTable.entrySet()){
		  if(session.getValue().expirationTimeStamp < (int)(System.currentTimeMillis()/1000)){
			  sessionTable.remove(session.getKey());
		  }
	  }
  }
  
  public static String makeUniqueId() {
	    return new BigInteger(130, new SecureRandom()).toString(32);
	  }
  
  public static void addToConcurrentHashMap(ConcurrentHashMap<String, Session> sessionTable, Session message){
	  sessionTable.put(message.sessionID, message);
  }
  
  
}


