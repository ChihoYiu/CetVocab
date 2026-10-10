package com.cetvocab.app.data.local

import androidx.room.migration.Migration

/**
 * Schema upgrade policy.
 *
 *  * Destructive migration is **never** used: a learner's history is the most valuable thing
 *    in the app and must survive every upgrade.
 *  * Every schema change ships with a hand written [Migration] and with a schema snapshot in
 *    `app/schemas/`, which the migration test loads to verify the upgrade path on a real
 *    database file.
 *  * Version 1 is the first shipped schema. When version 2 lands, its migration is appended
 *    to [ALL_MIGRATIONS]; the list is what the database builder receives.
 */
object Migrations {

    /** All migrations known to this build, oldest first. */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf()

    /** Current schema version, kept in sync with the `@Database(version = ...)` value. */
    const val CURRENT_VERSION: Int = 1
}
