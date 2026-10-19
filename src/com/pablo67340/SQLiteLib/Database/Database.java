package com.pablo67340.SQLiteLib.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import java.sql.Connection;

import com.pablo67340.SQLiteLib.Error.Errors;
import com.pablo67340.SQLiteLib.Error.Error;
import com.pablo67340.SQLiteLib.Main.SQLiteLib;

public abstract class Database {
	protected Connection connection;

	public abstract Connection getSQLConnection();

	public abstract void load();

	public void initialize() {
		connection = getSQLConnection();
		try {
			PreparedStatement ps = connection.prepareStatement("SELECT * FROM test");
			ResultSet rs = ps.executeQuery();
			close(ps, rs);

		} catch (SQLException ex) {
			SQLiteLib.getInstance().getLogger().log(Level.SEVERE, "Unable to retrieve connection", ex);
		}
	}

	/**
	 * <p>
	 * Execute any statement using this method. This will return a success or
	 * failure boolean.
	 * </p>
	 * 
	 * @param statement The statement to execute.
	 * 
	 * @return the {@link Database}'s success or failure (true/false).
	 */
	public Boolean executeStatement(String statement) {
		Connection conn = null;
		PreparedStatement ps = null;
		try {
			conn = getSQLConnection();
			ps = conn.prepareStatement(statement);
			return !ps.execute();
		} catch (SQLException ex) {
			SQLiteLib.log(Errors.sqlConnectionExecute(), ex);
			return false;
		} finally {
			try {
				if (ps != null) ps.close();
				if (conn != null) conn.close();
			} catch (SQLException ex) {
				SQLiteLib.log(Errors.sqlConnectionClose(), ex);
			}
		}
	}

	/**
	 * Get a single value from the database. Your If your statement returns multiple
	 * values, only the first value will return. Use queryRow for multiple values in
	 * 1 row.
	 * 
	 * @param statement The statement to execute.
	 * @param row The row you would like to store data from.
	 * 
	 * @return the {@link Database}'s Query in Object format. Casting required to
	 *         change variables into their original form.
	 */
	public Object queryValue(String statement, String row) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs;
		try {
			conn = getSQLConnection();
			ps = conn.prepareStatement(statement);

			rs = ps.executeQuery();
			if (rs.next()) return rs.getObject(row);
		} catch (SQLException ex) {
			SQLiteLib.log(Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null) ps.close();
				if (conn != null) conn.close();
			} catch (SQLException ex) {
				SQLiteLib.log(Errors.sqlConnectionClose(), ex);
			}
		}
		return null;
	}

	/**
	 * Get a list of data based on your statement. This should only be used when
	 * querying for multiple values, in 1 row. I.E If you had a row called test,
	 * with numbers 1 to 10. This method could be used to build a list containing
	 * the data 1 to 10 from this row.
	 * 
	 * @param statement The statement to execute.
	 * @param row The row you would like to store data from.
	 *
	 * @return the {@link Database}'s Query in {@code List<Object>} format.
	 * 		   Casting required to change variables into their original form.
	 */
	public List<Object> queryRow(String statement, String row) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs;

		List<Object> objects = new ArrayList<>();

		try {
			conn = getSQLConnection();
			ps = conn.prepareStatement(statement);

			rs = ps.executeQuery();
			while (rs.next())
				objects.add(rs.getObject(row));
			return objects;
		} catch (SQLException ex) {
			SQLiteLib.log(Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null) ps.close();
				if (conn != null) conn.close();
			} catch (SQLException ex) {
				SQLiteLib.log(Errors.sqlConnectionClose(), ex);
			}
		}
		return null;
	}

	/**
	 * Get a map which contains lists for each row specified. For each row, a list
	 * is stored containing all the queried values.
	 * <p>
	 * You can access the list for each row using {@code <Row, List>} format. The list is in
	 * List<Object> format.
	 * 
	 * @param statement The statement to execute.
	 * @param row The row(s) you would like to store data from. Minimum 1 row required.
	 * 
	 * @return the {@link Database}'s Query in {@code Map<Row,List<Object>>} format. Casting
	 *         required to change variables into their original form.
	 */
	public Map<String, List<Object>> queryMultipleRows(String statement, String... row) {
		Connection conn = null;
		PreparedStatement ps = null;
		ResultSet rs;

		List<Object> objects = new ArrayList<>();
		Map<String, List<Object>> map = new HashMap<>();

		try {
			conn = getSQLConnection();
			ps = conn.prepareStatement(statement);

			rs = ps.executeQuery();
			while (rs.next()) {
				for (String singleRow : row)
					objects.add(rs.getObject(singleRow));

				for (String singleRow : row)
					map.put(singleRow, objects);
			}

			return map;
		} catch (SQLException ex) {
			SQLiteLib.log(Errors.sqlConnectionExecute(), ex);
		} finally {
			try {
				if (ps != null) ps.close();
				if (conn != null) conn.close();
			} catch (SQLException ex) {
				SQLiteLib.log(Errors.sqlConnectionClose(), ex);
			}
		}
		return null;
	}

	/**
	 * Close the current connection of the statement to the database.
	 * 
	 * @param ps The statement previously used.
	 * @param rs The result set that was returned from the statement.
	 */
	public void close(PreparedStatement ps, ResultSet rs) {
		try {
			if (ps != null) ps.close();
			if (rs != null) rs.close();
		} catch (SQLException ex) {
			Error.close(ex);
		}
	}
	

	/**
	 * Close the current connection to the database.
	 * <p>
	 * The database will need to be re-initialized if this is used. When
	 * initializing using the main class, it will delete this current object
	 * and create a new object connected to the db. If you'd like to reload
	 * this db without trashing the database object, invoke the {@link #load()}
	 * method through the global map of databases.
	 * <p>
	 * Ex: getDatabase("name").load();
	 */
	public void closeConnection() {
		try {
			connection.close();
		} catch (SQLException ex) {
			Error.close(ex);
		}
	}
}
