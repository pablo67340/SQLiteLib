package com.pablo67340.SQLiteLib.Database;

/**
 * Converts a single result {@link Row} into your own object.
 *
 * <pre>
 * List&lt;Home&gt; homes = db.query("SELECT * FROM homes WHERE owner = ?",
 *         row -&gt; new Home(row.getString("name"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z")),
 *         player.getUniqueId());
 * </pre>
 *
 * @param <T> mapped type
 */
@FunctionalInterface
public interface RowMapper<T> {
	T map(Row row);
}
