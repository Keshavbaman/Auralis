package app.auralis.data

import app.auralis.importer.ImportEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationGraph {
    fun database(): AuralisDatabase

    fun importer(): ImportEngine

    fun backup(): BackupManager

    fun icons(): app.auralis.branding.LauncherIcons
}
