import { readdir, readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { parse } from '@vue/compiler-sfc';
import { baseParse } from '@vue/compiler-dom';
const root = fileURLToPath(new URL('../src/', import.meta.url));
const errors = [];
let files = 0;
async function scan(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const file = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      await scan(file);
      continue;
    }
    if (!file.endsWith('.vue')) continue;
    const template = parse(await readFile(file, 'utf8')).descriptor.template;
    if (!template) continue;
    files++;
    function walk(node) {
      if (
        node.type === 1 &&
        ['input', 'select', 'textarea', 'button', 'BusinessTableColumn'].includes(node.tag)
      )
        errors.push(
          `${file}:${node.loc.start.line}: use Element Plus or its shared business component instead of ${node.tag}`,
        );
      for (const child of node.children || []) walk(child);
    }
    walk(baseParse(template.content));
  }
}
await scan(root);
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else
  console.log(
    `UI control checks passed: ${files} Vue components; no hand-rendered native controls or obsolete column wrappers.`,
  );
