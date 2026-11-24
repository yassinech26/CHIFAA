package com.example.chifaaapp.local;

import androidx.lifecycle.*;
import java.util.concurrent.*;

public class ScreenViewModel extends ViewModel {
    @FunctionalInterface public interface Operation { Object run() throws Exception; }
    public static final class Result {
        public final Object value;
        public final Exception error;
        private boolean consumed;
        Result(Object value, Exception error) { this.value = value; this.error = error; }
        public boolean consume() {
            if (consumed) return false;
            consumed = true;
            return true;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Result> results = new MutableLiveData<>();
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<Result> results() { return results; }

    public void run(Operation operation) {
        if (Boolean.TRUE.equals(busy.getValue())) return;
        busy.setValue(true);
        worker.execute(() -> {
            Result result;
            try { result = new Result(operation.run(), null); }
            catch (Exception error) { result = new Result(null, error); }
            busy.postValue(false);
            results.postValue(result);
        });
    }

    @Override protected void onCleared() { worker.shutdownNow(); }
}
