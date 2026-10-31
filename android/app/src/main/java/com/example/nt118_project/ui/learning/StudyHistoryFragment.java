package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.nt118_project.R;
import com.google.gson.*;
import java.util.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class StudyHistoryFragment extends StudyFragment {
    private String kind="mock";
    private int page=1;
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved){super.onViewCreated(view,saved);if(saved!=null){kind=saved.getString("kind","mock");page=saved.getInt("page",1);}load();}
    private void select(String value){kind=value;page=1;load();}
    private void load(){
        title("Lịch sử học tập");body.removeAllViews();
        button("Thi thử",()->select("mock"));button("Luyện Part 5",()->select("practice"));button("Quiz",()->select("quiz"));
        request(api.history(kind,page,20,Collections.emptyMap()),data->{
            JsonArray list=rows(data,"rows");if(list.size()==0)text("Chưa có bài làm ở mục này.",false);
            for(JsonElement item:list){JsonObject row=item.getAsJsonObject();boolean done=value(row,"status").equals("SUBMITTED");
                String details=done?"Đúng "+number(row,"correct_count")+"/"+number(row,"total_questions")+" · "+value(row,"accuracy")+"%":"Đang làm · Tiếp tục hoặc xem kết quả nếu đã hết giờ";
                button(value(row,"content_title")+"\n"+details,()->{
                    Bundle args=new Bundle();args.putString("kind",kind);args.putLong("attemptId",number(row,"id"));open(R.id.exam_take,args);
                });
            }
            text("Trang "+page+" · "+number(data,"total")+" lượt làm bài",false);
            if(page>1)button("Trang trước",()->{page--;load();});
            if((long)page*20<number(data,"total"))button("Trang tiếp",()->{page++;load();});
        },this::load);
    }
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putString("kind",kind);out.putInt("page",page);super.onSaveInstanceState(out);}
}
