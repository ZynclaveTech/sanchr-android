package com.sanchr.core.common.di

import javax.inject.Qualifier

/**
 * Qualifier for the app-wide [kotlinx.coroutines.CoroutineScope] used by
 * singleton observers that outlive any Activity (for example the
 * `NewMessageNotifier` in `core:notifications` and the real-time stream
 * subscriber in `:sync`).
 *
 * The scope itself is provided from `:app` — the qualifier lives in
 * `:core:common` so downstream modules can depend on it without a
 * dependency cycle on `:app`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
