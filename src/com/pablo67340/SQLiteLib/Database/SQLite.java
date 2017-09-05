package com.pablo67340.SQLiteLib.Database;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import com.pablo67340.SQLiteLib.Main.SQLiteLib;

public class SQLite extends Database {

	private String dbname;

	private String createTestTable = "CREATE TABLE IF NOT EXISTS test (" + "`test` varchar(32) NOT NULL,"
			+ "PRIMARY KEY (`test`)" + ");";

	public SQLite(String databaseName, String createStatement) {
		dbname = databaseName;
	}

	public Connection getSQLConnection() {
		File dataFolder = new File(SQLiteLib.getInstance().getDataFolder(), dbname + ".db");
		if (!dataFolder.exists()) {
			try {
				dataFolder.createNewFile();
			} catch (IOException e) {
				SQLiteLib.getInstance().getLogger().log(Level.SEVERE, "File write error: " + dbname + ".db");
			}
		}
		try {
			if (connection != null && !connection.isClosed()) {
				return connection;
			}
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
			return connection;
		} catch (SQLException ex) {
			SQLiteLib.getInstance().getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
		} catch (ClassNotFoundException ex) {
			SQLiteLib.getInstance().getLogger().log(Level.SEVERE,
					"You need the SQLite JBDC library. Google it. Put it in /lib folder.");
		}
		return null;
	}

	public void load() {
		connection = getSQLConnection();
		try {
			Statement s = connection.createStatement();
			s.executeUpdate(createTestTable);
			s.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		initialize();
	}
}
