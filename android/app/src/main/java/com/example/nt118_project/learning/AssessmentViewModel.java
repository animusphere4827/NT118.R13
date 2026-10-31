package com.example.nt118_project.learning;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import com.example.nt118_project.App;
import com.google.gson.*;
import java.text.SimpleDateFormat;
import java.util.*;
import retrofit2.Call;
import static com.example.nt118_project.learning.StudyJson.*;

public final class AssessmentViewModel extends ViewModel {
    public final MutableLiveData<Integer> changes=new MutableLiveData<>(0);
    public JsonObject data;
    public String kind="mock",error;
    public boolean loading,saving,submitting,conflict;
    public long attemptId,contentId;
    public int index;
    private final SavedStateHandle state;
    private final StudyApi api=App.container().apiClient.create(StudyApi.class);
    private final SharedPreferences prefs=App.container().applicationContext.getSharedPreferences("study_drafts",0);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AttemptDraft draft=new AttemptDraft();
    private final List<Call<?>> calls=new ArrayList<>();
    private long version,deadlineElapsed;
    private final long ownerId=App.container().session.currentUser().getValue()==null?0:App.container().session.currentUser().getValue().id;
    private boolean expiryRequested,reloadAfterSave,submitAfterSave;
    private final Runnable saveTask=this::save;
    public AssessmentViewModel(SavedStateHandle state){this.state=state;index=state.get("index")==null?0:state.get("index");}
    private String key(){return App.container().config.baseUrl+":"+ownerId+":"+kind+":"+attemptId;}
    private void changed(){changes.setValue(changes.getValue()+1);}
    private <T> void call(Call<T> call,java.util.function.Consumer<T> success,java.util.function.Consumer<com.example.nt118_project.common.Resource<T>> failure){
        calls.add(call);App.container().apiCalls.enqueue(call,result->{calls.remove(call);if(call.isCanceled())return;if(result.isSuccess())success.accept(result.data);else failure.accept(result);});
    }
    public void prepare(String type,long content,long suppliedId){
        if(data!=null||loading)return;
        kind=Arrays.asList("mock","practice","quiz").contains(type)?type:"mock";contentId=content;
        Long saved=state.get("attemptId");attemptId=saved==null?suppliedId:saved;
        if(attemptId>0){refresh();return;}
        if(contentId<=0){error="Chọn một bộ luyện hoặc một bài trong lịch sử.";changed();return;}
        loading=true;changed();
        Map<String,String> filters=new HashMap<>();filters.put("content_id",String.valueOf(contentId));filters.put("status","IN_PROGRESS");
        call(api.history(kind,1,1,filters),history->{
            loading=false;JsonArray rows=rows(history,"rows");
            if(rows.size()>0){attemptId=number(rows.get(0).getAsJsonObject(),"id");state.set("attemptId",attemptId);refresh();}
            else {
                loading=true;
                Call<JsonObject> metadata=kind.equals("quiz")?api.quiz(contentId):api.content(kind.equals("mock")?"mock_tests":"practice_sets",contentId);
                call(metadata,parent->{loading=false;data=parent;data.addProperty("ready",true);changed();},failure->{loading=false;error=failure.message;changed();});
            }
        },failure->{loading=false;error=failure.message;changed();});
    }
    public void start(){
        if(loading)return;loading=true;error=null;changed();
        Call<JsonObject> request=kind.equals("quiz")?api.startQuiz(contentId,new JsonObject()):api.start(kind,contentId,new JsonObject());
        call(request,result->{attemptId=number(result,"id");state.set("attemptId",attemptId);loading=false;refresh();},failure->{
            loading=false;data=null;error=failure.message;changed();
        });
    }
    public JsonArray questions(){return rows(data,"questions");}
    public JsonObject attempt(){return data!=null&&data.has("attempt")?data.getAsJsonObject("attempt"):new JsonObject();}
    public boolean submitted(){return value(attempt(),"status").equals("SUBMITTED");}
    public long remaining(){return AttemptDraft.remaining(deadlineElapsed,SystemClock.elapsedRealtime());}
    private void anchor(JsonObject response){
        String server=value(response,"server_time"),deadline=value(attempt(),"deadline_at");
        if(!server.isEmpty()&&!deadline.isEmpty())deadlineElapsed=SystemClock.elapsedRealtime()+Math.max(0,parseTime(deadline)-parseTime(server));
    }
    private long parseTime(String value){try{return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX",Locale.US).parse(value).getTime();}catch(java.text.ParseException error){throw new IllegalStateException("Thời gian server không hợp lệ",error);}}
    public void refresh(){
        if(loading||submitting)return;if(saving){reloadAfterSave=true;return;}
        if(attemptId<=0){data=null;prepare(kind,contentId,0);return;}
        loading=true;error=null;changed();
        call(api.attempt(kind,attemptId),response->{
            data=response;loading=false;version=number(attempt(),"answer_version");anchor(response);
            String stored=prefs.getString(key(),null);draft.clear();conflict=false;
            if(submitted()){prefs.edit().remove(key()).apply();}
            else if(stored!=null&&!kind.equals("quiz")){
                try{JsonObject saved=JsonParser.parseString(stored).getAsJsonObject();draft.restore(rows(saved,"answers"));
                    conflict=!draft.isEmpty()&&number(saved,"version")!=version;if(!conflict)draft.apply(questions());
                }catch(RuntimeException ignored){prefs.edit().remove(key()).apply();}
            }
            index=Math.min(index,Math.max(0,questions().size()-1));changed();
            if(!draft.isEmpty()&&!conflict)handler.postDelayed(saveTask,600);
        },failure->{loading=false;error=failure.message;changed();});
    }
    private void persist(){if(kind.equals("quiz"))return;JsonObject saved=new JsonObject();saved.addProperty("version",version);saved.add("answers",draft.snapshot());prefs.edit().putString(key(),saved.toString()).commit();}
    public void choose(String label,boolean marked){
        if(submitted()||submitting||loading||conflict||questions().size()==0)return;
        JsonObject question=questions().get(index).getAsJsonObject();
        draft.put(number(question,"id"),label,marked);draft.apply(questions());persist();
        handler.removeCallbacks(saveTask);handler.postDelayed(saveTask,600);changed();
    }
    public void resolveConflict(boolean keepLocal){conflict=false;if(keepLocal)draft.apply(questions());else draft.clear();persist();changed();if(!draft.isEmpty())save();}
    public void move(int next){index=Math.max(0,Math.min(next,questions().size()-1));state.set("index",index);changed();}
    public void save(){
        if(saving||loading||submitting||submitted()||conflict||draft.isEmpty()||kind.equals("quiz"))return;
        if(remaining()==0){expire();return;}
        JsonArray sent=draft.snapshot();JsonObject body=new JsonObject();body.addProperty("answer_version",version);body.add("answers",sent);
        saving=true;error=null;changed();
        call(api.save(kind,attemptId,body),result->{
            saving=false;version=number(result,"answer_version");attempt().addProperty("answer_version",version);anchor(result);draft.acknowledge(sent);persist();
            if(reloadAfterSave){reloadAfterSave=false;refresh();return;}
            if(!draft.isEmpty()){handler.postDelayed(saveTask,100);changed();return;}
            changed();if(submitAfterSave){submitAfterSave=false;finish();}
        },failure->{saving=false;submitAfterSave=false;error=failure.message;if(failure.code==409)refresh();else changed();});
    }
    public void finish(){
        if(submitted()||submitting||loading||conflict)return;
        if(saving||!draft.isEmpty()){submitAfterSave=true;if(!saving)save();return;}
        submitting=true;error=null;changed();JsonObject body=new JsonObject();
        if(!kind.equals("quiz"))body.addProperty("answer_version",version);
        call(api.submit(kind,attemptId,body),result->{submitting=false;draft.clear();prefs.edit().remove(key()).apply();refresh();},failure->{
            submitting=false;error=failure.message;if(failure.code==409||failure.code==0)refresh();else changed();
        });
    }
    public void expire(){if(!expiryRequested&&!submitted()&&attemptId>0&&!loading&&!submitting){expiryRequested=true;refresh();}}
    public JsonElement quizDraft(){String saved=state.get("quizDraft:"+index);return saved==null?JsonNull.INSTANCE:JsonParser.parseString(saved);}
    public void rememberQuiz(JsonElement value){state.set("quizDraft:"+index,value.toString());}
    public void checkQuiz(JsonElement answer){
        if(loading||submitting||submitted())return;
        String validation=QuizInput.error(questions().get(index).getAsJsonObject(),answer);
        if(validation!=null){error=validation;changed();return;}
        rememberQuiz(answer);loading=true;error=null;changed();
        JsonObject body=new JsonObject();body.addProperty("question_id",number(questions().get(index).getAsJsonObject(),"id"));body.add("answer",answer);
        call(api.checkQuiz(attemptId,body),feedback->{loading=false;questions().get(index).getAsJsonObject().add("feedback",feedback);changed();},failure->{
            loading=false;error=failure.message;if(failure.code==409||failure.code==0)refresh();else changed();
        });
    }
    @Override protected void onCleared(){handler.removeCallbacksAndMessages(null);for(Call<?> call:calls)call.cancel();calls.clear();}
}
