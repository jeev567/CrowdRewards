import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxy avoids cross-origin problems in local development.
// React calls /api/... and Vite forwards the request to Spring Boot.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://localhost:8080" } }
});
