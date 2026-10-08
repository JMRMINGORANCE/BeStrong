package com.bestrong.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bestrong.mobile.AppContainer
import com.bestrong.mobile.BeStrongApplication

/** Crea un ViewModel con acceso al [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    viewModelStoreOwner = owner,
    key = key,
    factory = viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as BeStrongApplication).container) }
    },
)
