package org.project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Scanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AIQuizApp {

    static final String API_KEY = System.getenv("GROQ_API_KEY");
    static final String MODEL = "openai/gpt-oss-20b";

    static final String API_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        Scanner input = new Scanner(System.in);

        System.out.println(" JAVA QUIZ ");


        System.out.print("Enter Java topic: ");
        String topic = input.nextLine().trim();

        if (topic.isEmpty()) {
            System.out.println("Topic cannot be empty.");
            input.close();
            return;
        }

        try {

            JsonNode[] quiz = generateQuiz(topic);

            int score = 0;

            for (int i = 0; i < quiz.length; i++) {

                JsonNode question = quiz[i];

                System.out.println("\nQuestion " + (i + 1));
                System.out.println(question.get("question").asText());

                JsonNode options = question.get("options");

                System.out.println("A. " + options.get("A").asText());
                System.out.println("B. " + options.get("B").asText());
                System.out.println("C. " + options.get("C").asText());
                System.out.println("D. " + options.get("D").asText());

                System.out.print("Your answer (A/B/C/D): ");

                String userAnswer = input.nextLine()
                        .trim()
                        .toUpperCase();

                String correctAnswer =
                        question.get("answer").asText().toUpperCase();

                if (userAnswer.equals(correctAnswer)) {

                    System.out.println("Correct answer!");
                    score++;

                } else {

                    System.out.println("Wrong answer.");
                    System.out.println(
                            "Correct answer: " + correctAnswer
                    );
                }
            }


            System.out.println("          QUIZ RESULT");

            System.out.println(
                    "Your score: " + score + " out of " + quiz.length
            );

            System.out.println(
                    "Percentage: " + (score * 100 / quiz.length) + "%"
            );

        } catch (Exception e) {

            System.out.println("\nQuiz generation failed.");
            System.out.println("Error: " + e.getMessage());

        } finally {
            input.close();
        }
    }

    static JsonNode[] generateQuiz(String topic) throws Exception {

        if (API_KEY == null || API_KEY.isBlank()) {
            throw new RuntimeException(
                    "API_KEY environment variable is missing."
            );
        }

        // Create AI prompt
        String prompt =
                "Generate exactly 5 beginner-level Java multiple-choice  "
                        + "questions about: " + topic + ". "
                        + "Each question must have exactly four options. "
                        + "There must be exactly one correct answer. "
                        + "Return only valid JSON in this structure: "
                        + "{\"questions\":["
                        + "{\"question\":\"Question text\","
                        + "\"options\":{\"A\":\"Option 1\","
                        + "\"B\":\"Option 2\","
                        + "\"C\":\"Option 3\","
                        + "\"D\":\"Option 4\"},"
                        + "\"answer\":\"A\"}]}";

        ObjectNode requestBody = mapper.createObjectNode();

        requestBody.put("model", MODEL);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 1500);

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");

        requestBody.set("response_format", responseFormat);

        ArrayNode messages = mapper.createArrayNode();

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);

        messages.add(message);

        requestBody.set("messages", messages);

        String requestJson =
                mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        System.out.println("\nConnecting to Groq AI...");

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println(
                "API Status: " + response.statusCode()
        );

        if (response.statusCode() != 200) {

            throw new RuntimeException(
                    "API Error: " + response.body()
            );
        }

        JsonNode root = mapper.readTree(response.body());

        JsonNode choices = root.path("choices");

        if (!choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException(
                    "No choices returned by Groq."
            );
        }

        String aiContent = choices.get(0)
                .path("message")
                .path("content")
                .asText("");

        if (aiContent.isBlank()) {
            throw new RuntimeException(
                    "Groq returned an empty response."
            );
        }

        // Parse AI-generated JSON
        JsonNode quizRoot = mapper.readTree(aiContent);

        JsonNode questions = quizRoot.path("questions");

        if (!questions.isArray() || questions.size() != 5) {
            throw new RuntimeException(
                    "AI did not generate exactly 5 questions."
            );
        }
        JsonNode[] result = new JsonNode[5];

        for (int i = 0; i < 5; i++) {

            JsonNode q = questions.get(i);

            if (!q.path("question").isTextual()) {
                throw new RuntimeException(
                        "Question " + (i + 1) + " is invalid."
                );
            }

            JsonNode options = q.path("options");

            if (!options.path("A").isTextual()
                    || !options.path("B").isTextual()
                    || !options.path("C").isTextual()
                    || !options.path("D").isTextual()) {

                throw new RuntimeException(
                        "Options are missing in question " + (i + 1)
                );
            }

            String answer = q.path("answer")
                    .asText("")
                    .trim()
                    .toUpperCase();

            if (!answer.matches("[ABCD]")) {
                throw new RuntimeException(
                        "Invalid correct answer in question " + (i + 1)
                );
            }

            result[i] = q;
        }

        System.out.println("AI quiz generated successfully!");

        return result;
    }
}

