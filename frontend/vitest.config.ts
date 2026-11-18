import { defineConfig } from 'vitest/config';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

function angularInlineResources() {
  return {
    name: 'angular-inline-resources',
    transform(code: string, id: string) {
      if (!id.endsWith('.ts')) return;
      let transformed = code;
      transformed = transformed.replace(/templateUrl:\s*['"`]([^'"`]+)['"`]/g, (_, relPath) => {
        const fullPath = resolve(dirname(id), relPath);
        if (existsSync(fullPath)) {
          const content = JSON.stringify(readFileSync(fullPath, 'utf-8'));
          return `template: ${content}`;
        }
        return `template: ""`;
      });
      transformed = transformed.replace(/styleUrl:\s*['"`][^'"`]+['"`]/g, 'styles: []');
      transformed = transformed.replace(/styleUrls:\s*\[[^\]]*\]/g, 'styles: []');
      return { code: transformed, map: null };
    },
  };
}

export default defineConfig({
  plugins: [angularInlineResources()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
  },
});
