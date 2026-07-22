package com.example.safetymode

import android.app.Application
import com.facebook.soloader.SoLoader

/**
 * Initializes SoLoader once for the whole process. ExecuTorch's native libraries
 * (shipped inside the executorch-android AAR) are loaded through SoLoader, so this
 * must run before any [org.pytorch.executorch.Module] is loaded.
 */
class SafetyModeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, /* nativeExopackage = */ false)
    }
}
