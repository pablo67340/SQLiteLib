package com.pablo67340.SQLiteLib.Database;

import java.sql.SQLException;

/**
 * A consumer that may throw {@link SQLException}. Used by
 * {@link Database#transaction(SqlConsumer)}.
 *
 * @param <T> input type
 */
@FunctionalInterface
public interface SqlConsumer<T> {
	void accept(T value) throws SQLException;
}
