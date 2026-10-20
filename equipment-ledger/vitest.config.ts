import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    server: { deps: { inline: [/element-plus/] } },
    css: true,
    include: ['src/__tests__/**/*.test.ts'],
    restoreMocks: true,
    reporters: ['default', 'junit'],
    outputFile: { junit: 'test-results/frontend.xml' },
    unstubGlobals: true,
  },
});
