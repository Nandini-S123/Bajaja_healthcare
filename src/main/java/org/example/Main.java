package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.ApplicationRunner;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import java.time.Duration;

import org.example.dto.GenerateWebhookRequest;
import org.example.dto.GenerateWebhookResponse;
import org.example.model.QuerySolution;
import org.example.repo.QuerySolutionRepository;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ApplicationRunner run(WebClient webClient, QuerySolutionRepository repo) {
        return args -> {
            System.out.println("🚀 Starting Bajaj Finserv webhook flow...");

            // 1️⃣ Prepare the request
            GenerateWebhookRequest req = new GenerateWebhookRequest(
                    "John Doe", "REG12347", "john@example.com"
            );

            String url = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

            // 2️⃣ Generate webhook
            GenerateWebhookResponse resp = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(GenerateWebhookResponse.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (resp == null) {
                System.err.println("❌ No response from API");
                return;
            }

            System.out.println("✅ Webhook: " + resp.getWebhook());
            System.out.println("✅ Token: " + resp.getAccessToken());

            // 3️⃣ Read SQL query from resources
            String sql = readSQL();
            if (sql == null) {
                System.err.println("⚠️ final-query.sql not found on classpath!");
                return;
            }

            // 4️⃣ Save SQL in DB
            QuerySolution sol = new QuerySolution();
            sol.setRegNo(req.getRegNo());
            sol.setFinalQuery(sql);
            repo.save(sol);

            System.out.println("✅ SQL stored locally");

            // 5️⃣ Submit SQL to webhook
            try {
                String response = webClient.post()
                        .uri(resp.getWebhook())
                        .header(HttpHeaders.AUTHORIZATION, resp.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(java.util.Map.of("finalQuery", sql))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                System.out.println("✅ Submitted: " + response);
            } catch (Exception e) {
                System.err.println("❌ Submission failed: " + e.getMessage());
            }
        };
    }

    // ✅ Safe method to read final-query.sql from inside the JAR
    private static String readSQL() {
        try (var inputStream = Main.class.getResourceAsStream("/final-query.sql")) {
            if (inputStream == null) {
                System.err.println("⚠️ final-query.sql not found on classpath!");
                return null;
            }
            return new String(inputStream.readAllBytes());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
