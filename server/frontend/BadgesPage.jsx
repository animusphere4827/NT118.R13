import React,{useEffect,useState} from 'react';
import {api} from './api.js';
import {Dialog} from './Dialog.jsx';

const unlockedDate = new Intl.DateTimeFormat('vi-VN',{
  dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'
});

function BadgeEditor({badge,onClose,onSaved}) {
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const close=()=>{if(!busy)onClose();};

  async function save(event) {
    event.preventDefault();
    if(busy)return;
    const values=Object.fromEntries(new FormData(event.currentTarget));
    setBusy(true);
    setError('');
    try {
      const saved=await api('/admin/badges/'+badge.id,'PUT',values,{
        headers:{'If-Match':badge.edit_version}
      });
      onSaved(saved);
    } catch(error) {
      setError(error.status===409 ? 'Huy hiệu vừa được sửa. Đóng và tải lại danh sách để chỉnh bản mới.' : error.message);
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title="Sửa huy hiệu" onClose={close}>
    <form onSubmit={save}>
      <p>{badge.code} · Điều kiện nhận: {badge.requirement}</p>
      <label>Tên huy hiệu<input name="name" defaultValue={badge.name} required maxLength={80} disabled={busy}/></label>
      <label>Mô tả<textarea name="description" defaultValue={badge.description} required maxLength={500} rows={4} disabled={busy}/></label>
      {error&&<p className="error" role="alert">{error}</p>}
      <div className="modal-footer">
        <button type="button" className="secondary" onClick={close} disabled={busy}>Hủy</button>
        <button className="primary" disabled={busy}>{busy?'Đang lưu…':'Lưu huy hiệu'}</button>
      </div>
    </form>
  </Dialog>;
}

function BadgeRecipients({badge,onClose}) {
  const [page,setPage]=useState(1);
  const [result,setResult]=useState(null);
  const [error,setError]=useState('');

  useEffect(()=>{
    let active=true;
    api('/admin/badges/'+badge.id+'/recipients?page='+page)
      .then(data=>{if(active)setResult(data);})
      .catch(error=>{if(active)setError(error.message);});
    return ()=>{active=false;};
  },[badge.id,page]);

  function move(next) {
    setResult(null);
    setError('');
    setPage(next);
  }

  return <Dialog title={'Người đã nhận · '+badge.name} onClose={onClose}>
    {error&&<p className="error" role="alert">{error}</p>}
    {!result&&!error&&<p role="status">Đang tải danh sách…</p>}
    {result&&<>{result.total===0?<p>Chưa có học viên nhận huy hiệu này.</p>:<>
      <div className="table-scroll"><table>
        <thead><tr><th>Học viên</th><th>Nhận lúc</th><th>Gắn hồ sơ</th></tr></thead>
        <tbody>{result.rows.map(row=><tr key={row.user_id}>
          <td>{row.full_name} (#{row.user_id})</td>
          <td>{unlockedDate.format(new Date(row.unlocked_at))}</td>
          <td>{row.featured?'Có':'—'}</td>
        </tr>)}</tbody>
      </table></div>
      <div className="pagination">
        <span>Trang {page} · {result.total} học viên</span>
        <div>
          <button type="button" onClick={()=>move(page-1)} disabled={page<=1}>Trang trước</button>
          <button type="button" onClick={()=>move(page+1)} disabled={page*result.limit>=result.total}>Trang tiếp</button>
        </div>
      </div>
    </>}</>}
  </Dialog>;
}

export function BadgesPage() {
  const [rows,setRows]=useState(null);
  const [error,setError]=useState('');
  const [reload,setReload]=useState(0);
  const [dialog,setDialog]=useState(null);

  useEffect(()=>{
    let active=true;
    api('/admin/badges').then(data=>{if(active)setRows(data.rows);})
      .catch(error=>{if(active)setError(error.message);});
    return ()=>{active=false;};
  },[reload]);

  function refresh() {
    setError('');
    setRows(null);
    setReload(value=>value+1);
  }

  function saved(badge) {
    setRows(current=>current.map(row=>row.id===badge.id?{...row,...badge}:row));
    setDialog(null);
  }

  return <>
    <div className="page-heading">
      <div><h1>Huy hiệu</h1><p>Chỉnh nội dung hiển thị và xem học viên đã đạt từng huy hiệu.</p></div>
      <button type="button" className="secondary" onClick={refresh}>Tải lại</button>
    </div>
    {error&&<p className="error" role="alert">{error}</p>}
    {!rows&&!error&&<p role="status">Đang tải huy hiệu…</p>}
    {rows&&<section className="panel data-panel"><div className="table-scroll"><table>
      <thead><tr><th>Huy hiệu</th><th>Mô tả</th><th>Điều kiện</th><th>Đã nhận</th><th>Thao tác</th></tr></thead>
      <tbody>{rows.map(badge=><tr key={badge.id}>
        <td><strong>{badge.name}</strong><br/><small>{badge.code}</small></td>
        <td>{badge.description}</td>
        <td>{badge.requirement}</td>
        <td>{badge.earned_count}</td>
        <td className="actions">
          <button type="button" onClick={()=>setDialog({badge,edit:true})}>Sửa</button>
          <button type="button" onClick={()=>setDialog({badge,edit:false})}>Xem người nhận</button>
        </td>
      </tr>)}</tbody>
    </table></div></section>}
    {dialog?.edit&&<BadgeEditor badge={dialog.badge} onClose={()=>setDialog(null)} onSaved={saved}/>}
    {dialog&&!dialog.edit&&<BadgeRecipients badge={dialog.badge} onClose={()=>setDialog(null)}/>}
  </>;
}
