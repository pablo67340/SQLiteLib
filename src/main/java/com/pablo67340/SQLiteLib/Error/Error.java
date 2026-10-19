package com.pablo67340.SQLiteLib.Error;

import com.pablo67340.SQLiteLib.Main.SQLiteLib;

/**
 * @deprecated No longer used; errors are logged by each {@code Database}.
 *             Will be removed in a future major version.
 */
@Deprecated
public class Error {
	public static void execute(Exception ex) {
		SQLiteLib.log(Errors.sqlConnectionExecute(), ex);
	}

	public static void close(Exception ex) {
		SQLiteLib.log(Errors.sqlConnectionClose(), ex);
	}
}
