package com.g4vrk.hologram.gradle

import org.gradle.api.Action
import org.gradle.api.file.FileCopyDetails
import java.io.Serializable

class ExpandAddonDescriptor(
    private val properties: Map<String, String>
) : Action<FileCopyDetails>, Serializable {

    override fun execute(details: FileCopyDetails) {
        details.expand(this.properties)
    }
}
