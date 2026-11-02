package com.atunwadigital.examples.rn.atunwa

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

class AtunwaMediaPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(AtunwaMediaModule(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        listOf(MiniPlayerManager(), LiveCardManager())
}
