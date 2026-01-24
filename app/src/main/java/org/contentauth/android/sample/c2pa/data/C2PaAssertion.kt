package org.contentauth.android.sample.c2pa.data

import kotlinx.serialization.Serializable

@Serializable
data class C2paAssertion(
    val label: String,
    val data: AssertionData
) {
    companion object {
        val Allowed = C2paAssertion(
            label = "cawg.training-mining",
            data = AssertionData(
                entries = mapOf(
                    "cawg.ai_inference" to EntryUse(use = "allowed"),
                    "cawg.ai_generative_training" to EntryUse(use = "allowed")
                )
            )
        )

        // Default instance for NOT allowed AI usage
        val NotAllowed = C2paAssertion(
            label = "cawg.training-mining",
            data = AssertionData(
                entries = mapOf(
                    "cawg.ai_inference" to EntryUse(use = "notAllowed"),
                    "cawg.ai_generative_training" to EntryUse(use = "notAllowed")
                )
            )
        )
    }
}

@Serializable
data class AssertionData(
    val entries: Map<String, EntryUse>
)

@Serializable
data class EntryUse(
    val use: String
)
