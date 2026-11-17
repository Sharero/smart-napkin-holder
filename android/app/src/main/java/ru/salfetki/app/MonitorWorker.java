package ru.salfetki.app;
import android.content.Context;
import androidx.work.*;

public class MonitorWorker extends Worker {
    public MonitorWorker(Context context, WorkerParameters params) {
        super(context, params);
    }
    @Override
    public Result doWork() {
        try {
            Sync.run(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }
}
