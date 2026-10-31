package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class GrammarStudyFragment extends StudyFragment {
    private long id;
    private int index;
    private JsonObject workspace;
    private JsonObject feedback=new JsonObject();
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved){
        super.onViewCreated(view,saved);id=getArguments()==null?0:getArguments().getLong("contentId");
        index=saved==null?0:saved.getInt("index");if(saved!=null)feedback=JsonParser.parseString(saved.getString("feedback","{}")).getAsJsonObject();load();
    }
    private void load(){if(id<=0){loading(false);error("Chọn bài ngữ pháp trong thư viện.",null);return;}
        request(api.grammar(id),data->{workspace=data;render();},this::load);
    }
    private void render(){
        body.removeAllViews();JsonObject lesson=workspace.getAsJsonObject("lesson");title(value(lesson,"title"));
        text(value(lesson,"content"),false);JsonArray questions=rows(workspace,"questions");JsonObject progress=workspace.getAsJsonObject("progress");
        if(questions.size()==0){text("Bài này chỉ có phần lý thuyết, chưa có bài tập kiểm tra.",false);button("Đã đọc lý thuyết",()->{
            JsonObject body=new JsonObject();body.addProperty("progress_percent",100);
            request(api.grammarProgress(id,body),done->{status.setText("Đã ghi nhận đọc bài học");},null);
        });return;}
        text("Đã trả lời "+number(progress,"answered")+"/"+number(progress,"total")+" câu · Cần đúng ít nhất 80% và làm đủ câu.",false);
        if(yes(progress,"passed"))text("Bạn đã đạt yêu cầu bài học. Có thể làm lại để ôn.",true);
        index=Math.min(index,questions.size()-1);JsonObject question=questions.get(index).getAsJsonObject();
        text("Bài tập "+(index+1)+"/"+questions.size(),true);
        for(JsonElement item:rows(question,"passages")){text(value(item.getAsJsonObject(),"title"),true);text(value(item.getAsJsonObject(),"content"),false);}
        text(value(question,"question_text"),true);
        String key=String.valueOf(number(question,"id"));JsonObject checked=feedback.has(key)?feedback.getAsJsonObject(key):null;
        for(JsonElement item:rows(question,"options")){
            JsonObject option=item.getAsJsonObject();String label=value(option,"label");
            button((checked!=null&&label.equals(value(checked,"correct_label"))?"✓ ":"")+label+". "+value(option,"option_text"),()->{
                JsonObject answer=new JsonObject();answer.addProperty("question_id",number(question,"id"));answer.addProperty("label",label);
                request(api.checkGrammar(id,answer),result->{feedback.add(key,result);workspace.add("progress",result.get("progress"));render();},null);
            });
        }
        if(checked!=null){text(yes(checked,"is_correct")?"Chính xác":"Xem lại cách giải nhé",true);text(value(checked,"explanation"),false);}
        if(index>0)button("Bài tập trước",()->{index--;render();});
        if(index+1<questions.size())button("Bài tập tiếp",()->{index++;render();});
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putInt("index",index);out.putString("feedback",feedback.toString());super.onSaveInstanceState(out);}
}
