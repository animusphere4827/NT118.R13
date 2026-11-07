package com.example.nt118_project.support;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import org.junit.After;
import org.junit.Before;

public abstract class ImmediateTaskTest {
    @Before public void useImmediateExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable task) { task.run(); }
            @Override public void postToMainThread(Runnable task) { task.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }
    @After public void resetExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }
}
