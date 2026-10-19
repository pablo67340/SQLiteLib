package com.pablo67340.SQLiteLib.Main;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.pablo67340.SQLiteLib.Database.Database;
import com.pablo67340.SQLiteLib.Database.SQLite;

public class SQLiteLib extends JavaPlugin {

	private static SQLiteLib INSTANCE;

	private final Map<String, Database> databases = new HashMap<>();

	/**
	 * Override the onEnable Method, which runs only when the plugin is fully
	 * loaded. Save the instance and store it to a variable.
	 */
	@Override
	public void onEnable() {
		getDataFolder().mkdirs();
		INSTANCE = this;
	}

	/**
	 * Placeholder method for future features.
	 * <p>
	 * For right now it is crash saving.
	 */
	@Override
	public void onDisable() {

	}

	/**
	 * Log a severe message.
	 * @param msg The string message.
	 * @param thrown Throwable associated with log message.
	 */
	public static void log(String msg, Throwable thrown) {
		SQLiteLib.getInstance().getLogger().log(Level.SEVERE, msg, thrown);
	}

	/**
	 * Get the current instance of the plugin within the server. 
	 * Do not use this to hook into SQLiteLib, as it can become
	 * unsafe in the future. Use the hookSQLiteLib method.
	 * 
	 * @return the {@link SQLiteLib}'s prefix.
	 */
	public static SQLiteLib getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Get the current instance of the plugin within the server.
	 * Needed to hook into the API to save things.
	 * 
	 * @return the {@link SQLiteLib}'s prefix.
	 */
	public static SQLiteLib hookSQLiteLib(Plugin hostPlugin) {
        SQLiteLib plugin = (SQLiteLib) Bukkit.getPluginManager().getPlugin("SQLiteLib");
        if (plugin == null) {
            Bukkit.getLogger().severe("SQLiteLib is not yet ready! You have you called hookSQLiteLib() too early.");
            return null;
        }
        return plugin;
    }

	/**
	 * Create and load a new database
	 * 
	 * @param databaseName Database name.
	 * @param createStatement Initial statement once the database is
	 *                        created. Usually used to create tables.
	 * 			  			  <p>
	 *            			  Sets the string sent to player when an
	 *            			  item cannot be purchased.
	 */
	public void initializeDatabase(String databaseName, String createStatement) {
		Database db = new SQLite(databaseName, createStatement, this.getDataFolder());
		db.load();
		databases.put(databaseName, db);
	}
	
	/**
	 * Create and load a new database within a different plugin's
	 * folder.
	 *
	 * @param plugin Plugin to create database file inside.
	 * @param databaseName Database name.
	 * @param createStatement Initial statement once the database is
	 *                        created. Usually used to create tables.
	 * 						  <p>
	 *            			  Sets the string sent to player when an
	 *            			  item cannot be purchased.
	 */
	public void initializeDatabase(Plugin plugin, String databaseName, String createStatement) {
		Database db = new SQLite(databaseName, createStatement, plugin.getDataFolder());
		db.load();
		databases.put(databaseName, db);
	}

	/**
	 * Get the global list of currently loaded databased.
	 * 
	 * @return the {@link SQLiteLib}'s global database list.
	 */
	public Map<String, Database> getDatabases() {
		return databases;
	}

	/**
	 * Gets a specific {@link Database}'s class.
	 *
	 * @param databaseName Database name
	 *
	 * @return The database.
	 */
	public Database getDatabase(String databaseName) {
		return getDatabases().get(databaseName);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("sqlite") || command.getName().equalsIgnoreCase("sl")) {
			if (sender.hasPermission("sqlite.use") || sender.isOp() || sender instanceof ConsoleCommandSender) {
				if (args.length != 0) {

					// EXECUTE
					if (args[0].equalsIgnoreCase("execute")) {
						if (args.length > 2) {
							String database = args[1];
							StringBuilder statement = new StringBuilder();

							for (int x = 2; x <= args.length - 1; x++)
								statement.append(args[x]).append(" ");
							statement = new StringBuilder(statement.toString().trim());

							//System.out.println("Statement: " + statement);

							if (getDatabase(database).executeStatement(statement.toString())) {
								sender.sendMessage("Query successful!");
							} else {
								sender.sendMessage("Query failure!");
							}
						} else {
							printHelp(sender);
						}

						
						
						
						// INIT/Create
					} else if (args[0].equalsIgnoreCase("init")) {
						if (args.length > 2) {
							String database = args[1];
							StringBuilder statement = new StringBuilder();

							for (int x = 2; x <= args.length - 1; x++)
								statement.append(args[x]).append(" ");
							statement = new StringBuilder(statement.toString().trim());

							initializeDatabase(database, statement.toString());

							sender.sendMessage("Database initialized!");
						} else {
							printHelp(sender);
						}
						
						
						
						
						// QUERY VALUE
					} else if (args[0].equalsIgnoreCase("queryvalue")) {
						if (args.length >= 3) {
							String database = args[1];
							String row = args[2];
							StringBuilder statement = new StringBuilder();

							for (int x = 3; x <= args.length - 1; x++)
								statement.append(args[x]).append(" ");
							statement = new StringBuilder(statement.toString().trim());
							
							sender.sendMessage((String)getDatabase(database).queryValue(statement.toString(), row));
						}
					}else if (args[0].equalsIgnoreCase("queryrow")) {
						if (args.length > 3) {
							String database = args[1];
							String row = args[2];
							StringBuilder statement = new StringBuilder();

							for (int x = 3; x <= args.length - 1; x++)
								statement.append(args[x]).append(" ");
							statement = new StringBuilder(statement.toString().trim());
							
							for(Object obj : getDatabase(database).queryRow(statement.toString(), row))
								sender.sendMessage((String)obj);

						}
					}
				} else {
					printHelp(sender);
				}
			}
		}
		return false;
	}

	private void printHelp(CommandSender sender) {
		sender.sendMessage("     SQLiteLib Help     ");
		sender.sendMessage("Command aliases: /sl");
		sender.sendMessage("All commands do not require { }");
		sender.sendMessage("/sqlite init {database} {statement} - Initializes a database");
		sender.sendMessage("/sqlite execute {database} {statement} - Execute any statement in the database");
		sender.sendMessage("/sqlite queryValue {database} {statement} - Query 1 value from the database");
		sender.sendMessage("/sqlite queryRow {database} {row} {statement} - Query list of values from the database");
	}

}
