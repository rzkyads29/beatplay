/**
 * Routes DAO calls to the database of the active BeatPlay account.
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The app used to hold one Room database for its whole lifetime. To support per-account
 * data without killing the process, [DatabaseRouter] keeps the active [InternalDatabase]
 * and exposes a stable [DatabaseDao] facade that forwards every call to whichever database
 * is current. Switching accounts swaps the database in-process and the UI is relaunched.
 */

package com.metrolist.music.db

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.metrolist.music.db.daos.SpeedDialDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseRouter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val lock = Any()

        @Volatile
        private var active: InternalDatabase? = null

        /** Stable facade: every DAO call is routed to the database that is active right now. */
        val dao: DatabaseDao =
            Proxy.newProxyInstance(
                DatabaseDao::class.java.classLoader,
                arrayOf(DatabaseDao::class.java),
            ) { _, method: Method, args: Array<Any?>? ->
                try {
                    method.invoke(current.dao, *(args ?: emptyArray<Any?>()))
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            } as DatabaseDao

        val current: InternalDatabase
            get() {
                active?.let { return it }
                synchronized(lock) {
                    active?.let { return it }
                    val email = AccountDatabase.signedInEmail(context)
                    val name = AccountDatabase.prepare(context, AccountDatabase.nameFor(email))
                    return InternalDatabase
                        .newInternalDatabaseInstance(context, name)
                        .also { active = it }
                }
            }

        val speedDialDao: SpeedDialDao
            get() = current.speedDialDao

        val openHelper: SupportSQLiteOpenHelper
            get() = current.openHelper

        /**
         * Opens the database for [email]. Returns true when the active database actually
         * changed (the caller should then relaunch the UI so flows bind to the new data).
         */
        fun switchTo(email: String?): Boolean {
            val name = AccountDatabase.nameFor(email)
            synchronized(lock) {
                if (name == AccountDatabase.currentName && active != null) return false
                val old = active
                AccountDatabase.prepare(context, name)
                val opened = InternalDatabase.newInternalDatabaseInstance(context, name)
                active = opened
                if (old != null && old !== opened) {
                    // Let the old UI finish detaching before releasing its connection.
                    Handler(Looper.getMainLooper()).postDelayed({
                        runCatching { old.close() }
                    }, 1_500)
                }
                return true
            }
        }

        /** Closes the active database (used by backup/restore before swapping files). */
        fun closeActive() {
            synchronized(lock) {
                runCatching { active?.close() }
                active = null
            }
        }
    }