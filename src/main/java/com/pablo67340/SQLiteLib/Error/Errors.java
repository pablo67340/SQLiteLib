package com.pablo67340.SQLiteLib.Error;

/**
 * @deprecated No longer used. Will be removed in a future major version.
 */
@Deprecated
public class Errors {

	public static String sqlConnectionExecute() {
		return "Couldn't execute SQLite statement: ";
	}

	public static String sqlConnectionClose() {
		return "Failed to close SQLite connection: ";
	}

	public static String noSQLConnection() {
		return "Unable to retrieve SQLite connection: ";
	}

	public static String noTableFound() {
		return "Database Error: No Table Found";
	}
}
