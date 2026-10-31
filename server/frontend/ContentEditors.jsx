import React,{useEffect,useState} from 'react';
import {api} from './api.js';
import {Dialog} from './Dialog.jsx';
import {RelationSelect} from './RelationSelect.jsx';
import {choices,SelectField,QuestionOptions,emptyOptions} from './Editor.jsx';

const passage=()=>({passage_type:'EMAIL',title:'',content:'',translation_vi:'',image_url:''});
const question=()=>({question_type:'DETAIL',question_text:'',explanation:'',difficulty:'MEDIUM',options:emptyOptions()});
const freshReading=()=>({title:'',instructions:'',part_id:7,status:'DRAFT',is_practical_reading:true,passages:[passage()],questions:[question()]});
const readingForm=record=>({title:record.title,instructions:record.instructions,part_id:record.part_id,status:record.status,is_practical_reading:record.is_practical_reading,
  passages:record.passages.map(p=>({id:p.id,passage_type:p.passage_type,title:p.title,content:p.content,translation_vi:p.translation_vi,image_url:p.image_url||''})),
  questions:record.questions.map(q=>({id:q.id,question_type:q.question_type,question_text:q.question_text,
    explanation:q.explanation,difficulty:q.difficulty,options:q.options}))});
const move=(rows,index,offset)=>{
  const copy=[...rows];
  [copy[index],copy[index+offset]]=[copy[index+offset],copy[index]];
  return copy;
};

function useContentForm(endpoint,record,makeForm,fromRecord,onClose,onSaved) {
  const [body,setBody]=useState(null),[initial,setInitial]=useState(''),[version,setVersion]=useState('');
  const [loading,setLoading]=useState(true),[saving,setSaving]=useState(false),[loadError,setLoadError]=useState(''),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    let live=true;setLoading(true);setLoadError('');
    async function load(){
      try {
        const data=record?await api(endpoint+'/'+record.id):null;
        if(!live)return;
        const form=data?fromRecord(data):makeForm();
        if(live){setBody(form);setInitial(JSON.stringify(form));setVersion(data?.edit_version||'');}
      }catch(error){if(live)setLoadError(error.message);}
      finally{if(live)setLoading(false);}
    }
    load();return()=>{live=false;};
  },[endpoint,record?.id,retry]);
  function close(){if(saving)return;if(body&&JSON.stringify(body)!==initial&&!window.confirm('Bỏ các thay đổi chưa lưu?'))return;onClose();}
  async function save(event){
    event.preventDefault();setError('');setSaving(true);
    try {
      const result=await api(endpoint+(record?'/'+record.id:''),record?'PUT':'POST',body,{headers:record?{'If-Match':version}:{}});
      onSaved(result.id);
    }catch(error){setError(error.message);}
    finally{setSaving(false);}
  }
  return {body,setBody,loading,saving,loadError,error,close,save,retry:()=>setRetry(n=>n+1)};
}
function LoadState({form}) {
  return form.loading?<p className="editor-note">Đang tải nội dung…</p>:form.loadError?<div className="editor-note">
    <p className="error" role="alert">{form.loadError}</p><button type="button" onClick={form.retry}>Tải lại</button></div>:null;
}
function SaveBar({form,busy=false}) {
  return <>{form.error&&<p role="alert" className="error">{form.error}</p>}<div className="modal-footer">
    <button type="button" className="secondary" disabled={form.saving} onClick={form.close}>Hủy</button>
    <button className="primary" disabled={form.saving||busy}>{busy?'Đang lấy câu…':form.saving?'Đang lưu…':'Lưu bài'}</button>
  </div></>;
}

export function ReadingEditor({record,onClose,onSaved}) {
  const form=useContentForm('/admin/content/readings',record,freshReading,readingForm,onClose,onSaved);
  const {body,setBody}=form;
  const set=(key,value)=>setBody(current=>({...current,[key]:value}));
  const update=(key,index,field,value)=>setBody(current=>({...current,[key]:current[key].map((row,i)=>i===index?{...row,[field]:value}:row)}));
  return <Dialog title={(record?'Chỉnh sửa':'Soạn mới')+' · Bài đọc Reading'} onClose={form.close}>
    <LoadState form={form}/>{!form.loading&&!form.loadError&&body&&<form onSubmit={form.save}>
      <p className="editor-note">Soạn đoạn văn và câu hỏi trong cùng bài. Chọn xuất bản vào thư viện để học viên đọc độc lập.</p>
      <fieldset disabled={form.saving} className="editor-fields">
        <div className="form-grid">
          <label>Tiêu đề bài đọc *<input required maxLength={200} value={body.title} onChange={event=>set('title',event.target.value)}/></label>
          <SelectField label="Part" value={body.part_id} onChange={value=>set('part_id',Number(value))}
            options={body.passages.length>1?[[7,'Part 7 · Đọc hiểu']]:[[6,'Part 6 · Điền đoạn'],[7,'Part 7 · Đọc hiểu']]}/>
          <SelectField label="Trạng thái" value={body.status} onChange={value=>set('status',value)} options={choices.status}/>
          <label className="library-toggle"><input type="checkbox" checked={body.is_practical_reading} onChange={event=>set('is_practical_reading',event.target.checked)}/> Đưa bài đã xuất bản vào thư viện đọc</label>
          <label>Hướng dẫn<textarea rows={2} maxLength={60000} value={body.instructions} onChange={event=>set('instructions',event.target.value)}/></label>
        </div>
        <div className="section-heading"><h3>Đoạn văn <span>{body.passages.length}/{body.part_id===6?1:3}</span></h3>
          <button type="button" disabled={body.passages.length>=(body.part_id===6?1:3)} onClick={()=>set('passages',[...body.passages,passage()])}>＋ Thêm đoạn</button></div>
        {body.passages.map((row,index)=><section className="content-card" key={row.id||'new-'+index}>
          <div className="content-card-heading"><h3>Đoạn {index+1}</h3><div className="inline-actions">
            <button type="button" aria-label={'Đưa đoạn '+(index+1)+' lên'} disabled={!index} onClick={()=>set('passages',move(body.passages,index,-1))}>↑</button>
            <button type="button" aria-label={'Đưa đoạn '+(index+1)+' xuống'} disabled={index===body.passages.length-1} onClick={()=>set('passages',move(body.passages,index,1))}>↓</button>
            <button type="button" disabled={body.passages.length===1} onClick={()=>set('passages',body.passages.filter((_,i)=>i!==index))}>Bỏ đoạn</button>
          </div></div>
          <div className="form-grid">
            <SelectField label={'Loại văn bản đoạn '+(index+1)} value={row.passage_type} options={choices.passageType} onChange={value=>update('passages',index,'passage_type',value)}/>
            <label>Tiêu đề đoạn<input maxLength={200} value={row.title} onChange={event=>update('passages',index,'title',event.target.value)}/></label>
            <label className="wide">Nội dung tiếng Anh *<textarea required maxLength={60000} rows={6} value={row.content} onChange={event=>update('passages',index,'content',event.target.value)}/></label>
            <label className="wide">Bản dịch<textarea maxLength={60000} rows={3} value={row.translation_vi} onChange={event=>update('passages',index,'translation_vi',event.target.value)}/></label>
            <label className="wide">URL ảnh minh họa<input type="url" maxLength={2048} value={row.image_url} onChange={event=>update('passages',index,'image_url',event.target.value)}/></label>
          </div>
        </section>)}
        <div className="section-heading"><h3>Câu hỏi <span>{body.questions.length}</span></h3>
          <button type="button" disabled={body.questions.length>=50} onClick={()=>set('questions',[...body.questions,question()])}>＋ Thêm câu hỏi</button></div>
        {body.questions.map((row,index)=><section className="content-card" key={row.id||'new-'+index}>
          <div className="content-card-heading"><h3>Câu {index+1}{row.id?' · #'+row.id:''}</h3>
            <button type="button" onClick={()=>set('questions',body.questions.filter((_,i)=>i!==index))}>Bỏ câu</button></div>
          <div className="form-grid">
            <SelectField label={'Dạng câu '+(index+1)} value={row.question_type} options={choices.questionType} onChange={value=>update('questions',index,'question_type',value)}/>
            <SelectField label={'Độ khó câu '+(index+1)} value={row.difficulty} options={choices.difficulty} onChange={value=>update('questions',index,'difficulty',value)}/>
            <label className="wide">Câu hỏi *<textarea required maxLength={60000} rows={2} value={row.question_text} onChange={event=>update('questions',index,'question_text',event.target.value)}/></label>
            <label className="wide">Giải thích *<textarea required maxLength={60000} rows={2} value={row.explanation} onChange={event=>update('questions',index,'explanation',event.target.value)}/></label>
          </div>
          <QuestionOptions name={'reading-correct-'+index} value={row.options} onChange={value=>update('questions',index,'options',value)}/>
        </section>)}
        <details className="reading-preview"><summary>Xem trước bài đọc</summary><h3>{body.title||'Bài đọc chưa có tiêu đề'}</h3>
          {body.passages.map((row,index)=><article key={index}><strong>{row.title||'Đoạn '+(index+1)}</strong><p>{row.content||'Chưa có nội dung.'}</p></article>)}
        </details>
      </fieldset><SaveBar form={form}/>
    </form>}
  </Dialog>;
}

const freshExam=kind=>({title:'',description:'',time_limit:kind==='mock'?4500:600,status:'DRAFT',
  ...(kind==='mock'?{test_format:'MINI'}:{part_id:5}),question_ids:[]});
const examForm=(record,kind)=>({title:record.title,description:record.description,time_limit:record.time_limit,status:record.status,
  ...(kind==='mock'?{test_format:record.test_format}:{part_id:record.part_id}),question_ids:record.question_ids});

function QuestionPreview({id}) {
  const [data,setData]=useState(null),[error,setError]=useState('');
  return <details className="question-preview" onToggle={event=>{if(event.currentTarget.open&&!data&&!error)api('/admin/questions/'+id).then(setData).catch(error=>setError(error.message));}}>
    <summary>Xem câu #{id}</summary>{error?<p role="alert">{error}</p>:!data?<p>Đang tải…</p>:<div>
      {data.passages?.map((passage,index)=><p className="passage-preview" key={index}>{passage.content}</p>)}
      <p>{data.question_text}</p><ul>{data.options.map(option=><li key={option.label}>{option.label}. {option.option_text}{option.is_correct?' ✓':''}</li>)}</ul>
      <p>{data.explanation}</p>
    </div>}
  </details>;
}

export function ExamEditor({kind,record,onClose,onSaved}) {
  const [entries,setEntries]=useState([]),[source,setSource]=useState(null),[picker,setPicker]=useState(null);
  const [search,setSearch]=useState(''),[applied,setApplied]=useState(''),[page,setPage]=useState(1),[part,setPart]=useState('');
  const [pickerError,setPickerError]=useState(''),[copying,setCopying]=useState(false);
  const form=useContentForm('/admin/content/exams/'+kind,record,()=>freshExam(kind),data=>{setEntries(data.entries);return examForm(data,kind);},onClose,onSaved);
  const {body,setBody}=form;
  const set=(key,value)=>setBody(current=>({...current,[key]:value}));
  const selected=body?.question_ids||[];
  useEffect(()=>{
    if(!body)return;
    let live=true;setPicker(null);setPickerError('');
    const query=new URLSearchParams({search:applied,page:String(page),limit:'10',status:'PUBLISHED'});
    const chosenPart=kind==='practice'?body.part_id:part;
    if(chosenPart)query.set('part_id',String(chosenPart));
    api('/admin/questions?'+query).then(data=>{if(live)setPicker(data);}).catch(error=>{if(live)setPickerError(error.message);});
    return()=>{live=false;};
  },[kind,body?.part_id,Boolean(body),applied,page,part]);
  function add(rows) {
    const additions=rows.filter(row=>!selected.includes(row.question_id||row.id));
    if(selected.length+additions.length>200){setPickerError('Mỗi đề tối đa 200 câu.');return;}
    set('question_ids',[...selected,...additions.map(row=>row.question_id||row.id)]);
    setEntries(current=>[...current,...additions.map(row=>({...row,question_id:row.question_id||row.id}))]);
  }
  async function addGroup(group) {
    setCopying(true);setPickerError('');
    try {
      const result=await api('/admin/questions?group_id='+group+'&status=PUBLISHED&limit=100');
      if(result.total>100)throw new Error('Nhóm có quá nhiều câu; hãy chọn từng câu.');
      add(result.rows);
    }catch(error){setPickerError(error.message);}
    finally{setCopying(false);}
  }
  async function copy() {
    if(!source)return;setCopying(true);setPickerError('');
    try {
      const result=await api('/admin/content/exams/'+kind+'/'+source);
      add(result.entries);
    }catch(error){setPickerError(error.message);}
    finally{setCopying(false);}
  }
  const byId=new Map(entries.map(row=>[row.question_id,row]));
  const counts=[5,6,7].map(part=>selected.filter(id=>byId.get(id)?.part_id===part).length);
  return <Dialog title={(record?'Chỉnh sửa':'Ráp mới')+' · '+(kind==='mock'?'Đề thi Reading':'Bộ luyện theo Part')} onClose={form.close}>
    <LoadState form={form}/>{!form.loading&&!form.loadError&&body&&<form onSubmit={form.save}>
      <fieldset className="editor-fields" disabled={form.saving||copying}>
        <div className="form-grid">
          <label>Tên {kind==='mock'?'đề thi':'bộ luyện'} *<input required maxLength={200} value={body.title} onChange={event=>set('title',event.target.value)}/></label>
          <SelectField label="Trạng thái" value={body.status} options={choices.status} onChange={value=>set('status',value)}/>
          {kind==='mock'?<SelectField label="Loại đề" value={body.test_format} options={choices.testFormat}
            onChange={value=>setBody(current=>({...current,test_format:value,...(value==='FULL'?{time_limit:4500}:{})}))}/>:
            <SelectField label="Part của bộ luyện" value={body.part_id} options={[[5,'Part 5'],[6,'Part 6'],[7,'Part 7']]}
              onChange={value=>{if(selected.length&&!window.confirm('Đổi Part sẽ bỏ các câu đang chọn. Tiếp tục?'))return;setBody(current=>({...current,part_id:Number(value),question_ids:[]}));setEntries([]);setSource(null);setPage(1);}}/>}
          <label>Thời gian (giây) *<input type="number" required min={60} max={14400} value={body.time_limit}
            onChange={event=>set('time_limit',event.target.value===''?'':Number(event.target.value))}/></label>
          <label className="wide">Mô tả<textarea rows={2} maxLength={60000} value={body.description} onChange={event=>set('description',event.target.value)}/></label>
        </div>
        <div className="exam-counts"><strong>{selected.length} câu</strong>{counts.map((count,index)=><span key={index}>Part {index+5}: {count}</span>)}
          <span>{Number(body.time_limit)/60} phút</span></div>
        {body.test_format==='FULL'&&<p className="editor-note">Đề đầy đủ cần 100 câu: Part 5/6/7 = 30/16/54, đúng 75 phút. Bản nháp có thể lưu khi chưa đủ.</p>}
        <div className="copy-exam"><label>Lấy câu từ đề có sẵn<RelationSelect field={{resource:kind==='mock'?'mock_tests':'practice_sets',label:'Lấy câu từ đề hoặc bộ luyện',nullable:true,
          ...(kind==='practice'?{filters:{part_id:body.part_id}}:{}),placeholder:'Chọn đề nguồn'}} value={source} onChange={setSource}/></label>
          <button type="button" disabled={!source||copying} onClick={copy}>Lấy các câu</button></div>
        <div className="exam-builder-grid">
          <section className="question-bank"><h3>Ngân hàng câu hỏi</h3><div className="picker-search">
            <input aria-label="Tìm câu hỏi để thêm" placeholder="Tìm nội dung hoặc #ID" value={search} onChange={event=>setSearch(event.target.value)}/>
            <button type="button" onClick={()=>{setApplied(search);setPage(1);}}>Tìm</button>
            {kind==='mock'&&<select aria-label="Lọc Part câu hỏi" value={part} onChange={event=>{setPart(event.target.value);setPage(1);}}>
              <option value="">Tất cả Part</option>{[5,6,7].map(value=><option key={value} value={value}>Part {value}</option>)}</select>}
          </div>
          {pickerError&&<p className="error" role="alert">{pickerError}</p>}
          {!picker&&!pickerError?<p>Đang tải câu hỏi…</p>:picker?.rows.map(row=><article className="bank-question" key={row.id}>
            <small>#{row.id} · Part {row.part_id}</small><p>{row.question_text}</p><div className="inline-actions">
              <button type="button" disabled={selected.includes(row.id)} onClick={()=>add([row])}>{selected.includes(row.id)?'Đã chọn':'Thêm câu'}</button>
              {row.group_id&&<button type="button" onClick={()=>addGroup(row.group_id)}>Thêm cả nhóm #{row.group_id}</button>}
            </div><QuestionPreview id={row.id}/>
          </article>)}
          {picker&&!picker.rows.length&&<p>Không có câu phù hợp.</p>}
          <div className="pagination"><button type="button" disabled={page===1} onClick={()=>setPage(n=>n-1)}>Trước</button><span>Trang {page}</span>
            <button type="button" disabled={!picker||page*10>=picker.total} onClick={()=>setPage(n=>n+1)}>Sau</button></div>
          </section>
          <section className="selected-questions"><h3>Thứ tự trong đề · {selected.length} câu</h3>
            {!selected.length&&<p className="editor-note">Thêm câu từ ngân hàng hoặc lấy từ đề có sẵn.</p>}
            {selected.map((id,index)=>{const row=byId.get(id);return <article className="selected-question" key={id}>
              <small>Câu {index+1} · #{id} · Part {row?.part_id}</small><p>{row?.question_text}</p>
              {row?.issues?.length>0&&<small className="validation-note">{row.issues.join('. ')}</small>}
              <div className="inline-actions"><button type="button" aria-label={'Đưa câu '+(index+1)+' lên'} disabled={!index} onClick={()=>set('question_ids',move(selected,index,-1))}>↑</button>
                <button type="button" aria-label={'Đưa câu '+(index+1)+' xuống'} disabled={index===selected.length-1} onClick={()=>set('question_ids',move(selected,index,1))}>↓</button>
                <button type="button" onClick={()=>{set('question_ids',selected.filter(value=>value!==id));setEntries(current=>current.filter(row=>row.question_id!==id));}}>Bỏ câu</button></div>
            </article>;})}
          </section>
        </div>
      </fieldset><SaveBar form={form} busy={copying}/>
    </form>}
  </Dialog>;
}
