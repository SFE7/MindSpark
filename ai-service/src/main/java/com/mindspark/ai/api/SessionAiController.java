package com.mindspark.ai.api;

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
import com.mindspark.ai.feature.prompt.AiPromptBuilders;
import com.mindspark.ai.feature.summarize.dto.SummarizeSessionRequest;
import com.mindspark.ai.feature.summarize.dto.SummarizeSessionResult;
import com.mindspark.ai.provider.model.ChatRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai/sessions")
public class SessionAiController {

    private final AiOrchestrator orchestrator;
    private final AiCapsProperties capsProperties;

    public SessionAiController(AiOrchestrator orchestrator, AiCapsProperties capsProperties) {
        this.orchestrator = orchestrator;
        this.capsProperties = capsProperties;
    }

    @PostMapping("/summarize")
    public ResponseEntity<AiResponse<SummarizeSessionResult>> summarizeSession(
            @Valid @RequestBody SummarizeSessionRequest request,
            Authentication authentication) {

        int payloadLength = (request.sessionTitle() != null ? request.sessionTitle().length() : 0)
                + request.ideas().stream().mapToInt(i -> i.text().length()).sum();
        Set<String> validIds = request.ideas().stream().map(SummarizeSessionRequest.SessionIdeaItem::id).collect(Collectors.toSet());

        ChatRequest chatRequest = AiPromptBuilders.buildSummarize(request, capsProperties.getMaxOutputTokens());

        AiResponse<SummarizeSessionResult> response = orchestrator.execute(
                authentication,
                payloadLength,
                chatRequest,
                SummarizeSessionResult.class,
                null
        );

        SummarizeSessionResult normalized = response.result().filterUnknownIds(validIds);
        return ResponseEntity.ok(new AiResponse<>(normalized, response.meta()));
    }
}
