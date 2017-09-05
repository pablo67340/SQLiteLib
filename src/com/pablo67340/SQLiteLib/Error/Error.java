package com.pablo67340.SQLiteLib.Error;

import java.util.logging.Level;

import com.pablo67340.SQLiteLib.Main.SQLiteLib;

public class Error {
	public static void execute(SQLiteLib plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
	}

	public static void close(SQLiteLib plugin, Exception ex) {
		plugin.getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
	}
}
