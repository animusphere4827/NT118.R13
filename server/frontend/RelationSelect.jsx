import React,{useEffect,useState} from 'react';
import {api} from './api.js';

const caption = row=>row.word || row.title || row.name || row.full_name || row.front_text || row.question_text || '#'+row.id;

export function RelationSelect({field,value,onChange}) {
  const [search,setSearch] = useState('');
  const [rows,setRows] = useState([]);
  const [error,setError] = useState('');
  const filterKey = JSON.stringify(field.filters || {});
  useEffect(()=>{
    let live = true;
    const timer = setTimeout(async()=>{
      try {
        const params = new URLSearchParams({limit:'50',search});
        for(const [key,value] of Object.entries(field.filters || {})) if(value!==undefined && value!==null && value!=='') params.set(key,String(value));
        const result = await api('/admin/'+field.resource+'?'+params);
        const records = result.rows;
        if (value && !records.some(row=>row.id===value)) records.unshift(await api('/admin/'+field.resource+'/'+value));
        if (live) {setRows(records);setError('');}
      } catch(error) {if(live)setError(error.message);}
    },200);
    return ()=>{live=false;clearTimeout(timer);};
  },[field.resource,search,value,filterKey]);
  return <span className="relation-picker">
    <input type="search" aria-label={'Tìm '+field.label} value={search} placeholder="Tìm theo tên hoặc nội dung" onChange={event=>setSearch(event.target.value)}/>
    <select aria-label={field.label} value={value ?? ''} required={!field.nullable} onChange={event=>onChange(event.target.value?Number(event.target.value):null)}>
      <option value="">{field.placeholder||(field.nullable?'Không gắn liên kết':'Chọn mục')}</option>
      {rows.map(row=><option key={row.id} value={row.id}>#{row.id} · {caption(row).slice(0,100)}</option>)}
    </select>
    {error&&<small role="alert">{error}</small>}
  </span>;
}
