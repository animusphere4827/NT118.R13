package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.nt118_project.R;
import com.google.gson.*;
import java.util.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class StudyCatalogFragment extends StudyFragment {
    private String kind="mock",search="";
    private int page=1;
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved) {
        super.onViewCreated(view,saved);
        kind=saved!=null?saved.getString("kind","mock"):getArguments()==null?"mock":getArguments().getString("catalogKind","mock");
        page=saved==null?1:saved.getInt("page",1);search=saved==null?"":saved.getString("search","");
        bottom(kind.equals("mock")?R.id.tabExam:0);load();
    }
    private void choose(String value) {kind=value;page=1;search="";load();}
    private void load() {
        body.removeAllViews();
        title(kind.equals("reading")?"Đọc hiểu thực tế":kind.equals("mock")?"Thi thử Reading":"Luyện tập và ngữ pháp");
        if(!kind.equals("reading")&&!kind.equals("mock")) {
            button("Luyện câu Part 5",()->choose("practice"));button("Quiz nhớ và vận dụng",()->choose("quiz"));button("Bài học ngữ pháp",()->choose("grammar"));
        }
        button("Lịch sử và bài đang làm",()->open(R.id.study_history,new Bundle()));
        if(kind.equals("reading")) {
            EditText input=new EditText(requireContext());input.setHint("Tìm tiêu đề bài đọc");input.setSingleLine(true);input.setText(search);body.addView(input);
            button("Tìm kiếm",()->{search=input.getText().toString().trim();page=1;load();});
        }
        retrofit2.Call<JsonObject> call;
        if(kind.equals("reading"))call=api.readings(page,20,search);
        else if(kind.equals("quiz"))call=api.quizzes(page,20);
        else call=api.catalog(kind.equals("mock")?"mock_tests":kind.equals("grammar")?"grammar_lessons":"practice_sets",page,20,
                kind.equals("practice")?Collections.singletonMap("part_id","5"):Collections.emptyMap());
        request(call,data->{
            JsonArray list=rows(data,"rows");long total=number(data,"total");
            if(list.size()==0)text("Chưa có nội dung ở trang này.",false);
            for(JsonElement item:list) {
                JsonObject row=item.getAsJsonObject();long id=number(row,"id");
                String extra=kind.equals("reading")?"Part "+number(row,"part_id")+" · "+number(row,"passage_count")+" đoạn":
                    kind.equals("grammar")?"Bài học và bài tập":Math.max(1,number(row,"time_limit")/60)+" phút";
                button(value(row,"title")+"\n"+extra,()->{
                    Bundle args=new Bundle();args.putLong("contentId",id);args.putString("kind",kind);
                    open(kind.equals("reading")?R.id.reading_article:kind.equals("grammar")?R.id.grammar_lesson:R.id.exam_take,args);
                });
            }
            text("Trang "+page+" · "+total+" nội dung",false);
            if(page>1)button("Trang trước",()->{page--;load();});
            if((long)page*20<total)button("Trang tiếp",()->{page++;load();});
        },this::load);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out) {out.putString("kind",kind);out.putInt("page",page);out.putString("search",search);super.onSaveInstanceState(out);}
}
