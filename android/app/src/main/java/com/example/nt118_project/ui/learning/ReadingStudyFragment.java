package com.example.nt118_project.ui.learning;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.example.nt118_project.R;
import com.google.gson.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class ReadingStudyFragment extends StudyFragment {
    private long id;
    private boolean translation;
    private AlertDialog dialog;
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        super.onViewCreated(view,saved);id=getArguments()==null?0:getArguments().getLong("contentId",0);
        translation=saved!=null&&saved.getBoolean("translation");title("Đọc hiểu thực tế");load();
    }
    private void load() {
        if(id<=0){loading(false);error("Chọn một bài đọc trong thư viện.",null);button("Mở thư viện",()->open(R.id.reading_list,new Bundle()));return;}
        request(api.reading(id,translation),data->{
            body.removeAllViews();title(value(data,"title"));
            text("Part "+number(data,"part_id")+" · "+number(data,"passage_count")+" đoạn",false);
            button(translation?"Ẩn bản dịch":"Hiện bản dịch",()->{translation=!translation;load();});
            text("Nhấn giữ để chọn một từ tiếng Anh, rồi chọn Tra từ.",false);
            for(JsonElement item:rows(data,"passages")) {
                JsonObject passage=item.getAsJsonObject();text(value(passage,"title"),true);
                String image=value(passage,"image_url");
                if(!image.isEmpty()){ImageView picture=new ImageView(requireContext());picture.setAdjustViewBounds(true);body.addView(picture,new LinearLayout.LayoutParams(-1,dp(220)));Glide.with(this).load(image).fitCenter().into(picture);}
                TextView content=text(value(passage,"content"),false);content.setTextIsSelectable(true);
                content.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
                    @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){menu.add(0,8241,0,"Tra từ");return true;}
                    @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){return false;}
                    @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){
                        if(item.getItemId()!=8241)return false;
                        int start=content.getSelectionStart(),end=content.getSelectionEnd();
                        if(start>=0&&end>start)lookup(content.getText().subSequence(start,end).toString().trim());mode.finish();return true;
                    }
                    @Override public void onDestroyActionMode(ActionMode mode){}
                });
                if(translation)text(value(passage,"translation_vi"),false);
            }
            if(!value(data,"completed_at").isEmpty())text("Bạn đã hoàn thành bài đọc này. Có thể đọc lại để ôn tập.",false);
            else button("Đã đọc xong",()->{
                JsonObject body=new JsonObject();body.addProperty("completed",true);
                request(api.completeReading(id,body),done->load(),this::load);
            });
        },this::load);
    }
    private void lookup(String word) {
        if(busy)return;
        request(api.lookup(id,body("keyword",word)),data->{
            JsonArray found=rows(data,"rows");
            if(dialog!=null)dialog.dismiss();
            if(found.size()==0){dialog=new AlertDialog.Builder(requireContext()).setTitle(word).setMessage("Chưa có nghĩa của từ này trong thư viện hiện tại.").setPositiveButton("Đóng",null).show();return;}
            JsonObject entry=found.get(0).getAsJsonObject();
            dialog=new AlertDialog.Builder(requireContext()).setTitle(value(entry,"word"))
                .setMessage(value(entry,"ipa")+"\n"+value(entry,"part_of_speech")+"\n\n"+value(entry,"meaning_vi")+"\n\n"+value(entry,"example_sentence"))
                .setPositiveButton("Lưu vào bộ thẻ",(d,w)->chooseDeck(number(entry,"id")))
                .setNegativeButton("Đóng",null).show();
        },null);
    }
    private void chooseDeck(long vocabularyId) {
        request(api.decks(),data->{
            JsonArray decks=rows(data,"rows");String[] names=new String[decks.size()+1];
            for(int i=0;i<decks.size();i++)names[i]=value(decks.get(i).getAsJsonObject(),"name");names[decks.size()]="Tạo bộ thẻ mới";
            dialog=new AlertDialog.Builder(requireContext()).setTitle("Chọn bộ thẻ").setItems(names,(d,index)->{
                if(index==decks.size())newDeck(vocabularyId);else saveWord(number(decks.get(index).getAsJsonObject(),"id"),vocabularyId);
            }).setNegativeButton("Hủy",null).show();
        },null);
    }
    private void newDeck(long vocabularyId) {
        EditText name=new EditText(requireContext());name.setHint("Tên bộ thẻ");
        dialog=new AlertDialog.Builder(requireContext()).setTitle("Tạo bộ thẻ").setView(name)
            .setPositiveButton("Tạo và lưu từ",(d,w)->{
                JsonObject body=body("name",name.getText().toString().trim());body.addProperty("description","");body.add("cover_image_url",JsonNull.INSTANCE);
                request(api.deck(body),data->saveWord(number(data,"id"),vocabularyId),null);
            }).setNegativeButton("Hủy",null).show();
    }
    private void saveWord(long deck,long vocabulary) {
        JsonObject body=new JsonObject();body.addProperty("vocabulary_id",vocabulary);
        request(api.card(deck,body),data->Toast.makeText(requireContext(),"Đã lưu từ vào bộ thẻ",Toast.LENGTH_SHORT).show(),null);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putBoolean("translation",translation);super.onSaveInstanceState(out);}
    @Override public void onDestroyView(){if(dialog!=null)dialog.dismiss();dialog=null;super.onDestroyView();}
}
