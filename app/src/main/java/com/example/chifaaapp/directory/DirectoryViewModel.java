package com.example.chifaaapp.directory;

import android.content.Context;
import com.example.chifaaapp.local.ScreenViewModel;

public final class DirectoryViewModel extends ScreenViewModel {
    void refresh(Context context) {
        run(() -> new DirectoryRepository(context).sync());
    }
}
