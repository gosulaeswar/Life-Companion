package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiActionType
import com.example.ai.GrokApiClient
import com.example.ai.GrokHttpResponse
import com.example.ai.GrokInlinePart
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

    private fun grokOkBody(assistantText: String): String =
        JSONObject()
            .put("id", "chatcmpl-test-123")
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("index", 0)
                        .put(
                            "message",
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", assistantText)
                        )
                )
            )
            .toString()

    @Test
    fun personalAiKeyStore_encryptsMasksValidatesAndClearsGrokKeysSafely() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        store.clearPersonalKey()

        // Validation guards
        assertTrue(store.savePersonalKey("").isFailure)
        assertTrue(store.savePersonalKey("short_key").isFailure)
        assertTrue(store.savePersonalKey("xai-12345 678901234567890").isFailure)
        assertFalse(store.hasPersonalKey())

        // Valid save encrypts with LCENC1 envelope and masks preview
        val sampleKey = "xai-TestExampleGrokKey9876543210ABCD9xQ2"
        val saved = store.savePersonalKey(sampleKey).getOrThrow()
        assertTrue(saved.hasPersonalKey)
        assertEquals("xai-••••••9xQ2", saved.maskedPreview)
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

        // Clear removes encrypted key and reverts to default Grok engine label
        store.clearPersonalKey()
        assertFalse(store.hasPersonalKey())
        assertNull(store.readPersonalKeyOrNull())
        assertEquals(PersonalAiKeyState.defaultEngineLabel(), store.currentState().activeEngineLabel)
    }

    @Test
    fun grokApiClient_buildsMultimodalSchemasHandles404FailoverAndStrictSchemaDowngrade() = runBlocking {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val requestJson = GrokApiClient.buildChatCompletionRequestJson(
            modelName = GrokApiClient.DEFAULT_VISION_MODEL,
            promptText = "Explain this lab report",
            inlinePart = GrokInlinePart("image/jpeg", jpegBytes),
            schemaName = "lifeweave_report_explanation",
            responseSchema = GrokApiClient.reportExplanationResponseSchema(),
            useStrictJsonSchema = true
        )
        assertEquals(GrokApiClient.DEFAULT_VISION_MODEL, requestJson.getString("model"))
        val messages = requestJson.getJSONArray("messages")
        assertEquals(2, messages.length()) // system + user
        val userContent = messages.getJSONObject(1).getJSONArray("content")
        assertEquals(2, userContent.length())
        assertEquals("text", userContent.getJSONObject(0).getString("type"))
        assertEquals("image_url", userContent.getJSONObject(1).getString("type"))
        assertTrue(
            userContent.getJSONObject(1).getJSONObject("image_url").getString("url")
                .startsWith("data:image/jpeg;base64,")
        )
        assertEquals("json_schema", requestJson.getJSONObject("response_format").getString("type"))
        assertNotNull(GrokApiClient.assistantResponseSchema().getJSONObject("properties"))
        assertNotNull(GrokApiClient.prescriptionResponseSchema().getJSONObject("properties"))
        assertNotNull(GrokApiClient.healthIntelligenceResponseSchema().getJSONObject("properties"))
        assertNotNull(GrokApiClient.indianNutritionResponseSchema().getJSONObject("properties"))

        // Simulate 404 on DEFAULT_TEXT_MODEL -> automatic failover to FALLBACK_TEXT_MODEL
        val requestedModels = mutableListOf<String>()
        val client = GrokApiClient(
            delayFn = { _ -> },
            httpExecutor = { endpointUrl, apiKey, bodyJson, _ ->
                assertEquals(GrokApiClient.CHAT_COMPLETIONS_URL, endpointUrl)
                assertEquals("xai-FallbackTestKey00000000001234", apiKey)
                val model = JSONObject(bodyJson).getString("model")
                requestedModels += model
                if (model == GrokApiClient.DEFAULT_TEXT_MODEL) {
                    GrokHttpResponse(404, """{"error":{"message":"Model not found"}}""")
                } else {
                    GrokHttpResponse(200, grokOkBody("Grok Ready"))
                }
            }
        )

        val pingResult = client.ping("xai-FallbackTestKey00000000001234").getOrThrow()
        assertEquals(2, requestedModels.size)
        assertEquals(GrokApiClient.DEFAULT_TEXT_MODEL, requestedModels[0])
        assertEquals(GrokApiClient.FALLBACK_TEXT_MODEL, requestedModels[1])
        assertEquals(GrokApiClient.FALLBACK_TEXT_MODEL, pingResult.modelUsed)
        assertEquals("Grok Ready", pingResult.sampleText)

        // Simulate HTTP 400 rejecting strict json_schema -> automatic downgrade to json_object
        val responseFormatTypes = mutableListOf<String>()
        val downgradeClient = GrokApiClient(
            delayFn = { _ -> },
            httpExecutor = { _, _, bodyJson, _ ->
                val fmtType = JSONObject(bodyJson).getJSONObject("response_format").getString("type")
                responseFormatTypes += fmtType
                if (fmtType == "json_schema") {
                    GrokHttpResponse(400, """{"error":{"message":"json_schema response_format unsupported"}}""")
                } else {
                    GrokHttpResponse(200, grokOkBody("""```json\n{"assistantReply":"Hydration logged","actions":[]}\n```"""))
                }
            }
        )
        val rawResult = downgradeClient.generateContent(
            apiKey = "xai-SchemaDowngradeTestKey1234567890",
            promptText = "Test prompt",
            schemaName = "lifeweave_assistant",
            responseSchema = GrokApiClient.assistantResponseSchema()
        ).getOrThrow()
        assertEquals(listOf("json_schema", "json_object"), responseFormatTypes)
        assertEquals("""{"assistantReply":"Hydration logged","actions":[]}""", rawResult)

        // Friendly error mapping
        val deniedErr = GrokApiClient.mapHttpError(
            401,
            """{"error":{"message":"Incorrect API key provided"}}""",
            GrokApiClient.DEFAULT_TEXT_MODEL
        )
        assertTrue(deniedErr.contains("rejected", ignoreCase = true))
        val rateLimitErr = GrokApiClient.mapHttpError(429, "{}", GrokApiClient.DEFAULT_TEXT_MODEL)
        assertTrue(rateLimitErr.contains("429"))
    }

    @Test
    fun lifeWeaveAiService_routesThroughPersonalGrokKeyAndReportsDiagnostics() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        val testKey = "xai-PersonalRoutingKey1234567890WXYZ"
        store.savePersonalKey(testKey).getOrThrow()

        val fakeAssistantJson = JSONObject()
            .put("assistantReply", "Logged 250 ml of water via Grok!")
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

        val fakeClient = GrokApiClient(
            delayFn = { _ -> },
            httpExecutor = { _, receivedKey, body, _ ->
                assertEquals(testKey, receivedKey)
                val replyText = if (body.contains("Grok Ready")) "Grok Ready" else fakeAssistantJson
                GrokHttpResponse(200, grokOkBody(replyText))
            }
        )

        val service = LifeWeaveAiService(
            context = null,
            personalAiKeyStore = store,
            grokClient = fakeClient,
            buildConfigKeyProvider = { "" }
        )

        val outcome = service.processVoiceOrTextCommand(
            userMessage = "I drank 250 ml water",
            languageOption = SupportedVoiceLanguages.options.first(),
            wellnessContextSummary = "Hydration: 0 / 2500 ml"
        ).getOrThrow()

        assertEquals("Logged 250 ml of water via Grok!", outcome.replyText)
        assertEquals(1, outcome.actions.size)
        assertEquals(AiActionType.LOG_WATER, outcome.actions.first().actionType)
        assertEquals(250, outcome.actions.first().amountOrValue)

        val diag = service.runDiagnostics()
        assertTrue(diag.isHealthy)
        assertTrue(diag.providerStatus.contains("xAI Grok", ignoreCase = true))
        assertTrue(diag.authStatus.contains("Personal xAI Grok Key", ignoreCase = true))
        assertTrue(diag.grokStatus.contains("Connected", ignoreCase = true))

        store.clearPersonalKey()
    }

    @Test
    fun grokApiClient_retries503WithBackoffAndFallsBackToGrok3Mini() = runBlocking {
        val modelsCalled = mutableListOf<String>()
        val delays = mutableListOf<Long>()

        val client = GrokApiClient(
            delayFn = { ms -> delays += ms },
            httpExecutor = { _, _, bodyJson, _ ->
                val model = JSONObject(bodyJson).getString("model")
                modelsCalled += model
                if (model == GrokApiClient.DEFAULT_TEXT_MODEL) {
                    GrokHttpResponse(
                        503,
                        """{"error":{"message":"Service temporarily overloaded"}}"""
                    )
                } else {
                    GrokHttpResponse(200, grokOkBody("Grok Ready"))
                }
            }
        )

        val pingResult = client.ping("xai-Retry503TestKey1234567890ABCD").getOrThrow()
        assertEquals(GrokApiClient.FALLBACK_TEXT_MODEL, pingResult.modelUsed)
        assertEquals("Grok Ready", pingResult.sampleText)
        // 2 attempts on grok-3 (initial + 1 backoff retry), then 1 winning call on grok-3-mini
        assertEquals(
            listOf(
                GrokApiClient.DEFAULT_TEXT_MODEL,
                GrokApiClient.DEFAULT_TEXT_MODEL,
                GrokApiClient.FALLBACK_TEXT_MODEL
            ),
            modelsCalled
        )
        assertEquals(1, delays.size)
        assertTrue(delays.first() > 0L)
    }

    @Test
    fun grokApiClient_discoversLiveModelViaV1ModelsAndRedactsKeysInErrors() = runBlocking {
        val secretKey = "xai-SuperSecretGrokKey99999ABCD"
        val modelsCalled = mutableListOf<String>()
        val getUrls = mutableListOf<String>()

        val modelsListPayload = JSONObject().put(
            "data",
            JSONArray()
                .put(JSONObject().put("id", "grok-2-image-1212")) // excluded imagine/image-gen or lower priority
                .put(JSONObject().put("id", "grok-4-fast-non-reasoning"))
                .put(JSONObject().put("id", "grok-3-beta"))
        ).toString()

        val client = GrokApiClient(
            httpGetExecutor = { url, key, _ ->
                getUrls += url
                assertEquals(secretKey, key)
                GrokHttpResponse(200, modelsListPayload)
            },
            delayFn = { _ -> },
            httpExecutor = { _, _, bodyJson, _ ->
                val model = JSONObject(bodyJson).getString("model")
                modelsCalled += model
                if (model == "grok-4-fast-non-reasoning") {
                    GrokHttpResponse(200, grokOkBody("Discovered Grok OK"))
                } else {
                    GrokHttpResponse(429, """{"error":{"message":"Quota exceeded for model"}}""")
                }
            }
        )

        val result = client.generateContent(secretKey, "Hello Grok").getOrThrow()
        assertEquals("Discovered Grok OK", result)
        assertEquals(1, getUrls.size)
        assertEquals(GrokApiClient.MODELS_URL, getUrls.first())
        assertEquals("grok-4-fast-non-reasoning", modelsCalled.last())

        // Verify 5xx diagnostics surface sanitized server details and redact any accidental key echo
        val mapped503 = GrokApiClient.mapHttpError(
            statusCode = 503,
            responseBody = """{"error":{"message":"High demand for key $secretKey on xAI backend"}}""",
            modelName = GrokApiClient.FALLBACK_TEXT_MODEL,
            attemptedModels = listOf(GrokApiClient.DEFAULT_TEXT_MODEL, GrokApiClient.FALLBACK_TEXT_MODEL),
            apiKeyToRedact = secretKey
        )
        assertTrue(mapped503.contains("HTTP 503"))
        assertTrue(mapped503.contains("High demand"))
        assertTrue(mapped503.contains(GrokApiClient.DEFAULT_TEXT_MODEL))
        assertTrue(mapped503.contains(GrokApiClient.FALLBACK_TEXT_MODEL))
        assertTrue(mapped503.contains("[redacted-key]"))
        assertFalse(mapped503.contains(secretKey))
    }
}
