package com.example.nt118_project.ui.learning;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.example.nt118_project.App;
import com.example.nt118_project.R;
import com.example.nt118_project.common.Resource;
import com.example.nt118_project.learning.StudyApi;
import com.example.nt118_project.ui.common.BottomNavHelper;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;

public abstract class StudyFragment extends Fragment {
    protected LinearLayout body;
    protected TextView status;
    protected StudyApi api;
    protected boolean busy;
    private final List<Call<?>> calls=new ArrayList<>();
    public StudyFragment() {super(R.layout.fragment_study_workspace);}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle savedInstanceState) {
        body=view.findViewById(R.id.studyBody);status=view.findViewById(R.id.studyStatus);
        api=App.container().apiClient.create(StudyApi.class);
        view.findViewById(R.id.studyBack).setOnClickListener(v->NavHostFragment.findNavController(this).popBackStack());
        view.findViewById(R.id.studyBottomNav).setVisibility(View.GONE);
    }
    protected void title(String title) {((TextView)requireView().findViewById(R.id.studyTitle)).setText(title);}
    protected void bottom(int tab) {
        requireView().findViewById(R.id.studyBottomNav).setVisibility(View.VISIBLE);
        BottomNavHelper.bind(requireView(),NavHostFragment.findNavController(this),tab);
    }
    protected int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    protected TextView text(String value,boolean heading) {
        TextView view=new TextView(requireContext());view.setText(value);view.setTextSize(heading?21:17);
        view.setTextColor(ContextCompat.getColor(requireContext(),R.color.app_text));
        if(heading)view.setTypeface(null,Typeface.BOLD);
        view.setLineSpacing(dp(4),1);view.setPadding(0,dp(8),0,dp(12));body.addView(view);return view;
    }
    protected Button button(String title,Runnable action) {
        Button button=new Button(requireContext());button.setText(title);button.setAllCaps(false);button.setMinHeight(dp(48));
        button.setOnClickListener(v->{if(!busy)action.run();});body.addView(button);return button;
    }
    protected void open(int destination,Bundle args) {if(isResumed())NavHostFragment.findNavController(this).navigate(destination,args);}
    protected void loading(boolean value) {busy=value;if(getView()!=null)getView().findViewById(R.id.studyLoading).setVisibility(value?View.VISIBLE:View.GONE);}
    protected void error(String message,Runnable retry) {status.setText(message==null?"Không tải được dữ liệu":message);if(retry!=null)button("Thử lại",retry);}
    protected interface Done<T> {void accept(T value);}
    protected <T> void request(Call<T> call,Done<T> success,Runnable retry) {
        calls.add(call);loading(true);
        App.container().apiCalls.enqueue(call,result->{
            calls.remove(call);if(call.isCanceled()||getView()==null)return;loading(false);
            if(result.isSuccess()) {status.setText("");success.accept(result.data);}
            else error(result.message,retry);
        });
    }
    @Override public void onDestroyView() {for(Call<?> call:calls)call.cancel();calls.clear();body=null;status=null;super.onDestroyView();}
}
