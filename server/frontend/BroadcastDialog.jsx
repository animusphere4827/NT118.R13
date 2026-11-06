import React,{useState} from 'react';
import {api} from './api.js';
import {Dialog} from './Dialog.jsx';
import {choices} from './Editor.jsx';

const broadcastTypes=choices.notificationType.filter(([type])=>
  ['SYSTEM','STUDY_REMINDER','NEW_CONTENT'].includes(type)
);

export function BroadcastDialog({onClose,onSent}) {
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState('');
  const close=()=>{if(!busy)onClose();};

  async function send(event) {
    event.preventDefault();
    if(busy)return;
    const {type,title,body}=Object.fromEntries(new FormData(event.currentTarget));
    setBusy(true);
    setError('');
    try {
      const {sent}=await api('/admin/notifications/broadcast','POST',{type,title,body});
      onSent(sent);
    } catch(error) {
      setError(error.message);
    } finally {
      setBusy(false);
    }
  }

  return <Dialog title="Gửi thông báo hàng loạt" onClose={close}>
    <form onSubmit={send}>
      <p>Thông báo sẽ xuất hiện trong hộp thư trên app của học viên đang hoạt động.</p>
      <label>Loại<select name="type" disabled={busy}>
        {broadcastTypes.map(([type,label])=><option key={type} value={type}>{label}</option>)}
      </select></label>
      <label>Tiêu đề<input name="title" required maxLength={200} disabled={busy}/></label>
      <label>Nội dung<textarea name="body" maxLength={5000} rows={4} disabled={busy}/></label>
      <label><input name="confirm" type="checkbox" required disabled={busy} style={{width:'auto',marginRight:8}}/>
        Tôi xác nhận gửi đến tất cả học viên đang hoạt động
      </label>
      {error&&<p className="error" role="alert">{error}</p>}
      <div className="modal-footer">
        <button type="button" className="secondary" disabled={busy} onClick={close}>Hủy</button>
        <button className="primary" disabled={busy}>{busy?'Đang gửi…':'Gửi thông báo'}</button>
      </div>
    </form>
  </Dialog>;
}
