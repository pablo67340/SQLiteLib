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
	private final String dbname;
	private final String createStatement;
	private final File dataFolder;

	/**
	 * Create a new SQLite database.
	 *
	 * @param databaseName the database's name.
	 * @param createStatement the statement to create our first table.
	 * @param folder the folder the database file will be created.
	 */
	public SQLite(String databaseName, String createStatement, File folder) {
		this.dbname = databaseName;
		this.createStatement = createStatement;
		this.dataFolder = folder;
	}

	/**
	 * Get the connection to the database file.
	 *
	 * @return The connection to the database.
	 */
	public Connection getSQLConnection() {
		File folder = new File(dataFolder, dbname + ".db");

		if (!folder.exists()) {
			try {
				folder.createNewFile();
			} catch (IOException e) {
				SQLiteLib.getInstance().getLogger().log(Level.SEVERE, "File write error: " + dbname + ".db");
			}
		}

		try {
			if (connection != null && !connection.isClosed()) return connection;
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + folder);
			return connection;
		} catch (SQLException ex) {
			SQLiteLib.log("SQLite exception on initialize", ex);
		} catch (ClassNotFoundException ex) {
			SQLiteLib.getInstance().getLogger().log(Level.SEVERE,
					"You need the SQLite JBDC library. Google it. Put it in /lib folder.");
		}

		return null;
	}

	/**
	 * Load the database.
	 */
	public void load() {
		connection = getSQLConnection();
		try {
			Statement s = connection.createStatement();
			String createTestTable = "CREATE TABLE IF NOT EXISTS test (" + "`test` varchar(32) NOT NULL,"
					+ "PRIMARY KEY (`test`)" + ");";
			s.executeUpdate(createTestTable);
			s.executeUpdate(createStatement);
			s.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		initialize();
	}
	
	public File getDataFolder() {
		return dataFolder;
	}
}
