package org.eclipse.jetty.nosql.memcached.hashmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.nosql.kvs.AbstractKeyValueStoreClient;
import org.eclipse.jetty.nosql.kvs.KeyValueStoreClientException;

// intend to use this as test mock
public class HashMapClient extends AbstractKeyValueStoreClient {
	class Entry {
		private byte[] data = null;
		private long expiry = 0;
		Entry(byte[] raw, long exp) {
			data = raw;
			expiry = exp;
		}
		byte[] getData() {
			return data;
		}
		long getExpiry() {
			return expiry;
		}
	}

	private static final int FOREVER = 0;
	private static Map<String, Entry> data = new ConcurrentHashMap<String, Entry>();

	public HashMapClient() {
		this("127.0.0.1:11211");
	}

	public HashMapClient(String serverString) {
		super(serverString);
	}

	@Override
	public boolean establish() throws KeyValueStoreClientException {
		if (isAlive()) {
			shutdown();
		}
		return true;
	}

	@Override
	public boolean shutdown() throws KeyValueStoreClientException {
		return true;
	}

	@Override
	public boolean isAlive() {
		return data != null;
	}

	@Override
	public byte[] get(String key) throws KeyValueStoreClientException {
		if (!isAlive()) {
			throw(new KeyValueStoreClientException(new IllegalStateException("client not established")));
		}

		byte[] raw = null;
		Entry entry = data.get(key);
		if ( entry != null) {
			long exp = entry.getExpiry();
			long now = System.currentTimeMillis();
			if (now < exp || exp == FOREVER) {
				raw = entry.getData();
			} else {
				delete(key);
			}
		}
		return raw;
	}

	@Override
	public boolean set(String key, byte[] raw) throws KeyValueStoreClientException {
		return this.set(key, raw, FOREVER);
	}

	@Override
	public boolean set(String key, byte[] raw, int exp) throws KeyValueStoreClientException {
		if (!isAlive()) {
			throw(new KeyValueStoreClientException(new IllegalStateException("client not established")));
		}

		// the actual value sent may either be Unix time (number of seconds since
		// January 1, 1970, as a 32-bit value), or a number of seconds starting 
		// from current time. In the latter case, this number of seconds may not
		// exceed 60*60*24*30 (number of seconds in 30 days); if the number sent
		// by a client is larger than that, the server will consider it to be real
		// Unix time value rather than an offset from current time.
		// http://code.sixapart.com/svn/memcached/trunk/server/doc/protocol.txt
		long timestamp;
		if (exp < 60*60*24*30) { // relative time
			long now = System.currentTimeMillis();
			timestamp = now+TimeUnit.SECONDS.toMillis(exp);
		} else { // absolute time
			timestamp = TimeUnit.SECONDS.toMillis(exp);
		}
		data.put(key, new Entry(raw, timestamp));
		return true;
	}

	@Override
	public boolean add(String key, byte[] raw) throws KeyValueStoreClientException {
		return this.add(key, raw, FOREVER);
	}

	@Override
	public boolean add(String key, byte[] raw, int exp) throws KeyValueStoreClientException {
		if (!isAlive()) {
			throw(new KeyValueStoreClientException(new IllegalStateException("client not established")));
		}

		boolean notExists = !data.containsKey(key);
		if (notExists) {
			set(key, raw, exp);
		}
		return notExists;
	}

	@Override
	public boolean delete(String key) throws KeyValueStoreClientException {
		if (!isAlive()) {
			throw(new KeyValueStoreClientException(new IllegalStateException("client not established")));
		}

		data.remove(key);

		return true;
	}
}
