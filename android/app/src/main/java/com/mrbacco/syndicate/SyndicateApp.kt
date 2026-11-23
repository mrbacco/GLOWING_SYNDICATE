/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/SyndicateApp.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-11-09
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate

import android.app.Application
import com.mrbacco.syndicate.data.AppContainer

class SyndicateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
