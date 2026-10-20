import fs from 'node:fs';import * as OpenCC from 'opencc-js';
const source=JSON.parse(fs.readFileSync('src/i18n/source.json','utf8'));
const components=JSON.parse(fs.readFileSync('scripts/i18n/component-translations.json','utf8'));
const terms=JSON.parse(fs.readFileSync('scripts/i18n/terminology-en.json','utf8'));
const draft=fs.existsSync('scripts/i18n/en-draft.json')?JSON.parse(fs.readFileSync('scripts/i18n/en-draft.json','utf8')):{};
const convert=OpenCC.Converter({from:'cn',to:'tw'});const zh={},tw={},en={};
for(const [key,value]of Object.entries(source)){
 zh[key]=value;tw[key]=components['zh-TW'][key]??convert(value);en[key]=components['en-US'][key]??terms[value]??draft[value]??value;
}
for(const [locale,data]of [['zh-CN',zh],['zh-TW',tw],['en-US',en]]){
 fs.writeFileSync('src/i18n/'+locale+'.json',JSON.stringify(data,null,2)+'\n');
 fs.writeFileSync('backend/src/main/resources/i18n/'+locale+'.json',JSON.stringify(data,null,2)+'\n');
}
const missing=Object.entries(en).filter(([key,value])=>!key.startsWith('component.')&&!key.startsWith('calendar.')&&/[\u3400-\u9fff]/.test(value));
console.log(JSON.stringify({entries:Object.keys(source).length,englishMissing:missing.length,missing:missing.slice(0,12)}));
