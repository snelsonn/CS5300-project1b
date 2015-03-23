package project1b;

public class Session {
	public String sessionID;
	public int version;
	public String message;
	public int expirationTimeStamp;
	
	public Session(String sessionID,
				   int version,
				   String message,
				   int expirationTimeStamp){
		this.sessionID = sessionID;
		this.version = version;
		this.message = message;
		this.expirationTimeStamp = expirationTimeStamp;
	}
	
	public Session(byte[] SessionWriteRequestData){
		String[] requestData = new String(SessionWriteRequestData).trim().split("_");
		this.sessionID = requestData[2];
		this.version = Integer.parseInt(requestData[3]);
		this.message = requestData[5];
		this.expirationTimeStamp = Integer.parseInt(requestData[4]);
//		String outBufferInfo = callID + operationSESSIONWRITE + sessionID + version + expirationTime + data;
	}
}
