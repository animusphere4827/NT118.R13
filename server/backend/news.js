import {Router} from 'express';
import {z} from 'zod';
import {query} from './db.js';
import {parseId} from './resources.js';

// Bài đọc tiếng Anh từ RSS The Guardian. Chỉ lưu tiêu đề + đoạn tóm tắt mà RSS công khai,
// luôn kèm tên nguồn và link bài gốc để người học mở đọc toàn văn trên trang của báo.
export const NEWS_SOURCE = 'The Guardian';
export const NEWS_FEEDS = [
  {category:'BUSINESS',url:'https://www.theguardian.com/business/rss'},
  {category:'TECHNOLOGY',url:'https://www.theguardian.com/technology/rss'},
  {category:'WORLD',url:'https://www.theguardian.com/world/rss'},
  {category:'SCIENCE',url:'https://www.theguardian.com/science/rss'},
  {category:'ENVIRONMENT',url:'https://www.theguardian.com/environment/rss'},
  {category:'TRAVEL',url:'https://www.theguardian.com/travel/rss'}
];
export const NEWS_CATEGORIES = NEWS_FEEDS.map(feed => feed.category);
const MIN_SUMMARY = 200;
const KEEP_DAYS = 60;
const REFRESH_MS = 3 * 60 * 60 * 1000;

const ENTITIES = {amp:'&',lt:'<',gt:'>',quot:'"',apos:"'",nbsp:' ',hellip:'…',mdash:'—',ndash:'–',lsquo:'‘',rsquo:'’',ldquo:'“',rdquo:'”'};
export function decodeEntities(text) {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi,(match,code)=>{
    if (code[0]==='#') {
      const value = code[1].toLowerCase()==='x' ? parseInt(code.slice(2),16) : parseInt(code.slice(1),10);
      return Number.isFinite(value) && value>0 && value<0x110000 ? String.fromCodePoint(value) : '';
    }
    return ENTITIES[code.toLowerCase()] ?? match;
  });
}

function tag(block,name) {
  const match = new RegExp(`<${name}(?:\\s[^>]*)?>([\\s\\S]*?)</${name}>`,'i').exec(block);
  if (!match) return '';
  const raw = match[1].trim();
  const cdata = /^<!\[CDATA\[([\s\S]*)\]\]>$/.exec(raw);
  return cdata ? cdata[1] : decodeEntities(raw);
}

/** HTML của đoạn tóm tắt -> văn bản thuần, mỗi đoạn cách nhau một dòng trống. Bỏ link "Continue reading...". */
export function htmlToText(html) {
  const text = html
    .replace(/<a\b[^>]*>\s*Continue reading[\s\S]*?<\/a>/gi,'')
    .replace(/<li\b[^>]*>/gi,'\n• ')
    .replace(/<\/(p|li|ul|ol|h\d|div)>|<br\s*\/?>/gi,'\n')
    .replace(/<[^>]+>/g,'');
  return decodeEntities(text).split('\n').map(line=>line.replace(/\s+/g,' ').trim()).filter(Boolean)
    .filter(line=>!/^continue reading/i.test(line)).join('\n\n');
}

/** Đọc RSS 2.0: trả về danh sách bài {guid,title,summary,url,image_url,image_credit,published_at}. */
export function parseFeed(xml) {
  const items = [];
  for (const [,block] of xml.matchAll(/<item\b[^>]*>([\s\S]*?)<\/item>/gi)) {
    const title = htmlToText(tag(block,'title'));
    const url = tag(block,'link').trim();
    const guid = (tag(block,'guid') || url).trim();
    const summary = htmlToText(tag(block,'description'));
    const date = new Date(tag(block,'pubDate'));
    let image = null, width = -1, credit = null;
    for (const [whole,attrs] of block.matchAll(/<media:content\b([^>]*)>([\s\S]*?<\/media:content>)?/gi)) {
      const src = /\burl="([^"]+)"/i.exec(attrs)?.[1];
      const w = Number(/\bwidth="(\d+)"/i.exec(attrs)?.[1] || 0);
      if (src && w>width) { image=decodeEntities(src); width=w; credit=htmlToText(tag(whole,'media:credit'))||null; }
    }
    if (!title || !/^https:\/\//.test(url) || !guid || Number.isNaN(date.getTime())) continue;
    items.push({guid:guid.slice(0,1000),title:title.slice(0,400),summary,url:url.slice(0,1000),
      image_url:image && /^https:\/\//.test(image) ? image.slice(0,1000) : null,image_credit:credit?.slice(0,300) ?? null,
      published_at:date.toISOString()});
  }
  return items;
}

const words = text => (text.match(/[A-Za-z]+(?:['’-][A-Za-z]+)*/g) || []).length;

/** Tải mọi mục RSS, thêm bài mới (bỏ qua bài đã có), xóa bài cũ hơn KEEP_DAYS. Lỗi của một mục không chặn mục khác. */
export async function refreshNews(fetchImpl=fetch) {
  let added = 0;
  for (const feed of NEWS_FEEDS) {
    try {
      const response = await fetchImpl(feed.url,{headers:{'User-Agent':'TOEIC4US-reader/1.0 (+https://toeic4us.duckdns.org)'},
        signal:AbortSignal.timeout(15000)});
      if (!response.ok) throw new Error('HTTP '+response.status);
      for (const item of parseFeed(await response.text())) {
        if (item.summary.length<MIN_SUMMARY) continue;
        const rows = await query(`INSERT INTO news_articles(source,category,guid,title,summary,url,image_url,image_credit,word_count,published_at)
          VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT (guid) DO NOTHING RETURNING id`,
        [NEWS_SOURCE,feed.category,item.guid,item.title,item.summary,item.url,item.image_url,item.image_credit,words(item.summary),item.published_at]);
        added += rows.length;
      }
    } catch (error) {
      console.error('News feed failed:',feed.category,error.message);
    }
  }
  await query(`DELETE FROM news_articles WHERE published_at < now() - make_interval(days => ?::int)`,[KEEP_DAYS]);
  return added;
}

/** Tải lúc khởi động (sau 10 giây) và mỗi 3 giờ. Đặt NEWS_REFRESH=off để tắt (ví dụ máy không có Internet). */
export function startNewsRefresh() {
  if (process.env.NEWS_REFRESH==='off') return ()=>{};
  const run = () => refreshNews().then(n=>{ if (n) console.log('News articles added:',n); })
    .catch(error=>console.error('News refresh failed:',error.message));
  const first = setTimeout(run,10000); first.unref();
  const timer = setInterval(run,REFRESH_MS); timer.unref();
  return ()=>{ clearTimeout(first); clearInterval(timer); };
}

const listQuery = z.object({
  page:z.coerce.number().int().min(1).max(100000).default(1),
  limit:z.coerce.number().int().min(1).max(50).default(20),
  category:z.enum(NEWS_CATEGORIES).optional()
}).strict();
const columns = 'id,source,category,title,summary,url,image_url,image_credit,word_count,published_at';

export function registerNewsRoutes(app) {
  const router = Router();
  router.use((req,res,next)=>{res.set('Cache-Control','no-store');next();});
  router.get('/news',async(req,res)=>{
    const {page,limit,category} = listQuery.parse(req.query);
    const [result] = await query(`WITH filtered AS (
        SELECT ${columns} FROM news_articles ${category ? 'WHERE category=?' : ''}
      ), page AS (SELECT * FROM filtered ORDER BY published_at DESC,id DESC LIMIT ? OFFSET ?)
      SELECT (SELECT COUNT(*) FROM filtered) AS total,
        COALESCE((SELECT jsonb_agg(page ORDER BY published_at DESC,id DESC) FROM page),'[]'::jsonb) AS rows`,
    [...(category ? [category] : []),limit,(page-1)*limit]);
    res.json({...result,page,limit,categories:NEWS_CATEGORIES});
  });
  router.get('/news/:id',async(req,res)=>{
    const [row] = await query(`SELECT ${columns} FROM news_articles WHERE id=?`,[parseId(req.params.id)]);
    if (!row) return res.status(404).json({error:'Không tìm thấy bài đọc'});
    res.json(row);
  });
  app.use('/api/learn',router);
}
