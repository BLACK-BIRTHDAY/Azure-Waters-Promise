package com.ainovel.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ainovel.local.data.model.TaskType
import com.ainovel.local.data.remote.DeepSeekAdapter
import com.ainovel.local.data.remote.GenerationSettings
import com.ainovel.local.data.remote.LogicalContextItem
import com.ainovel.local.data.remote.ProviderMessage
import com.ainovel.local.data.remote.StreamEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 可选真实厂商验收。默认跳过；仅通过 instrumentation argument 临时传入密钥时执行。
 * 不会将密钥写入项目文件、数据库、日志或请求快照。
 */
@RunWith(AndroidJUnit4::class)
class DeepSeekLiveTest {
    @Test
    fun listModelsAndGenerateLowCostText() {
        runBlocking {
            val arguments = InstrumentationRegistry.getArguments()
            val apiKey = arguments.getString("deepseekApiKey").orEmpty()
            assumeTrue("未提供临时 DeepSeek 测试密钥", apiKey.isNotBlank())
            val baseUrl = arguments.getString("deepseekBaseUrl") ?: "https://api.deepseek.com"
            val adapter = DeepSeekAdapter()

            val models = adapter.listModels(baseUrl, apiKey)
            assertTrue("真实模型列表不应为空", models.isNotEmpty())
            val model = models.firstOrNull { it.id == "deepseek-chat" }
                ?: models.firstOrNull { !it.id.contains("reason", ignoreCase = true) && !it.id.contains("think", ignoreCase = true) }
                ?: models.first()
            val prepared = adapter.prepare(
                taskType = TaskType.MODEL_VALIDATION,
                novelId = null,
                targetEntityId = null,
                modelId = model.id,
                logicalContext = listOf(
                    LogicalContextItem(
                        sourceId = "live-validation",
                        sourceType = "VALIDATION",
                        transmittedContent = "请只回复：连接成功",
                        order = 0,
                    ),
                ),
                messages = listOf(ProviderMessage("user", "请只回复：连接成功")),
                settings = GenerationSettings(
                    streamOutput = false,
                    thinkingEnabled = false,
                    temperature = 0.0,
                    maxOutputTokens = 16,
                ),
            )
            assertFalse(prepared.serializedRequestBody.contains(apiKey))
            assertFalse(prepared.redactedRequestBody.contains(apiKey))

            var finished: com.ainovel.local.data.remote.StreamResult? = null
            adapter.stream(baseUrl, apiKey, prepared).collect { event ->
                if (event is StreamEvent.Finished) finished = event.result
            }
            val result = finished
            assertNotNull("厂商应返回结束事件", result)
            assertTrue("真实生成应返回非空最终文本", !result?.content.isNullOrBlank())
            result?.usage?.let { usage ->
                assertTrue(usage.inputTokens > 0)
                assertTrue(usage.outputTokens > 0)
                assertTrue(usage.totalTokens >= usage.inputTokens + usage.outputTokens)
            }
        }
    }
}
