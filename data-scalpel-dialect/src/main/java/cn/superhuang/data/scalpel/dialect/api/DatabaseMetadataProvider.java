package cn.superhuang.data.scalpel.dialect.api;

import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.NamespaceInfo;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableList;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Opt-in metadata reader for engines whose JDBC DatabaseMetaData does not expose the business
 * resource boundary accurately. Ordinary relational dialects continue to use the generic reader.
 */
public interface DatabaseMetadataProvider {

    List<NamespaceInfo> listNamespaces(Connection connection, JdbcConnectionConfig config) throws SQLException;

    TableList listTables(Connection connection, JdbcConnectionConfig config, TableQuery query) throws SQLException;

    TableMetadata readTableMetadata(Connection connection, TableIdentifier table) throws SQLException;
}
