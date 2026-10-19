package com.pablo67340.SQLiteLib.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * A database connection with a small, safe API on top of JDBC.
 * <p>
 * Every call is thread-safe: a single connection is shared and access to it is
 * serialized, which is exactly how SQLite wants to be used. Blocking calls run
 * on the calling thread; the {@code *Async} variants run on a dedicated worker
 * thread per database so the Minecraft main thread never waits on disk I/O.
 * <p>
 * Always pass user-supplied values as {@code ?} parameters rather than
 * concatenating them into SQL:
 *
 * <pre>
 * db.update("INSERT INTO homes (owner, name, x, y, z) VALUES (?, ?, ?, ?, ?)",
 *         player.getUniqueId(), "base", loc.getX(), loc.getY(), loc.getZ());
 * </pre>
 *
 * Supported parameter types are everything JDBC accepts plus {@link java.util.UUID},
 * enums (stored by name) and booleans (stored as 0/1).
 */
public abstract class Database implements AutoCloseable {

	private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	protected Connection connection;
	protected Logger logger = Logger.getLogger("SQLiteLib");

	private final ReentrantLock lock = new ReentrantLock();
	private final Object executorLock = new Object();
	private final Map<String, KeyValueStore> keyValueStores = new ConcurrentHashMap<>();
	private ExecutorService executor;
	private volatile Thread workerThread;

	public abstract Connection getSQLConnection();

	public abstract void load();

	/** @return a human readable name for this database, used in logs and threads. */
	public String getName() {
		return getClass().getSimpleName();
	}

	/** @return the database file, or {@code null} if it is not file backed. */
	public File getFile() {
		return null;
	}

	public Logger getLogger() {
		return logger;
	}

	public void setLogger(Logger logger) {
		this.logger = logger == null ? Logger.getLogger("SQLiteLib") : logger;
	}

	/** Open the connection if it isn't already. */
	public void initialize() {
		connection = getSQLConnection();
	}

	/** @return whether the underlying connection is currently open. */
	public boolean isOpen() {
		try {
			return connection != null && !connection.isClosed();
		} catch (SQLException ex) {
			return false;
		}
	}

	// ------------------------------------------------------------------
	// Queries
	// ------------------------------------------------------------------

	/**
	 * Run a query and return every row.
	 *
	 * @param sql    SQL with {@code ?} placeholders.
	 * @param params values for the placeholders, in order.
	 * @return all rows; empty if nothing matched.
	 * @throws DatabaseException if the query fails.
	 */
	public List<Row> query(String sql, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params); ResultSet rs = ps.executeQuery()) {
				List<Row> rows = new ArrayList<>();
				while (rs.next())
					rows.add(Row.from(rs));
				return rows;
			}
		});
	}

	/**
	 * Run a query and map each row to your own type.
	 *
	 * @throws DatabaseException if the query fails.
	 */
	public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
		List<Row> rows = query(sql, params);
		List<T> mapped = new ArrayList<>(rows.size());
		for (Row row : rows)
			mapped.add(mapper.map(row));
		return mapped;
	}

	/**
	 * @return the first row of the result, if any.
	 * @throws DatabaseException if the query fails.
	 */
	public Optional<Row> queryFirst(String sql, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params); ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(Row.from(rs)) : Optional.empty();
			}
		});
	}

	/**
	 * @return the first row of the result mapped to your type, if any.
	 * @throws DatabaseException if the query fails.
	 */
	public <T> Optional<T> queryFirst(String sql, RowMapper<T> mapper, Object... params) {
		return queryFirst(sql, params).map(mapper::map);
	}

	/**
	 * Read a single value: the first column of the first row.
	 *
	 * <pre>
	 * long homes = db.queryScalar("SELECT COUNT(*) FROM homes WHERE owner = ?", Long.class, uuid).orElse(0L);
	 * </pre>
	 *
	 * @return the value, or empty if there were no rows or it was NULL.
	 * @throws DatabaseException if the query fails.
	 */
	public <T> Optional<T> queryScalar(String sql, Class<T> type, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params); ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.ofNullable(Values.convert(rs.getObject(1), type)) : Optional.empty();
			}
		});
	}

	/**
	 * Read the first column of every row.
	 *
	 * @throws DatabaseException if the query fails.
	 */
	public <T> List<T> queryColumn(String sql, Class<T> type, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params); ResultSet rs = ps.executeQuery()) {
				List<T> values = new ArrayList<>();
				while (rs.next())
					values.add(Values.convert(rs.getObject(1), type));
				return values;
			}
		});
	}

	/**
	 * @return whether the query returns at least one row.
	 * @throws DatabaseException if the query fails.
	 */
	public boolean exists(String sql, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params); ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		});
	}

	// ------------------------------------------------------------------
	// Updates
	// ------------------------------------------------------------------

	/**
	 * Run an INSERT, UPDATE, DELETE or DDL statement.
	 *
	 * @return the number of rows changed.
	 * @throws DatabaseException if the statement fails.
	 */
	public int update(String sql, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params)) {
				return ps.executeUpdate();
			}
		});
	}

	/**
	 * Run an INSERT and return the row id of the inserted row (the value of an
	 * {@code INTEGER PRIMARY KEY} column).
	 *
	 * @throws DatabaseException if the statement fails.
	 */
	public long insert(String sql, Object... params) {
		return withConnection(sql, conn -> {
			try (PreparedStatement ps = prepare(conn, sql, params)) {
				ps.executeUpdate();
			}
			try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
				return rs.next() ? rs.getLong(1) : -1L;
			}
		});
	}

	/**
	 * Run the same statement once for each parameter set, inside a single
	 * transaction. Orders of magnitude faster than calling {@link #update} in a
	 * loop.
	 *
	 * <pre>
	 * List&lt;Object[]&gt; rows = new ArrayList&lt;&gt;();
	 * for (Player p : Bukkit.getOnlinePlayers())
	 *     rows.add(new Object[] { p.getUniqueId(), p.getName() });
	 * db.batch("INSERT OR REPLACE INTO seen (uuid, name) VALUES (?, ?)", rows);
	 * </pre>
	 *
	 * @return the update count for each parameter set.
	 * @throws DatabaseException if any statement fails; nothing is committed.
	 */
	public int[] batch(String sql, Collection<Object[]> paramSets) {
		return transactionResult(db -> {
			try (PreparedStatement ps = connection().prepareStatement(sql)) {
				for (Object[] params : paramSets) {
					bind(ps, params);
					ps.addBatch();
				}
				return ps.executeBatch();
			}
		});
	}

	/**
	 * Run one or more statements separated by {@code ;}. Parameters are not
	 * supported; use this for schema scripts.
	 *
	 * @throws DatabaseException if any statement fails.
	 */
	public void executeScript(String sql) {
		withConnection(sql, conn -> {
			try (Statement st = conn.createStatement()) {
				st.executeUpdate(sql);
			}
			return null;
		});
	}

	// ------------------------------------------------------------------
	// Transactions
	// ------------------------------------------------------------------

	/**
	 * Run several operations atomically: either all of them are committed or,
	 * if anything throws, none are. Nested calls join the outer transaction.
	 *
	 * <pre>
	 * db.transaction(tx -&gt; {
	 *     tx.update("UPDATE balances SET coins = coins - ? WHERE uuid = ?", 100, from);
	 *     tx.update("UPDATE balances SET coins = coins + ? WHERE uuid = ?", 100, to);
	 * });
	 * </pre>
	 *
	 * @throws DatabaseException if the work or the commit fails.
	 */
	public void transaction(SqlConsumer<Database> work) {
		transactionResult(db -> {
			work.accept(db);
			return null;
		});
	}

	/**
	 * Same as {@link #transaction(SqlConsumer)} but returns a value.
	 *
	 * @throws DatabaseException if the work or the commit fails.
	 */
	public <T> T transactionResult(SqlFunction<Database, T> work) {
		lock.lock();
		try {
			Connection conn = connection();
			if (!conn.getAutoCommit()) return work.apply(this);

			conn.setAutoCommit(false);
			try {
				T result = work.apply(this);
				conn.commit();
				return result;
			} catch (Throwable ex) {
				try {
					conn.rollback();
				} catch (SQLException rollbackEx) {
					ex.addSuppressed(rollbackEx);
				}
				throw ex;
			} finally {
				conn.setAutoCommit(true);
			}
		} catch (SQLException ex) {
			throw new DatabaseException("Transaction failed on database " + getName() + ": " + ex.getMessage(), ex);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Run work with the database in read-only mode: any attempt to modify it
	 * fails. Handy for running untrusted or admin-supplied queries.
	 *
	 * @throws DatabaseException if the work fails or tries to write.
	 */
	public <T> T readOnly(SqlFunction<Database, T> work) {
		lock.lock();
		try {
			executeScript("PRAGMA query_only = ON");
			try {
				return transactionResult(work);
			} finally {
				executeScript("PRAGMA query_only = OFF");
			}
		} finally {
			lock.unlock();
		}
	}

	// ------------------------------------------------------------------
	// Schema
	// ------------------------------------------------------------------

	/**
	 * Bring the schema up to date. Each argument is one version step and may
	 * contain several statements. Steps already applied (tracked with SQLite's
	 * {@code user_version}) are skipped, so call this every time your plugin
	 * enables and only ever append new steps.
	 *
	 * <pre>
	 * db.migrate(
	 *     "CREATE TABLE homes (owner TEXT NOT NULL, name TEXT NOT NULL, x REAL, y REAL, z REAL, PRIMARY KEY (owner, name))",
	 *     "ALTER TABLE homes ADD COLUMN world TEXT NOT NULL DEFAULT 'world'"
	 * );
	 * </pre>
	 *
	 * @return the number of steps that were applied now.
	 * @throws DatabaseException if a step fails; that step is rolled back.
	 */
	public int migrate(String... steps) {
		lock.lock();
		try {
			int current = getSchemaVersion();
			if (current > steps.length) {
				logger.warning("Database " + getName() + " is at schema version " + current
						+ " but only " + steps.length + " migrations are known. Was the plugin downgraded?");
			}
			int applied = 0;
			for (int version = current; version < steps.length; version++) {
				String step = steps[version];
				int target = version + 1;
				transaction(db -> {
					db.executeScript(step);
					db.executeScript("PRAGMA user_version = " + target);
				});
				applied++;
			}
			return applied;
		} finally {
			lock.unlock();
		}
	}

	/** @return the schema version stored in SQLite's {@code user_version}. */
	public int getSchemaVersion() {
		return queryScalar("PRAGMA user_version", Integer.class).orElse(0);
	}

	/** @return the names of all user tables. */
	public List<String> getTables() {
		return queryColumn("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
				String.class);
	}

	public boolean tableExists(String table) {
		return exists("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", table);
	}

	public boolean columnExists(String table, String column) {
		for (Row row : query("PRAGMA table_info(" + quote(table) + ")"))
			if (column.equalsIgnoreCase(row.getString("name"))) return true;
		return false;
	}

	// ------------------------------------------------------------------
	// Key/value storage
	// ------------------------------------------------------------------

	/**
	 * Get a simple persistent key/value store backed by the given table. The
	 * table is created if needed. Great for replacing small YAML files.
	 *
	 * <pre>
	 * KeyValueStore coins = db.keyValue("coins");
	 * coins.increment(player.getUniqueId().toString(), 50);
	 * long balance = coins.getLong(player.getUniqueId().toString(), 0);
	 * </pre>
	 *
	 * @param table table name (letters, digits and underscores).
	 */
	public KeyValueStore keyValue(String table) {
		requireIdentifier(table);
		return keyValueStores.computeIfAbsent(table.toLowerCase(), t -> new KeyValueStore(this, table));
	}

	// ------------------------------------------------------------------
	// Maintenance
	// ------------------------------------------------------------------

	/**
	 * Write a consistent copy of the database to {@code target} while it stays
	 * online. The target file must not already exist.
	 *
	 * @throws DatabaseException if the backup fails.
	 */
	public void backup(File target) {
		if (target.exists()) throw new DatabaseException("Backup target already exists: " + target);
		File parent = target.getAbsoluteFile().getParentFile();
		if (parent != null) parent.mkdirs();
		update("VACUUM INTO ?", target.getAbsolutePath());
	}

	/** Rebuild the database file, reclaiming unused space. */
	public void vacuum() {
		executeScript("VACUUM");
	}

	// ------------------------------------------------------------------
	// Async
	// ------------------------------------------------------------------

	/**
	 * Run any work against this database on its worker thread.
	 *
	 * <pre>
	 * db.supplyAsync(d -&gt; d.queryFirst("SELECT * FROM homes WHERE owner = ?", uuid))
	 *   .thenAcceptAsync(home -&gt; player.sendMessage(...), SQLiteLib.mainThread(plugin));
	 * </pre>
	 *
	 * Failures complete the future exceptionally and are logged.
	 */
	public <T> CompletableFuture<T> supplyAsync(Function<Database, T> task) {
		CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> task.apply(this), executor());
		future.whenComplete((result, error) -> {
			if (error != null)
				logger.log(Level.SEVERE, "Async task failed on database " + getName(), unwrap(error));
		});
		return future;
	}

	/** Run work against this database on its worker thread. */
	public CompletableFuture<Void> runAsync(Consumer<Database> task) {
		return supplyAsync(db -> {
			task.accept(db);
			return null;
		});
	}

	/** Async version of {@link #query(String, Object...)}. */
	public CompletableFuture<List<Row>> queryAsync(String sql, Object... params) {
		return supplyAsync(db -> db.query(sql, params));
	}

	/** Async version of {@link #update(String, Object...)}. */
	public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
		return supplyAsync(db -> db.update(sql, params));
	}

	// ------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------

	/**
	 * Finish any queued async work and close the connection. Using the database
	 * again afterwards transparently reopens it.
	 */
	@Override
	public void close() {
		ExecutorService pending;
		synchronized (executorLock) {
			pending = executor;
			executor = null;
		}
		if (pending != null) {
			pending.shutdown();
			if (Thread.currentThread() != workerThread) {
				try {
					if (!pending.awaitTermination(30, TimeUnit.SECONDS))
						logger.warning("Timed out waiting for async tasks on database " + getName());
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
		}

		lock.lock();
		try {
			if (connection == null || connection.isClosed()) return;
			try (Statement st = connection.createStatement()) {
				st.execute("PRAGMA optimize");
			} catch (SQLException ignored) {
			}
			connection.close();
		} catch (SQLException ex) {
			logger.log(Level.SEVERE, "Failed to close database " + getName(), ex);
		} finally {
			connection = null;
			lock.unlock();
		}
	}

	/**
	 * Close the connection to the database. It will reopen automatically the
	 * next time it is used, or you can call {@link #load()}.
	 */
	public void closeConnection() {
		close();
	}

	// ------------------------------------------------------------------
	// Legacy API (1.x) - kept so existing plugins keep working
	// ------------------------------------------------------------------

	/**
	 * Execute any single statement.
	 *
	 * @param statement The statement to execute.
	 * @return {@code true} if it ran successfully, {@code false} if it failed
	 *         (the error is logged).
	 */
	public Boolean executeStatement(String statement) {
		try {
			withConnection(statement, conn -> {
				try (Statement st = conn.createStatement()) {
					st.execute(statement);
				}
				return null;
			});
			return true;
		} catch (DatabaseException ex) {
			logger.log(Level.SEVERE, ex.getMessage(), ex.getCause());
			return false;
		}
	}

	/**
	 * Get a single value from the first row of the result.
	 *
	 * @param statement The statement to execute.
	 * @param row       The column to read.
	 * @return the value, or {@code null} if there were no rows or it failed.
	 * @deprecated Use {@link #queryScalar(String, Class, Object...)} or
	 *             {@link #queryFirst(String, Object...)}, which support
	 *             parameters and typed results.
	 */
	@Deprecated
	public Object queryValue(String statement, String row) {
		try {
			return queryFirst(statement).map(r -> r.getObject(row)).orElse(null);
		} catch (DatabaseException ex) {
			logger.log(Level.SEVERE, ex.getMessage(), ex.getCause());
			return null;
		}
	}

	/**
	 * Get the value of one column for every row of the result.
	 *
	 * @param statement The statement to execute.
	 * @param row       The column to read.
	 * @return the values, or {@code null} if it failed.
	 * @deprecated Use {@link #queryColumn(String, Class, Object...)} or
	 *             {@link #query(String, Object...)}.
	 */
	@Deprecated
	public List<Object> queryRow(String statement, String row) {
		try {
			List<Object> values = new ArrayList<>();
			for (Row r : query(statement))
				values.add(r.getObject(row));
			return values;
		} catch (DatabaseException ex) {
			logger.log(Level.SEVERE, ex.getMessage(), ex.getCause());
			return null;
		}
	}

	/**
	 * Get a list of values for each requested column.
	 *
	 * @param statement The statement to execute.
	 * @param row       The column(s) to read.
	 * @return {@code column -> values}, or {@code null} if it failed.
	 * @deprecated Use {@link #query(String, Object...)}, which returns whole rows.
	 */
	@Deprecated
	public Map<String, List<Object>> queryMultipleRows(String statement, String... row) {
		try {
			Map<String, List<Object>> map = new LinkedHashMap<>();
			for (String column : row)
				map.put(column, new ArrayList<>());
			for (Row r : query(statement))
				for (String column : row)
					map.get(column).add(r.getObject(column));
			return map;
		} catch (DatabaseException ex) {
			logger.log(Level.SEVERE, ex.getMessage(), ex.getCause());
			return null;
		}
	}

	/**
	 * Close a statement and result set, ignoring nulls.
	 */
	public void close(PreparedStatement ps, ResultSet rs) {
		try {
			if (rs != null) rs.close();
			if (ps != null) ps.close();
		} catch (SQLException ex) {
			logger.log(Level.SEVERE, "Failed to close statement", ex);
		}
	}

	// ------------------------------------------------------------------
	// Internals
	// ------------------------------------------------------------------

	/** @return the open connection, opening it if needed. */
	protected Connection connection() {
		Connection conn = getSQLConnection();
		if (conn == null) throw new DatabaseException("No connection available for database " + getName());
		return conn;
	}

	private <T> T withConnection(String sql, SqlFunction<Connection, T> work) {
		lock.lock();
		try {
			return work.apply(connection());
		} catch (SQLException ex) {
			throw new DatabaseException("Failed to execute on database " + getName() + ": " + sql + " (" + ex.getMessage() + ")", ex);
		} finally {
			lock.unlock();
		}
	}

	private static PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
		PreparedStatement ps = conn.prepareStatement(sql);
		try {
			bind(ps, params);
			return ps;
		} catch (SQLException ex) {
			ps.close();
			throw ex;
		}
	}

	private static void bind(PreparedStatement ps, Object... params) throws SQLException {
		if (params == null) return;
		for (int i = 0; i < params.length; i++)
			ps.setObject(i + 1, Values.toSql(params[i]));
	}

	private ExecutorService executor() {
		synchronized (executorLock) {
			if (executor == null) {
				executor = Executors.newSingleThreadExecutor(runnable -> {
					Thread thread = new Thread(runnable, "SQLiteLib-" + getName());
					thread.setDaemon(true);
					workerThread = thread;
					return thread;
				});
			}
			return executor;
		}
	}

	private static Throwable unwrap(Throwable error) {
		return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
	}

	static void requireIdentifier(String name) {
		if (name == null || !IDENTIFIER.matcher(name).matches())
			throw new IllegalArgumentException("Invalid table name '" + name + "': use letters, digits and underscores only");
	}

	static String quote(String identifier) {
		return '"' + identifier.replace("\"", "\"\"") + '"';
	}
}
