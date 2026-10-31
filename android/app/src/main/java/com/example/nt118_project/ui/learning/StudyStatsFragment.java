package com.example.nt118_project.ui.learning;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.nt118_project.R;
import com.google.gson.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class StudyStatsFragment extends StudyFragment {
    private int mistakePage=1;
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved){super.onViewCreated(view,saved);load();}
    private void load(){body.removeAllViews();title("Tiến bộ học tập");
        request(api.analytics(),data->{
            text("Học đều: "+number(data,"current_streak")+" ngày · Dài nhất: "+number(data,"longest_streak")+" ngày",true);
            text("Từ vựng: "+number(data,"vocabulary_completed")+" bài · Đọc hiểu: "+number(data,"readings_completed")+" bài\nNgữ pháp: "+number(data,"grammar_completed")+" bài\nLuyện Part 5: "+number(data,"practice_completed")+" lượt · Thi thử: "+number(data,"mocks_completed")+" lượt · Quiz: "+number(data,"quizzes_completed")+" lượt",false);
            text("Kết quả theo Part",true);
            for(JsonElement item:rows(data,"by_part")){
                JsonObject part=item.getAsJsonObject();long total=number(part,"total");
                text("Part "+number(part,"part_id")+": "+(total==0?"Chưa có bài làm":number(part,"correct")+"/"+total+" câu · "+value(part,"accuracy")+"%"),false);
                ProgressBar bar=new ProgressBar(requireContext(),null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress((int)number(part,"accuracy"));body.addView(bar);
            }
            text(value(data,"score_note"),false);text(value(data,"recommendation"),false);
            for(JsonElement item:rows(data,"weak_types")){JsonObject row=item.getAsJsonObject();text("Cần luyện thêm: Part "+number(row,"part_id")+" · "+typeName(value(row,"question_type"))+" · "+value(row,"accuracy")+"%",false);}
            text("Những ngày có hoạt động gần đây",true);
            JsonArray activity=rows(data,"activity");if(activity.size()==0)text("Hoàn thành bài học đầu tiên để bắt đầu ghi nhận tiến độ.",false);
            for(int i=Math.max(0,activity.size()-14);i<activity.size();i++){JsonObject row=activity.get(i).getAsJsonObject();text(value(row,"day")+": "+number(row,"count")+" hoạt động",false);}
            button("Lịch sử và bài đang làm",()->open(R.id.study_history,new Bundle()));button("Huy hiệu của tôi",()->open(R.id.badges,new Bundle()));
            button("Ôn câu sai gần nhất",()->{mistakePage=1;mistakes();});
        },this::load);
    }
    private void mistakes(){
        body.removeAllViews();title("Ôn câu sai");
        request(api.mistakes(mistakePage,20),data->{
            text("Câu sai hoặc bỏ trống ở lần làm gần nhất. Mở bài để xem lời giải rồi luyện lại bộ câu hỏi tương ứng.",false);
            JsonArray list=rows(data,"rows");if(list.size()==0)text("Chưa có câu sai cần hiển thị.",false);
            for(JsonElement item:list){JsonObject row=item.getAsJsonObject();button("Part "+number(row,"part_id")+" · "+value(row,"question_text"),()->{
                Bundle args=new Bundle();args.putString("kind",value(row,"kind"));args.putLong("attemptId",number(row,"attempt_id"));args.putInt("questionPosition",(int)number(row,"position"));open(R.id.exam_take,args);
            });}
            if(mistakePage>1)button("Trang trước",()->{mistakePage--;mistakes();});
            if((long)mistakePage*20<number(data,"total"))button("Trang tiếp",()->{mistakePage++;mistakes();});
            button("Về thống kê",this::load);
        },this::mistakes);
    }
    private String typeName(String type){switch(type){case "WORD_FORM":return "Từ loại";case "VERB_TENSE":return "Thì và dạng động từ";case "PREPOSITION":return "Giới từ";case "DETAIL":return "Chi tiết";case "INFERENCE":return "Suy luận";case "MAIN_IDEA":return "Ý chính";case "VOCABULARY":case "VOCAB_IN_CONTEXT":return "Từ vựng";default:return "Đọc hiểu và ngữ pháp";}}
}
