package com.pablo67340.SQLiteLib.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import com.pablo67340.SQLiteLib.Database.Database;
import com.pablo67340.SQLiteLib.Database.SQLite;

/**
 * SQLiteLib plugin entry point and database registry.
 *
 * <pre>
 * SQLiteLib lib = SQLiteLib.hookSQLiteLib();
 * Database db = lib.open(this, "data");
 * db.migrate("CREATE TABLE homes (owner TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY (owner, name))");
 * </pre>
 *
 * Add {@code depend: [SQLiteLib]} to your plugin.yml so SQLiteLib is enabled
 * before your plugin.
 */
public class SQLiteLib extends JavaPlugin {

	private static SQLiteLib INSTANCE;

	private final Map<String, Database> databases = new ConcurrentHashMap<>();
	private final Map<String, String> owners = new ConcurrentHashMap<>();

	@Override
	public void onEnable() {
		INSTANCE = this;
		getDataFolder().mkdirs();

		PluginCommand command = getCommand("sqlite");
		if (command != null) {
			SQLiteCommand executor = new SQLiteCommand(this);
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}

		getServer().getServicesManager().register(SQLiteLib.class, this, this, ServicePriority.Normal);
	}

	/**
	 * Waits for pending async work and closes every database cleanly.
	 */
	@Override
	public void onDisable() {
		for (Database db : databases.values())
			db.close();
		databases.clear();
		owners.clear();
		getServer().getServicesManager().unregisterAll(this);
		INSTANCE = null;
	}

	/**
	 * Log a severe message.
	 *
	 * @param msg    The string message.
	 * @param thrown Throwable associated with log message.
	 */
	public static void log(String msg, Throwable thrown) {
		Logger logger = INSTANCE != null ? INSTANCE.getLogger() : Logger.getLogger("SQLiteLib");
		logger.log(Level.SEVERE, msg, thrown);
	}

	/**
	 * Get the current instance of the plugin within the server. Prefer
	 * {@link #hookSQLiteLib()}.
	 *
	 * @return the running {@link SQLiteLib}, or {@code null} if not enabled.
	 */
	public static SQLiteLib getInstance() {
		return INSTANCE;
	}

	/**
	 * Get the running SQLiteLib instance. Call this from your plugin's
	 * {@code onEnable} with {@code depend: [SQLiteLib]} in your plugin.yml.
	 *
	 * @return the running {@link SQLiteLib}, or {@code null} if it isn't loaded.
	 */
	public static SQLiteLib hookSQLiteLib() {
		Plugin plugin = Bukkit.getPluginManager().getPlugin("SQLiteLib");
		if (!(plugin instanceof SQLiteLib) || !plugin.isEnabled()) {
			Bukkit.getLogger().severe("SQLiteLib is not enabled yet! Add 'depend: [SQLiteLib]' to your plugin.yml "
					+ "and call hookSQLiteLib() from onEnable.");
			return null;
		}
		return (SQLiteLib) plugin;
	}

	/**
	 * @param hostPlugin your plugin (unused, kept for compatibility).
	 * @see #hookSQLiteLib()
	 */
	public static SQLiteLib hookSQLiteLib(Plugin hostPlugin) {
		return hookSQLiteLib();
	}

	/**
	 * An {@link Executor} that runs tasks on the server main thread. Use it to
	 * get back from an async query to the Bukkit API:
	 *
	 * <pre>
	 * db.queryAsync("SELECT * FROM homes WHERE owner = ?", uuid)
	 *   .thenAcceptAsync(rows -&gt; player.sendMessage("You have " + rows.size() + " homes"),
	 *           SQLiteLib.mainThread(this));
	 * </pre>
	 */
	public static Executor mainThread(Plugin plugin) {
		return task -> {
			if (Bukkit.isPrimaryThread()) task.run();
			else Bukkit.getScheduler().runTask(plugin, task);
		};
	}

	// ------------------------------------------------------------------
	// Database registry
	// ------------------------------------------------------------------

	/**
	 * Open (creating if needed) a database stored in your plugin's folder as
	 * {@code plugins/<YourPlugin>/<name>.db}. Calling it again with the same
	 * name returns the same instance. Registered as {@code <YourPlugin>:<name>}.
	 *
	 * @param plugin your plugin.
	 * @param name   database name (file name without {@code .db}).
	 * @return the open database.
	 */
	public Database open(Plugin plugin, String name) {
		return open(plugin, name, null);
	}

	/**
	 * Same as {@link #open(Plugin, String)}, also running
	 * {@code createStatement} (may contain several statements). For schemas
	 * that change between versions use {@link Database#migrate(String...)}.
	 */
	public Database open(Plugin plugin, String name, String createStatement) {
		String key = key(plugin, name);
		Database db = databases.computeIfAbsent(key, k -> {
			SQLite created = new SQLite(name, null, plugin.getDataFolder(), plugin.getLogger());
			created.load();
			owners.put(k, plugin.getName());
			return created;
		});
		if (createStatement != null && !createStatement.isBlank()) db.executeScript(createStatement);
		return db;
	}

	/**
	 * @return a database previously opened with {@link #open(Plugin, String)},
	 *         or {@code null}.
	 */
	public Database getDatabase(Plugin plugin, String name) {
		return databases.get(key(plugin, name));
	}

	/**
	 * Create and load a new database in SQLiteLib's own folder, registered
	 * under {@code databaseName}. Re-initializing an existing name replaces
	 * (and closes) the previous instance.
	 *
	 * @param databaseName    Database name.
	 * @param createStatement Statement(s) run on load, usually used to create
	 *                        tables.
	 */
	public void initializeDatabase(String databaseName, String createStatement) {
		register(databaseName, new SQLite(databaseName, createStatement, getDataFolder(), getLogger()), this);
	}

	/**
	 * Create and load a new database within a different plugin's folder,
	 * registered under {@code databaseName}.
	 *
	 * @param plugin          Plugin to create database file inside.
	 * @param databaseName    Database name.
	 * @param createStatement Statement(s) run on load, usually used to create
	 *                        tables.
	 * @see #open(Plugin, String, String)
	 */
	public void initializeDatabase(Plugin plugin, String databaseName, String createStatement) {
		register(databaseName, new SQLite(databaseName, createStatement, plugin.getDataFolder(), plugin.getLogger()), plugin);
	}

	/**
	 * Get every loaded database, keyed by registration name.
	 */
	public Map<String, Database> getDatabases() {
		return databases;
	}

	/**
	 * Gets a loaded database by its registration name: the name given to
	 * {@code initializeDatabase}, or {@code <Plugin>:<name>} for
	 * {@link #open(Plugin, String)}.
	 *
	 * @return The database, or {@code null}.
	 */
	public Database getDatabase(String databaseName) {
		return databases.get(databaseName);
	}

	/** @return the name of the plugin that registered the database, or {@code null}. */
	public String getOwner(String databaseName) {
		return owners.get(databaseName);
	}

	/**
	 * Close and unregister a database.
	 *
	 * @return whether a database was registered under that name.
	 */
	public boolean closeDatabase(String databaseName) {
		Database db = databases.remove(databaseName);
		owners.remove(databaseName);
		if (db == null) return false;
		db.close();
		return true;
	}

	/**
	 * Close and unregister every database registered by {@code plugin}. Call
	 * this at the end of your {@code onDisable} if your plugin can be reloaded
	 * on its own.
	 */
	public void closeDatabases(Plugin plugin) {
		List<String> keys = new ArrayList<>();
		owners.forEach((key, owner) -> {
			if (owner.equals(plugin.getName())) keys.add(key);
		});
		keys.forEach(this::closeDatabase);
	}

	private void register(String key, Database db, Plugin owner) {
		Database previous = databases.remove(key);
		if (previous != null) previous.close();
		db.load();
		databases.put(key, db);
		owners.put(key, owner.getName());
	}

	private static String key(Plugin plugin, String name) {
		return plugin.getName() + ":" + name;
	}
}
