package com.juren233.easyopen.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class EasyOpenNavigationTest {
    @Test
    fun navigatorDoesNotDuplicateTheTopRoute() {
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(EasyOpenRoute.Home)
        val navigator = EasyOpenNavigator(stack)

        navigator.navigate(EasyOpenRoute.Settings)
        navigator.navigate(EasyOpenRoute.Settings)

        assertEquals(2, stack.size)
        assertEquals(EasyOpenRoute.Home, stack[0])
        assertEquals(EasyOpenRoute.Settings, stack[1])
    }

    @Test
    fun navigatorKeepsTheRootRouteWhenPopping() {
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(EasyOpenRoute.Home)
        val navigator = EasyOpenNavigator(stack)

        navigator.pop()

        assertEquals(1, stack.size)
        assertEquals(EasyOpenRoute.Home, stack[0])
    }
}
