package com.pablo67340.SQLiteLib.Database;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A persistent key/value map stored in a single table. Obtain one with
 * {@link Database#keyValue(String)}.
 * <p>
 * Values keep their SQLite type (integer, real, text or blob). Like every
 * {@link Database} call these methods block; wrap them in
 * {@link Database#supplyAsync} when calling from the main thread in hot paths.
 */
public final class KeyValueStore {

	private final Database db;
	private final String table;
	private final String quoted;

	KeyValueStore(Database db, String table) {
		this.db = db;
		this.table = table;
		this.quoted = Database.quote(table);
		db.executeScript("CREATE TABLE IF NOT EXISTS " + quoted + " (\"key\" TEXT PRIMARY KEY NOT NULL, \"value\")");
	}

	public String getTable() {
		return table;
	}

	/** Store a value, replacing any existing one. A {@code null} value removes the key. */
	public void set(String key, Object value) {
		if (value == null) {
			remove(key);
			return;
		}
		db.update("INSERT INTO " + quoted + " (\"key\", \"value\") VALUES (?, ?) "
				+ "ON CONFLICT(\"key\") DO UPDATE SET \"value\" = excluded.\"value\"", key, value);
	}

	/** Store several values in one transaction. */
	public void setAll(Map<String, ?> values) {
		db.transaction(tx -> values.forEach(this::set));
	}

	/** @return the raw stored value, if present. */
	public Optional<Object> get(String key) {
		return db.queryScalar("SELECT \"value\" FROM " + quoted + " WHERE \"key\" = ?", Object.class, key);
	}

	/** @return the stored value converted to {@code type}, if present. */
	public <T> Optional<T> get(String key, Class<T> type) {
		return db.queryScalar("SELECT \"value\" FROM " + quoted + " WHERE \"key\" = ?", type, key);
	}

	public String getString(String key, String def) {
		return get(key, String.class).orElse(def);
	}

	public int getInt(String key, int def) {
		return get(key, Integer.class).orElse(def);
	}

	public long getLong(String key, long def) {
		return get(key, Long.class).orElse(def);
	}

	public double getDouble(String key, double def) {
		return get(key, Double.class).orElse(def);
	}

	public boolean getBoolean(String key, boolean def) {
		return get(key, Boolean.class).orElse(def);
	}

	/**
	 * Atomically add {@code delta} to a numeric value (missing keys start at 0).
	 *
	 * @return the new value.
	 */
	public long increment(String key, long delta) {
		return db.transactionResult(tx -> {
			upsertAdd(key, delta);
			return get(key, Long.class).orElse(0L);
		});
	}

	/**
	 * Atomically add {@code delta} to a numeric value (missing keys start at 0).
	 *
	 * @return the new value.
	 */
	public double increment(String key, double delta) {
		return db.transactionResult(tx -> {
			upsertAdd(key, delta);
			return get(key, Double.class).orElse(0D);
		});
	}

	public boolean has(String key) {
		return db.exists("SELECT 1 FROM " + quoted + " WHERE \"key\" = ?", key);
	}

	/** @return whether the key existed. */
	public boolean remove(String key) {
		return db.update("DELETE FROM " + quoted + " WHERE \"key\" = ?", key) > 0;
	}

	/** @return all keys, sorted. */
	public List<String> keys() {
		return db.queryColumn("SELECT \"key\" FROM " + quoted + " ORDER BY \"key\"", String.class);
	}

	/** @return all keys starting with {@code prefix}, sorted. */
	public List<String> keys(String prefix) {
		return db.queryColumn("SELECT \"key\" FROM " + quoted + " WHERE substr(\"key\", 1, ?) = ? ORDER BY \"key\"",
				String.class, prefix.length(), prefix);
	}

	/** @return a snapshot of every entry, sorted by key. */
	public Map<String, Object> getAll() {
		Map<String, Object> all = new LinkedHashMap<>();
		for (Row row : db.query("SELECT \"key\", \"value\" FROM " + quoted + " ORDER BY \"key\""))
			all.put(row.getString("key"), row.getObject("value"));
		return all;
	}

	public long size() {
		return db.queryScalar("SELECT COUNT(*) FROM " + quoted, Long.class).orElse(0L);
	}

	public void clear() {
		db.update("DELETE FROM " + quoted);
	}

	private void upsertAdd(String key, Object delta) {
		db.update("INSERT INTO " + quoted + " (\"key\", \"value\") VALUES (?, ?) "
				+ "ON CONFLICT(\"key\") DO UPDATE SET \"value\" = \"value\" + excluded.\"value\"", key, delta);
	}
}
