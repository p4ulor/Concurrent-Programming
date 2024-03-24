/***
 *  ISEL, LEIC, Concurrent Programming
 *
 *  TimeoutHolder class
 *
 *  Carlos Martins, October 2016
 *
 ***/

import java.util.concurrent.TimeUnit;

public class TimeoutHolder {
	private long expiresAt;
	
	public TimeoutHolder(long millis) {
		expiresAt = millis >= 0 ? System.currentTimeMillis() + millis: -1L;
	}
	
	public TimeoutHolder(long time, TimeUnit unit) {
		expiresAt = time >= 0 ? System.currentTimeMillis() + unit.toMillis(time) : -1L;
	}
	
	public boolean isTimed() { return expiresAt >= 0; }
	
	public long value() {
		if (expiresAt < 0)
			return Long.MAX_VALUE;
		long remainder = expiresAt - System.currentTimeMillis();
		return remainder > 0 ? remainder : 0;
	}	
}
