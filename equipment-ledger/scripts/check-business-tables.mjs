import { readdir, readFile } from 'node:fs/promises';
import { resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from '@vue/compiler-sfc';
import { baseParse } from '@vue/compiler-dom';
const root = fileURLToPath(new URL('../src/', import.meta.url));
const exceptions = new Set(['PrintGridDesigner.vue', 'RolePermissionEditor.vue']);
const errors = [];
let pages = 0,
  tables = 0;
async function scan(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = resolve(dir, entry.name);
    if (entry.isDirectory()) {
      await scan(path);
      continue;
    }
    if (!path.endsWith('.vue')) continue;
    const source = await readFile(path, 'utf8');
    const template = parse(source).descriptor.template;
    if (!template) continue;
    let count = 0;
    function walk(node) {
      if (node.type === 1) {
        if (node.tag === 'table' && !exceptions.has(basename(path))) {
          errors.push(`${path}: ordinary tables must use AppBusinessTable`);
        }
        if (node.tag === 'AppBusinessTable') count++;
        if (
          node.props.some(
            (prop) =>
              prop.type === 6 &&
              prop.name === 'class' &&
              /(?:^|\s)sort-mark(?:\s|$)/.test(prop.value?.content || ''),
          )
        )
          errors.push(`${path}: use Element Plus sort controls, not page-owned sort icons`);
        if (node.tag === 'ElTableColumn') {
          const header = node.children.find(
            (child) =>
              child.type === 1 &&
              child.tag === 'template' &&
              child.props.some(
                (prop) => prop.type === 7 && prop.name === 'slot' && prop.arg?.content === 'header',
              ),
          );
          const label =
            node.props.find((prop) => prop.type === 6 && prop.name === 'label')?.value?.content ||
            header?.loc.source.replace(/<[^>]+>/g, '').trim();
          if (label === '操作') {
            const fixed = node.props.find((prop) => prop.type === 6 && prop.name === 'fixed')?.value
              ?.content;
            const width = node.props.find(
              (prop) => prop.type === 7 && prop.arg?.content === 'width',
            )?.exp?.content;
            if (fixed !== 'right' || (width && Number(width) < 156))
              errors.push(`${path}: action column needs fixed right and width >= 156`);
          }
        }
      }
      for (const child of node.children || []) walk(child);
    }
    walk(baseParse(template.content));
    if (count) {
      pages++;
      tables += count;
    }
  }
}
await scan(root);
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else
  console.log(
    `Business table checks passed: ${tables} tables in ${pages} components; native tables limited to print canvas and permission matrix.`,
  );
