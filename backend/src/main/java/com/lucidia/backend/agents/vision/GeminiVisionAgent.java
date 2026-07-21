package com.lucidia.backend.agents.vision;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

@Service
public class GeminiVisionAgent implements VisionAgent {

    private static final String SYSTEM_PROMPT = """
        You are a radiology assistant analyzing a CT scan image for documentation
        purposes only - you do not provide diagnoses, only observations for a
        clinician to review. Respond in this exact format:

        SUMMARY: <one paragraph overall impression>
        OBSERVATIONS: <bullet list of discrete notable features>
        REGION: <rough anatomical location>
        CONFIDENCE: <0.0 to 1.0>
        """;

    private final ChatClient chatClient;

    public GeminiVisionAgent(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build(); // ✅ KEEP SIMPLE
    }

    @Override
    public VisionFindings analyze(byte[] imageBytes, String mimeType) {

        try {
            Media imageMedia = new Media(
                    MimeTypeUtils.parseMimeType(mimeType),
                    new ByteArrayResource(imageBytes)
            );

            UserMessage userMessage = UserMessage.builder()
                    .text("Analyze this CT scan image.")
                    .media(List.of(imageMedia))
                    .build();

            String response = chatClient
                    .prompt(new Prompt(List.of(userMessage)))
                    .system(SYSTEM_PROMPT)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new RuntimeException("Empty response from Gemini");
            }

            return VisionFindingsParser.parse(response, providerName());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Gemini Vision failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "gemini-2.5-flash";
    }
}