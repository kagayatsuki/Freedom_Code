package com.coding.agent.utils;

import com.coding.agent.workflow.WorkflowContext;
import org.springframework.stereotype.Component;

@Component
public class WorkflowContextHolder {

    private static final ThreadLocal<WorkflowContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(WorkflowContext context)  {
        CONTEXT.set(context);
    }

    public static WorkflowContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}