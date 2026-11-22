package com.mindspark.ai.api;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mindspark.ai.common.dto.AiResponse;
import com.mindspark.ai.common.orchestrator.AiOrchestrator;
import com.mindspark.ai.config.AiCapsProperties;
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
import com.mindspark.ai.provider.model.ChatRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai/ideas")
public class IdeaAiController {

    private final AiOrchestrator orchestrator;
    private final AiCapsProperties capsProperties;

    public IdeaAiController(AiOrchestrator orchestrator, AiCapsProperties capsProperties) {
        this.orchestrator = orchestrator;
        this.capsProperties = capsProperties;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiResponse<GenerateIdeasResult>> generateIdeas(
            @Valid @RequestBody GenerateIdeasRequest request,
            Authentication authentication) {

        int payloadLength = (request.topic() != null ? request.topic().length() : 0)
                + (request.context() != null ? request.context().length() : 0);

        ChatRequest chatRequest = AiPromptBuilders.buildGenerate(request, capsProperties.getMaxOutputTokens());

        AiResponse<GenerateIdeasResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                GenerateIdeasResult.class,
                result -> {
                    if (result.ideas() == null || result.ideas().isEmpty()) {
                        throw new IllegalArgumentException("Model returned no ideas");
                    }
                }
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/improve")
    public ResponseEntity<AiResponse<ImproveIdeaResult>> improveIdea(
            @Valid @RequestBody ImproveIdeaRequest request,
            Authentication authentication) {

        int payloadLength = request.idea().text().length();
        ChatRequest chatRequest = AiPromptBuilders.buildImprove(request, capsProperties.getMaxOutputTokens());

        AiResponse<ImproveIdeaResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                ImproveIdeaResult.class,
                result -> {
                    if (result.text() == null || result.text().isBlank()) {
                        throw new IllegalArgumentException("Model returned empty idea text");
                    }
                }
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/group")
    public ResponseEntity<AiResponse<GroupIdeasResult>> groupIdeas(
            @Valid @RequestBody GroupIdeasRequest request,
            Authentication authentication) {

        int payloadLength = request.ideas().stream().mapToInt(i -> i.text().length()).sum();
        List<String> inputIds = request.ideas().stream().map(GroupIdeasRequest.IdeaItem::id).toList();

        ChatRequest chatRequest = AiPromptBuilders.buildGroup(request, capsProperties.getMaxOutputTokens());

        AiResponse<GroupIdeasResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                GroupIdeasResult.class,
                null
        );

        // Server-side normalization: every input id appears exactly once across groups + ungroupedIds
        GroupIdeasResult normalized = response.result().normalizeWithInputIds(inputIds);
        return ResponseEntity.ok(new AiResponse<>(normalized, response.meta()));
    }

    @PostMapping("/action-plan")
    public ResponseEntity<AiResponse<ActionPlanResult>> actionPlan(
            @Valid @RequestBody ActionPlanRequest request,
            Authentication authentication) {

        int payloadLength = (request.goal() != null ? request.goal().length() : 0)
                + request.ideas().stream().mapToInt(i -> i.text().length()).sum();
        Set<String> validIds = request.ideas().stream().map(ActionPlanRequest.IdeaItem::id).collect(Collectors.toSet());

        ChatRequest chatRequest = AiPromptBuilders.buildActionPlan(request, capsProperties.getMaxOutputTokens());

        AiResponse<ActionPlanResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                ActionPlanResult.class,
                null
        );

        ActionPlanResult normalized = response.result().normalizeAndFilter(validIds);
        return ResponseEntity.ok(new AiResponse<>(normalized, response.meta()));
    }

    @PostMapping("/risks")
    public ResponseEntity<AiResponse<FindRisksResult>> findRisks(
            @Valid @RequestBody FindRisksRequest request,
            Authentication authentication) {

        int payloadLength = request.idea().text().length()
                + (request.context() != null ? request.context().length() : 0);

        ChatRequest chatRequest = AiPromptBuilders.buildRisks(request, capsProperties.getMaxOutputTokens());

        AiResponse<FindRisksResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                FindRisksResult.class,
                null
        );

        FindRisksResult normalized = response.result().normalize();
        return ResponseEntity.ok(new AiResponse<>(normalized, response.meta()));
    }

    @PostMapping("/compare")
    public ResponseEntity<AiResponse<CompareIdeasResult>> compareIdeas(
            @Valid @RequestBody CompareIdeasRequest request,
            Authentication authentication) {

        int payloadLength = request.ideas().stream().mapToInt(i -> i.text().length()).sum();
        Set<String> validIds = request.ideas().stream().map(CompareIdeasRequest.IdeaItem::id).collect(Collectors.toSet());

        ChatRequest chatRequest = AiPromptBuilders.buildCompare(request, capsProperties.getMaxOutputTokens());

        AiResponse<CompareIdeasResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                CompareIdeasResult.class,
                null
        );

        CompareIdeasResult normalized = response.result().filterAndValidate(validIds);
        return ResponseEntity.ok(new AiResponse<>(normalized, response.meta()));
    }
}
