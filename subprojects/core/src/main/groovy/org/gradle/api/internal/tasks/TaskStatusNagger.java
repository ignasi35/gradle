/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskStatusNagger {
    private final TaskInternal taskInternal;
    private boolean nagUser = true;
    private static Logger logger = LoggerFactory.getLogger(TaskStatusNagger.class);

    public TaskStatusNagger(TaskInternal taskInternal) {
        this.taskInternal = taskInternal;
    }

    public void nagIfTaskNotInConfigurableState(String method) {
        if (!taskInternal.getStateInternal().isConfigurable() && nagUser) {
            logger.warn(String.format("Calling %s after task execution has started has been deprecated and is scheduled to be removed in Gradle 2.0 Check the configuration of task %s. You may have misused '<<' at task declaration.", method, taskInternal));
        }
    }

    public void whileDisabled(Runnable runnable) {
        nagUser = false;
        try {
            runnable.run();
        } finally {
            nagUser = true;
        }
    }

    public <T> T whileDisabled(Factory<T> factory) {
        nagUser = false;
        try {
            return factory.create();
        } finally {
            nagUser = true;
        }
    }
}
