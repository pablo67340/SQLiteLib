package com.pablo67340.SQLiteLib.Database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

	enum Rank { MEMBER, ADMIN }

	@TempDir
	File dir;

	SQLite db;

	@BeforeEach
	void setUp() {
		db = SQLite.open(new File(dir, "test.db"));
		db.migrate("CREATE TABLE players (id INTEGER PRIMARY KEY, uuid TEXT UNIQUE NOT NULL, name TEXT, "
				+ "coins INTEGER NOT NULL DEFAULT 0, vip BOOLEAN NOT NULL DEFAULT 0, rank TEXT)");
	}

	@AfterEach
	void tearDown() {
		db.close();
	}

	@Test
	void insertQueryAndTypedGetters() {
		UUID uuid = UUID.randomUUID();
		long id = db.insert("INSERT INTO players (uuid, name, coins, vip, rank) VALUES (?, ?, ?, ?, ?)",
				uuid, "Notch", 100, true, Rank.ADMIN);
		assertEquals(1, id);
		assertEquals(2, db.insert("INSERT INTO players (uuid, name) VALUES (?, ?)", UUID.randomUUID(), "jeb_"));

		Row row = db.queryFirst("SELECT * FROM players WHERE uuid = ?", uuid).orElseThrow();
		assertEquals(uuid, row.getUUID("uuid"));
		assertEquals("Notch", row.getString("NAME"));
		assertEquals(100, row.getInt("coins"));
		assertEquals(100L, row.getLong("coins"));
		assertTrue(row.getBoolean("vip"));
		assertEquals(Rank.ADMIN, row.getEnum("rank", Rank.class));

		List<String> names = db.query("SELECT name FROM players ORDER BY id", r -> r.getString("name"));
		assertEquals(Arrays.asList("Notch", "jeb_"), names);
		assertEquals(Arrays.asList("Notch", "jeb_"), db.queryColumn("SELECT name FROM players ORDER BY id", String.class));
		assertEquals(2L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());
		assertTrue(db.exists("SELECT 1 FROM players WHERE name = ?", "jeb_"));
		assertFalse(db.queryFirst("SELECT * FROM players WHERE name = ?", "Herobrine").isPresent());
	}

	@Test
	void parametersPreventInjection() {
		db.update("INSERT INTO players (uuid, name) VALUES (?, ?)", "u1", "Robert'); DROP TABLE players;--");
		assertTrue(db.tableExists("players"));
		assertEquals("Robert'); DROP TABLE players;--", db.queryScalar("SELECT name FROM players", String.class).orElseThrow());
	}

	@Test
	void transactionRollsBackOnFailure() {
		assertThrows(DatabaseException.class, () -> db.transaction(tx -> {
			tx.update("INSERT INTO players (uuid) VALUES (?)", "a");
			tx.update("INSERT INTO players (uuid) VALUES (?)", "a");
		}));
		assertEquals(0L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());

		db.transaction(tx -> {
			tx.update("INSERT INTO players (uuid) VALUES (?)", "a");
			tx.transaction(inner -> inner.update("INSERT INTO players (uuid) VALUES (?)", "b"));
		});
		assertEquals(2L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());
	}

	@Test
	void batchInsert() {
		List<Object[]> rows = new ArrayList<>();
		for (int i = 0; i < 500; i++)
			rows.add(new Object[] { "uuid-" + i, "player" + i });
		int[] counts = db.batch("INSERT INTO players (uuid, name) VALUES (?, ?)", rows);
		assertEquals(500, counts.length);
		assertEquals(500L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());
	}

	@Test
	void migrationsOnlyRunOnce() {
		assertEquals(1, db.getSchemaVersion());
		String[] steps = {
				"CREATE TABLE players (id INTEGER PRIMARY KEY, uuid TEXT UNIQUE NOT NULL, name TEXT, "
						+ "coins INTEGER NOT NULL DEFAULT 0, vip BOOLEAN NOT NULL DEFAULT 0, rank TEXT)",
				"ALTER TABLE players ADD COLUMN last_seen INTEGER; CREATE INDEX idx_name ON players (name);" };
		assertEquals(1, db.migrate(steps));
		assertEquals(0, db.migrate(steps));
		assertEquals(2, db.getSchemaVersion());
		assertTrue(db.columnExists("players", "last_seen"));

		assertThrows(DatabaseException.class, () -> db.migrate(steps[0], steps[1], "ALTER TABLE nope ADD COLUMN x"));
		assertEquals(2, db.getSchemaVersion());
	}

	@Test
	void keyValueStore() {
		KeyValueStore kv = db.keyValue("settings");
		kv.set("motd", "Hello");
		kv.set("max", 20);
		kv.set("pvp", true);
		kv.set("player.abc.coins", 5);
		kv.set("PLAYER.xyz", 1);

		assertEquals("Hello", kv.getString("motd", null));
		assertEquals(20, kv.getInt("max", 0));
		assertTrue(kv.getBoolean("pvp", false));
		assertEquals(7, kv.getInt("missing", 7));
		assertEquals(15, kv.increment("player.abc.coins", 10));
		assertEquals(3, kv.increment("new.counter", 3));
		assertEquals(2.5, kv.increment("ratio", 2.5));
		assertEquals(Arrays.asList("player.abc.coins"), kv.keys("player."));

		kv.set("motd", "Changed");
		assertEquals("Changed", kv.getString("motd", null));
		assertTrue(kv.remove("motd"));
		assertFalse(kv.has("motd"));
		kv.set("max", null);
		assertFalse(kv.has("max"));

		Map<String, Object> all = kv.getAll();
		assertEquals(kv.size(), all.size());
		kv.clear();
		assertEquals(0, kv.size());
	}

	@Test
	void legacyApiStillWorks() {
		assertTrue(db.executeStatement("INSERT INTO players (uuid, name, coins) VALUES ('a', 'A', 1)"));
		assertTrue(db.executeStatement("INSERT INTO players (uuid, name, coins) VALUES ('b', 'B', 2)"));
		assertTrue(db.executeStatement("SELECT * FROM players"));
		assertFalse(db.executeStatement("INSERT INTO nope VALUES (1)"));

		assertEquals("A", db.queryValue("SELECT * FROM players WHERE uuid = 'a'", "name"));
		assertNull(db.queryValue("SELECT * FROM players WHERE uuid = 'zzz'", "name"));
		assertEquals(Arrays.asList("A", "B"), db.queryRow("SELECT * FROM players ORDER BY id", "name"));

		Map<String, List<Object>> map = db.queryMultipleRows("SELECT * FROM players ORDER BY id", "name", "coins");
		assertEquals(Arrays.asList("A", "B"), map.get("name"));
		assertEquals(Arrays.asList(1, 2), map.get("coins"));

		db.closeConnection();
		assertFalse(db.isOpen());
		assertEquals("A", db.queryValue("SELECT name FROM players WHERE uuid = 'a'", "name"));
	}

	@Test
	void legacyConstructorRunsMultiStatementCreate() {
		SQLite legacy = new SQLite("legacy", "CREATE TABLE IF NOT EXISTS a (x INTEGER); CREATE TABLE IF NOT EXISTS b (y TEXT);", dir);
		legacy.load();
		legacy.load();
		assertEquals(Arrays.asList("a", "b"), legacy.getTables());
		assertTrue(new File(dir, "legacy.db").exists());
		legacy.close();
	}

	@Test
	void rejectsPathTraversalNames() {
		assertThrows(IllegalArgumentException.class, () -> new SQLite("../evil", null, dir));
		assertThrows(IllegalArgumentException.class, () -> new SQLite("a/b", null, dir));
		assertThrows(IllegalArgumentException.class, () -> db.keyValue("bad name; DROP"));
	}

	@Test
	void readOnlyBlocksWrites() {
		db.update("INSERT INTO players (uuid) VALUES ('x')");
		assertEquals(1, db.readOnly(tx -> tx.query("SELECT * FROM players")).size());
		assertThrows(DatabaseException.class, () -> db.readOnly(tx -> tx.update("DELETE FROM players")));
		assertThrows(DatabaseException.class, () -> db.readOnly(tx -> tx.query("DELETE FROM players")));
		assertEquals(1L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());
		db.update("DELETE FROM players");
	}

	@Test
	void backupAndVacuum() {
		db.update("INSERT INTO players (uuid, name) VALUES (?, ?)", "u", "Steve");
		File target = new File(dir, "backups/copy.db");
		db.backup(target);
		assertTrue(target.length() > 0);
		assertThrows(DatabaseException.class, () -> db.backup(target));

		try (SQLite copy = SQLite.open(target)) {
			assertEquals("Steve", copy.queryScalar("SELECT name FROM players", String.class).orElseThrow());
		}
		db.vacuum();
	}

	@Test
	void asyncWorkCompletesBeforeClose() throws Exception {
		List<CompletableFuture<Integer>> futures = new ArrayList<>();
		for (int i = 0; i < 200; i++)
			futures.add(db.updateAsync("INSERT INTO players (uuid) VALUES (?)", "async-" + i));
		CompletableFuture<Long> count = db.supplyAsync(d -> d.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElse(0L));
		assertEquals(200L, count.get(10, TimeUnit.SECONDS));

		db.updateAsync("INSERT INTO players (uuid) VALUES (?)", "last");
		db.close();
		assertEquals(201L, db.queryScalar("SELECT COUNT(*) FROM players", Long.class).orElseThrow());
	}

	@Test
	void walModeAndForeignKeys() {
		assertEquals("wal", db.queryScalar("PRAGMA journal_mode", String.class).orElseThrow());
		assertEquals(1, db.queryScalar("PRAGMA foreign_keys", Integer.class).orElseThrow());
	}
}
