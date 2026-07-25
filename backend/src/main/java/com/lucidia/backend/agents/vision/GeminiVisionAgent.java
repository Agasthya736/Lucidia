package com.lucidia.backend.agents.vision;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import com.lucidia.backend.agents.RetryHelper;

@Service
public class GeminiVisionAgent implements VisionAgent {

    private static final String SYSTEM_PROMPT = """
        You are a radiology assistant analyzing a CT scan image for documentation
        purposes only - you do not provide diagnoses, only observations for a
        clinician to review. Respond in this exact format:

        SUMMARY: <one paragraph overall impression>
        OBSERVATIONS: <bullet list of discrete notable features, one per line, prefixed with "-">
        REGION: <rough anatomical location of the primary finding>
        CONFIDENCE: <a number from 0.0 to 1.0 representing your certainty>
        """;

    private final ChatClient chatClient;

    public GeminiVisionAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public VisionFindings analyze(byte[] imageBytes, String mimeType) {
        Media imageMedia = new Media(
                MimeTypeUtils.parseMimeType(mimeType),
                new ByteArrayResource(imageBytes)
        );

        UserMessage userMessage = UserMessage.builder()
                .text("Analyze this CT scan.")
                .media(List.of(imageMedia))
                .build();

        // Retries the configured model (set in application.yml) up to 3 times
        // with exponential backoff before giving up - handles transient
        // rate limits/network issues without needing per-call model switching.
        String response = RetryHelper.callWithFallback(
                List.of("default"),
                ignored -> callModel(userMessage),
                3,
                1000
        );

        return VisionFindingsParser.parse(response, providerName());
    }

    private String callModel(UserMessage userMessage) {
        return chatClient.prompt(new Prompt(List.of(userMessage)))
                .system(SYSTEM_PROMPT)
                .call()
                .content();
    }

    @Override
    public String providerName() {
        return "gemini-2.5-flash";
    }
}