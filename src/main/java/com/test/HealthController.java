package com.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Health check endpoint for container liveness and readiness probes.
 * Exposes GET /health returning HTTP 200 with JSON status.
 */
public class HealthController {

    private static final int HEALTH_PORT = Integer.parseInt(
            System.getenv().getOrDefault("HEALTH_CHECK_PORT", "8081"));

    private HttpServer server;

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(HEALTH_PORT), 0);
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Health check endpoint started on port " + HEALTH_PORT + " at /health");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String response = "{\"status\":\"UP\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
