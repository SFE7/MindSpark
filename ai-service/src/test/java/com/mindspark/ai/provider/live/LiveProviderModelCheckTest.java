package com.mindspark.ai.provider.live;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.mindspark.ai.common.util.StructuredOutputHelper;
import com.mindspark.ai.config.AiTiersProperties;
import com.mindspark.ai.feature.actionplan.dto.ActionPlanRequest;
import com.mindspark.ai.feature.actionplan.dto.ActionPlanResult;
import com.mindspark.ai.feature.compare.dto.CompareIdeasRequest;
import com.mindspark.ai.feature.compare.dto.CompareIdeasResult;
import com.mindspark.ai.feature.generate.dto.GenerateIdeasRequest;
import com.mindspark.ai.feature.generate.dto.GenerateIdeasResult;
import com.mindspark.ai.feature.group.dto.GroupIdeasRequest;
import com.mindspark.ai.feature.group.dto.GroupIdeasResult;
import com.mindspark.ai.feature.improve.dto.ImproveIdeaRequest;
import com.mindspark.ai.feature.improve.dto.ImproveIdeaResult;
import com.mindspark.ai.feature.prompt.AiPromptBuilders;
import com.mindspark.ai.feature.risks.dto.FindRisksRequest;
import com.mindspark.ai.feature.risks.dto.FindRisksResult;
import com.mindspark.ai.feature.summarize.dto.SummarizeSessionRequest;
import com.mindspark.ai.feature.summarize.dto.SummarizeSessionResult;
import com.mindspark.ai.provider.client.DefaultLlmClientFactory;
import com.mindspark.ai.provider.client.OpenAiCompatibleClient;
import com.mindspark.ai.provider.exception.ProviderException;
import com.mindspark.ai.provider.model.ChatRequest;
import com.mindspark.ai.provider.model.ChatResult;
import com.mindspark.ai.provider.router.ModelRouter;
import com.mindspark.ai.tier.Tier;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_AI_TESTS", matches = "true")
class LiveProviderModelCheckTest {

    @Test
    @DisplayName("Diagnose Groq models with exact HTTP status code and mapped exception type")
    void diagnoseGroqModels() {
        String groqKey = System.getenv("GROQ_API_KEY");

        AiTiersProperties properties = new AiTiersProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient restClient = RestClient.builder().build();

        ChatRequest tinyRequest = ChatRequest.builder()
                .userPrompt("Respond with JSON: {\"status\": \"ok\"}")
                .maxOutputTokens(10)
                .wantJson(true)
                .build();

        System.out.println("tier | configured model id | exact HTTP status | mapped exception type | latency ms | nonEmptyReply (boolean)");

        for (String model : properties.getPremium().getModels()) {
            long start = System.currentTimeMillis();
            String exactStatus;
            String mappedException;
            boolean nonEmptyReply = false;
            try {
                OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                        restClient,
                        properties.getGroqBaseUrl(),
                        groqKey != null ? groqKey : "",
                        model,
                        objectMapper
                );
                ChatResult res = client.complete(tinyRequest);
                exactStatus = "200";
                mappedException = "None";
                nonEmptyReply = res.text() != null && !res.text().isBlank();
            } catch (ProviderException e) {
                exactStatus = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "unknown");
                mappedException = e.getClass().getSimpleName();
            } catch (Exception e) {
                exactStatus = "error";
                mappedException = e.getClass().getSimpleName();
            }
            long latencyMs = System.currentTimeMillis() - start;
            System.out.printf("PREMIUM | %s | %s | %s | %d ms | %s%n",
                    model, exactStatus, mappedException, latencyMs, nonEmptyReply);
        }
    }

    @Test
    @DisplayName("Re-run Gemini gemini-3.5-flash-lite 3 times to measure latency consistency")
    void checkGemini35FlashLiteRepeatedLatency() {
        String geminiKey = System.getenv("GEMINI_API_KEY");

        AiTiersProperties properties = new AiTiersProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient restClient = RestClient.builder().build();

        ChatRequest tinyRequest = ChatRequest.builder()
                .userPrompt("Respond with JSON: {\"status\": \"ok\"}")
                .maxOutputTokens(10)
                .wantJson(true)
                .build();

        String model = "gemini-3.5-flash-lite";
        System.out.println("run # | model id | HTTP status | latency ms | nonEmptyReply");

        for (int i = 1; i <= 3; i++) {
            long start = System.currentTimeMillis();
            String exactStatus;
            boolean nonEmptyReply = false;
            try {
                OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                        restClient,
                        properties.getGeminiBaseUrl(),
                        geminiKey != null ? geminiKey : "",
                        model,
                        objectMapper
                );
                ChatResult res = client.complete(tinyRequest);
                exactStatus = "200";
                nonEmptyReply = res.text() != null && !res.text().isBlank();
            } catch (ProviderException e) {
                exactStatus = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "fail");
            } catch (Exception e) {
                exactStatus = "error";
            }
            long latencyMs = System.currentTimeMillis() - start;
            System.out.printf("Run %d | %s | %s | %d ms | %s%n", i, model, exactStatus, latencyMs, nonEmptyReply);
        }
    }

    @Test
    @DisplayName("Live real-prompt check for all 7 prompt builders across FREE and PREMIUM tiers")
    void checkRealPromptsAllEndpoints() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");

        AiTiersProperties properties = new AiTiersProperties();
        if (geminiKey != null && !geminiKey.isBlank()) {
            properties.setGeminiApiKey(geminiKey);
        }
        if (groqKey != null && !groqKey.isBlank()) {
            properties.setGroqApiKey(groqKey);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        StructuredOutputHelper outputHelper = new StructuredOutputHelper(objectMapper);
        RestClient.Builder restClientBuilder = RestClient.builder();
        DefaultLlmClientFactory clientFactory = new DefaultLlmClientFactory(properties, restClientBuilder, objectMapper);
        ModelRouter router = new ModelRouter(properties, clientFactory);

        record EndpointTest(String name, ChatRequest request, Class<?> resultClass) {}

        List<EndpointTest> tests = List.of(
                // 1. Generate (French)
                new EndpointTest("generate",
                        AiPromptBuilders.buildGenerate(new GenerateIdeasRequest(
                                "Plateforme éducative pour l'apprentissage du code",
                                "Destiné aux enfants de 8 à 14 ans", 3), 600),
                        GenerateIdeasResult.class),

                // 2. Improve (Arabic)
                new EndpointTest("improve",
                        AiPromptBuilders.buildImprove(new ImproveIdeaRequest(
                                new ImproveIdeaRequest.IdeaPayload("id-1", "لوحة تحكم ذكية لإدارة المهام وتتبع الإنتاجية"), "improve"), 600),
                        ImproveIdeaResult.class),

                // 3. Group (English)
                new EndpointTest("group",
                        AiPromptBuilders.buildGroup(new GroupIdeasRequest(List.of(
                                new GroupIdeasRequest.IdeaItem("id-1", "AI powered automated unit testing"),
                                new GroupIdeasRequest.IdeaItem("id-2", "Static code security vulnerability scanner"),
                                new GroupIdeasRequest.IdeaItem("id-3", "Automated marketing email campaigns"))), 600),
                        GroupIdeasResult.class),

                // 4. Summarize (French)
                new EndpointTest("summarize",
                        AiPromptBuilders.buildSummarize(new SummarizeSessionRequest(
                                "Session d'innovation produit", List.of(
                                new SummarizeSessionRequest.SessionIdeaItem("id-1", "Application mobile de covoiturage urbain", 5, "transport"),
                                new SummarizeSessionRequest.SessionIdeaItem("id-2", "Bornes de recharge solaire pour vélos", 3, "energie"))), 600),
                        SummarizeSessionResult.class),

                // 5. Action Plan (Arabic)
                new EndpointTest("actionplan",
                        AiPromptBuilders.buildActionPlan(new ActionPlanRequest(
                                "إطلاق منتج الذكاء الاصطناعي في السوق", List.of(
                                new ActionPlanRequest.IdeaItem("id-1", "بناء النموذج الأولي السريع"),
                                new ActionPlanRequest.IdeaItem("id-2", "اختبار المستخدمين الأوائل"))), 700),
                        ActionPlanResult.class),

                // 6. Risks (English)
                new EndpointTest("risks",
                        AiPromptBuilders.buildRisks(new FindRisksRequest(
                                new FindRisksRequest.IdeaPayload("id-1", "Decentralized crypto payment gateway for global e-commerce"),
                                "Regulatory compliance and volatility"), 600),
                        FindRisksResult.class),

                // 7. Compare (English)
                new EndpointTest("compare",
                        AiPromptBuilders.buildCompare(new CompareIdeasRequest(List.of(
                                new CompareIdeasRequest.IdeaItem("id-1", "Native iOS Swift app"),
                                new CompareIdeasRequest.IdeaItem("id-2", "Flutter cross-platform app"))), 700),
                        CompareIdeasResult.class)
        );

        System.out.println("endpoint | tier | model used | HTTP status | latency | tokens (prompt/completion) | finish_reason | json_valid | validated");

        for (Tier tier : List.of(Tier.FREE, Tier.PREMIUM)) {
            for (EndpointTest test : tests) {
                long start = System.currentTimeMillis();
                String modelUsed = "none";
                String httpStatus = "unknown";
                String tokens = "N/A";
                String finishReason = "none";
                boolean jsonValid = false;
                boolean validated = false;

                try {
                    ChatResult res = router.execute(tier, test.request());
                    modelUsed = res.modelUsed();
                    httpStatus = "200";
                    if (res.tokenUsage() != null) {
                        tokens = res.tokenUsage().promptTokens() + "/" + res.tokenUsage().completionTokens();
                    }
                    if (res.finishReason() != null) {
                        finishReason = res.finishReason();
                    }

                    String raw = res.text();
                    String cleaned = StructuredOutputHelper.cleanJsonFences(raw);
                    JsonNode node = objectMapper.readTree(cleaned);
                    jsonValid = node != null && (node.isObject() || node.isArray());

                    outputHelper.parseAndValidate(raw, test.resultClass(), null);
                    validated = true;
                } catch (ProviderException e) {
                    httpStatus = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "error");
                    modelUsed = e.getClass().getSimpleName();
                } catch (Exception e) {
                    httpStatus = "error";
                    modelUsed = e.getClass().getSimpleName();
                }

                long latencyMs = System.currentTimeMillis() - start;
                System.out.printf("%s | %s | %s | %s | %d ms | %s | %s | %s | %s%n",
                        test.name(), tier, modelUsed, httpStatus, latencyMs, tokens, finishReason, jsonValid, validated);
            }
        }
    }

    @Test
    @DisplayName("Run all 7 PromptBuilders with fallback models configured FIRST (qwen first on PREMIUM, gemini-3.1 first on FREE)")
    void checkFallbackModelsFirst() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");

        ObjectMapper objectMapper = new ObjectMapper();
        StructuredOutputHelper outputHelper = new StructuredOutputHelper(objectMapper);
        RestClient.Builder restClientBuilder = RestClient.builder();

        record EndpointTest(String name, ChatRequest request, Class<?> resultClass) {}

        List<EndpointTest> tests = List.of(
                new EndpointTest("generate",
                        AiPromptBuilders.buildGenerate(new GenerateIdeasRequest(
                                "Plateforme éducative pour l'apprentissage du code",
                                "Destiné aux enfants de 8 à 14 ans", 3), 600),
                        GenerateIdeasResult.class),
                new EndpointTest("improve",
                        AiPromptBuilders.buildImprove(new ImproveIdeaRequest(
                                new ImproveIdeaRequest.IdeaPayload("id-1", "لوحة تحكم ذكية لإدارة المهام وتتبع الإنتاجية"), "improve"), 600),
                        ImproveIdeaResult.class),
                new EndpointTest("group",
                        AiPromptBuilders.buildGroup(new GroupIdeasRequest(List.of(
                                new GroupIdeasRequest.IdeaItem("id-1", "AI powered automated unit testing"),
                                new GroupIdeasRequest.IdeaItem("id-2", "Static code security vulnerability scanner"),
                                new GroupIdeasRequest.IdeaItem("id-3", "Automated marketing email campaigns"))), 600),
                        GroupIdeasResult.class),
                new EndpointTest("summarize",
                        AiPromptBuilders.buildSummarize(new SummarizeSessionRequest(
                                "Session d'innovation produit", List.of(
                                new SummarizeSessionRequest.SessionIdeaItem("id-1", "Application mobile de covoiturage urbain", 5, "transport"),
                                new SummarizeSessionRequest.SessionIdeaItem("id-2", "Bornes de recharge solaire pour vélos", 3, "energie"))), 600),
                        SummarizeSessionResult.class),
                new EndpointTest("actionplan",
                        AiPromptBuilders.buildActionPlan(new ActionPlanRequest(
                                "إطلاق منتج الذكاء الاصطناعي في السوق", List.of(
                                new ActionPlanRequest.IdeaItem("id-1", "بناء النموذج الأولي السريع"),
                                new ActionPlanRequest.IdeaItem("id-2", "اختبار المستخدمين الأوائل"))), 700),
                        ActionPlanResult.class),
                new EndpointTest("risks",
                        AiPromptBuilders.buildRisks(new FindRisksRequest(
                                new FindRisksRequest.IdeaPayload("id-1", "Decentralized crypto payment gateway for global e-commerce"),
                                "Regulatory compliance and volatility"), 600),
                        FindRisksResult.class),
                new EndpointTest("compare",
                        AiPromptBuilders.buildCompare(new CompareIdeasRequest(List.of(
                                new CompareIdeasRequest.IdeaItem("id-1", "Native iOS Swift app"),
                                new CompareIdeasRequest.IdeaItem("id-2", "Flutter cross-platform app"))), 700),
                        CompareIdeasResult.class)
        );

        System.out.println("endpoint | model | status | exception type | latency | finish_reason | json_valid | validated");

        // 1. Qwen first on PREMIUM
        AiTiersProperties qwenProps = new AiTiersProperties();
        if (groqKey != null) qwenProps.setGroqApiKey(groqKey);
        qwenProps.getPremium().setModels(List.of("qwen/qwen3.8-27b", "openai/gpt-oss-120b"));
        DefaultLlmClientFactory qwenFactory = new DefaultLlmClientFactory(qwenProps, restClientBuilder, objectMapper);
        ModelRouter qwenRouter = new ModelRouter(qwenProps, qwenFactory);

        for (EndpointTest test : tests) {
            long start = System.currentTimeMillis();
            String modelUsed = "none";
            String status = "unknown";
            String exType = "None";
            String finishReason = "none";
            boolean jsonValid = false;
            boolean validated = false;
            try {
                ChatResult res = qwenRouter.execute(Tier.PREMIUM, test.request());
                modelUsed = res.modelUsed();
                status = "200";
                if (res.finishReason() != null) finishReason = res.finishReason();
                String raw = res.text();
                JsonNode node = objectMapper.readTree(StructuredOutputHelper.cleanJsonFences(raw));
                jsonValid = node != null;
                outputHelper.parseAndValidate(raw, test.resultClass(), null);
                validated = true;
            } catch (ProviderException e) {
                status = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "error");
                exType = e.getClass().getSimpleName();
            } catch (Exception e) {
                status = "error";
                exType = e.getClass().getSimpleName();
            }
            long latency = System.currentTimeMillis() - start;
            System.out.printf("%s | %s | %s | %s | %d ms | %s | %s | %s%n",
                    test.name(), modelUsed, status, exType, latency, finishReason, jsonValid, validated);
        }

        // 2. Gemini 3.1 first on FREE
        AiTiersProperties geminiProps = new AiTiersProperties();
        if (geminiKey != null) geminiProps.setGeminiApiKey(geminiKey);
        geminiProps.getFree().setModels(List.of("gemini-3.1-flash-lite", "gemini-3.5-flash-lite"));
        DefaultLlmClientFactory geminiFactory = new DefaultLlmClientFactory(geminiProps, restClientBuilder, objectMapper);
        ModelRouter geminiRouter = new ModelRouter(geminiProps, geminiFactory);

        for (EndpointTest test : tests) {
            long start = System.currentTimeMillis();
            String modelUsed = "none";
            String status = "unknown";
            String exType = "None";
            String finishReason = "none";
            boolean jsonValid = false;
            boolean validated = false;
            try {
                ChatResult res = geminiRouter.execute(Tier.FREE, test.request());
                modelUsed = res.modelUsed();
                status = "200";
                if (res.finishReason() != null) finishReason = res.finishReason();
                String raw = res.text();
                JsonNode node = objectMapper.readTree(StructuredOutputHelper.cleanJsonFences(raw));
                jsonValid = node != null;
                outputHelper.parseAndValidate(raw, test.resultClass(), null);
                validated = true;
            } catch (ProviderException e) {
                status = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "error");
                exType = e.getClass().getSimpleName();
            } catch (Exception e) {
                status = "error";
                exType = e.getClass().getSimpleName();
            }
            long latency = System.currentTimeMillis() - start;
            System.out.printf("%s | %s | %s | %s | %d ms | %s | %s | %s%n",
                    test.name(), modelUsed, status, exType, latency, finishReason, jsonValid, validated);
        }
    }

    @Test
    @DisplayName("Gemini latency and reasoning evaluation: 5 runs each for 3.5 and 3.1, with and without reasoning_effort")
    void checkGeminiLatencyFiveRuns() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        AiTiersProperties properties = new AiTiersProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient restClient = RestClient.builder().build();

        ChatRequest request = AiPromptBuilders.buildGenerate(new GenerateIdeasRequest(
                "Plateforme d'apprentissage du code pour debutants",
                "Interface interactive", 2), 500);

        System.out.println("model | reasoning_effort | run # | status | failure_class | latency | tokens (prompt/comp) | finish_reason");

        for (String model : List.of("gemini-3.5-flash-lite", "gemini-3.1-flash-lite")) {
            for (String effort : List.of("low", "none")) {
                for (int run = 1; run <= 5; run++) {
                    long start = System.currentTimeMillis();
                    String status = "200";
                    String failClass = "None";
                    String tokens = "N/A";
                    String finishReason = "none";
                    try {
                        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                                restClient,
                                properties.getGeminiBaseUrl(),
                                geminiKey != null ? geminiKey : "",
                                model,
                                effort.equals("none") ? "" : effort,
                                objectMapper
                        );
                        ChatResult res = client.complete(request);
                        if (res.tokenUsage() != null) {
                            tokens = res.tokenUsage().promptTokens() + "/" + res.tokenUsage().completionTokens();
                        }
                        if (res.finishReason() != null) {
                            finishReason = res.finishReason();
                        }
                    } catch (ProviderException e) {
                        status = String.valueOf(e.getStatusCode() != null ? e.getStatusCode() : "error");
                        failClass = e.getClass().getSimpleName();
                    } catch (Exception e) {
                        status = "error";
                        failClass = e.getClass().getSimpleName();
                    }
                    long latency = System.currentTimeMillis() - start;
                    System.out.printf("%s | %s | run %d | %s | %s | %d ms | %s | %s%n",
                            model, effort, run, status, failClass, latency, tokens, finishReason);
                }
            }
        }
    }
}
