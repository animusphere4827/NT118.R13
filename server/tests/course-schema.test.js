import {test, after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {pool, transaction} from '../backend/db.js';
import {applyMigrations} from '../backend/migration-runner.js';
import {populateCourse} from '../backend/course-seed.js';

after(() => pool.end());

test('Course migration and seed preserve content and reject invalid lesson targets', async () => {
  await transaction(async db => {
    const schema = 'course_' + randomUUID().replaceAll('-','');
    await db.query('CREATE SCHEMA ' + schema);
    await db.query('SET LOCAL search_path TO ' + schema);
    await applyMigrations(db);
    await applyMigrations(db);
    assert.deepEqual(await populateCourse(db), {added:0});
    const {rows:[topic]} = await db.query("INSERT INTO vocabulary_topics(name,status) VALUES('Office','PUBLISHED') RETURNING id");
    await db.query("INSERT INTO vocabularies(topic_id,word,meaning_vi,status) VALUES($1,'invoice','hóa đơn','PUBLISHED')",[topic.id]);
    assert.deepEqual(await populateCourse(db), {added:1});
    await db.query("UPDATE course_lessons SET title='Edited by teacher'");
    assert.deepEqual(await populateCourse(db), {added:0});
    const {rows:[lesson]} = await db.query('SELECT * FROM course_lessons');
    assert.equal(lesson.title,'Edited by teacher');
    assert.equal(lesson.vocabulary_topic_id,topic.id);
    for (const statement of [
      "UPDATE course_lessons SET type='READING'",
      'UPDATE course_lessons SET vocabulary_topic_id=NULL',
      'UPDATE course_lessons SET minutes=0',
      'DELETE FROM vocabulary_topics WHERE id=' + topic.id
    ]) {
      await db.query('SAVEPOINT invalid_course');
      await assert.rejects(db.query(statement), error => ['23514','23503'].includes(error.code));
      await db.query('ROLLBACK TO SAVEPOINT invalid_course');
    }
    await db.query('SET LOCAL search_path TO public');
    await db.query('DROP SCHEMA ' + schema + ' CASCADE');
  });
});
