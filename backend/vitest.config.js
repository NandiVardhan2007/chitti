import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    testTimeout: 30_000,
    hookTimeout: 300_000, // the first run downloads the MongoDB binary for mongodb-memory-server
  },
});
