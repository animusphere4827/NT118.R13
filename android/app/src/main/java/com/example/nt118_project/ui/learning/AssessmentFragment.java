package com.example.nt118_project.ui.learning;

import android.app.AlertDialog;
import android.os.*;
import android.text.*;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.example.nt118_project.R;
import com.example.nt118_project.learning.AssessmentViewModel;
import com.google.gson.*;
import java.util.*;
import static com.example.nt118_project.learning.StudyJson.*;

public final class AssessmentFragment extends StudyFragment {
    private AssessmentViewModel model;
    private final Handler timer=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private boolean onlyWrong;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(getView()==null)return;
        if(model.attemptId>0&&!model.submitted()) {
            long seconds=model.remaining()/1000;
            status.setText(String.format(Locale.getDefault(),"Còn %02d:%02d · %s",seconds/60,seconds%60,
                model.error!=null?model.error:model.saving?"Đang lưu đáp án":"Đáp án lưu tự động"));
            if(seconds==0)model.expire();
        }
        timer.postDelayed(this,1000);
    }};
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle saved){
        super.onViewCreated(view,saved);model=new ViewModelProvider(this).get(AssessmentViewModel.class);
        onlyWrong=saved!=null&&saved.getBoolean("onlyWrong");
        view.findViewById(R.id.studyBack).setOnClickListener(v->back());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){back();}});
        model.changes.observe(getViewLifecycleOwner(),ignored->render());
        Bundle args=getArguments()==null?new Bundle():getArguments();
        long supplied=args.getLong("attemptId",0);
        if(saved==null && args.getInt("questionPosition",0)>0) model.index=args.getInt("questionPosition")-1;
        model.prepare(args.getString("kind","mock"),args.getLong("contentId",0),supplied);
        timer.post(tick);
    }
    private void back(){
        if(model.attemptId<=0||model.submitted()){NavHostFragment.findNavController(this).popBackStack();return;}
        dialog=new AlertDialog.Builder(requireContext()).setTitle("Tạm rời bài làm?")
            .setMessage("Thời gian vẫn tiếp tục chạy. Đáp án chưa gửi được giữ trên thiết bị này để tiếp tục khi mở lại.")
            .setPositiveButton("Lưu và rời bài",(d,w)->{model.save();NavHostFragment.findNavController(this).popBackStack();})
            .setNegativeButton("Làm tiếp",null).show();
    }
    private void render(){
        if(body==null)return;body.removeAllViews();loading(model.loading||model.submitting);
        title(model.kind.equals("quiz")?"Quiz vận dụng":model.kind.equals("practice")?"Luyện Part 5":"Thi thử Reading");
        if(model.error!=null){text(model.error,false);button("Tải lại bài làm",model::refresh);}
        if(model.data==null){if(!model.loading&&model.error==null)text("Đang tải bài làm…",false);return;}
        if(yes(model.data,"ready")) {
            text(value(model.data,"title"),true);text(value(model.data,"description"),false);
            text("Thời lượng: "+Math.max(1,number(model.data,"time_limit")/60)+" phút. Đồng hồ bắt đầu khi bạn bấm bắt đầu.",false);
            text(model.kind.equals("quiz")?"Kiểm tra từng câu để xem phản hồi. Các câu đã chấm không thể đổi đáp án.":"Làm bài, đánh dấu câu cần xem lại, rồi nộp để xem kết quả và lời giải. Đóng app không dừng giờ.",false);
            button("Bắt đầu làm bài",model::start);return;
        }
        if(model.questions().size()==0){text("Chưa có câu hỏi. Hãy mở bài từ thư viện hoặc lịch sử.",false);button("Mở lịch sử",()->open(R.id.study_history,new Bundle()));return;}
        if(model.conflict){
            text("Bài làm đã thay đổi trên thiết bị khác. Chọn cách xử lý phần chưa gửi từ máy này.",true);
            button("Dùng đáp án trên server",()->model.resolveConflict(false));
            button("Áp dụng các câu chưa lưu của máy này",()->model.resolveConflict(true));return;
        }
        boolean submitted=model.submitted();
        if(submitted){
            JsonObject result=model.attempt().has("result")&&!model.attempt().get("result").isJsonNull()?model.attempt().getAsJsonObject("result"):new JsonObject();
            text("Kết quả: "+number(result,"correct_count")+"/"+number(result,"total_questions")+" câu · "+value(result,"accuracy")+"%",true);
            text(model.kind.equals("quiz")?"Xem lại cách làm và các câu chưa đúng.":"Đây là tỷ lệ đúng Reading luyện tập, không phải điểm TOEIC quy đổi.",false);
            for(JsonElement part:rows(result,"by_part")){JsonObject row=part.getAsJsonObject();if(number(row,"total_questions")>0)text("Part "+number(row,"part_id")+": "+number(row,"correct_count")+"/"+number(row,"total_questions"),false);}
            CheckBox filter=new CheckBox(requireContext());filter.setText("Chỉ xem câu sai hoặc bỏ trống");filter.setChecked(onlyWrong);body.addView(filter);
            filter.setOnCheckedChangeListener((button,checked)->{onlyWrong=checked;model.move(firstVisible());});
            status.setText("Đã nộp · Kết quả được lưu trên server");
        }
        JsonObject question=model.questions().get(model.index).getAsJsonObject();
        if(onlyWrong&&submitted&&correct(question)){text("Không còn câu sai trong bộ lọc này.",false);button("Xem tất cả câu",()->{onlyWrong=false;render();});return;}
        button("Danh sách câu hỏi",this::jump);
        text("Câu "+(model.index+1)+"/"+model.questions().size(),true);
        if(model.kind.equals("quiz"))quiz(question);else readingQuestion(question,submitted);
        if(model.index>0)button("Câu trước",()->model.move(nextVisible(-1)));
        if(model.index+1<model.questions().size())button("Câu tiếp",()->model.move(nextVisible(1)));
        if(!submitted)button(model.submitting?"Đang nộp…":"Nộp bài",()->{
            int answered=0;for(JsonElement item:model.questions()){
                JsonObject q=item.getAsJsonObject();if(!value(q,"selected_label").isEmpty()||(q.has("feedback")&&!q.get("feedback").isJsonNull()))answered++;
            }
            dialog=new AlertDialog.Builder(requireContext()).setTitle("Nộp bài?")
                .setMessage("Đã trả lời "+answered+"/"+model.questions().size()+" câu. Sau khi nộp sẽ không đổi đáp án.")
                .setPositiveButton("Nộp bài",(d,w)->model.finish()).setNegativeButton("Kiểm tra lại",null).show();
        });
        else {button("Lịch sử học tập",()->open(R.id.study_history,new Bundle()));button("Ôn câu sai",()->open(R.id.study_stats,new Bundle()));}
    }
    private boolean correct(JsonObject q){return model.kind.equals("quiz")?q.has("feedback")&&!q.get("feedback").isJsonNull()&&yes(q.getAsJsonObject("feedback"),"is_correct"):yes(q,"is_correct");}
    private int firstVisible(){for(int i=0;i<model.questions().size();i++)if(!onlyWrong||!correct(model.questions().get(i).getAsJsonObject()))return i;return model.index;}
    private int nextVisible(int direction){for(int i=model.index+direction;i>=0&&i<model.questions().size();i+=direction)if(!onlyWrong||!correct(model.questions().get(i).getAsJsonObject()))return i;return model.index;}
    private void jump(){
        List<Integer> indices=new ArrayList<>();List<String> names=new ArrayList<>();
        for(int i=0;i<model.questions().size();i++){
            JsonObject q=model.questions().get(i).getAsJsonObject();if(onlyWrong&&model.submitted()&&correct(q))continue;
            indices.add(i);names.add("Câu "+(i+1)+(model.submitted()?(correct(q)?" · Đúng":" · Cần ôn"):
                (!value(q,"selected_label").isEmpty()?" · Đã chọn":"")+(yes(q,"marked_for_review")?" · Đánh dấu":"")));
        }
        dialog=new AlertDialog.Builder(requireContext()).setTitle("Đi tới câu hỏi").setItems(names.toArray(new String[0]),(d,index)->model.move(indices.get(index))).setNegativeButton("Đóng",null).show();
    }
    private void readingQuestion(JsonObject q,boolean review){
        if(!value(q,"group_instructions").isEmpty())text(value(q,"group_instructions"),false);
        for(JsonElement item:rows(q,"passages")){
            JsonObject p=item.getAsJsonObject();text(value(p,"title"),true);text(value(p,"content"),false);
            if(!value(p,"image_url").isEmpty()){
                ImageView image=new ImageView(requireContext());image.setAdjustViewBounds(true);
                body.addView(image,new LinearLayout.LayoutParams(-1,dp(240)));
                com.bumptech.glide.Glide.with(this).load(value(p,"image_url")).fitCenter().into(image);
            }
        }
        text(value(q,"question_text"),true);String selected=value(q,"selected_label");
        for(JsonElement item:rows(q,"options")){
            JsonObject option=item.getAsJsonObject();String label=value(option,"label");
            String prefix=label.equals(selected)?"● ":"○ ";if(review&&yes(option,"is_correct"))prefix="✓ ";
            Button button=button(prefix+label+". "+value(option,"option_text"),()->model.choose(label,yes(q,"marked_for_review")));button.setEnabled(!review&&!model.loading&&!model.submitting);
        }
        if(review){text(correct(q)?"Trả lời đúng":selected.isEmpty()?"Bạn đã bỏ trống câu này":"Câu này cần ôn lại",false);text(value(q,"explanation"),false);
            for(JsonElement item:rows(q,"passages")){String translation=value(item.getAsJsonObject(),"translation_vi");if(!translation.isEmpty())text(translation,false);}
        } else {
            CheckBox marked=new CheckBox(requireContext());marked.setText("Đánh dấu để xem lại");marked.setChecked(yes(q,"marked_for_review"));marked.setEnabled(!model.loading&&!model.submitting);body.addView(marked);
            marked.setOnCheckedChangeListener((button,checked)->model.choose(selected,checked));
            if(!selected.isEmpty())button("Xóa lựa chọn",()->model.choose(null,yes(q,"marked_for_review")));
        }
    }
    private void quiz(JsonObject q){
        text(value(q,"prompt"),true);JsonObject payload=q.getAsJsonObject("payload");
        boolean answered=q.has("feedback")&&!q.get("feedback").isJsonNull();JsonElement stored=model.quizDraft();
        String type=value(q,"type");
        if(type.equals("FILL_BLANK")){
            text(value(payload,"text"),false);EditText input=new EditText(requireContext());input.setHint("Điền từ hoặc cụm từ");input.setSingleLine(true);
            if(stored.isJsonPrimitive())input.setText(stored.getAsString());input.setEnabled(!answered&&!model.submitted());body.addView(input);
            input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){model.rememberQuiz(new JsonPrimitive(s.toString()));}public void afterTextChanged(Editable value){}});
            if(!answered&&!model.submitted())button("Kiểm tra",()->{if(input.getText().toString().trim().isEmpty()){status.setText("Nhập câu trả lời trước khi kiểm tra.");return;}model.checkQuiz(new JsonPrimitive(input.getText().toString()));});
        }else if(type.equals("MATCH")){
            JsonArray left=rows(payload,"left"),right=rows(payload,"right");List<Spinner> selectors=new ArrayList<>();
            String[] names=new String[right.size()+1];names[0]="Chọn nghĩa tương ứng";for(int i=0;i<right.size();i++)names[i+1]=value(right.get(i).getAsJsonObject(),"text");
            JsonObject restored=stored.isJsonObject()?stored.getAsJsonObject():new JsonObject();
            for(int i=0;i<left.size();i++){
                JsonObject item=left.get(i).getAsJsonObject();text(value(item,"text"),false);Spinner spinner=new Spinner(requireContext());spinner.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,names));spinner.setEnabled(!answered&&!model.submitted());body.addView(spinner);selectors.add(spinner);
                for(JsonElement pair:rows(restored,"pairs")){JsonObject p=pair.getAsJsonObject();if(value(p,"left_id").equals(value(item,"id")))for(int j=0;j<right.size();j++)if(value(right.get(j).getAsJsonObject(),"id").equals(value(p,"right_id")))spinner.setSelection(j+1);}
            }
            Runnable remember=()->{JsonArray pairs=new JsonArray();for(int i=0;i<left.size();i++){int selected=selectors.get(i).getSelectedItemPosition();if(selected==0)continue;JsonObject pair=new JsonObject();pair.addProperty("left_id",value(left.get(i).getAsJsonObject(),"id"));pair.addProperty("right_id",value(right.get(selected-1).getAsJsonObject(),"id"));pairs.add(pair);}JsonObject answer=new JsonObject();answer.add("pairs",pairs);model.rememberQuiz(answer);};
            for(Spinner spinner:selectors)spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){remember.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});
            if(!answered&&!model.submitted())button("Kiểm tra cặp nối",()->{remember.run();model.checkQuiz(model.quizDraft());});
        }else{
            JsonArray tokens=rows(payload,"tokens");List<String> order=new ArrayList<>();
            if(stored.isJsonObject())for(JsonElement item:rows(stored.getAsJsonObject(),"order"))order.add(item.getAsString());
            List<String> sentence=new ArrayList<>();for(String id:order)for(JsonElement token:tokens)if(value(token.getAsJsonObject(),"id").equals(id))sentence.add(value(token.getAsJsonObject(),"text"));
            text(sentence.isEmpty()?"Chọn các từ theo thứ tự để tạo câu.":android.text.TextUtils.join(" ",sentence),false);
            if(!answered&&!model.submitted()){
                for(JsonElement token:tokens){JsonObject item=token.getAsJsonObject();String id=value(item,"id");if(!order.contains(id))button(value(item,"text"),()->{order.add(id);JsonObject answer=new JsonObject();JsonArray array=new JsonArray();for(String entry:order)array.add(entry);answer.add("order",array);model.rememberQuiz(answer);render();});}
                button("Xếp lại từ đầu",()->{model.rememberQuiz(new JsonObject());render();});
                button("Kiểm tra thứ tự",()->model.checkQuiz(model.quizDraft()));
            }
        }
        if(answered){JsonObject feedback=q.getAsJsonObject("feedback");text(yes(feedback,"is_correct")?"Chính xác":"Cùng xem lại nhé",true);text(value(feedback,"explanation"),false);
            JsonObject solution=feedback.getAsJsonObject("correct_answer");
            if(type.equals("FILL_BLANK")){List<String> accepted=new ArrayList<>();for(JsonElement word:rows(solution,"answers"))accepted.add(word.getAsString());text("Đáp án: "+android.text.TextUtils.join(" / ",accepted),false);}
            if(type.equals("MATCH"))for(JsonElement pair:rows(solution,"pairs")){JsonObject p=pair.getAsJsonObject();text(tokenText(rows(payload,"left"),value(p,"left_id"))+" → "+tokenText(rows(payload,"right"),value(p,"right_id")),false);}
            if(type.equals("ORDER")){List<String> sentence=new ArrayList<>();for(JsonElement id:rows(solution,"order"))sentence.add(tokenText(rows(payload,"tokens"),id.getAsString()));text(android.text.TextUtils.join(" ",sentence),false);}
        }
    }
    private String tokenText(JsonArray rows,String id){for(JsonElement item:rows)if(value(item.getAsJsonObject(),"id").equals(id))return value(item.getAsJsonObject(),"text");return "";}
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putBoolean("onlyWrong",onlyWrong);super.onSaveInstanceState(out);}
    @Override public void onPause(){if(model!=null)model.save();super.onPause();}
    @Override public void onDestroyView(){timer.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();super.onDestroyView();}
}
