package project1b;

public class View {
	public String svrId;
	public int status;
	public static final int UP = 0;
	public static final int DOWN = 1;
	public long time;
	
	public View(String svrId) {
		this.svrId = svrId;
		this.status = UP;
		this.time = System.currentTimeMillis();
	}
	
	public View(String svrId, int status) {
		this.svrId = svrId;
		this.status = status;
		this.time = System.currentTimeMillis();
	}
	
	public View(String svrId, int status, long time) {
		this.svrId = svrId;
		this.status = status;
		this.time = time;
	}
	
	public Long getTime() {
		return this.time;
	}
	
	public String getSvrId() {
		return this.svrId;
	}
	
	public int getStatus() {
		return this.status;
	}
	
	public View updateUPTime() {
		this.status = UP;
		this.time = System.currentTimeMillis();
		return this;
	}
	
	public View updateDOWNTime() {
		this.status = DOWN;
		this.time = System.currentTimeMillis();
		return this;
	}
	
	// svrId = 32 bytes
	// underscore = 1 byte
	// status = 1 byte
	// underscore = 1 byte
	// time = 8 bytes
	// total = 43 bytes
	public String toString() {
		return this.svrId
				+ "_" + this.status 
				+ "_" + this.time;
	}
	
	// add underscore at beginning
	public static int getBytes() {
		return 44;
	}
	
}