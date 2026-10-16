import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const root = fileURLToPath(new URL('../src/', import.meta.url));
const registry = readFileSync(join(root, 'components/common/AppIcon.vue'), 'utf8');
const names = new Set(
  [...registry.matchAll(/import (\w+) from '@icon-park\/vue-next\/es\/icons\/(\w+)';/g)].map(
    (m) => m[1],
  ),
);
let components = 0,
  usages = 0;
function visit(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const file = join(dir, entry.name);
    if (entry.isDirectory()) {
      visit(file);
      continue;
    }
    if (!file.endsWith('.vue')) continue;
    const source = readFileSync(file, 'utf8');
    if (entry.name === 'TemplateThumbnail.vue') continue; // Printable document graphics, not UI icons.
    assert(!/<svg\b/.test(source), `Handwritten UI SVG: ${file}`);
    assert(!/[▦▣⚒◷✓▧⚙▤⌕↕↗←→‹›«»✕✎▸▾＋⇢⇣↩⇄↶♙⌘⌄☰⚿⌖]/.test(source), `Legacy UI icon glyph: ${file}`);
    assert(!/[·•]{3}/.test(source), `Legacy more glyph: ${file}`);
    for (const match of source.matchAll(/<AppIcon\b[^>]*?(?<!:)\bname="([^"]+)"/g)) {
      if (match[1].startsWith("'")) continue;
      assert(names.has(match[1]), `Unknown icon ${match[1]} in ${file}`);
      usages++;
    }
    if (source.includes('<AppIcon')) components++;
  }
}
visit(root);
const navigation = readFileSync(join(root, 'WorkspaceLayout.vue'), 'utf8');
for (const m of navigation.matchAll(/icon: '([^']+)'/g))
  assert(names.has(m[1]), `Unknown navigation icon ${m[1]}`);
console.log(
  `Icon checks passed: ${names.size} official IconPark mappings, ${components} components, ${usages} static usages; no handwritten UI SVG or legacy glyph icons.`,
);
