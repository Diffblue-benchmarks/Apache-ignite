/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.jdbc;

import org.apache.ignite.configuration.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.cache.query.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.testframework.junits.common.*;

import java.io.*;
import java.sql.*;
import java.util.*;

import static java.sql.Types.*;
import static org.gridgain.grid.cache.GridCacheMode.*;
import static org.gridgain.grid.cache.GridCacheWriteSynchronizationMode.*;

/**
 * Metadata tests.
 */
public class GridJdbcMetadataSelfTest extends GridCommonAbstractTest {
    /** IP finder. */
    private static final GridTcpDiscoveryIpFinder IP_FINDER = new GridTcpDiscoveryVmIpFinder(true);

    /** URL. */
    private static final String URL = "jdbc:gridgain://127.0.0.1/";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        GridCacheConfiguration cache = defaultCacheConfiguration();

        cache.setCacheMode(PARTITIONED);
        cache.setBackups(1);
        cache.setWriteSynchronizationMode(FULL_SYNC);

        cfg.setCacheConfiguration(cache);

        GridTcpDiscoverySpi disco = new GridTcpDiscoverySpi();

        disco.setIpFinder(IP_FINDER);

        cfg.setDiscoverySpi(disco);

        cfg.setRestEnabled(true);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        startGridsMultiThreaded(3);

        GridCache<String, Organization> orgCache = grid(0).cache(null);

        assert orgCache != null;

        orgCache.put("o1", new Organization(1, "A"));
        orgCache.put("o2", new Organization(2, "B"));

        GridCache<GridCacheAffinityKey<String>, Person> personCache = grid(0).cache(null);

        assert personCache != null;

        personCache.put(new GridCacheAffinityKey<>("p1", "o1"), new Person("John White", 25, 1));
        personCache.put(new GridCacheAffinityKey<>("p2", "o1"), new Person("Joe Black", 35, 1));
        personCache.put(new GridCacheAffinityKey<>("p3", "o2"), new Person("Mike Green", 40, 2));

        Class.forName("org.gridgain.jdbc.GridJdbcDriver");
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        stopAllGrids();
    }

    /**
     * @throws Exception If failed.
     */
    public void testResultSetMetaData() throws Exception {
        Statement stmt = DriverManager.getConnection(URL).createStatement();

        ResultSet rs = stmt.executeQuery(
            "select p.name, o.id as orgId from Person p, Organization o where p.orgId = o.id");

        assert rs != null;

        ResultSetMetaData meta = rs.getMetaData();

        assert meta != null;

        assert meta.getColumnCount() == 2;

        assert "Person".equalsIgnoreCase(meta.getTableName(1));
        assert "name".equalsIgnoreCase(meta.getColumnName(1));
        assert "name".equalsIgnoreCase(meta.getColumnLabel(1));
        assert meta.getColumnType(1) == VARCHAR;
        assert "VARCHAR".equals(meta.getColumnTypeName(1));
        assert "java.lang.String".equals(meta.getColumnClassName(1));

        assert "Organization".equalsIgnoreCase(meta.getTableName(2));
        assert "orgId".equalsIgnoreCase(meta.getColumnName(2));
        assert "orgId".equalsIgnoreCase(meta.getColumnLabel(2));
        assert meta.getColumnType(2) == INTEGER;
        assert "INTEGER".equals(meta.getColumnTypeName(2));
        assert "java.lang.Integer".equals(meta.getColumnClassName(2));
    }

    /**
     * @throws Exception If failed.
     */
    public void testGetTables() throws Exception {

        try (Connection conn = DriverManager.getConnection(URL)) {
            DatabaseMetaData meta = conn.getMetaData();

            Collection<String> names = new ArrayList<>(2);

            names.add("PERSON");
            names.add("ORGANIZATION");

            ResultSet rs = meta.getTables("", "PUBLIC", "%", new String[]{"TABLE"});

            assert rs != null;

            int cnt = 0;

            while (rs.next()) {
                assert "TABLE".equals(rs.getString("TABLE_TYPE"));
                assert names.remove(rs.getString("TABLE_NAME"));

                cnt++;
            }

            assert names.isEmpty();
            assert cnt == 2;

            names.add("PERSON");
            names.add("ORGANIZATION");

            rs = meta.getTables("", "PUBLIC", "%", null);

            assert rs != null;

            cnt = 0;

            while (rs.next()) {
                assert "TABLE".equals(rs.getString("TABLE_TYPE"));
                assert names.remove(rs.getString("TABLE_NAME"));

                cnt++;
            }

            assert names.isEmpty();
            assert cnt == 2;

            rs = meta.getTables("", "PUBLIC", "", new String[]{"WRONG"});

            assert !rs.next();
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testGetColumns() throws Exception {

        try (Connection conn = DriverManager.getConnection(URL)) {
            DatabaseMetaData meta = conn.getMetaData();

            ResultSet rs = meta.getColumns("", "PUBLIC", "Person", "%");

            assert rs != null;

            Collection<String> names = new ArrayList<>(2);

            names.add("NAME");
            names.add("AGE");
            names.add("ORGID");
            names.add("_KEY");
            names.add("_VAL");

            int cnt = 0;

            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");

                assert names.remove(name);

                if ("NAME".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == VARCHAR;
                    assert "VARCHAR".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 1;
                } else if ("AGE".equals(name) || "ORGID".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == INTEGER;
                    assert "INTEGER".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                }
                if ("_KEY".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == OTHER;
                    assert "OTHER".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                }
                if ("_VAL".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == OTHER;
                    assert "OTHER".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                }

                cnt++;
            }

            assert names.isEmpty();
            assert cnt == 5;

            rs = meta.getColumns("", "PUBLIC", "Organization", "%");

            assert rs != null;

            names.add("ID");
            names.add("NAME");
            names.add("_KEY");
            names.add("_VAL");

            cnt = 0;

            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");

                assert names.remove(name);

                if ("id".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == INTEGER;
                    assert "INTEGER".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                } else if ("name".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == VARCHAR;
                    assert "VARCHAR".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 1;
                }
                if ("_KEY".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == VARCHAR;
                    assert "VARCHAR".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                }
                if ("_VAL".equals(name)) {
                    assert rs.getInt("DATA_TYPE") == OTHER;
                    assert "OTHER".equals(rs.getString("TYPE_NAME"));
                    assert rs.getInt("NULLABLE") == 0;
                }

                cnt++;
            }

            assert names.isEmpty();
            assert cnt == 4;
        }
    }

    /**
     * Person.
     */
    @SuppressWarnings("UnusedDeclaration")
    private static class Person implements Serializable {
        /** Name. */
        @GridCacheQuerySqlField(index = false)
        private final String name;

        /** Age. */
        @GridCacheQuerySqlField
        private final int age;

        /** Organization ID. */
        @GridCacheQuerySqlField
        private final int orgId;

        /**
         * @param name Name.
         * @param age Age.
         * @param orgId Organization ID.
         */
        private Person(String name, int age, int orgId) {
            assert !F.isEmpty(name);
            assert age > 0;
            assert orgId > 0;

            this.name = name;
            this.age = age;
            this.orgId = orgId;
        }
    }

    /**
     * Organization.
     */
    @SuppressWarnings("UnusedDeclaration")
    private static class Organization implements Serializable {
        /** ID. */
        @GridCacheQuerySqlField
        private final int id;

        /** Name. */
        @GridCacheQuerySqlField(index = false)
        private final String name;

        /**
         * @param id ID.
         * @param name Name.
         */
        private Organization(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
