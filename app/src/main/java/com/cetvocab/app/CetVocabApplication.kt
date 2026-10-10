package com.cetvocab.app

import android.app.Application

/**
 * Process-wide entry point. The dependency graph lives in [AppContainer] and is created
 * here so that every ViewModel / Worker shares one database and one preferences store.
 *
 * Manual constructor injection is used instead of a DI framework on purpose: the object
 * graph is small, and this keeps the build fast and the wiring explicit and testable.
 */
class CetVocabApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
