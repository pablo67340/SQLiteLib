package com.pablo67340.SQLiteLib.Main;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import com.pablo67340.SQLiteLib.Database.Database;
import com.pablo67340.SQLiteLib.Database.DatabaseException;
import com.pablo67340.SQLiteLib.Database.Row;

/**
 * Handles {@code /sqlite} (alias {@code /sl}). All database work runs on the
 * database's worker thread; replies are sent from the main thread.
 */
final class SQLiteCommand implements TabExecutor {

	private static final String READ = "sqlite.command.read";
	private static final String WRITE = "sqlite.command.write";
	private static final String ADMIN = "sqlite.command.admin";

	private static final int MAX_ROWS = 10;
	private static final int MAX_CELL = 24;

	private enum Sub {
		HELP(null, "", "Show this help"),
		LIST(READ, "", "List loaded databases"),
		INFO(READ, "<db>", "File, size, schema version and tables"),
		QUERY(READ, "<db> <sql>", "Run a read-only query and show the rows"),
		QUERYVALUE(READ, "<db> <column> <sql>", "Show one value"),
		QUERYROW(READ, "<db> <column> <sql>", "Show a column for every row"),
		EXECUTE(WRITE, "<db> <sql>", "Run any statement"),
		INIT(ADMIN, "<db> [sql]", "Create/load a database in SQLiteLib's folder"),
		BACKUP(ADMIN, "<db>", "Write a live backup to SQLiteLib/backups"),
		VACUUM(ADMIN, "<db>", "Compact the database file");

		final String permission;
		final String usage;
		final String description;

		Sub(String permission, String usage, String description) {
			this.permission = permission;
			this.usage = usage;
			this.description = description;
		}

		String label() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Sub find(String name) {
			for (Sub sub : values())
				if (sub.name().equalsIgnoreCase(name)) return sub;
			return null;
		}
	}

	private final SQLiteLib plugin;

	SQLiteCommand(SQLiteLib plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		Sub sub = args.length == 0 ? Sub.HELP : Sub.find(args[0]);
		if (sub == null || sub == Sub.HELP) {
			help(sender, label);
			return true;
		}
		if (sub.permission != null && !sender.hasPermission(sub.permission)) {
			error(sender, "You don't have permission to do that.");
			return true;
		}

		switch (sub) {
		case LIST:
			list(sender);
			return true;
		case INIT:
			if (args.length < 2) return usage(sender, label, sub);
			init(sender, args[1], join(args, 2));
			return true;
		default:
			break;
		}

		int needed = sub == Sub.INFO || sub == Sub.BACKUP || sub == Sub.VACUUM ? 2
				: sub == Sub.QUERYVALUE || sub == Sub.QUERYROW ? 4 : 3;
		if (args.length < needed) return usage(sender, label, sub);

		String key = resolve(args[1]);
		Database db = key == null ? null : plugin.getDatabase(key);
		if (db == null) {
			error(sender, "Unknown database '" + args[1] + "'. Use /" + label + " list.");
			return true;
		}

		switch (sub) {
		case INFO:
			run(sender, db, d -> info(key, d));
			break;
		case QUERY: {
			String sql = join(args, 2);
			run(sender, db, d -> table(d.readOnly(tx -> tx.query(sql))));
			break;
		}
		case QUERYVALUE: {
			String column = args[2], sql = join(args, 3);
			run(sender, db, d -> Collections.singletonList(d.readOnly(tx -> tx.queryFirst(sql))
					.map(row -> String.valueOf(row.getObject(column))).orElse(ChatColor.GRAY + "(no rows)")));
			break;
		}
		case QUERYROW: {
			String column = args[2], sql = join(args, 3);
			run(sender, db, d -> {
				List<String> lines = new ArrayList<>();
				for (Row row : d.readOnly(tx -> tx.query(sql)))
					lines.add(String.valueOf(row.getObject(column)));
				if (lines.isEmpty()) lines.add(ChatColor.GRAY + "(no rows)");
				return lines;
			});
			break;
		}
		case EXECUTE: {
			String sql = join(args, 2);
			run(sender, db, d -> Collections.singletonList(ChatColor.GREEN + "OK, " + d.update(sql) + " row(s) changed."));
			break;
		}
		case BACKUP: {
			String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
			File target = new File(new File(plugin.getDataFolder(), "backups"), key.replace(':', '_') + "-" + stamp + ".db");
			run(sender, db, d -> {
				d.backup(target);
				return Collections.singletonList(ChatColor.GREEN + "Backup written to " + target.getPath()
						+ " (" + size(target.length()) + ")");
			});
			break;
		}
		case VACUUM:
			run(sender, db, d -> {
				long before = fileSize(d);
				d.vacuum();
				return Collections.singletonList(ChatColor.GREEN + "Vacuumed: " + size(before) + " -> " + size(fileSize(d)));
			});
			break;
		default:
			help(sender, label);
		}
		return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			List<String> subs = new ArrayList<>();
			for (Sub sub : Sub.values())
				if (sub.permission == null || sender.hasPermission(sub.permission)) subs.add(sub.label());
			return filter(subs, args[0]);
		}
		if (args.length == 2) {
			Sub sub = Sub.find(args[0]);
			if (sub == null || sub == Sub.HELP || sub == Sub.LIST) return Collections.emptyList();
			if (sub.permission != null && !sender.hasPermission(sub.permission)) return Collections.emptyList();
			return filter(new ArrayList<>(plugin.getDatabases().keySet()), args[1]);
		}
		if (args.length == 3) {
			Sub sub = Sub.find(args[0]);
			if (sub == Sub.QUERY) return filter(Arrays.asList("SELECT", "PRAGMA"), args[2]);
			if (sub == Sub.EXECUTE) return filter(Arrays.asList("INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP"), args[2]);
		}
		return Collections.emptyList();
	}

	// ------------------------------------------------------------------

	private void help(CommandSender sender, String label) {
		sender.sendMessage(ChatColor.GOLD + "SQLiteLib " + plugin.getDescription().getVersion() + ChatColor.GRAY
				+ " - " + plugin.getDatabases().size() + " database(s) loaded");
		for (Sub sub : Sub.values()) {
			if (sub.permission != null && !sender.hasPermission(sub.permission)) continue;
			sender.sendMessage(ChatColor.YELLOW + "/" + label + " " + sub.label()
					+ (sub.usage.isEmpty() ? "" : " " + sub.usage) + ChatColor.GRAY + " - " + sub.description);
		}
	}

	private boolean usage(CommandSender sender, String label, Sub sub) {
		error(sender, "Usage: /" + label + " " + sub.label() + " " + sub.usage);
		return true;
	}

	private void list(CommandSender sender) {
		Map<String, Database> dbs = plugin.getDatabases();
		if (dbs.isEmpty()) {
			sender.sendMessage(ChatColor.GRAY + "No databases are loaded.");
			return;
		}
		sender.sendMessage(ChatColor.GOLD + "Loaded databases (" + dbs.size() + "):");
		List<String> keys = new ArrayList<>(dbs.keySet());
		Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
		for (String key : keys) {
			Database db = dbs.get(key);
			sender.sendMessage(ChatColor.YELLOW + key + ChatColor.GRAY + " - " + size(fileSize(db))
					+ ", owner " + plugin.getOwner(key) + (db.isOpen() ? "" : ChatColor.RED + " (closed)"));
		}
	}

	private void init(CommandSender sender, String name, String sql) {
		try {
			plugin.initializeDatabase(name, sql.isEmpty() ? null : sql);
			sender.sendMessage(ChatColor.GREEN + "Database '" + name + "' initialized.");
		} catch (IllegalArgumentException | DatabaseException ex) {
			error(sender, ex.getMessage());
		}
	}

	private List<String> info(String key, Database db) {
		List<String> lines = new ArrayList<>();
		lines.add(ChatColor.GOLD + "Database " + key + ChatColor.GRAY + " (owner " + plugin.getOwner(key) + ")");
		if (db.getFile() != null) lines.add(ChatColor.YELLOW + "File: " + ChatColor.WHITE + db.getFile().getPath());
		lines.add(ChatColor.YELLOW + "Size: " + ChatColor.WHITE + size(fileSize(db)));
		lines.add(ChatColor.YELLOW + "Schema version: " + ChatColor.WHITE + db.getSchemaVersion());
		lines.add(ChatColor.YELLOW + "Journal mode: " + ChatColor.WHITE
				+ db.queryScalar("PRAGMA journal_mode", String.class).orElse("?"));
		List<String> tables = db.getTables();
		lines.add(ChatColor.YELLOW + "Tables (" + tables.size() + "):");
		for (String table : tables) {
			long rows = db.queryScalar("SELECT COUNT(*) FROM \"" + table.replace("\"", "\"\"") + "\"", Long.class).orElse(0L);
			lines.add(ChatColor.GRAY + " - " + ChatColor.WHITE + table + ChatColor.GRAY + " (" + rows + " rows)");
		}
		return lines;
	}

	private static List<String> table(List<Row> rows) {
		List<String> lines = new ArrayList<>();
		if (rows.isEmpty()) {
			lines.add(ChatColor.GRAY + "(no rows)");
			return lines;
		}
		lines.add(ChatColor.GOLD + String.join(ChatColor.GRAY + " | " + ChatColor.GOLD, rows.get(0).getColumns()));
		for (Row row : rows.subList(0, Math.min(rows.size(), MAX_ROWS))) {
			List<String> cells = new ArrayList<>();
			for (Object value : row.asMap().values())
				cells.add(cell(value));
			lines.add(ChatColor.WHITE + String.join(ChatColor.GRAY + " | " + ChatColor.WHITE, cells));
		}
		if (rows.size() > MAX_ROWS) lines.add(ChatColor.GRAY + "... and " + (rows.size() - MAX_ROWS) + " more row(s)");
		else lines.add(ChatColor.GRAY + "" + rows.size() + " row(s)");
		return lines;
	}

	private static String cell(Object value) {
		if (value == null) return ChatColor.DARK_GRAY + "NULL" + ChatColor.WHITE;
		if (value instanceof byte[]) return ChatColor.DARK_GRAY + "<blob " + ((byte[]) value).length + " bytes>" + ChatColor.WHITE;
		String text = value.toString();
		return text.length() > MAX_CELL ? text.substring(0, MAX_CELL - 1) + "\u2026" : text;
	}

	private void run(CommandSender sender, Database db, Function<Database, List<String>> work) {
		db.supplyAsync(work).whenCompleteAsync((lines, error) -> {
			if (error == null) {
				lines.forEach(sender::sendMessage);
				return;
			}
			Throwable cause = error.getCause() != null ? error.getCause() : error;
			error(sender, cause.getMessage());
		}, SQLiteLib.mainThread(plugin));
	}

	/** Exact key, then case-insensitive key, then a unique {@code Plugin:name} suffix match. */
	private String resolve(String input) {
		Map<String, Database> dbs = plugin.getDatabases();
		if (dbs.containsKey(input)) return input;
		String match = null;
		for (String key : dbs.keySet())
			if (key.equalsIgnoreCase(input)) return key;
		for (String key : dbs.keySet()) {
			if (key.toLowerCase(Locale.ROOT).endsWith(":" + input.toLowerCase(Locale.ROOT))) {
				if (match != null) return null;
				match = key;
			}
		}
		return match;
	}

	private static long fileSize(Database db) {
		File file = db.getFile();
		if (file == null) return 0;
		File wal = new File(file.getPath() + "-wal");
		return file.length() + (wal.exists() ? wal.length() : 0);
	}

	private static String size(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
	}

	private static void error(CommandSender sender, String message) {
		sender.sendMessage(ChatColor.RED + message);
	}

	private static String join(String[] args, int from) {
		return from >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, from, args.length));
	}

	private static List<String> filter(List<String> options, String prefix) {
		List<String> result = new ArrayList<>();
		for (String option : options)
			if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) result.add(option);
		return result;
	}
}
