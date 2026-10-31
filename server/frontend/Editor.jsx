import React,{useEffect,useState} from 'react';
import {api} from './api.js';
import {Dialog} from './Dialog.jsx';
import {RelationSelect} from './RelationSelect.jsx';

export const choices={
  status:[['DRAFT','Bản nháp'],['PUBLISHED','Đã xuất bản']],testFormat:[['MINI','Đề mini'],['FULL','Đề đầy đủ · 100 câu']],
  difficulty:[['EASY','Dễ'],['MEDIUM','Trung bình'],['HARD','Khó']],role:[['STUDENT','Học viên'],['ADMIN','Quản trị viên']],
  userStatus:[['ACTIVE','Hoạt động'],['LOCKED','Đã khóa'],['DISABLED','Vô hiệu hóa']],
  notificationType:[['SYSTEM','Hệ thống'],['STUDY_REMINDER','Nhắc học'],['NEW_CONTENT','Nội dung mới'],['RESULT','Kết quả'],['ACHIEVEMENT','Thành tựu']],
  passageType:[['EMAIL','Email'],['LETTER','Thư'],['MEMO','Bản ghi nhớ'],['NOTICE','Thông báo'],['ADVERTISEMENT','Quảng cáo'],['ARTICLE','Bài báo'],['FORM','Biểu mẫu'],['SCHEDULE','Lịch trình'],['INVOICE','Hóa đơn'],['CHAT','Tin nhắn'],['WEB_PAGE','Trang web'],['REVIEW','Đánh giá'],['OTHER','Khác']],
  questionType:[['WORD_FORM','Từ loại'],['VERB_TENSE','Thì động từ'],['PREPOSITION','Giới từ'],['CONJUNCTION','Liên từ'],['PRONOUN','Đại từ'],['VOCABULARY','Từ vựng'],['COLLOCATION','Collocation'],['SENTENCE_INSERTION','Điền câu'],['MAIN_IDEA','Ý chính'],['DETAIL','Chi tiết'],['INFERENCE','Suy luận'],['VOCAB_IN_CONTEXT','Từ trong ngữ cảnh'],['NOT_TRUE','NOT true'],['PURPOSE','Mục đích'],['OTHER','Khác']]
};
export const emptyOptions=()=>[...'ABCD'].map((label,index)=>({label,option_text:'',is_correct:index===0}));
export function SelectField({label,value,onChange,options,required=true}) {
  return <label>{label}<select required={required} value={value} onChange={event=>onChange(event.target.value)}>
    {options.map(([id,title])=><option key={id} value={id}>{title}</option>)}
  </select></label>;
}
export function QuestionOptions({value,onChange,name}) {
  return <fieldset className="options"><legend>Đáp án · chọn đúng một đáp án đúng</legend>
    {value.map((option,index)=><label className="option-row" key={option.label}>
      <input type="radio" name={name} aria-label={'Đáp án đúng '+option.label} checked={option.is_correct}
        onChange={()=>onChange(value.map((row,i)=>({...row,is_correct:i===index})))}/><b>{option.label}</b>
      <input aria-label={'Nội dung đáp án '+option.label} required maxLength={60000} value={option.option_text}
        onChange={event=>onChange(value.map((row,i)=>i===index?{...row,option_text:event.target.value}:row))}/>
    </label>)}
  </fieldset>;
}
export function Editor({resource,meta,record,onClose,onSaved}) {
  const [values,setValues]=useState({}),[options,setOptions]=useState(emptyOptions);
  const [initial,setInitial]=useState(''),[version,setVersion]=useState('');
  const [loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[loadError,setLoadError]=useState(''),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    let live=true;
    setLoading(true);setLoadError('');
    async function load() {
      try {
        const data=record?await api(`/admin/${resource}/${record.id}`):{};
        const init=Object.fromEntries(meta.fields.map(field=>[field.name,data[field.name]??field.defaultValue??
          (field.type==='boolean'?false:choices[field.type]?choices[field.type][0][0]:field.type==='number'?1:field.nullable?null:'')]));
        if(resource==='users'){init.password='';if(!record)init.target_score=300;}
        const answers=data.options||emptyOptions();
        if(live){setValues(init);setOptions(answers);setVersion(data.edit_version||'');setInitial(JSON.stringify([init,answers]));}
      } catch(error){if(live)setLoadError(error.message);}
      finally{if(live)setLoading(false);}
    }
    load();return()=>{live=false;};
  },[resource,record?.id,retry]);
  function close() {
    if(saving)return;
    if(initial && JSON.stringify([values,options])!==initial && !window.confirm('Bỏ các thay đổi chưa lưu?'))return;
    onClose();
  }
  function set(name,value) {
    setValues(current=>({...current,[name]:value,...(resource==='questions'&&name==='part_id'?{group_id:null}:{})}));
  }
  async function save(event) {
    event.preventDefault();setSaving(true);setError('');
    try {
      const body={...values};
      if(resource==='users'&&!body.password)delete body.password;
      if(resource==='questions')body.options=options;
      const result=await api(`/admin/${resource}${record?'/'+record.id:''}`,record?'PUT':'POST',body,
        {headers:record?{'If-Match':version}:{}});
      onSaved(result.id);
    } catch(error){setError(error.message);}
    finally{setSaving(false);}
  }
  return <Dialog title={(record?'Chỉnh sửa':'Thêm mới')+' · '+meta.label} onClose={close}>
    {loading?<p className="editor-note">Đang tải…</p>:loadError?<div className="editor-note"><p role="alert" className="error">{loadError}</p><button onClick={()=>setRetry(n=>n+1)}>Tải lại</button></div>:
      <form onSubmit={save}>
        <fieldset disabled={saving} className="editor-fields"><div className="form-grid">
          {meta.fields.filter(field=>!(resource==='questions'&&field.name==='group_id'&&Number(values.part_id)===5)).map(field=>{
            const attributes={required:field.required || (resource==='users'&&field.name==='password'&&!record),maxLength:field.maxLength,min:field.min,max:field.max};
            const value=values[field.name]??'';
            const linked={...field,...(field.name==='group_id'&&resource==='questions'?{filters:{part_id:values.part_id}}:{})};
            return <label key={field.name} className={field.type==='textarea'?'wide':''}>{field.label}{attributes.required&&<span className="required-mark"> *</span>}
              {field.type==='textarea'?<textarea {...attributes} rows={field.name==='content'?7:3} value={value} onChange={event=>set(field.name,event.target.value)}/>:
                field.type==='relation'?<RelationSelect field={linked} value={values[field.name]} onChange={value=>set(field.name,value)}/>:
                field.type==='boolean'?<input type="checkbox" checked={Boolean(value)} onChange={event=>set(field.name,event.target.checked)}/>:
                choices[field.type]?<select value={value} onChange={event=>set(field.name,event.target.value)}>{choices[field.type].map(([id,label])=><option value={id} key={id}>{label}</option>)}</select>:
                <input {...attributes} type={['number','url','email','password'].includes(field.type)?field.type:'text'} value={value}
                  autoComplete={field.type==='password'?'new-password':undefined}
                  onChange={event=>set(field.name,field.type==='number'&&event.target.value!==''?Number(event.target.value):event.target.value)}/>}
            </label>;
          })}
        </div>{resource==='questions'&&<QuestionOptions name="correct-option" value={options} onChange={setOptions}/>}</fieldset>
        {error&&<div role="alert" className="error">{error}</div>}
        <div className="modal-footer"><button type="button" className="secondary" disabled={saving} onClick={close}>Hủy</button>
          <button className="primary" disabled={saving}>{saving?'Đang lưu…':'Lưu thay đổi'}</button></div>
      </form>}
  </Dialog>;
}
