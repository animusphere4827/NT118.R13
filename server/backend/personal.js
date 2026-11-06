import {z} from 'zod';
import {query,insert,transaction} from './db.js';
import {parseId,mediaUrl} from './resources.js';
const fail=(status,message)=>{const e=new Error(message);e.status=status;throw e;};
const pageOf=v=>z.coerce.number().int().min(1).max(100000).default(1).parse(v);
const PAGE=20;
const MAX_REVIEW_INTERVAL_DAYS=365;
const MAX_REPETITIONS=2147483647;
const MAX_EASE_FACTOR=99.99;
const visibleVocabulary = `v.status='PUBLISHED' AND EXISTS (
  SELECT 1 FROM vocabulary_topics t WHERE t.id=v.topic_id AND t.status='PUBLISHED'
)`;
const deckBody=z.object({name:z.string().trim().min(1).max(150),description:z.string().max(2000).default(''),cover_image_url:mediaUrl}).strict();
const cardFields={front_text:z.string().trim().min(1).max(500),back_text:z.string().trim().min(1).max(5000),ipa:z.string().max(150).default(''),example_sentence:z.string().max(2000).default(''),note:z.string().max(2000).default(''),image_url:mediaUrl,is_pinned:z.boolean().default(false)};
const newCard=z.object({vocabulary_id:z.number().int().positive().max(2147483647).nullable().default(null),...cardFields,front_text:cardFields.front_text.optional(),back_text:cardFields.back_text.optional()}).strict().refine(c=>c.vocabulary_id||(c.front_text&&c.back_text),'Cần vocabulary_id hoặc front_text và back_text');
const editCard=z.object(cardFields).strict();
async function ownDeck(id,userId,db){const [d]=await query('SELECT id FROM flashcard_decks WHERE id=? AND user_id=? FOR UPDATE',[id,userId],db);if(!d)fail(404,'Không tìm thấy bộ thẻ');return d;}
async function ownCard(id,userId,db){const [c]=await query('SELECT c.* FROM flashcards c JOIN flashcard_decks d ON d.id=c.deck_id WHERE c.id=? AND d.user_id=? FOR UPDATE OF c',[id,userId],db);if(!c)fail(404,'Không tìm thấy thẻ');return c;}
export function sm2(card,quality) {
 const ease=Number(card.ease_factor);
 const repetitions=quality<3?0:Math.min(card.repetitions+1,MAX_REPETITIONS);
 let interval=1;
 if(repetitions===2)interval=6;
 if(repetitions>2)interval=Math.max(1,Math.min(MAX_REVIEW_INTERVAL_DAYS,Math.round(card.interval_days*ease)));
 const updatedEase=Number((ease+0.1-(5-quality)*(0.08+(5-quality)*0.02)).toFixed(2));
 return {
  ease_factor:Math.min(MAX_EASE_FACTOR,Math.max(1.3,updatedEase)),
  interval_days:interval,
  repetitions
 };
}

export function registerPersonalRoutes(app){
 app.get('/api/learn/dictionary',async(req,res)=>{const q=z.string().trim().min(1).max(150).parse(req.query.q);const rows=await query("SELECT v.id,v.word,v.ipa,v.part_of_speech,v.meaning_vi,v.image_url,t.name AS topic FROM vocabularies v JOIN vocabulary_topics t ON t.id=v.topic_id WHERE v.status='PUBLISHED' AND t.status='PUBLISHED' AND lower(v.word) LIKE lower(?) || '%' ORDER BY lower(v.word)=lower(?) DESC,length(v.word),v.word LIMIT 20",[q.replace(/[\\%_]/g,m=>'\\'+m),q]);res.json({rows});});

 app.get('/api/learn/decks',async(req,res)=>{res.json({rows:await query('SELECT d.*,COUNT(c.id) AS card_count,COUNT(c.id) FILTER (WHERE c.due_at<=now()) AS due_count,COUNT(c.id) FILTER (WHERE c.is_pinned) AS pinned_count FROM flashcard_decks d LEFT JOIN flashcards c ON c.deck_id=d.id WHERE d.user_id=? GROUP BY d.id ORDER BY d.updated_at DESC',[req.user.id])});});
 app.post('/api/learn/decks',async(req,res)=>{const b=deckBody.parse(req.body);res.status(201).json({id:await insert('INSERT INTO flashcard_decks(user_id,name,description,cover_image_url) VALUES(?,?,?,?)',[req.user.id,b.name,b.description,b.cover_image_url])});});
 app.put('/api/learn/decks/:id',async(req,res)=>{const id=parseId(req.params.id),b=deckBody.parse(req.body);const rows=await query('UPDATE flashcard_decks SET name=?,description=?,cover_image_url=? WHERE id=? AND user_id=? RETURNING id',[b.name,b.description,b.cover_image_url,id,req.user.id]);if(!rows.length)fail(404,'Không tìm thấy bộ thẻ');res.sendStatus(204);});
 app.delete('/api/learn/decks/:id',async(req,res)=>{const rows=await query('DELETE FROM flashcard_decks WHERE id=? AND user_id=? RETURNING id',[parseId(req.params.id),req.user.id]);if(!rows.length)fail(404,'Không tìm thấy bộ thẻ');res.sendStatus(204);});

 app.get('/api/learn/decks/:id/cards',async(req,res)=>{
  const id=parseId(req.params.id),page=pageOf(req.query.page);
  const due=req.query.due==='true',pinned=req.query.pinned==='true';
  const order=due?'due_at,id':'id';
  const [{owned,...result}]=await query(`WITH deck AS (
    SELECT id FROM flashcard_decks WHERE id=? AND user_id=?
  ), filtered AS (
    SELECT c.* FROM flashcards c JOIN deck d ON d.id=c.deck_id
    WHERE TRUE ${due?' AND c.due_at<=now()':''}${pinned?' AND c.is_pinned':''}
  ), visible AS (
    SELECT * FROM filtered ORDER BY ${order} LIMIT ? OFFSET ?
  )
  SELECT EXISTS (SELECT 1 FROM deck) AS owned,(SELECT COUNT(*) FROM filtered) AS total,
    COALESCE((SELECT jsonb_agg(visible ORDER BY ${order}) FROM visible),'[]'::jsonb) AS rows`,
  [id,req.user.id,PAGE,(page-1)*PAGE]);
  if(!owned)fail(404,'Không tìm thấy bộ thẻ');
  res.json({...result,page,limit:PAGE});
 });
 app.post('/api/learn/decks/:id/cards',async(req,res)=>{const deckId=parseId(req.params.id),b=newCard.parse(req.body);const id=await transaction(async db=>{await ownDeck(deckId,req.user.id,db);if(b.vocabulary_id){const [v]=await query(`SELECT word,ipa,meaning_vi,example_sentence,image_url FROM vocabularies v WHERE v.id=? AND ${visibleVocabulary}`,[b.vocabulary_id],db);if(!v)fail(404,'Từ vựng không tồn tại');b.front_text??=v.word;b.back_text??=v.meaning_vi;b.ipa||=v.ipa;b.example_sentence||=v.example_sentence;b.image_url??=v.image_url;}const cardId=await insert('INSERT INTO flashcards(deck_id,vocabulary_id,front_text,back_text,ipa,example_sentence,note,image_url,is_pinned) VALUES(?,?,?,?,?,?,?,?,?)',[deckId,b.vocabulary_id,b.front_text,b.back_text,b.ipa,b.example_sentence,b.note,b.image_url,b.is_pinned],db);await query('UPDATE flashcard_decks SET updated_at=now() WHERE id=?',[deckId],db);return cardId;});res.status(201).json({id});});
 app.put('/api/learn/cards/:id',async(req,res)=>{const id=parseId(req.params.id),b=editCard.parse(req.body);await transaction(async db=>{await ownCard(id,req.user.id,db);await query('UPDATE flashcards SET front_text=?,back_text=?,ipa=?,example_sentence=?,note=?,image_url=?,is_pinned=? WHERE id=?',[b.front_text,b.back_text,b.ipa,b.example_sentence,b.note,b.image_url,b.is_pinned,id],db);});res.sendStatus(204);});
 app.delete('/api/learn/cards/:id',async(req,res)=>{const id=parseId(req.params.id);await transaction(async db=>{await ownCard(id,req.user.id,db);await query('DELETE FROM flashcards WHERE id=?',[id],db);});res.sendStatus(204);});
 app.post('/api/learn/cards/:id/review',async(req,res)=>{const id=parseId(req.params.id),{quality}=z.object({quality:z.number().int().min(0).max(5)}).strict().parse(req.body);const result=await transaction(async db=>{const card=await ownCard(id,req.user.id,db);const next=sm2(card,quality);const [row]=await query('UPDATE flashcards SET ease_factor=?,interval_days=?,repetitions=?,due_at=now()+make_interval(days=>?::int),last_reviewed_at=now() WHERE id=? RETURNING id,ease_factor,interval_days,repetitions,due_at,last_reviewed_at',[next.ease_factor,next.interval_days,next.repetitions,next.interval_days,id],db);return row;});res.json(result);});

 app.get('/api/learn/lookups',async(req,res)=>{
  const page=pageOf(req.query.page);
  const [result]=await query(`WITH recent AS (
    SELECT id,keyword,source,lookup_count,first_looked_up_at,last_looked_up_at,vocabulary_id
    FROM lookup_history WHERE user_id=? ORDER BY last_looked_up_at DESC,id DESC LIMIT ? OFFSET ?
  ), visible AS (
    SELECT h.id,h.keyword,h.source,h.lookup_count,h.first_looked_up_at,h.last_looked_up_at,
      v.id AS vocabulary_id,v.ipa,v.meaning_vi
    FROM recent h LEFT JOIN vocabularies v ON v.id=h.vocabulary_id AND ${visibleVocabulary}
  )
  SELECT (SELECT COUNT(*) FROM lookup_history WHERE user_id=?) AS total,
    COALESCE((SELECT jsonb_agg(visible ORDER BY last_looked_up_at DESC,id DESC) FROM visible),'[]'::jsonb) AS rows`,
  [req.user.id,PAGE,(page-1)*PAGE,req.user.id]);
  res.json({...result,page,limit:PAGE});
 });
 app.post('/api/learn/lookups',async(req,res)=>{
  const {keyword,source}=z.object({
    keyword:z.string().trim().min(1).max(150),
    source:z.enum(['DICTIONARY','READING','FLASHCARD']).default('DICTIONARY')
  }).strict().parse(req.body);
  const [row]=await query(`WITH entry AS (
    SELECT v.id FROM vocabularies v WHERE lower(v.word)=lower(?) AND ${visibleVocabulary} ORDER BY v.id LIMIT 1
  ), saved AS (
    INSERT INTO lookup_history(user_id,keyword,vocabulary_id,source) VALUES(?,?,(SELECT id FROM entry),?)
    ON CONFLICT (user_id,(lower(keyword))) DO UPDATE SET lookup_count=lookup_history.lookup_count+1,
      last_looked_up_at=now(),source=EXCLUDED.source,vocabulary_id=COALESCE(EXCLUDED.vocabulary_id,lookup_history.vocabulary_id)
    RETURNING id,lookup_count
  )
  SELECT saved.*,(SELECT id FROM entry) AS vocabulary_id FROM saved`,[keyword,req.user.id,keyword,source]);
  res.status(201).json(row);
 });
 app.delete('/api/learn/lookups/:id',async(req,res)=>{const rows=await query('DELETE FROM lookup_history WHERE id=? AND user_id=? RETURNING id',[parseId(req.params.id),req.user.id]);if(!rows.length)fail(404,'Không tìm thấy');res.sendStatus(204);});
 app.delete('/api/learn/lookups',async(req,res)=>{await query('DELETE FROM lookup_history WHERE user_id=?',[req.user.id]);res.sendStatus(204);});

 app.get('/api/learn/notifications',async(req,res)=>{
  const page=pageOf(req.query.page);
  const unread=req.query.unread==='true';
  const [result]=await query(`WITH visible AS (
    SELECT id,type,title,body,data,is_read,read_at,created_at FROM notifications
    WHERE user_id=?${unread?' AND NOT is_read':''} ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
  )
  SELECT COUNT(*)${unread?' FILTER (WHERE NOT is_read)':''} AS total,
    COUNT(*) FILTER (WHERE NOT is_read) AS unread_count,
    COALESCE((SELECT jsonb_agg(visible ORDER BY created_at DESC,id DESC) FROM visible),'[]'::jsonb) AS rows
  FROM notifications WHERE user_id=?`,[req.user.id,PAGE,(page-1)*PAGE,req.user.id]);
  res.json({...result,page,limit:PAGE});
 });
 app.patch('/api/learn/notifications/:id/read',async(req,res)=>{const rows=await query('UPDATE notifications SET is_read=TRUE,read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=? RETURNING id',[parseId(req.params.id),req.user.id]);if(!rows.length)fail(404,'Không tìm thấy thông báo');res.sendStatus(204);});
 app.post('/api/learn/notifications/read-all',async(req,res)=>{const rows=await query('UPDATE notifications SET is_read=TRUE,read_at=now() WHERE user_id=? AND NOT is_read RETURNING id',[req.user.id]);res.json({updated:rows.length});});
 app.delete('/api/learn/notifications/:id',async(req,res)=>{const rows=await query('DELETE FROM notifications WHERE id=? AND user_id=? RETURNING id',[parseId(req.params.id),req.user.id]);if(!rows.length)fail(404,'Không tìm thấy thông báo');res.sendStatus(204);});
}
