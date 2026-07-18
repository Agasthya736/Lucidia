package com.lucidia.backend.agents.vision;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

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
                new org.springframework.core.io.ByteArrayResource(imageBytes)
        );

        UserMessage userMessage = UserMessage.builder()
                .text("Analyze this CT scan.")
                .media(List.of(imageMedia))
                .build();

        String response = chatClient.prompt(new Prompt(List.of(userMessage)))
                .system(SYSTEM_PROMPT)
                .call()
                .content();

        return VisionFindingsParser.parse(response, providerName());
    }

    @Override
    public String providerName() {
        return "gemini-2.5-flash";
    }
}