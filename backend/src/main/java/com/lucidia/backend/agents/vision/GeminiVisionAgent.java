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
    clinician to review.

    Be precise and cautious:
    - Describe only what is visually present (shape, density, location,
      borders, size relative to surrounding structures). Do not name a
      specific disease or condition (e.g. do not say "adenocarcinoma" or
      "pneumonia") - describe the morphology instead (e.g. "an irregular,
      spiculated hyperdense mass").
    - If you are not confident which anatomical region or organ you are
      looking at, say so explicitly rather than guessing. It is better to
      report uncertainty than to state a wrong anatomical location with
      false confidence.
    - Your CONFIDENCE score must reflect your actual certainty. If the
      image is ambiguous, ill-defined, or you are unsure of the anatomy,
      use a low score (below 0.5). Do not default to a high score.

    Respond in this exact format:

    SUMMARY: <one paragraph overall impression>
    OBSERVATIONS: <bullet list of discrete notable features, one per line, prefixed with "-">
    REGION: <rough anatomical location of the primary finding, or "uncertain" if unclear>
    BBOX: <a tight bounding box around the single most significant finding, as
    four comma-separated integers x1,y1,x2,y2 on a 0-1000 scale relative to
    the image width and height (top-left is 0,0; bottom-right is 1000,1000).
    If there is no single discrete finding to box (e.g. the scan is normal,
    or the finding is diffuse/whole-organ), write "unknown" instead.>
    CONFIDENCE: <a number from 0.0 to 1.0 representing your genuine certainty>
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