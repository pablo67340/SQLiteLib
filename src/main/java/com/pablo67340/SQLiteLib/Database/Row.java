package com.pablo67340.SQLiteLib.Database;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One row of a query result, detached from the database so it is safe to keep
 * around or hand to another thread. Column lookups are case-insensitive, like
 * SQLite itself.
 * <p>
 * Primitive getters ({@link #getInt}, {@link #getDouble}, ...) return
 * {@code 0}/{@code false} for {@code NULL}; use {@link #isNull} or
 * {@link #get(String, Class)} when you need to tell the difference.
 */
public final class Row {

	private final Map<String, Object> values;
	private final Map<String, String> lookup;

	Row(Map<String, Object> values) {
		this.values = Collections.unmodifiableMap(values);
		this.lookup = new LinkedHashMap<>();
		for (String column : values.keySet())
			lookup.putIfAbsent(column.toLowerCase(Locale.ROOT), column);
	}

	static Row from(ResultSet rs) throws SQLException {
		ResultSetMetaData meta = rs.getMetaData();
		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 1; i <= meta.getColumnCount(); i++)
			values.put(meta.getColumnLabel(i), rs.getObject(i));
		return new Row(values);
	}

	/** @return whether this row contains the given column. */
	public boolean has(String column) {
		return lookup.containsKey(column.toLowerCase(Locale.ROOT));
	}

	/** @return whether the column is missing or {@code NULL}. */
	public boolean isNull(String column) {
		return getObject(column) == null;
	}

	/** @return the raw value as returned by the driver, or {@code null}. */
	public Object getObject(String column) {
		String key = lookup.get(column.toLowerCase(Locale.ROOT));
		return key == null ? null : values.get(key);
	}

	/**
	 * Get a value converted to the requested type. Handles the common SQLite
	 * quirks (Integer vs Long, booleans stored as 0/1, UUIDs and enums stored as
	 * text).
	 *
	 * @return the converted value, or {@code null} if the column is NULL.
	 */
	public <T> T get(String column, Class<T> type) {
		return Values.convert(getObject(column), type);
	}

	public String getString(String column) {
		return get(column, String.class);
	}

	public int getInt(String column) {
		Integer value = get(column, Integer.class);
		return value == null ? 0 : value;
	}

	public long getLong(String column) {
		Long value = get(column, Long.class);
		return value == null ? 0L : value;
	}

	public double getDouble(String column) {
		Double value = get(column, Double.class);
		return value == null ? 0D : value;
	}

	public float getFloat(String column) {
		Float value = get(column, Float.class);
		return value == null ? 0F : value;
	}

	public boolean getBoolean(String column) {
		Boolean value = get(column, Boolean.class);
		return value != null && value;
	}

	public UUID getUUID(String column) {
		return get(column, UUID.class);
	}

	public byte[] getBytes(String column) {
		return get(column, byte[].class);
	}

	public <E extends Enum<E>> E getEnum(String column, Class<E> type) {
		return get(column, type);
	}

	/** @return column names in result order. */
	public Set<String> getColumns() {
		return values.keySet();
	}

	/** @return an unmodifiable view of this row, in column order. */
	public Map<String, Object> asMap() {
		return values;
	}

	@Override
	public String toString() {
		return values.toString();
	}
}
