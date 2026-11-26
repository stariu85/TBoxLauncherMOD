package vad.dashing.tbox.ui

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

// Класс MyLifecycleOwner с правильной реализацией интерфейса
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

internal class MyLifecycleOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    companion object {
        private const val TAG = "MyLifecycleOwner"
    }
    private lateinit var mLifecycleRegistry: LifecycleRegistry
    private lateinit var mSavedStateRegistryController: SavedStateRegistryController
    private lateinit var mViewModelStore: ViewModelStore

    /**
     * @return True if the Lifecycle has been initialized.
     */
    val isInitialized: Boolean
        get() = ::mLifecycleRegistry.isInitialized

    init {
        initialize()
    }

    private fun initialize() {
        // Создаем LifecycleRegistry
        mLifecycleRegistry = LifecycleRegistry(this)

        // Создаем ViewModelStore
        mViewModelStore = ViewModelStore()

        // Устанавливаем начальное состояние
        mLifecycleRegistry.currentState = Lifecycle.State.INITIALIZED

        // Создаем SavedStateRegistryController
        mSavedStateRegistryController = SavedStateRegistryController.create(this)

        // Восстанавливаем состояние (пустое)
        mSavedStateRegistryController.performRestore(null)
    }

    // Реализация интерфейса SavedStateRegistryOwner
    override val lifecycle: Lifecycle
        get() = mLifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = mSavedStateRegistryController.savedStateRegistry

    // Реализация интерфейса ViewModelStoreOwner
    override val viewModelStore: ViewModelStore
        get() = mViewModelStore

    fun setCurrentState(state: Lifecycle.State) {
        try {
            if (mLifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
            mLifecycleRegistry.currentState = state
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Lifecycle state transition to $state failed: ${e.message}")
        }
    }

    // Важно: очищать ViewModelStore при уничтожении
    fun clear() {
        mViewModelStore.clear()
    }

    /**
     * Safely transitions lifecycle to DESTROYED (so Compose WindowRecomposer cancels cleanly)
     * and clears ViewModelStore.
     */
    fun destroyAndClear() {
        setCurrentState(Lifecycle.State.DESTROYED)
        clear()
    }
}