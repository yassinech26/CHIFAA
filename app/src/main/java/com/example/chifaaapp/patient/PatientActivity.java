package com.example.chifaaapp.patient;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.core.view.*;
import com.example.chifaaapp.R;
import java.util.*;
import java.util.function.Consumer;
import retrofit2.*;

abstract class PatientActivity extends AppCompatActivity {
    final PatientRepository repository=PatientRepository.INSTANCE;
    final PatientApi api=repository.api;
    private Call<?> pending;
    abstract int layout();
    void bind() {}
    void refresh() {}
    boolean protectedPage() { return true; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(layout());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.patient_page),(v,insets)->{
            var bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            return insets;
        });
        bind();
    }
    @Override protected void onStart() {
        super.onStart();
        if (protectedPage() && !repository.signedIn()) { reset(LoginActivity.class); return; }
        refresh();
    }
    @Override protected void onStop() {
        if (pending!=null) { pending.cancel(); pending=null; }
        busy(false);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.remove("android:viewHierarchyState");
    }
    void go(Class<?> screen) {
        Intent next=new Intent(this,screen);
        if (!protectedPage() && getIntent().getExtras()!=null) next.putExtras(getIntent().getExtras());
        startActivity(next);
    }
    void reset(Class<?> screen) {
        startActivity(new Intent(this,screen).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
    void click(int id,Runnable action) { findViewById(id).setOnClickListener(v->{ if (pending==null) action.run(); }); }
    String input(int id) {
        String raw=((EditText)findViewById(id)).getText().toString().trim();
        StringBuilder normalized=new StringBuilder(raw.length());
        for (int i=0;i<raw.length();i++) {
            char ch=raw.charAt(i);
            int digit=Character.digit(ch,10);
            normalized.append(digit<0 ? ch : (char)('0'+digit));
        }
        return normalized.toString();
    }
    void value(int id,String value) { ((TextView)findViewById(id)).setText(value); }
    void show(int id,boolean visible) { findViewById(id).setVisibility(visible?View.VISIBLE:View.GONE); }
    void notice(int id) { value(R.id.message,getString(id)); show(R.id.message,true); }
    void busy(boolean active) { show(R.id.loading,active); }
    <T> void request(Call<T> call,Consumer<T> done) {
        if (pending!=null) return;
        Models.Session session = repository.session();
        pending=call;
        busy(true); show(R.id.message,false); show(R.id.retry,false);
        call.enqueue(new Callback<T>() {
            private boolean finishCall() {
                if (pending!=call || isFinishing() || isDestroyed()) return false;
                pending=null; busy(false); return true;
            }
            @Override public void onResponse(Call<T> c,Response<T> r) {
                if (!finishCall()) return;
                if (r.isSuccessful()) { done.accept(r.body()); return; }
                if (r.errorBody()!=null) r.errorBody().close();
                if (r.code()==401 && protectedPage()) {
                    repository.unauthorized(PatientActivity.this,session);
                    return;
                }
                if (r.code()==403 && PatientActivity.this instanceof HealthPage) {
                    reset(ProfileActivity.class); return;
                }
                int message;
                switch (r.code()) {
                    case 400: message=R.string.invalid; break;
                    case 401: message=R.string.unauthorized; break;
                    case 403: message=R.string.forbidden; break;
                    case 409: message=R.string.conflict; break;
                    case 429: message=R.string.limited; break;
                    default: message=R.string.unavailable;
                }
                notice(message); offerRetry();
            }
            private void offerRetry() {
                if ("GET".equals(call.request().method())) {
                    show(R.id.retry,true); click(R.id.retry,()->request(call.clone(),done));
                }
            }
            @Override public void onFailure(Call<T> c,Throwable t) {
                if (finishCall()) { notice(R.string.network_error); offerRetry(); }
            }
        });
    }
    String[] array(int id) { return getResources().getStringArray(id); }
    void options(int id,String[] codes,String[] labels,String current,int prompt) {
        Spinner s=findViewById(id);
        List<String> choices=new ArrayList<>();
        choices.add(getString(prompt)); choices.addAll(Arrays.asList(labels));
        var adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices);
        s.setAdapter(adapter); s.setTag(codes); s.setSelection(Arrays.asList(codes).indexOf(current)+1);
    }
    String selection(int id) {
        Spinner s=findViewById(id); int index=s.getSelectedItemPosition()-1;
        return index<0?null:((String[])s.getTag())[index];
    }
    String label(String code,int codes,int labels) {
        int i=Arrays.asList(array(codes)).indexOf(code);
        return i<0?getString(R.string.skip):array(labels)[i];
    }
    void profileFields(Models.Profile p) {
        options(R.id.age_band,array(R.array.age_band_codes),array(R.array.age_band_labels),p==null?null:p.ageBand,R.string.choose);
        options(R.id.governorate,array(R.array.governorate_codes),array(R.array.governorate_labels),p==null?null:p.governorate,R.string.choose);
        options(R.id.language,array(R.array.language_codes),array(R.array.language_labels),p==null?null:p.preferredLanguage,R.string.choose);
    }
    Models.Profile profileInput() {
        var p=new Models.Profile(selection(R.id.age_band),selection(R.id.governorate),selection(R.id.language));
        if (p.ageBand!=null && p.governorate!=null && p.preferredLanguage!=null) return p;
        notice(R.string.invalid); return null;
    }
    void choices(int id,List<String> codes,List<String> labels,List<String> selected) {
        LinearLayout box=findViewById(id); box.removeAllViews();
        for (int i=0;i<codes.size();i++) {
            CheckBox check=new CheckBox(this);
            check.setMinHeight(getResources().getDimensionPixelSize(R.dimen.patient_target));
            check.setText(labels.get(i)); check.setTag(codes.get(i)); check.setChecked(selected.contains(codes.get(i)));
            box.addView(check);
        }
    }
    List<String> checked(int id) {
        LinearLayout box=findViewById(id); List<String> values=new ArrayList<>();
        for (int i=0;i<box.getChildCount();i++) {
            if (box.getChildAt(i) instanceof CheckBox) {
                CheckBox c=(CheckBox)box.getChildAt(i);
                if (c.isChecked()) values.add((String)c.getTag());
            }
        }
        return values;
    }
    void line(LinearLayout box,String text) {
        TextView v=new TextView(this); v.setTextAppearance(R.style.PatientBody); v.setText(text); box.addView(v);
    }
}
