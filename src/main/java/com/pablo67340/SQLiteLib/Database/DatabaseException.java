package com.pablo67340.SQLiteLib.Database;

/**
 * Unchecked wrapper for any failure raised by the SQLiteLib API (usually an
 * underlying {@link java.sql.SQLException}).
 */
public class DatabaseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DatabaseException(String message) {
		super(message);
	}

	public DatabaseException(String message, Throwable cause) {
		super(message, cause);
	}
}
