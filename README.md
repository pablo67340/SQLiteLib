# SQLiteLib

**Simple, safe and fast SQLite storage for Spigot/Paper plugins (and plain Java). Ditch those YML files!**

[SpigotMC](https://www.spigotmc.org/resources/sqlitelib.46801/) · [Discord](https://discord.gg/DpNFSQu) · [Issues](https://github.com/pablo67340/SQLiteLib/issues)

SQLiteLib started as a follow-up to the [How to: SQLite](https://www.spigotmc.org/threads/how-to-sqlite.56847/) tutorial, for everyone who wanted SQLite without writing JDBC boilerplate. Version 2 keeps every 1.x method working and adds what a modern plugin needs:

- **Parameterized queries** - no SQL injection, no string concatenation
- **Typed results** - `Row#getInt`, `getUUID`, `getBoolean`, `getEnum`, or map rows straight to your own objects
- **Async API** - every database has its own worker thread, so the main thread never touches the disk
- **Transactions & batches** - atomic multi-step changes and fast bulk inserts
- **Schema migrations** - versioned `migrate(...)` steps that only run once
- **Key/value stores** - drop-in replacement for small YAML data files, with atomic counters
- **Live backups** - consistent backups while the server is running
- **Sensible defaults** - WAL mode, foreign keys and a busy timeout out of the box
- **Admin commands** - inspect, query, back up and vacuum databases in game or from the console

## Requirements

- Java 17+
- Spigot or Paper 1.18+ (both already include the SQLite driver)

## Installation

**Server owners:** drop `SQLiteLib.jar` into `plugins/`.

**Developers:** add the dependency (via [JitPack](https://jitpack.io/#pablo67340/SQLiteLib)) and `depend: [SQLiteLib]` to your `plugin.yml`.

Maven:

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.pablo67340</groupId>
    <artifactId>SQLiteLib</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

Gradle:

```kotlin
repositories { maven("https://jitpack.io") }
dependencies { compileOnly("com.github.pablo67340:SQLiteLib:2.0.0") }
```

## Quick start

```java
public class MyPlugin extends JavaPlugin {

    private Database db;

    @Override
    public void onEnable() {
        SQLiteLib sqlite = SQLiteLib.hookSQLiteLib();
        db = sqlite.open(this, "data"); // plugins/MyPlugin/data.db

        db.migrate(
            // version 1
            "CREATE TABLE homes (owner TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL, "
                + "x REAL, y REAL, z REAL, PRIMARY KEY (owner, name))",
            // version 2 - added later; existing servers only run this step
            "ALTER TABLE homes ADD COLUMN created INTEGER NOT NULL DEFAULT 0"
        );
    }
}
```

SQLiteLib closes every database cleanly when the server shuts down.

## Queries

Always pass values as `?` parameters. `UUID`s, enums and booleans are converted for you.

```java
// Write
db.update("INSERT OR REPLACE INTO homes (owner, name, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
        player.getUniqueId(), "base", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());

long id = db.insert("INSERT INTO warps (name) VALUES (?)", "spawn"); // returns the new row id

// Read rows
for (Row row : db.query("SELECT * FROM homes WHERE owner = ?", player.getUniqueId())) {
    String name = row.getString("name");
    double x = row.getDouble("x");
}

// Map rows to your own type
List<Home> homes = db.query("SELECT * FROM homes WHERE owner = ?",
        row -> new Home(row.getString("name"), row.getString("world"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z")),
        player.getUniqueId());

// Single row / single value / existence
Optional<Row> home = db.queryFirst("SELECT * FROM homes WHERE owner = ? AND name = ?", uuid, "base");
long count = db.queryScalar("SELECT COUNT(*) FROM homes WHERE owner = ?", Long.class, uuid).orElse(0L);
List<String> names = db.queryColumn("SELECT name FROM homes WHERE owner = ?", String.class, uuid);
boolean has = db.exists("SELECT 1 FROM homes WHERE owner = ?", uuid);
```

Failures throw an unchecked `DatabaseException` with the SQL and the reason.

## Async (recommended on the main thread)

Each database runs async work on its own thread, in order. Use `SQLiteLib.mainThread(plugin)` to get back to the Bukkit API:

```java
db.queryAsync("SELECT name FROM homes WHERE owner = ?", player.getUniqueId())
  .thenAcceptAsync(rows -> player.sendMessage("You have " + rows.size() + " homes"),
          SQLiteLib.mainThread(this));

db.updateAsync("DELETE FROM homes WHERE owner = ? AND name = ?", uuid, name);

db.supplyAsync(d -> d.queryFirst("SELECT * FROM homes WHERE owner = ?", uuid))
  .thenAcceptAsync(home -> { /* back on the main thread */ }, SQLiteLib.mainThread(this));
```

Async failures are logged automatically and complete the future exceptionally.

## Transactions & batches

```java
db.transaction(tx -> {
    tx.update("UPDATE balances SET coins = coins - ? WHERE uuid = ?", 100, from);
    tx.update("UPDATE balances SET coins = coins + ? WHERE uuid = ?", 100, to);
}); // both or neither

List<Object[]> rows = new ArrayList<>();
for (Player p : Bukkit.getOnlinePlayers())
    rows.add(new Object[] { p.getUniqueId(), p.getName(), System.currentTimeMillis() });
db.batch("INSERT OR REPLACE INTO seen (uuid, name, time) VALUES (?, ?, ?)", rows); // one fast transaction
```

## Key/value stores

Perfect for replacing `data.yml`-style files:

```java
KeyValueStore coins = db.keyValue("coins");
String key = player.getUniqueId().toString();

long balance = coins.increment(key, 50); // atomic, returns new value
coins.getLong(key, 0);
coins.set("motd", "Welcome!");
coins.getString("motd", "");
coins.remove(key);
coins.keys("prefix.");  // keys starting with a prefix
coins.getAll();         // Map<String, Object>
```

## Maintenance

```java
db.backup(new File(getDataFolder(), "backups/data-" + System.currentTimeMillis() + ".db")); // safe while running
db.vacuum();
db.getTables();
db.tableExists("homes");
db.columnExists("homes", "created");
db.getSchemaVersion();
db.readOnly(tx -> tx.query(untrustedSql)); // any write attempt fails
```

## Plain Java (no Minecraft)

The `Database` classes don't depend on Bukkit. Add `org.xerial:sqlite-jdbc` to your project and:

```java
try (SQLite db = SQLite.open(new File("app.db"))) {
    db.migrate("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)");
    db.insert("INSERT INTO notes (text) VALUES (?)", "hello");
}
```

## Commands

`/sqlite` (alias `/sl`). SQL runs off the main thread.

| Command | Permission | Description |
| --- | --- | --- |
| `/sl list` | `sqlite.command.read` | List loaded databases with size and owner |
| `/sl info <db>` | `sqlite.command.read` | File, size, schema version, tables and row counts |
| `/sl query <db> <sql>` | `sqlite.command.read` | Run a **read-only** query and show the rows |
| `/sl queryvalue <db> <column> <sql>` | `sqlite.command.read` | Show a single value |
| `/sl queryrow <db> <column> <sql>` | `sqlite.command.read` | Show one column for every row |
| `/sl execute <db> <sql>` | `sqlite.command.write` | Run any statement |
| `/sl init <db> [sql]` | `sqlite.command.admin` | Create/load a database in SQLiteLib's folder |
| `/sl backup <db>` | `sqlite.command.admin` | Live backup to `plugins/SQLiteLib/backups/` |
| `/sl vacuum <db>` | `sqlite.command.admin` | Compact the database file |

`sqlite.use` grants all of the above. Everything defaults to op. Databases opened with `open(plugin, name)` are listed as `Plugin:name`; you can type just `name` if it's unique.

## Upgrading from 1.x

Plugins compiled against 1.x keep working without changes. What changed:

- `executeStatement` now returns `true` for any statement that succeeds (it used to return `false` for `SELECT`).
- `queryMultipleRows` returns a separate list per column (it used to put the same list under every column).
- New databases no longer get an unused `test` table.
- Connections stay open instead of being reopened for every query, and use WAL mode with foreign keys enforced.
- `queryValue`, `queryRow` and `queryMultipleRows` are deprecated in favour of the parameterized methods above.

## Building

```
mvn package
```

Requires JDK 17. The plugin jar is written to `target/`.
