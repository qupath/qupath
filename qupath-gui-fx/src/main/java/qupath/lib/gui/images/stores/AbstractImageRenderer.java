package qupath.lib.gui.images.stores;
import java.util.concurrent.atomic.AtomicLong;

import qupath.lib.gui.images.stores.ImageRenderer;

public abstract class AbstractImageRenderer implements ImageRenderer {
	
	private static AtomicLong NEXT_COUNT = new AtomicLong();
	
	private long id = NEXT_COUNT.incrementAndGet();
	
	protected long timestamp = System.currentTimeMillis();
	
	@Override
	public long getLastChangeTimestamp() {
		return timestamp;
	}

	@Override
	public String getUniqueID() {
		return this.getClass().getName() + ":" + id + ":" + getLastChangeTimestamp();
	}

}
