package com.pablo67340.SQLiteLib.Main;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import com.pablo67340.SQLiteLib.Database.Database;
import com.pablo67340.SQLiteLib.Database.SQLite;

public class SQLiteLib extends JavaPlugin {

	private static SQLiteLib INSTANCE;

	private Map<String, Database> databases = new HashMap<>();

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
	 * Placeholder method for future features (Crash saving)
	 */
	@Override
	public void onDisable() {

	}

	/**
	 * Get the current instance of the plugin within the server. 
	 * Do not use this to hook into SQLiteLib, as it can become unsafe
	 * in the future. Use the hookSQLiteLib method.
	 * <p>
	 * 
	 * @return the {@link SQLiteLib}'s prefix.
	 */
	public static SQLiteLib getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Get the current instance of the plugin within the server. Needed to hook into
	 * the API to save things.
	 * <p>
	 * 
	 * @return the {@link SQLiteLib}'s prefix.
	 */
	public static SQLiteLib hookSQLiteLib() {
        SQLiteLib plugin = (SQLiteLib) Bukkit.getPluginManager().getPlugin("SQLiteLib");
        if (plugin == null) {
            Bukkit.getLogger().severe("SQLiteLib is not yet ready! You have you called hookSQLiteLib() too early.");
            return null;
        }
        return new SQLiteLib();
    }

	/**
	 * 
	 * @param Database
	 *            name
	 * @param Initial
	 *            statement once the database is created. Usually used to create
	 *            tables.
	 * 
	 *            Sets the string sent to player when an item cannot be purchased.
	 */
	public void initializeDatabase(String databaseName, String createStatement) {
		Database db = new SQLite(databaseName, createStatement);
		db.load();
		databases.put(databaseName, db);
	}

	/**
	 * Get the global list of currently loaded databased.
	 * <p>
	 * 
	 * @return the {@link SQLiteLib}'s global database list.
	 */
	public Map<String, Database> getDatabases() {
		return databases;
	}

	/**
	 * 
	 * @param Database
	 *            name
	 * 
	 *            Gets a specific {@link Database}'s class.
	 */
	public Database getDatabase(String databaseName) {
		return getDatabases().get(databaseName);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String args[]) {
		if (command.getName().equalsIgnoreCase("sqlite") || command.getName().equalsIgnoreCase("sl")) {
			if (sender.hasPermission("sqlite.use") || sender.isOp() || sender instanceof ConsoleCommandSender) {
				if (args.length != 0) {

					// EXECUTE
					if (args[0].equalsIgnoreCase("execute")) {
						if (args.length > 2) {
							String database = args[1];
							String statement = "";

							for (int x = 2; x <= args.length - 1; x++) {
								statement += args[x] + " ";
							}
							statement = statement.trim();
							System.out.println("Statement: " + statement);
							if (getDatabase(database).executeStatement(statement)) {
								sender.sendMessage("Query successful!");
							} else {
								sender.sendMessage("Query failure!");
							}
						} else {
							printHelp(sender);
						}

						
						
						
						// INIT/Create
					} else if (args[0].equalsIgnoreCase("init")) {
						if (args.length == 2) {
							String database = args[1];
							String statement = "";

							for (int x = 2; x <= args.length - 1; x++) {
								statement += args[x] + " ";
							}
							statement = statement.trim();
							initializeDatabase(database, statement);
							sender.sendMessage("Database intialized!");
						} else {
							printHelp(sender);
						}
						
						
						
						
						// QUERY VALUE
					} else if (args[0].equalsIgnoreCase("queryvalue")) {
						if (args.length > 1) {
							String database = args[1];
							String row = args[2];
							String statement = "";

							for (int x = 3; x <= args.length - 1; x++) {
								statement += args[x] + " ";
							}
							statement = statement.trim();
							
							sender.sendMessage((String)getDatabase(database).queryValue(statement, row));
							
						}
					}else if (args[0].equalsIgnoreCase("queryrow")) {
						if (args.length > 1) {
							String database = args[1];
							String row = args[2];
							String statement = "";

							for (int x = 3; x <= args.length - 1; x++) {
								statement += args[x] + " ";
							}
							statement = statement.trim();
							
							for(Object obj : getDatabase(database).queryRow(statement, row)) {
								sender.sendMessage((String)obj);
							}
							
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
