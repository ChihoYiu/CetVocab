package com.cetvocab.app.ui

/**
 * Central record of every experimental API this app opts into.
 *
 * Policy: an experimental API is only used when the stable alternative would either lose
 * functionality or force a hand-written re-implementation of a platform component. Each use is
 * annotated at the *narrowest* scope (the single composable that needs it) with
 * `@OptIn(ExperimentalMaterial3Api::class)` rather than with a file-wide `@file:OptIn`, and the
 * reason is written down here so a future upgrade can revisit each one deliberately.
 *
 * Current list:
 *  * `ExperimentalMaterial3Api` — `TopAppBar` and the `Scaffold` top-bar overloads. Material 3
 *    still marks the app-bar family experimental while its API settles; every screen needs a
 *    themed app bar, and the alternatives (a hand-written `Row` header) would lose the
 *    Material 3 look, the correct status-bar inset handling and the accessibility behaviour the
 *    design spec requires.
 *
 * Nothing else in the project opts into an experimental API: Navigation Compose, Room,
 * DataStore, WorkManager, `FilterChip` and `Surface(onClick = …)` are all used through stable
 * entry points, and no experimental Compose foundation API is used anywhere.
 */
internal object ExperimentalApiPolicy {
    /** Documentation anchor; see the KDoc above for the complete list and the justification. */
    const val DOCUMENTED = true
}

