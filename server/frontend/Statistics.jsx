import React,{useEffect,useState} from 'react';
import {api} from './api.js';

function Bars({title,rows,label,value,unit=''}) {
  const max = Math.max(1,...rows.map(row=>Number(row[value]) || 0));
  return <section className="panel detail-chart">
    <h2>{title}</h2>
    {rows.length?<div className="stat-bars" role="img" aria-label={title}>
      {rows.map((row,index)=><div className="stat-row" key={row[label] || index}>
        <span>{row[label]}</span>
        <div><i style={{width:((Number(row[value]) || 0)/max*100)+'%'}}/></div>
        <b>{row[value] ?? 'Chưa có'}{row[value]===null?'':unit}</b>
      </div>)}
    </div>:<p>Chưa có dữ liệu.</p>}
  </section>;
}

export function Statistics({initialData}) {
  const [days,setDays] = useState(30);
  const [data,setData] = useState(initialData || null);
  const [error,setError] = useState('');
  useEffect(()=>{
    if(days===30&&initialData){setData(initialData);return;}
    let live = true;
    setData(null);
    setError('');
    api('/admin/dashboard?days='+days).then(value=>{if(live)setData(value);})
      .catch(error=>{if(live)setError(error.message);});
    return ()=>{live=false;};
  },[days,initialData]);
  const dailyMax=Math.max(1,...(data?.daily || []).map(day=>Number(day.completed)));
  return <section className="statistics">
    <div className="panel-title">
      <div><h2>Phân tích chi tiết</h2><p>Số liệu theo ngày UTC, không quy đổi điểm TOEIC</p></div>
      <label>Khoảng thời gian<select value={days} onChange={event=>setDays(Number(event.target.value))}>
        {[7,30,90].map(value=><option key={value} value={value}>{value} ngày</option>)}
      </select></label>
    </div>
    {error?<p className="error">{error}</p>:!data?<p role="status">Đang tải thống kê…</p>:<>
      <div className="dashboard-grid">
        <Bars title="Độ chính xác theo Part" rows={data.part_performance.map(row=>({...row,label:'Part '+row.part_id}))} label="label" value="accuracy" unit="%"/>
        <Bars title="Tài khoản theo trạng thái" rows={data.user_states.map(row=>({...row,label:row.role+' · '+row.status}))} label="label" value="count"/>
        <Bars title="Độ khó ngân hàng câu hỏi" rows={data.difficulties.map(row=>({...row,label:row.part_id+' · '+row.difficulty+' · '+row.status}))} label="label" value="count"/>
        <Bars title="Phiên đăng nhập" rows={[
          {label:'Đang hoạt động',count:data.security.active_sessions},
          {label:'Đã thu hồi',count:data.security.revoked_sessions},
          {label:'Hết hạn chờ dọn',count:data.security.expired_sessions}
        ]} label="label" value="count"/>
      </div>
      <section className="panel">
        <h2>Hoạt động và tăng trưởng</h2>
        <div className="daily-chart" role="img" aria-label="Số bài hoàn thành theo ngày">
          {data.daily.map(row=><div key={row.day} title={row.day+': '+row.completed+' bài hoàn thành'}>
            <i style={{height:(Number(row.completed)/dailyMax*120+2)+'px'}}/>
            <small>{row.day.slice(5)}</small>
          </div>)}
        </div>
        <details><summary>Xem bảng số liệu theo ngày</summary><div className="table-scroll">
          <table><thead><tr><th>Ngày UTC</th><th>Tài khoản mới</th><th>Bắt đầu</th><th>Hoàn thành</th><th>Độ chính xác</th></tr></thead>
            <tbody>{data.daily.map(row=><tr key={row.day}><td>{row.day}</td><td>{row.users}</td><td>{row.started}</td><td>{row.completed}</td><td>{row.accuracy===null?'Chưa có':row.accuracy+'%'}</td></tr>)}</tbody>
          </table></div>
        </details>
      </section>
    </>}
  </section>;
}
