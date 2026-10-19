package com.pablo67340.SQLiteLib.Database;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Converts between Java values and what SQLite actually stores. SQLite only
 * has INTEGER, REAL, TEXT, BLOB and NULL, so e.g. a column declared BOOLEAN
 * comes back as an Integer and a small INTEGER may come back as Integer or
 * Long depending on its size.
 */
final class Values {

	private Values() {
	}

	/** Prepare a Java value for binding to a statement parameter. */
	static Object toSql(Object value) {
		if (value instanceof UUID || value instanceof Character) return value.toString();
		if (value instanceof Enum<?>) return ((Enum<?>) value).name();
		if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
		return value;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static <T> T convert(Object value, Class<T> type) {
		if (value == null) return null;
		if (type.isInstance(value)) return (T) value;

		Object result;
		if (type == String.class) {
			result = value instanceof byte[] ? new String((byte[]) value, StandardCharsets.UTF_8) : value.toString();
		} else if (type == Integer.class || type == int.class) {
			result = toNumber(value).intValue();
		} else if (type == Long.class || type == long.class) {
			result = toNumber(value).longValue();
		} else if (type == Double.class || type == double.class) {
			result = toNumber(value).doubleValue();
		} else if (type == Float.class || type == float.class) {
			result = toNumber(value).floatValue();
		} else if (type == Short.class || type == short.class) {
			result = toNumber(value).shortValue();
		} else if (type == Byte.class || type == byte.class) {
			result = toNumber(value).byteValue();
		} else if (type == Boolean.class || type == boolean.class) {
			result = toBoolean(value);
		} else if (type == UUID.class) {
			result = UUID.fromString(value.toString());
		} else if (type == byte[].class) {
			result = value.toString().getBytes(StandardCharsets.UTF_8);
		} else if (type.isEnum()) {
			result = Enum.valueOf((Class<Enum>) type, value.toString());
		} else {
			throw new DatabaseException("Cannot convert " + value.getClass().getName() + " to " + type.getName());
		}
		return (T) result;
	}

	private static Number toNumber(Object value) {
		if (value instanceof Number) return (Number) value;
		if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
		String text = value.toString().trim();
		try {
			if (text.contains(".") || text.contains("e") || text.contains("E")) return Double.parseDouble(text);
			return Long.parseLong(text);
		} catch (NumberFormatException ex) {
			throw new DatabaseException("Value '" + text + "' is not a number", ex);
		}
	}

	private static boolean toBoolean(Object value) {
		if (value instanceof Number) return ((Number) value).doubleValue() != 0;
		String text = value.toString().trim();
		return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equals("1");
	}
}
