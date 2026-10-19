package com.pablo67340.SQLiteLib.Database;

import java.sql.SQLException;

/**
 * A function that may throw {@link SQLException}. Used by
 * {@link Database#transactionResult(SqlFunction)}.
 *
 * @param <T> input type
 * @param <R> result type
 */
@FunctionalInterface
public interface SqlFunction<T, R> {
	R apply(T value) throws SQLException;
}
