package com.quark.data.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Opens `quark.db` in the app's private database folder. There is no Flutter
 * install to adopt on Android, so this is always a schema SQLDelight created.
 */
object AndroidDatabaseFactory {

    fun open(context: Context, name: String = "quark.db"): QuarkDatabase =
        QuarkDatabase(AndroidSqliteDriver(QuarkDatabase.Schema, context.applicationContext, name))
}
