package koh.realm;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import koh.realm.utils.Settings;
import koh.realm.utils.sql.ConnectionResult;
import koh.realm.utils.sql.ConnectionStatement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 *
 * @author Neo-Craft
 */
public class DatabaseSource {

    private final HikariDataSource dataSource;

    @Inject
    public DatabaseSource(Settings settings) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + settings.getStringElement("Database.Host") + "/" + settings.getStringElement("Database.Name"));

        //config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        config.setUsername(settings.getStringElement("Database.User"));
        config.setPassword(settings.getStringElement("Database.Password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);

        Main.onShutdown(this::stop);
    }

    public Connection getConnectionOfPool() throws SQLException {
        return dataSource.getConnection();
    }

    public ConnectionStatement<Statement> createStatement() throws SQLException {
        Connection connection = this.getConnectionOfPool();
        return new ConnectionStatement<>(connection, connection.createStatement());
    }

    public ConnectionStatement<PreparedStatement> prepareStatement(String query) throws SQLException {
        Connection connection = this.getConnectionOfPool();
        return new ConnectionStatement<>(connection, connection.prepareStatement(query));
    }

    public ConnectionStatement<PreparedStatement> prepareStatement(String query, boolean autoGeneratedKeys) throws SQLException {
        Connection connection = this.getConnectionOfPool();
        PreparedStatement statement = connection.prepareStatement(query,
                autoGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
        return new ConnectionStatement<>(connection, statement);
    }

    public ConnectionResult executeQuery(String query) throws SQLException {
        Connection connection = this.getConnectionOfPool();
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(300);
        return new ConnectionResult(connection, statement, statement.executeQuery(query));
    }

    public ConnectionResult executeQuery(String query, int secsTimeout) throws SQLException {
        Connection connection = this.getConnectionOfPool();
        Statement statement = connection.createStatement();
        if(secsTimeout > 0)
            statement.setQueryTimeout(300);
        return new ConnectionResult(connection, statement, statement.executeQuery(query));
    }

    public void stop() {
        dataSource.close();
    }

}
