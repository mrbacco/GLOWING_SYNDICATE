/*
 * File:     android/app/src/main/java/com/mrbacco/syndicate/data/AppContainer.kt
 * Author:   mrbacco04@gmail.com
 * GitHub:   https://github.com/mrbacco
 * Created:  2026-10-20
 * Updated:  2026-11-23
 * Project:  SYNDICATE
 * License:  Proprietary, all rights reserved (see LICENSE)
 */

package com.mrbacco.syndicate.data

import android.content.Context
import com.mrbacco.syndicate.data.local.LocalStore
import java.io.File

/** Holds the app-wide objects. [init] is called once, when the app starts (SyndicateApp). */
object AppContainer {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The data file lives in the app's private storage, which Android backs up (allowBackup in the manifest). */
    val repository: SyndicateRepository by lazy {
        SyndicateRepository(LocalStore(File(appContext.filesDir, LocalStore.FILE_NAME)))
    }
}
