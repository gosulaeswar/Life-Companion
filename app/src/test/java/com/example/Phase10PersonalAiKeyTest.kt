package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiActionType
import com.example.ai.DirectGeminiHttpResponse
import com.example.ai.DirectGeminiInlinePart
import com.example.ai.DirectGeminiRestClient
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.security.KeystoreVaultManager
import com.example.security.PersonalAiKeyState
import com.example.security.PersonalAiKeyStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Phase10PersonalAiKeyTest {

    private fun createTestVaultManager(): KeystoreVaultManager {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val secretKey = keyGen.generateKey()
        return KeystoreVaultManager(testKeyProvider = { secretKey })
    }

    @Test
    fun personalAiKeyStore_encryptsMasksValidatesAndClearsSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        store.clearPersonalKey()

        // Validation guards
        assertTrue(store.savePersonalKey("").isFailure)
        assertTrue(store.savePersonalKey("short_key").isFailure)
        assertTrue(store.savePersonalKey("AIzaSyA12345 678901234567890").isFailure)
        assertFalse(store.hasPersonalKey())

        // Valid save encrypts with LCENC1 envelope and masks preview
        val sampleKey = "AIzaSyTestExampleKey9876543210ABCD9xQ2"
        val saved = store.savePersonalKey(sampleKey).getOrThrow()
        assertTrue(saved.hasPersonalKey)
        assertEquals("AIza••••••9xQ2", saved.maskedPreview)
        assertEquals(PersonalAiKeyState.ENGINE_PERSONAL_KEY, saved.activeEngineLabel)

        // Raw SharedPreferences must contain encrypted envelope, never plaintext
        val rawStored = context.getSharedPreferences(
            PersonalAiKeyStore.PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(PersonalAiKeyStore.KEY_ENCRYPTED_API_KEY, "").orEmpty()
        assertTrue(rawStored.startsWith("LCENC1:"))
        assertFalse(rawStored.contains(sampleKey))

        // Decryption recovers exact key
        assertEquals(sampleKey, store.readPersonalKeyOrNull())

        // Clear removes encrypted key and reverts to default engine
        store.clearPersonalKey()
        assertFalse(store.hasPersonalKey())
        assertNull(store.readPersonalKeyOrNull())
        assertEquals(PersonalAiKeyState.ENGINE_FIREBASE_AI_LOGIC, store.currentState().activeEngineLabel)
    }

    @Test
    fun directGeminiClient_buildsSchemasHandlesMultimodalAndFallsBackOn404() = runBlocking {
        val pdfBytes = "%PDF-1.4 sample".toByteArray()
        val requestJson = DirectGeminiRestClient.buildRequestJson(
            promptText = "Explain this report",
            inlinePart = DirectGeminiInlinePart("application/pdf", pdfBytes),
            responseSchema = DirectGeminiRestClient.reportExplanationResponseSchema()
        )
        val parts = requestJson.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(2, parts.length())
        assertEquals("application/pdf", parts.getJSONObject(0).getJSONObject("inline_data").getString("mime_type"))
        assertEquals("Explain this report", parts.getJSONObject(1).getString("text"))
        assertEquals("application/json", requestJson.getJSONObject("generationConfig").getString("responseMimeType"))
        assertNotNull(DirectGeminiRestClient.assistantResponseSchema().getJSONObject("properties"))
        assertNotNull(DirectGeminiRestClient.prescriptionResponseSchema().getJSONObject("properties"))
        assertNotNull(DirectGeminiRestClient.healthIntelligenceResponseSchema().getJSONObject("properties"))
        assertNotNull(DirectGeminiRestClient.indianNutritionResponseSchema().getJSONObject("properties"))

        // Simulate 404 on default model -> automatic fallback to gemini-3.5-flash-lite
        val calledUrls = mutableListOf<String>()
        val client = DirectGeminiRestClient { url, apiKey, _, _ ->
            calledUrls += url
            assertEquals("AIzaSyFallbackTestKey00000000001234", apiKey)
            if (url.contains(DirectGeminiRestClient.DEFAULT_MODEL)) {
                DirectGeminiHttpResponse(404, """{"error":{"message":"Model not found"}}""")
            } else {
                val okBody = JSONObject().put(
                    "candidates",
                    JSONArray().put(
                        JSONObject().put(
                            "content",
                            JSONObject().put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "AI Ready"))
                            )
                        )
                    )
                ).toString()
                DirectGeminiHttpResponse(200, okBody)
            }
        }

        val pingResult = client.ping("AIzaSyFallbackTestKey00000000001234").getOrThrow()
        assertEquals(2, calledUrls.size)
        assertTrue(calledUrls[0].contains(DirectGeminiRestClient.DEFAULT_MODEL))
        assertTrue(calledUrls[1].contains(DirectGeminiRestClient.FALLBACK_MODEL))
        assertEquals(DirectGeminiRestClient.FALLBACK_MODEL, pingResult.modelUsed)
        assertEquals("AI Ready", pingResult.sampleText)

        // Friendly error mapping without leaking key
        val invalidErr = DirectGeminiRestClient.mapHttpError(
            400,
            """{"error":{"message":"API key not valid. Please pass a valid API key."}}""",
            DirectGeminiRestClient.DEFAULT_MODEL
        )
        assertTrue(invalidErr.contains("invalid", ignoreCase = true))
        val rateLimitErr = DirectGeminiRestClient.mapHttpError(429, "{}", DirectGeminiRestClient.DEFAULT_MODEL)
        assertTrue(rateLimitErr.contains("429"))
    }

    @Test
    fun lifeWeaveAiService_routesThroughPersonalKeyBypassingFirebaseAndAppCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        val testKey = "AIzaSyPersonalRoutingKey1234567890WXYZ"
        store.savePersonalKey(testKey).getOrThrow()

        val fakeAssistantJson = JSONObject()
            .put("assistantReply", "Logged 250 ml of water for you!")
            .put(
                "actions",
                JSONArray().put(
                    JSONObject()
                        .put("actionType", "LOG_WATER")
                        .put("titleOrName", "Water")
                        .put("amountOrValue", 250)
                        .put("secondaryText", "Water")
                )
            )
            .toString()

        val fakeClient = DirectGeminiRestClient { _, receivedKey, body, _ ->
            assertEquals(testKey, receivedKey)
            val replyText = if (body.contains("Reply with two words")) "AI Ready" else fakeAssistantJson
            val envelope = JSONObject().put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", replyText))
                        )
                    )
                )
            ).toString()
            DirectGeminiHttpResponse(200, envelope)
        }

        // Passing null context proves FirebaseApp / AppCheck is completely bypassed when personal key is active
        val service = LifeWeaveAiService(
            context = null,
            personalAiKeyStore = store,
            directGeminiClient = fakeClient
        )

        val outcome = service.processVoiceOrTextCommand(
            userMessage = "I drank 250 ml water",
            languageOption = SupportedVoiceLanguages.options.first(),
            wellnessContextSummary = "Hydration: 0 / 2500 ml"
        ).getOrThrow()

        assertEquals("Logged 250 ml of water for you!", outcome.replyText)
        assertEquals(1, outcome.actions.size)
        assertEquals(AiActionType.LOG_WATER, outcome.actions.first().actionType)
        assertEquals(250, outcome.actions.first().amountOrValue)

        val diag = service.runDiagnostics()
        assertTrue(diag.isHealthy)
        assertTrue(diag.firebaseStatus.contains("Personal Gemini API Key", ignoreCase = true))
        assertTrue(diag.appCheckStatus.contains("Not required", ignoreCase = true))

        store.clearPersonalKey()
    }

    @Test
    fun personalAiKeyStore_acceptsAndMasksNewAqPrefixKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        store.clearPersonalKey()

        val aqKey = "AQ.Ab8RN6ExampleGoogleAIStudioKey12345JyfQ"
        val saved = store.savePersonalKey(aqKey).getOrThrow()
        assertTrue(saved.hasPersonalKey)
        assertEquals("AQ.A••••••JyfQ", saved.maskedPreview)
        assertEquals(aqKey, store.readPersonalKeyOrNull())

        store.clearPersonalKey()
    }

    @Test
    fun directGeminiClient_retries503WithBackoffAndFallsBackToFlashLite() = runBlocking {
        val calls = mutableListOf<String>()
        val delays = mutableListOf<Long>()
        val okEnvelope = JSONObject().put(
            "candidates",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "AI Ready"))
                    )
                )
            )
        ).toString()

        val client = DirectGeminiRestClient(
            delayFn = { ms -> delays += ms },
            httpExecutor = { url, _, _, _ ->
                calls += url
                if (url.contains(DirectGeminiRestClient.DEFAULT_MODEL)) {
                    DirectGeminiHttpResponse(
                        503,
                        """{"error":{"message":"This model is currently experiencing high demand."}}"""
                    )
                } else {
                    DirectGeminiHttpResponse(200, okEnvelope)
                }
            }
        )

        val pingResult = client.ping("AQ.Ab8RN6TestKey1234567890JyfQ").getOrThrow()
        assertEquals(DirectGeminiRestClient.FALLBACK_MODEL, pingResult.modelUsed)
        assertEquals("AI Ready", pingResult.sampleText)
        // 2 attempts on DEFAULT_MODEL (initial + 1 backoff retry), then 1 winning call on FALLBACK_MODEL
        assertEquals(3, calls.size)
        assertTrue(calls[0].contains(DirectGeminiRestClient.DEFAULT_MODEL))
        assertTrue(calls[1].contains(DirectGeminiRestClient.DEFAULT_MODEL))
        assertTrue(calls[2].contains(DirectGeminiRestClient.FALLBACK_MODEL))
        assertEquals(1, delays.size)
        assertTrue(delays.first() > 0L)
    }

    @Test
    fun directGeminiClient_discoversLiveGemini3FlashModelWhenStaticModels503AndRedactsErrors() = runBlocking {
        val secretKey = "AQ.Ab8RN6SuperSecretKey99999JyfQ"
        val postUrls = mutableListOf<String>()
        val getUrls = mutableListOf<String>()

        val modelsListPayload = JSONObject().put(
            "models",
            JSONArray()
                .put(
                    JSONObject()
                        .put("name", "models/gemini-3-tts-preview")
                        .put("supportedGenerationMethods", JSONArray().put("generateContent"))
                )
                .put(
                    JSONObject()
                        .put("name", "models/gemini-3-flash-preview")
                        .put("supportedGenerationMethods", JSONArray().put("generateContent"))
                )
        ).toString()

        val okEnvelope = JSONObject().put(
            "candidates",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "Discovered OK"))
                    )
                )
            )
        ).toString()

        val client = DirectGeminiRestClient(
            httpGetExecutor = { url, key, _ ->
                getUrls += url
                assertEquals(secretKey, key)
                DirectGeminiHttpResponse(200, modelsListPayload)
            },
            delayFn = { _ -> },
            httpExecutor = { url, _, _, _ ->
                postUrls += url
                if (url.contains("gemini-3-flash-preview")) {
                    DirectGeminiHttpResponse(200, okEnvelope)
                } else {
                    DirectGeminiHttpResponse(
                        429,
                        """{"error":{"message":"Quota exceeded for model"}}"""
                    )
                }
            }
        )

        val result = client.generateContent(secretKey, "Hello").getOrThrow()
        assertEquals("Discovered OK", result)
        assertEquals(1, getUrls.size)
        assertEquals(DirectGeminiRestClient.BASE_URL, getUrls.first())
        assertTrue(postUrls.last().contains("gemini-3-flash-preview"))

        // Verify 5xx diagnostics surface sanitized server details and redact any accidental key echo
        val mapped503 = DirectGeminiRestClient.mapHttpError(
            statusCode = 503,
            responseBody = """{"error":{"message":"High demand for key $secretKey on backend"}}""",
            modelName = DirectGeminiRestClient.FALLBACK_MODEL,
            attemptedModels = listOf(DirectGeminiRestClient.DEFAULT_MODEL, DirectGeminiRestClient.FALLBACK_MODEL),
            apiKeyToRedact = secretKey
        )
        assertTrue(mapped503.contains("HTTP 503"))
        assertTrue(mapped503.contains("High demand"))
        assertTrue(mapped503.contains(DirectGeminiRestClient.DEFAULT_MODEL))
        assertTrue(mapped503.contains(DirectGeminiRestClient.FALLBACK_MODEL))
        assertTrue(mapped503.contains("[redacted-key]"))
        assertFalse(mapped503.contains(secretKey))
    }
}
