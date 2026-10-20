import fs from 'node:fs';import * as OpenCC from 'opencc-js';
import cn from 'element-plus/es/locale/lang/zh-cn.mjs';import tw from 'element-plus/es/locale/lang/zh-tw.mjs';import en from 'element-plus/es/locale/lang/en.mjs';
const source=JSON.parse(fs.readFileSync('src/i18n/source.json','utf8'));const base={};
function flatten(value,prefix,target){for(const [k,v] of Object.entries(value)){if(v&&typeof v==='object')flatten(v,prefix+k+'.',target);else target[prefix+k]=String(v);}}
flatten(cn,'component.',source);for(const [locale,data] of [['zh-CN',cn],['zh-TW',tw],['en-US',en]]){const m={};flatten(data,'component.',m);base[locale]=m;}
const calendars={
 'zh-CN':{months:'一月|二月|三月|四月|五月|六月|七月|八月|九月|十月|十一月|十二月',monthsShort:'1月|2月|3月|4月|5月|6月|7月|8月|9月|10月|11月|12月',weekdays:'星期日|星期一|星期二|星期三|星期四|星期五|星期六',weekdaysShort:'周日|周一|周二|周三|周四|周五|周六',weekdaysMin:'日|一|二|三|四|五|六'},
 'en-US':{months:'January|February|March|April|May|June|July|August|September|October|November|December',monthsShort:'Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec',weekdays:'Sunday|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday',weekdaysShort:'Sun|Mon|Tue|Wed|Thu|Fri|Sat',weekdaysMin:'Su|Mo|Tu|We|Th|Fr|Sa'}
};
const convert=OpenCC.Converter({from:'cn',to:'tw'});calendars['zh-TW']=Object.fromEntries(Object.entries(calendars['zh-CN']).map(([k,v])=>[k,convert(v)]));
for(const [locale,calendar] of Object.entries(calendars))for(const [k,v]of Object.entries(calendar)){base[locale]['calendar.'+k]=v;if(locale==='zh-CN')source['calendar.'+k]=v;}
fs.writeFileSync('src/i18n/source.json',JSON.stringify(source,null,2)+'\n');fs.writeFileSync('scripts/i18n/component-translations.json',JSON.stringify(base,null,2)+'\n');
