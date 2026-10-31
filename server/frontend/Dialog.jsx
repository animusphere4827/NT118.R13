import React,{useEffect,useRef} from 'react';

export function Dialog({title,onClose,children}) {
  const panel=useRef(null);
  const close=useRef(onClose);
  close.current=onClose;
  useEffect(()=>{
    const before=document.activeElement;
    const overflow=document.body.style.overflow;
    document.body.style.overflow='hidden';
    panel.current?.querySelector('button')?.focus();
    const escape=event=>{if(event.key==='Escape')close.current();};
    document.addEventListener('keydown',escape);
    return()=>{
      document.removeEventListener('keydown',escape);
      document.body.style.overflow=overflow;
      before?.focus();
    };
  },[]);
  function trap(event) {
    if(event.key!=='Tab')return;
    const items=[...panel.current.querySelectorAll('button,input,select,textarea,a[href],[tabindex="0"]')]
      .filter(item=>!item.disabled && item.getClientRects().length);
    const first=items[0],last=items.at(-1);
    if(event.shiftKey && document.activeElement===first){event.preventDefault();last?.focus();}
    else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first?.focus();}
  }
  return <div className="modal-backdrop" onClick={event=>{if(event.target===event.currentTarget)onClose();}}>
    <section ref={panel} className="modal content-dialog" role="dialog" aria-modal="true" aria-label={title} onKeyDown={trap}>
      <div className="modal-header"><h2>{title}</h2><button type="button" className="icon-button" aria-label="Đóng" onClick={onClose}>×</button></div>
      {children}
    </section>
  </div>;
}
