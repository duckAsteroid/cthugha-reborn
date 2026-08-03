package com.asteroid.duck.opengl.util.renderaction;

import com.asteroid.duck.opengl.util.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe Queue for render actions, lets clients queue up actions to be performed during rendering loop.
 * Actions have an "action type" (a string identifying a particular kind of action).
 * Certain types can be marked as "singleton"; this means that only the latest action of that type is kept in the 
 * queue. (e.g. variable updates where only the latest value matters)
 */
public class RenderActionQueue {
    private static final Logger LOG = LoggerFactory.getLogger(RenderActionQueue.class);
    private final Queue<Action> queue = new LinkedList<>();
    private final Map<String, Action> latestByType = new HashMap<>();
    private final Set<String> singletonTypes;
    private final ReentrantLock lock = new ReentrantLock(false);

    /**
     * Create a queue with the given action type names declared as singletons.
     *
     * @param singletonTypes zero or more action type strings that should be deduplicated;
     *                       only the most recently enqueued action of each type is retained
     */
    public RenderActionQueue(String ... singletonTypes) {
        this(new HashSet<>(Arrays.asList(singletonTypes)));
    }
    /**
     * Constructs a new RenderActionQueue with a given list of singleton action types.
     * @param singletonTypes the set of action types that are singleton
     */
    public RenderActionQueue(Set<String> singletonTypes) {
        this.singletonTypes = new HashSet<>(singletonTypes);
    }

    /**
     * Enqueues an action with the given type.
     * If the action type is singleton, it replaces any existing action of that type in the queue.
     * Otherwise, it is simply added to the end of the queue.
     * @param actionType the type of the action
     * @param action the action to enqueue
     */
    public void enqueue(String actionType, Action action) {
        if (action != null) {
            try {
                lock.lock();
                if (singletonTypes.contains(actionType)) {
                    latestByType.put(actionType, action);
                } else {
                    queue.offer(action);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Drains the queue, returning a list of all actions in the order they were added.
     * Deduplicated actions are included as the latest version.
     * @return a list of all actions in the queue
     */
    public List<Action> drain() {
        try {
            lock.lock();
            // create a copy to avoid concurrent modification issues, then clear the original queue and latestByType map
            List<Action> actions = new ArrayList<>(queue);
            // clear the queue
            queue.clear();
            // add the latest actions for singleton types
            actions.addAll(latestByType.values());
            // clear the latestByType map
            latestByType.clear();
            return actions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Drains the queue and processes all actions by calling their onRender method with the given RenderContext.
     * @param ctx the RenderContext to pass to each action's onRender method
     */
    public void processAll(RenderContext ctx) {
        for (Action action : drain()) {
            try {
                action.onRender(ctx);
            } catch (Exception e) {
                LOG.error("Error processing render action of type {}: {}", action.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
