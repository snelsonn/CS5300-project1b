package project1b;

import java.io.IOException;
import java.util.Random;

public class ViewThread extends Thread {
	public static final int GOSSIP_SECS = 1;
	public static Random generator = new Random();
	
	@Override
	public void run() {
		while (true) {
			
			/*try {
				Server.ExchangeViews();
			} catch (IOException e1) {
				e1.printStackTrace();
			}*/
			
			try {
				sleep ( (GOSSIP_SECS / 2) + generator.nextInt( GOSSIP_SECS ) );
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
	}
}
