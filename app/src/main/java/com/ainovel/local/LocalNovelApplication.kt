package com.ainovel.local

import android.app.Application

class LocalNovelApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
