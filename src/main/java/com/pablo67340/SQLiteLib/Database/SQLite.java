package com.pablo67340.SQLiteLib.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A file-backed SQLite database.
 * <p>
 * Works with or without Bukkit. Outside a Minecraft server, add
 * {@code org.xerial:sqlite-jdbc} to your project and use:
 *
 * <pre>
 * try (SQLite db = SQLite.open(new File("data/app.db"))) {
 *     db.migrate("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)");
 *     db.insert("INSERT INTO notes (text) VALUES (?)", "hello");
 * }
 * </pre>
 *
 * Connections are opened in WAL mode with foreign keys enforced and a 5 second
 * busy timeout.
 */
public class SQLite extends Database {

	private final String dbname;
	private final String createStatement;
	private final File dataFolder;
	private final File file;

	/**
	 * Create a new SQLite database stored as {@code <folder>/<databaseName>.db}.
	 * Call {@link #load()} before using it.
	 *
	 * @param databaseName    the database's name.
	 * @param createStatement SQL run every time the database loads, usually
	 *                        {@code CREATE TABLE IF NOT EXISTS ...}. May be
	 *                        {@code null} or contain several statements.
	 * @param folder          the folder the database file will be created in.
	 */
	public SQLite(String databaseName, String createStatement, File folder) {
		this(databaseName, createStatement, folder, null);
	}

	/**
	 * Same as {@link #SQLite(String, String, File)} but logging to your own
	 * logger.
	 */
	public SQLite(String databaseName, String createStatement, File folder, Logger logger) {
		this(new File(folder, validateName(databaseName) + ".db"), databaseName, createStatement, logger);
	}

	private SQLite(File file, String databaseName, String createStatement, Logger logger) {
		this.dbname = databaseName;
		this.createStatement = createStatement;
		this.file = file;
		this.dataFolder = file.getAbsoluteFile().getParentFile();
		setLogger(logger);
	}

	/**
	 * Open (creating if needed) the SQLite database at any path.
	 *
	 * @param file the database file, e.g. {@code new File("data.db")}.
	 * @return a loaded database.
	 */
	public static SQLite open(File file) {
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		SQLite db = new SQLite(file, dot > 0 ? name.substring(0, dot) : name, null, null);
		db.load();
		return db;
	}

	@Override
	public String getName() {
		return dbname;
	}

	@Override
	public File getFile() {
		return file;
	}

	public File getDataFolder() {
		return dataFolder;
	}

	/**
	 * Get the connection to the database file, opening it if needed.
	 *
	 * @return the connection, or {@code null} if it could not be opened (the
	 *         reason is logged).
	 */
	@Override
	public Connection getSQLConnection() {
		try {
			if (connection != null && !connection.isClosed()) return connection;

			if (dataFolder != null && !dataFolder.isDirectory() && !dataFolder.mkdirs())
				logger.severe("Could not create folder " + dataFolder);

			Class.forName("org.sqlite.JDBC");
			Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
			try (Statement st = conn.createStatement()) {
				st.execute("PRAGMA journal_mode = WAL");
				st.execute("PRAGMA synchronous = NORMAL");
				st.execute("PRAGMA foreign_keys = ON");
				st.execute("PRAGMA busy_timeout = 5000");
			}
			connection = conn;
			return conn;
		} catch (SQLException ex) {
			logger.log(Level.SEVERE, "Could not open SQLite database " + file, ex);
		} catch (ClassNotFoundException ex) {
			logger.severe("The SQLite JDBC driver (org.xerial:sqlite-jdbc) is missing. Spigot and Paper ship it; "
					+ "outside a server add it to your dependencies.");
		}
		return null;
	}

	/**
	 * Open the database and run the create statement, if any.
	 */
	@Override
	public void load() {
		initialize();
		if (connection == null || createStatement == null || createStatement.isBlank()) return;
		try {
			executeScript(createStatement);
		} catch (DatabaseException ex) {
			logger.log(Level.SEVERE, "Create statement failed for database " + dbname, ex);
		}
	}

	private static String validateName(String name) {
		if (name == null || name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")
				|| name.contains(":"))
			throw new IllegalArgumentException("Invalid database name '" + name + "'");
		return name;
	}
}
