package com.juren233.easyopen.shared.navigation

import androidx.navigation3.runtime.NavKey

/** Minimal stack wrapper following HLE's Navigator contract. */
class EasyOpenNavigator(
    private val backStack: MutableList<NavKey>,
) {
    fun navigate(route: NavKey) {
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
