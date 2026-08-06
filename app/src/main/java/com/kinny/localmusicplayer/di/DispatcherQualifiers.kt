package com.kinny.localmusicplayer.di

import javax.inject.Qualifier

/**
 * Description: IO 协程 Dispatcher 限定符
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Description: Default 协程 Dispatcher 限定符
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * Description: Main 协程 Dispatcher 限定符
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Description: 应用协程 Dispatcher 类型枚举
 * Author: kinny
 * Created: 2026/8/5 15:36
 */
enum class AppDispatchers {
    IO,
    Default,
    Main,
}
