import {randomUUID} from 'node:crypto';
import {query} from './db.js';
import {validateQuizQuestion} from './quiz-model.js';

const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};

// Loại bài lộ trình -> cột nội dung tương ứng (mỗi bài trỏ đúng một nội dung)
export const LESSON_CONTENT={VOCABULARY:'vocabulary_topic_id',GRAMMAR:'grammar_lesson_id',PRACTICE_PART5:'practice_set_id',
  READING:'reading_group_id',MOCK_TEST:'mock_test_id'};

/** Kiểm tra trước khi thêm/sửa quiz và lộ trình trong web admin. Có thể sửa b (ví dụ sinh seed_key). */
export async function checkCourseQuiz(t,b,id,old,db) {
  if (t==='quiz_sets') {
    if (b.vocabulary_topic_id && b.grammar_lesson_id) fail(422,'Bộ quiz chỉ gắn một chủ đề từ vựng hoặc một bài ngữ pháp');
    if (b.status==='PUBLISHED') {
      if (!id) fail(422,'Lưu bản nháp và thêm câu hỏi trước khi xuất bản');
      const questions=await query('SELECT type,prompt,payload,solution,explanation FROM quiz_questions WHERE quiz_set_id=? ORDER BY position',[id],db);
      if (!questions.length) fail(422,'Bộ quiz cần ít nhất một câu hỏi trước khi xuất bản');
      for (const [index,q] of questions.entries()) {
        try { validateQuizQuestion(q); } catch { fail(422,`Câu ${index+1} chưa hợp lệ, sửa lại trước khi xuất bản`); }
      }
    }
  }
  if (t==='quiz_questions') {
    try {
      validateQuizQuestion({type:b.type,prompt:b.prompt,payload:b.payload,solution:b.solution,explanation:b.explanation});
    } catch (error) {
      if (error.status) throw error;
      fail(422,'Nội dung hoặc đáp án JSON không đúng cấu trúc của dạng '+b.type+': '+
        (error.issues?.slice(0,2).map(i=>(i.path.join('.')||'gốc')+' '+i.message).join('; ')||error.message));
    }
    const [set]=await query('SELECT status FROM quiz_sets WHERE id=? FOR UPDATE',[b.quiz_set_id],db);
    if (!set) fail(422,'Bộ quiz không tồn tại');
    if (old && old.quiz_set_id!==b.quiz_set_id) fail(422,'Không chuyển câu hỏi sang bộ quiz khác; hãy tạo câu mới');
  }
  if (t==='course_units' && old && b.slug!==old.slug) fail(422,'Không đổi mã phần đã tạo vì lộ trình tự sinh dùng mã này');
  if (t==='course_lessons') {
    const column=LESSON_CONTENT[b.type];
    for (const other of Object.values(LESSON_CONTENT)) if (other!==column) b[other]=null;
    if (!b[column]) fail(422,'Chọn nội dung cho loại bài này');
    if (b.type==='PRACTICE_PART5') {
      const [set]=await query('SELECT part_id FROM practice_sets WHERE id=?',[b.practice_set_id],db);
      if (!set || set.part_id!==5) fail(422,'Bài Part 5 cần chọn bộ luyện Part 5');
    }
    if (old && old.type!==b.type) {
      const [progress]=await query('SELECT 1 FROM course_vocabulary_progress WHERE lesson_id=? LIMIT 1',[id],db);
      if (progress) fail(409,'Bài đã có học viên học, không đổi loại bài; hãy tạo bài mới');
    }
    if (!old) b.seed_key='admin:'+randomUUID();
  }
}

/** Chặn xóa khi sẽ làm mất lịch sử học của học viên; khuyên chuyển về bản nháp. */
export async function checkCourseQuizDelete(t,old,db) {
  if (t==='quiz_sets' && (await query('SELECT 1 FROM quiz_attempts WHERE quiz_set_id=? LIMIT 1',[old.id],db)).length) {
    fail(409,'Bộ quiz đã có bài làm của học viên. Chuyển về bản nháp để ẩn thay vì xóa');
  }
  if (t==='quiz_questions' && (await query('SELECT 1 FROM quiz_answers WHERE question_id=? LIMIT 1',[old.id],db)).length) {
    fail(409,'Câu hỏi đã có trong bài làm của học viên. Chuyển bộ quiz về bản nháp hoặc sửa câu thay vì xóa');
  }
  if (t==='course_lessons' && (await query('SELECT 1 FROM course_vocabulary_progress WHERE lesson_id=? LIMIT 1',[old.id],db)).length) {
    fail(409,'Bài đã có tiến độ học của học viên. Chuyển về bản nháp để ẩn thay vì xóa');
  }
  if (t==='course_units' && (await query(`SELECT 1 FROM course_lessons l JOIN course_vocabulary_progress p ON p.lesson_id=l.id
      WHERE l.unit_id=? LIMIT 1`,[old.id],db)).length) {
    fail(409,'Phần có bài đã có tiến độ học của học viên. Chuyển về bản nháp để ẩn thay vì xóa');
  }
}
