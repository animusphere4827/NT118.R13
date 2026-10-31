import React,{useEffect,useState} from 'react';
import {api} from './api.js';
import {Editor,choices} from './Editor.jsx';
import {ReadingEditor,ExamEditor} from './ContentEditors.jsx';
import {Dialog} from './Dialog.jsx';
import {RelationSelect} from './RelationSelect.jsx';

const display=value=>value===null||value===undefined?'—':typeof value==='boolean'?(value?'Có':'Không'):typeof value==='object'?JSON.stringify(value):String(value);
const cellValue=(column,value)=>column==='test_format'?(value==='FULL'?'Đề đầy đủ':'Đề mini'):column==='time_limit'?value/60+' phút':display(value);
const caption=row=>row.word||row.title||row.name||row.full_name||row.question_text||row.front_text||'#'+row.id;

export function ResourcePage({resource,meta,readonly,listColumns={},columnNames={},labels={}}) {
  const [data,setData]=useState(null),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const [search,setSearch]=useState(''),[applied,setApplied]=useState(''),[page,setPage]=useState(1),[filters,setFilters]=useState({});
  const [editor,setEditor]=useState(undefined),[refresh,setRefresh]=useState(0),[detail,setDetail]=useState(null),[deleting,setDeleting]=useState(null);
  const filterKey=JSON.stringify(filters);
  useEffect(()=>{
    let live=true;setData(null);setError('');
    const params=new URLSearchParams({page:String(page),search:applied});
    for(const [key,value] of Object.entries(filters))if(value!==''&&value!==null&&value!==undefined)params.set(key,String(value));
    api('/admin/'+resource+'?'+params).then(result=>{if(live)setData(result);}).catch(error=>{if(live)setError(error.message);});
    return()=>{live=false;};
  },[resource,page,applied,filterKey,refresh]);
  function filter(key,value){setPage(1);setFilters(current=>({...current,[key]:value}));}
  async function remove(row) {
    setDeleting(row.id);setError('');
    try {
      const grouped=resource==='question_groups';
      const endpoint=grouped?'/admin/content/readings':'/admin/'+resource;
      const current=await api(endpoint+'/'+row.id);
      if(!window.confirm('Xóa '+caption(current).slice(0,80)+'? Nội dung có lịch sử hoặc liên kết có thể không xóa được.'))return;
      await api(endpoint+'/'+row.id,'DELETE',undefined,{headers:{'If-Match':current.edit_version}});
      setNotice('Đã xóa bản ghi #'+row.id+'.');
      if(data.rows.length===1&&page>1)setPage(n=>n-1);else setRefresh(n=>n+1);
    }catch(error){setError(error.message);}
    finally{setDeleting(null);}
  }
  const title=resource==='question_groups'?'Bài đọc Reading':meta?.label||labels[resource];
  const extraNames={passage_count:'Đoạn văn',question_count:'Số câu',is_published:'Có câu xuất bản',part_id:'Part',test_format:'Loại đề'};
  const columns=resource==='question_groups'?['id','title','part_id','passage_count','question_count','is_published']:
    ['mock_tests','practice_sets'].includes(resource)?['id','title',resource==='mock_tests'?'test_format':'part_id','time_limit','question_count','status']:
    listColumns[resource]||(meta?['id',...meta.fields.filter(field=>field.type!=='password').map(field=>field.name).slice(0,5)]:
      data?.rows[0]?Object.keys(data.rows[0]).filter(key=>!['snapshot','password_hash'].includes(key)).slice(0,7):['id']);
  const stateField=meta?.fields.find(field=>field.name==='status');
  const relationFilter=meta?.fields.find(field=>['topic_id','group_id'].includes(field.name));
  function saved(id){setEditor(undefined);setPage(1);setRefresh(n=>n+1);setNotice('Đã lưu '+title.toLowerCase()+' #'+id+'.');}
  return <>
    <div className="page-heading"><div><span className="eyebrow">{readonly?'LEARNING INSIGHTS':'CONTENT MANAGEMENT'}</span><h1>{title}</h1>
      <p>{resource==='question_groups'?'Soạn đoạn văn, câu hỏi và lời giải trong cùng một bài.':resource==='mock_tests'||resource==='practice_sets'?
        'Chọn câu, sắp xếp thứ tự và kiểm tra trước khi xuất bản.':readonly?'Theo dõi dữ liệu học tập đã ghi nhận.':'Tìm kiếm, biên soạn và xuất bản nội dung TOEIC.'}</p></div>
      {!readonly&&<button className="primary" onClick={()=>setEditor(null)}>＋ Thêm mới</button>}
    </div>
    {notice&&<p className="success-notice" role="status">{notice}</p>}
    <section className="panel data-panel"><div className="table-toolbar">
      <form onSubmit={event=>{event.preventDefault();setPage(1);setApplied(search);setRefresh(n=>n+1);}}>
        <input type="search" aria-label="Tìm kiếm" placeholder={readonly?'Nhập #ID bản ghi':'Tìm nội dung hoặc #ID…'} value={search} onChange={event=>setSearch(event.target.value)}/>
        <button className="secondary">Tìm kiếm</button>
      </form><span>{data?.total??'…'} bản ghi</span>
    </div>
    {(stateField||meta?.fields.some(field=>field.name==='part_id')||relationFilter)&&<div className="table-filters">
      {stateField&&<label>Trạng thái<select value={filters.status||''} onChange={event=>filter('status',event.target.value)}><option value="">Tất cả trạng thái</option>
        {choices[stateField.type].map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>}
      {meta?.fields.some(field=>field.name==='part_id')&&<label>Part<select value={filters.part_id||''} onChange={event=>filter('part_id',event.target.value)}>
        <option value="">Tất cả Part</option>{(resource==='question_groups'?[6,7]:[5,6,7]).map(part=><option value={part} key={part}>Part {part}</option>)}</select></label>}
      {relationFilter&&<label>{relationFilter.label}<RelationSelect field={{...relationFilter,nullable:true,placeholder:'Tất cả'}} value={filters[relationFilter.name]||null} onChange={value=>filter(relationFilter.name,value)}/></label>}
      <button className="secondary" onClick={()=>{setFilters({});setPage(1);}}>Bỏ bộ lọc</button>
    </div>}
    {error&&<div className="editor-note"><p role="alert" className="error">{error}</p><button onClick={()=>setRefresh(n=>n+1)}>Thử lại</button></div>}
    {!data&&!error?<div className="empty">Đang tải dữ liệu…</div>:data&&<>
      <div className="table-scroll"><table><thead><tr>{columns.map(column=><th key={column}>{column==='id'?'ID':column==='time_limit'?'Thời lượng':meta?.fields.find(field=>field.name===column)?.label||extraNames[column]||columnNames[column]||column}</th>)}<th>Thao tác</th></tr></thead>
        <tbody>{data.rows.map(row=><tr key={row.id}>{columns.map(column=><td key={column}>
          {['status','role','difficulty'].includes(column)?<span className={'badge '+row[column]?.toLowerCase()}>{Object.values(choices).flat().find(([key])=>key===row[column])?.[1]||row[column]}</span>:
            <span className="cell-content" title={cellValue(column,row[column])}>{cellValue(column,row[column])}</span>}
        </td>)}<td className="actions"><button onClick={async()=>{if(!readonly){setEditor(row);return;}try{setDetail(await api('/admin/'+resource+'/'+row.id));}catch(error){setError(error.message);}}}>{readonly?'Xem':'Sửa'}</button>
          {!readonly&&resource!=='users'&&<button className="danger-text" disabled={deleting!==null} onClick={()=>remove(row)}>{deleting===row.id?'Đang xóa…':'Xóa'}</button>}
        </td></tr>)}</tbody>
      </table></div>
      {!data.rows.length&&<div className="empty"><h3>Chưa có dữ liệu phù hợp</h3><p>Thử đổi từ khóa, bộ lọc hoặc thêm nội dung mới.</p></div>}
      <div className="pagination"><span>Trang {page} / {Math.max(1,Math.ceil(data.total/data.limit))}</span><div>
        <button disabled={page===1} onClick={()=>setPage(n=>n-1)}>← Trước</button><button disabled={page*data.limit>=data.total} onClick={()=>setPage(n=>n+1)}>Sau →</button>
      </div></div>
    </>}
    </section>
    {editor!==undefined&&(resource==='question_groups'?<ReadingEditor record={editor} onClose={()=>setEditor(undefined)} onSaved={saved}/>:
      ['mock_tests','practice_sets'].includes(resource)?<ExamEditor kind={resource==='mock_tests'?'mock':'practice'} record={editor} onClose={()=>setEditor(undefined)} onSaved={saved}/>:
        <Editor resource={resource} meta={meta} record={editor} onClose={()=>setEditor(undefined)} onSaved={saved}/>)}
    {detail&&<Dialog title={'Chi tiết #'+detail.id} onClose={()=>setDetail(null)}><pre>{JSON.stringify(detail,null,2)}</pre></Dialog>}
  </>;
}
