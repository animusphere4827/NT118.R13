import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import pg from 'pg';
import request from 'supertest';
import {applyMigrations} from '../backend/migration-runner.js';

const schema='news_'+randomUUID().replaceAll('-','');
const bootstrap=new pg.Client({connectionString:process.env.DATABASE_URL});
await bootstrap.connect();
await bootstrap.query('BEGIN');
await bootstrap.query('CREATE SCHEMA '+schema);
await bootstrap.query('SET LOCAL search_path TO '+schema);
await applyMigrations(bootstrap);
await bootstrap.query('COMMIT');
const connection=new URL(process.env.DATABASE_URL);
connection.searchParams.set('options','-c search_path='+schema+' -c TimeZone=UTC');
process.env.DATABASE_URL=connection.toString();
const {app}=await import('../backend/app.js');
const {query,pool}=await import('../backend/db.js');
const {refreshNews,parseFeed,htmlToText,NEWS_FEEDS}=await import('../backend/news.js');
const api=request(app);
after(async()=>{await pool.end();await bootstrap.query('DROP SCHEMA '+schema+' CASCADE');await bootstrap.end();});

const paragraph='Ministers announced a new plan to help small businesses hire young workers, offering grants for training and mentoring programmes across the country.';
const feed=(guid,extra='')=>`<?xml version="1.0"?><rss xmlns:media="http://search.yahoo.com/mrss/"><channel><title>Feed</title>
<item><title>Firms &amp; workers welcome &#8216;jobs plan&#8217;</title><link>https://example.com/${guid}</link>
<description>&lt;p&gt;${paragraph}&lt;/p&gt;&lt;p&gt;Business groups said the scheme &lt;a href="https://example.com/x"&gt;could help&lt;/a&gt; thousands of people.&lt;/p&gt; &lt;a href="https://example.com/${guid}"&gt;Continue reading...&lt;/a&gt;</description>
<pubDate>Mon, 09 Nov 2026 06:00:03 GMT</pubDate><guid>https://example.com/${guid}</guid>
<media:content width="140" url="https://img.example.com/small.jpg"><media:credit>Photo: A</media:credit></media:content>
<media:content width="460" url="https://img.example.com/large.jpg?w=460&amp;q=85"><media:credit>Photo: B</media:credit></media:content>
</item>${extra}</channel></rss>`;

test('RSS parser keeps paragraphs, decodes entities and drops the continue link',()=>{
  const [item]=parseFeed(feed('one'));
  assert.equal(item.title,'Firms & workers welcome ‘jobs plan’');
  assert.equal(item.summary,paragraph+'\n\nBusiness groups said the scheme could help thousands of people.');
  assert.equal(item.image_url,'https://img.example.com/large.jpg?w=460&q=85');
  assert.equal(item.image_credit,'Photo: B');
  assert.equal(item.published_at,'2026-11-09T06:00:03.000Z');
  assert.equal(htmlToText('<ul><li>One</li><li>Two</li></ul>'),'• One\n\n• Two');
  assert.equal(parseFeed('<item><title>No link</title><description>x</description></item>').length,0);
});

test('refresh stores new articles once and the learner API lists them',async()=>{
  const short='<item><title>Short</title><link>https://example.com/short</link><description>Too short.</description><pubDate>Mon, 09 Nov 2026 07:00:00 GMT</pubDate></item>';
  const fetchFake=async url=>{
    if(url===NEWS_FEEDS[1].url)return {ok:false,status:503,text:async()=>''};
    return {ok:true,status:200,text:async()=>feed(url.includes('business')?'business-story':'other-'+encodeURIComponent(url),short)};
  };
  const added=await refreshNews(fetchFake);
  assert.equal(added,NEWS_FEEDS.length-1);
  assert.equal(await refreshNews(fetchFake),0);
  const credentials={email:'news@example.com',password:'News-reader-password-1'};
  await api.post('/api/auth/register').send({...credentials,full_name:'Reader'});
  const token=(await api.post('/api/auth/login').send(credentials)).body.token;
  const auth={Authorization:'Bearer '+token};
  assert.equal((await api.get('/api/learn/news')).status,401);
  const list=await api.get('/api/learn/news?limit=50').set(auth);
  assert.equal(list.status,200);
  assert.equal(list.body.total,NEWS_FEEDS.length-1);
  assert.ok(list.body.rows.every(row=>row.source==='The Guardian'&&row.word_count>20));
  const business=await api.get('/api/learn/news?category=BUSINESS').set(auth);
  assert.equal(business.body.total,1);
  assert.equal((await api.get('/api/learn/news?category=SPORT').set(auth)).status,422);
  const one=await api.get('/api/learn/news/'+business.body.rows[0].id).set(auth);
  assert.equal(one.body.url,'https://example.com/business-story');
  assert.equal((await api.get('/api/learn/news/999999').set(auth)).status,404);
  await query("UPDATE news_articles SET published_at=now()-interval '90 days' WHERE category='WORLD'");
  await refreshNews(async()=>({ok:false,status:500,text:async()=>''}));
  assert.equal((await api.get('/api/learn/news?category=WORLD').set(auth)).body.total,0);
});
