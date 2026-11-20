package org.elm.ide.intentions

import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.openapi.extensions.ExtensionPointName
import org.elm.lang.ElmTestBase

class ElmIntentionDocsTest : ElmTestBase() {

    fun `test intentions has documentation`() {
        ExtensionPointName<IntentionActionBean>("com.intellij.intentionAction")
                .extensionList
                .filter { it.category?.startsWith("Elm") == true }
                .forEach {
                    val simpleName = it.className.substringAfterLast(".")
                    val directory = "intentionDescriptions/$simpleName"
                    val files = listOf("before.elm.template", "after.elm.template", "description.html")
                    for (file in files) {
                        val text = getResourceAsString("$directory/$file")
                                ?: fail("No HTML docs found for ${it.className}.\n" +
                                        "Add ${files.joinToString()} to src/main/resources/$directory")

                        if (file.endsWith(".html")) {
                            checkHtmlStyle(text)
                        }
                    }
                }
    }
}
