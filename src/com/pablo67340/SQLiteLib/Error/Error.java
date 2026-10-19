package com.pablo67340.SQLiteLib.Error;

import com.pablo67340.SQLiteLib.Main.SQLiteLib;

public class Error {
	public static void execute(Exception ex) {
		SQLiteLib.log("Couldn't execute MySQL statement: ", ex);
	}

	public static void close(Exception ex) {
		SQLiteLib.log("Failed to close MySQL connection: ", ex);
	}
}
