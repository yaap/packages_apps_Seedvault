/*
 * SPDX-FileCopyrightText: 2024 The Calyx Institute
 * SPDX-License-Identifier: Apache-2.0
 */

package com.stevesoltys.seedvault.worker

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val workerModule = module {
    single {
        ApkBackup(
            pm = androidContext().packageManager,
            crypto = get(),
            settingsManager = get(),
            metadataManager = get()
        )
    }
    single {
        ApkBackupManager(
            context = androidContext(),
            settingsManager = get(),
            metadataManager = get(),
            packageService = get(),
            apkBackup = get(),
            plugin = get(),
            nm = get()
        )
    }
}
