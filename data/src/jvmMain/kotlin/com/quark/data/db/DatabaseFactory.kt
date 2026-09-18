package com.quark.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens `quark.db`.
 *
 * The schema is the one Drift created, so a database written by the Flutter
 * build is opened in place rather than imported. That is also why the version
 * handling is explicit: Drift stamps `user_version` itself, and calling
 * SQLDelight's `create` on a database that already has the tables would fail on
 * the first `CREATE TABLE`.
 */
object DatabaseFactory {

    fun open(file: Path): QuarkDatabase {
        Files.createDirectories(file.parent)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        // WAL keeps the scanner writing while the player reads.
        driver.execute(null, "PRAGMA journal_mode = WAL", 0)

        when {
            isEmpty(driver) -> {
                QuarkDatabase.Schema.create(driver)
                setVersion(driver, QuarkDatabase.Schema.version)
            }

            // Drift left the tables at its own schemaVersion 1, which is this
            // schema. Stamp the version so later migrations have a floor.
            version(driver) == 0L -> setVersion(driver, QuarkDatabase.Schema.version)
        }

        return QuarkDatabase(driver)
    }

    /** An in-memory database with the schema applied, for tests. */
    fun inMemory(): QuarkDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        QuarkDatabase.Schema.create(driver)
        return QuarkDatabase(driver)
    }

    private fun isEmpty(driver: SqlDriver): Boolean =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'known_tracks'",
            mapper = { cursor -> app.cash.sqldelight.db.QueryResult.Value(cursor.next().value && cursor.getLong(0) == 0L) },
            parameters = 0,
        ).value

    private fun version(driver: SqlDriver): Long =
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version",
            mapper = { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            parameters = 0,
        ).value

    private fun setVersion(driver: SqlDriver, version: Long) {
        driver.execute(null, "PRAGMA user_version = $version", 0)
    }
}
