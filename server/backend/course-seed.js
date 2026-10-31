import {query, transaction} from './db.js';

const units = [
  ['vocabulary','Từ vựng theo chủ đề','Xây dựng vốn từ TOEIC từ nội dung đã xuất bản.'],
  ['grammar','Ngữ pháp ứng dụng','Ôn cấu trúc thường gặp trong Part 5 và Part 6.'],
  ['part5','Luyện câu Part 5','Vận dụng từ vựng và ngữ pháp vào câu hoàn chỉnh.'],
  ['reading','Đọc hiểu thực tế','Luyện đọc các nhóm đoạn văn trong bộ luyện Part 6 và Part 7.'],
  ['mock','Thi thử Reading','Tổng hợp kiến thức với các đề Reading đã xuất bản.']
];

export async function populateCourse(db) {
    await db.query('SELECT pg_advisory_xact_lock(118,21)');
    for (const [index,[slug,title,description]] of units.entries()) {
      await query(`INSERT INTO course_units(slug,title,description,position,status,unlock_group)
        VALUES(?,?,?,?,'PUBLISHED',?) ON CONFLICT (slug) DO NOTHING`,
      [slug,title,description,(index+1)*10,slug],db);
    }
    const sources = [
      ['vocabulary','VOCABULARY','vocabulary_topic_id',10,
        `SELECT t.id,t.name AS title,t.description AS subtitle FROM vocabulary_topics t
         WHERE t.status='PUBLISHED' AND EXISTS (
           SELECT 1 FROM vocabularies v WHERE v.topic_id=t.id AND v.status='PUBLISHED')`],
      ['grammar','GRAMMAR','grammar_lesson_id',10,
        `SELECT l.id,l.title,t.name AS subtitle FROM grammar_lessons l
         JOIN grammar_topics t ON t.id=l.topic_id WHERE l.status='PUBLISHED' AND t.status='PUBLISHED'`],
      ['part5','PRACTICE_PART5','practice_set_id',10,
        `SELECT id,title,description AS subtitle FROM practice_sets WHERE status='PUBLISHED' AND part_id=5`],
      ['reading','READING','reading_group_id',8,
        `SELECT id,title,instructions AS subtitle FROM published_readings`],
      ['mock','MOCK_TEST','mock_test_id',75,
        `SELECT id,title,description AS subtitle FROM mock_tests WHERE status='PUBLISHED'`]
    ];
    let added = 0;
    for (const [slug,type,column,minutes,source] of sources) {
      const rows = await query(`WITH unit AS (
          SELECT id FROM course_units WHERE slug=?
        ), base AS (
          SELECT COALESCE(MAX(position),0) AS last FROM course_lessons WHERE unit_id=(SELECT id FROM unit)
        ), candidates AS (
          SELECT s.* FROM (${source}) s
          WHERE NOT EXISTS (SELECT 1 FROM course_lessons l WHERE l.seed_key=? || ':' || s.id::text)
        )
        INSERT INTO course_lessons(unit_id,seed_key,title,subtitle,type,minutes,position,status,${column})
        SELECT unit.id,? || ':' || c.id::text,c.title,c.subtitle,?, ?,
          base.last+row_number() OVER (ORDER BY c.id),'PUBLISHED',c.id
        FROM candidates c CROSS JOIN unit CROSS JOIN base RETURNING id`,
      [slug,slug,slug,type,minutes],db);
      added += rows.length;
    }
    return {added};
}

export const seedCourse = () => transaction(populateCourse);
