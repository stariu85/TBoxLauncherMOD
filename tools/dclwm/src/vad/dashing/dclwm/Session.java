package vad.dashing.dclwm;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One text command from the launcher. The caller has already checked that the
 * socket peer is root or the launcher uid.
 */
public final class Session {
    private static final int VERIFY_RETRIES = 2;
    private static final long VERIFY_RETRY_SLEEP_MS = 50L;
    private static final Object MISSING_METHOD = new Object();
    private static final Map<String, Object> METHOD_CACHE = new HashMap<String, Object>();
    private static volatile Constructor<?> cachedRectCtor;

    private final Am am;

    public Session(Am am) {
        this.am = am;
    }

    public String handle(String line, int peerUid, int launcherUid) {
        if (!isAllowed(peerUid, launcherUid)) {
            return "ERR forbidden\n";
        }
        if (line == null) {
            return "ERR bad_args\n";
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return "ERR bad_args\n";
        }
        String[] parts = trimmed.split(" ");
        String op = parts[0];
        try {
            if ("PING".equals(op) && parts.length == 1) {
                return "OK\n";
            }
            if ("VERSION".equals(op) && parts.length == 1) {
                return "OK " + Protocol.VERSION + "\n";
            }
            if ("LIST".equals(op) && parts.length == 1) {
                return list();
            }
            if ("RESIZE".equals(op) && parts.length == 6) {
                return resize(
                        parseId(parts[1]),
                        parseCoord(parts[2]),
                        parseCoord(parts[3]),
                        parseCoord(parts[4]),
                        parseCoord(parts[5]));
            }
            if ("RESTORE_FREEFORM".equals(op) && parts.length == 6) {
                return restoreFreeform(
                        parseId(parts[1]),
                        parseCoord(parts[2]),
                        parseCoord(parts[3]),
                        parseCoord(parts[4]),
                        parseCoord(parts[5]));
            }
            if ("MINIMIZE".equals(op) && parts.length == 2) {
                return minimize(parseId(parts[1]));
            }
            if ("DISMISS".equals(op) && parts.length == 2) {
                return dismiss(parseId(parts[1]));
            }
            if ("MOVE_TASK_TO_BACK".equals(op) && (parts.length == 1 || parts.length == 2)) {
                if (parts.length == 2) {
                    return moveTaskToBack(parseId(parts[1]));
                } else {
                    return moveForeignTasksToBack();
                }
            }
            if ("SET_WINDOWING_MODE".equals(op) && (parts.length == 1 || parts.length == 2 || parts.length == 3)) {
                int mode = 1; // 1 = WINDOWING_MODE_FULLSCREEN
                if (parts.length == 3) {
                    return setTaskWindowingMode(parseId(parts[1]), parseWindowingMode(parts[2]));
                } else if (parts.length == 2) {
                    return setTaskWindowingMode(parseId(parts[1]), mode);
                } else {
                    return setForeignTasksWindowingMode(mode);
                }
            }
            return "ERR unknown\n";
        } catch (NumberFormatException bad) {
            return "ERR bad_args\n";
        } catch (AmFailure failure) {
            return "ERR am " + failure.getMessage() + "\n";
        }
    }

    static boolean isAllowed(int peerUid, int launcherUid) {
        if (peerUid == 0) {
            return true;
        }
        return launcherUid >= 0 && peerUid == launcherUid;
    }

    private static int parseId(String text) {
        int value = Integer.parseInt(text);
        if (value < 0) {
            throw new NumberFormatException(text);
        }
        return value;
    }

    private static int parseCoord(String text) {
        int value = Integer.parseInt(text);
        if (value < -100 || value > 4000) {
            throw new NumberFormatException(text);
        }
        return value;
    }

    private static int parseWindowingMode(String text) {
        int value = Integer.parseInt(text);
        if (value < 0 || value > 15) {
            throw new NumberFormatException(text);
        }
        return value;
    }

    private String list() throws AmFailure {
        List<WindowTask> tasks = read();
        StringBuilder out = new StringBuilder("OK\n");
        for (int i = 0; i < tasks.size(); i++) {
            out.append(tasks.get(i).format()).append('\n');
        }
        out.append("END\n");
        return out.toString();
    }

    private String resize(int taskId, int left, int top, int right, int bottom) throws AmFailure {
        if (left >= right || top >= bottom) {
            return "ERR bad_args\n";
        }
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        moveStack(task.stackId, left, top, right, bottom);
        applyResizeTask(taskId, left, top, right, bottom);
        for (int attempt = 0; attempt <= VERIFY_RETRIES; attempt++) {
            if (attempt > 0) {
                sleepQuietly(VERIFY_RETRY_SLEEP_MS);
            }
            WindowTask after = StackListParser.find(read(), taskId);
            if (after != null
                    && after.left == left && after.top == top
                    && after.right == right && after.bottom == bottom) {
                return "OK\n";
            }
        }
        return "ERR am bounds\n";
    }

    private String restoreFreeform(int taskId, int left, int top, int right, int bottom) throws AmFailure {
        if (left >= right || top >= bottom) {
            return "ERR bad_args\n";
        }
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        if (!applyTaskWindowingMode(taskId, 5, false)) {
            return "ERR failed\n";
        }
        WindowTask updated = StackListParser.find(read(), taskId);
        int targetStackId = updated != null ? updated.stackId : task.stackId;
        moveStack(targetStackId, left, top, right, bottom);
        applyResizeTask(taskId, left, top, right, bottom);
        for (int attempt = 0; attempt <= VERIFY_RETRIES; attempt++) {
            if (attempt > 0) {
                sleepQuietly(VERIFY_RETRY_SLEEP_MS);
            }
            WindowTask after = StackListParser.find(read(), taskId);
            if (after != null
                    && after.left == left && after.top == top
                    && after.right == right && after.bottom == bottom) {
                return "OK\n";
            }
        }
        return "ERR am bounds\n";
    }

    private String minimize(int taskId) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        moveStack(task.stackId, Protocol.OFF_LEFT, Protocol.OFF_TOP, Protocol.OFF_RIGHT, Protocol.OFF_BOTTOM);
        for (int attempt = 0; attempt <= VERIFY_RETRIES; attempt++) {
            if (attempt > 0) {
                sleepQuietly(VERIFY_RETRY_SLEEP_MS);
            }
            WindowTask after = StackListParser.find(read(), taskId);
            if (after != null
                    && after.stackLeft == Protocol.OFF_LEFT
                    && after.stackTop == Protocol.OFF_TOP
                    && after.stackRight == Protocol.OFF_RIGHT
                    && after.stackBottom == Protocol.OFF_BOTTOM) {
                return "OK\n";
            }
        }
        return "ERR am bounds\n";
    }

    private String dismiss(int taskId) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        am.run("am", "stack", "remove", Integer.toString(task.stackId));
        for (int attempt = 0; attempt <= VERIFY_RETRIES; attempt++) {
            if (attempt > 0) {
                sleepQuietly(VERIFY_RETRY_SLEEP_MS);
            }
            if (StackListParser.find(read(), taskId) == null) {
                return "OK\n";
            }
        }
        return "ERR am still_there\n";
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String moveTaskToBack(int taskId) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        boolean done = false;
        try {
            Object iam = getService("android.app.ActivityManager");
            if (iam != null) {
                Method moveMethod = findMoveTaskToBackMethod(iam.getClass());
                if (moveMethod != null) {
                    moveMethod.invoke(iam, taskId);
                    done = true;
                }
            }
        } catch (Throwable ignored) {
        }
        if (!done && task != null) {
            try {
                am.run("am", "stack", "movetask", Integer.toString(taskId), Integer.toString(task.stackId), "false");
                done = true;
            } catch (Throwable ignored) {
            }
        }
        return done ? "OK\n" : "ERR failed\n";
    }

    private String moveForeignTasksToBack() throws AmFailure {
        List<WindowTask> tasks = read();
        Object iam = getService("android.app.ActivityManager");
        Method moveMethod = iam != null ? findMoveTaskToBackMethod(iam.getClass()) : null;

        for (int i = 0; i < tasks.size(); i++) {
            WindowTask t = tasks.get(i);
            if (Protocol.LAUNCHER_PACKAGE.equals(t.packageName)) {
                continue;
            }
            if ("freeform".equalsIgnoreCase(t.mode) || t.stackId != 0) {
                boolean moved = false;
                if (moveMethod != null && iam != null) {
                    try {
                        moveMethod.invoke(iam, t.id);
                        moved = true;
                    } catch (Throwable ignored) {
                    }
                }
                if (!moved) {
                    try {
                        am.run("am", "stack", "movetask", Integer.toString(t.id), Integer.toString(t.stackId), "false");
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return "OK\n";
    }

    private String setTaskWindowingMode(int taskId, int windowingMode) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        boolean done = applyTaskWindowingMode(taskId, windowingMode, false);
        return done ? "OK\n" : "ERR failed\n";
    }

    private String setForeignTasksWindowingMode(int windowingMode) throws AmFailure {
        List<WindowTask> tasks = read();
        for (int i = 0; i < tasks.size(); i++) {
            WindowTask t = tasks.get(i);
            if (Protocol.LAUNCHER_PACKAGE.equals(t.packageName)) {
                continue;
            }
            if ("freeform".equalsIgnoreCase(t.mode) || t.stackId != 0) {
                applyTaskWindowingMode(t.id, windowingMode, false);
            }
        }
        return "OK\n";
    }

    private boolean applyTaskWindowingMode(int taskId, int windowingMode, boolean toTop) {
        // 1. Try ActivityTaskManager (Android 10+)
        if (invokeSetTaskWindowingMode("android.app.ActivityTaskManager", taskId, windowingMode, toTop)) {
            return true;
        }
        // 2. Try ActivityManager (Android 9 Pie)
        if (invokeSetTaskWindowingMode("android.app.ActivityManager", taskId, windowingMode, toTop)) {
            return true;
        }
        // 3. Fallback to CLI
        try {
            am.run("am", "task", "set-windowing-mode", Integer.toString(taskId), Integer.toString(windowingMode));
            return true;
        } catch (Throwable ignored) {
        }
        try {
            am.run("cmd", "activity", "task", "set-windowing-mode", Integer.toString(taskId), Integer.toString(windowingMode));
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean invokeSetTaskWindowingMode(String serviceClass, int taskId, int windowingMode, boolean toTop) {
        try {
            Object svc = getService(serviceClass);
            if (svc == null) {
                return false;
            }
            Method m = findSetTaskWindowingModeMethod(svc.getClass());
            if (m != null) {
                m.invoke(svc, taskId, windowingMode, toTop);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** @return an ERR line, or null when the task may be moved */
    private static String reject(WindowTask task) {
        if (task == null) {
            return "ERR not_found\n";
        }
        if (Protocol.LAUNCHER_PACKAGE.equals(task.packageName)) {
            return "ERR launcher\n";
        }
        return null;
    }

    private void moveStack(int stackId, int left, int top, int right, int bottom) throws AmFailure {
        if (invokeResizeStack("android.app.ActivityTaskManager", stackId, left, top, right, bottom)
                || invokeResizeStack("android.app.ActivityManager", stackId, left, top, right, bottom)) {
            return;
        }
        am.run("am", "stack", "resize", Integer.toString(stackId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
    }

    private void applyResizeTask(int taskId, int left, int top, int right, int bottom) throws AmFailure {
        if (invokeResizeTask("android.app.ActivityTaskManager", taskId, left, top, right, bottom)
                || invokeResizeTask("android.app.ActivityManager", taskId, left, top, right, bottom)) {
            return;
        }
        am.run("am", "task", "resize", Integer.toString(taskId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
    }

    private static boolean invokeResizeStack(String serviceClass, int stackId, int left, int top, int right, int bottom) {
        try {
            Object svc = getService(serviceClass);
            if (svc == null) {
                return false;
            }
            Object rect = newRect(left, top, right, bottom);
            Method m = findResizeStackMethod(svc.getClass(), rect.getClass());
            if (m == null) {
                return false;
            }
            int paramCount = m.getParameterTypes().length;
            if (paramCount == 6) {
                m.invoke(svc, stackId, rect, false, true, false, -1);
                return true;
            }
            if (paramCount == 3) {
                m.invoke(svc, stackId, rect, false);
                return true;
            }
            if (paramCount == 2) {
                m.invoke(svc, stackId, rect);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean invokeResizeTask(String serviceClass, int taskId, int left, int top, int right, int bottom) {
        try {
            Object svc = getService(serviceClass);
            if (svc == null) {
                return false;
            }
            Object rect = newRect(left, top, right, bottom);
            Method m = findResizeTaskMethod(svc.getClass(), rect.getClass());
            if (m == null) {
                return false;
            }
            int paramCount = m.getParameterTypes().length;
            if (paramCount == 3) {
                m.invoke(svc, taskId, rect, 0);
                return true;
            }
            if (paramCount == 2) {
                m.invoke(svc, taskId, rect);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Method findMoveTaskToBackMethod(Class<?> svcClass) {
        String key = svcClass.getName() + "#moveTaskToBack";
        synchronized (METHOD_CACHE) {
            if (METHOD_CACHE.containsKey(key)) {
                Object cached = METHOD_CACHE.get(key);
                return cached == MISSING_METHOD ? null : (Method) cached;
            }
            Method found = null;
            for (Method m : svcClass.getMethods()) {
                if ("moveTaskToBack".equals(m.getName())) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == int.class) {
                        found = m;
                        break;
                    }
                }
            }
            METHOD_CACHE.put(key, found != null ? found : MISSING_METHOD);
            return found;
        }
    }

    private static Method findSetTaskWindowingModeMethod(Class<?> svcClass) {
        String key = svcClass.getName() + "#setTaskWindowingMode";
        synchronized (METHOD_CACHE) {
            if (METHOD_CACHE.containsKey(key)) {
                Object cached = METHOD_CACHE.get(key);
                return cached == MISSING_METHOD ? null : (Method) cached;
            }
            Method found = null;
            for (Method m : svcClass.getMethods()) {
                if ("setTaskWindowingMode".equals(m.getName())) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 3 && p[0] == int.class && p[1] == int.class && p[2] == boolean.class) {
                        found = m;
                        break;
                    }
                }
            }
            METHOD_CACHE.put(key, found != null ? found : MISSING_METHOD);
            return found;
        }
    }

    private static Method findResizeStackMethod(Class<?> svcClass, Class<?> rectClass) {
        String key = svcClass.getName() + "#resizeStack";
        synchronized (METHOD_CACHE) {
            if (METHOD_CACHE.containsKey(key)) {
                Object cached = METHOD_CACHE.get(key);
                return cached == MISSING_METHOD ? null : (Method) cached;
            }
            Method found = null;
            for (Method m : svcClass.getMethods()) {
                if (!"resizeStack".equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if ((p.length == 6 && p[0] == int.class && p[1] == rectClass
                        && p[2] == boolean.class && p[3] == boolean.class && p[4] == boolean.class && p[5] == int.class)
                        || (p.length == 3 && p[0] == int.class && p[1] == rectClass && p[2] == boolean.class)
                        || (p.length == 2 && p[0] == int.class && p[1] == rectClass)) {
                    found = m;
                    break;
                }
            }
            METHOD_CACHE.put(key, found != null ? found : MISSING_METHOD);
            return found;
        }
    }

    private static Method findResizeTaskMethod(Class<?> svcClass, Class<?> rectClass) {
        String key = svcClass.getName() + "#resizeTask";
        synchronized (METHOD_CACHE) {
            if (METHOD_CACHE.containsKey(key)) {
                Object cached = METHOD_CACHE.get(key);
                return cached == MISSING_METHOD ? null : (Method) cached;
            }
            Method found = null;
            for (Method m : svcClass.getMethods()) {
                if (!"resizeTask".equals(m.getName())) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if ((p.length == 3 && p[0] == int.class && p[1] == rectClass && p[2] == int.class)
                        || (p.length == 2 && p[0] == int.class && p[1] == rectClass)) {
                    found = m;
                    break;
                }
            }
            METHOD_CACHE.put(key, found != null ? found : MISSING_METHOD);
            return found;
        }
    }

    private List<WindowTask> read() throws AmFailure {
        List<WindowTask> binderTasks = readViaBinder();
        if (binderTasks != null) {
            return binderTasks;
        }
        return StackListParser.parse(am.run("am", "stack", "list"));
    }

    private static List<WindowTask> readViaBinder() {
        List<?> infos = fetchAllStackInfos("android.app.ActivityTaskManager");
        if (infos == null) {
            infos = fetchAllStackInfos("android.app.ActivityManager");
        }
        if (infos == null || infos.isEmpty()) {
            return null;
        }
        try {
            List<WindowTask> tasks = new ArrayList<WindowTask>();
            for (int i = 0; i < infos.size(); i++) {
                Object info = infos.get(i);
                if (info == null) {
                    continue;
                }
                Class<?> infoClass = info.getClass();
                int stackId = infoClass.getField("stackId").getInt(info);
                Object stackBoundsObj = infoClass.getField("bounds").get(info);
                int[] sb = stackBoundsObj != null ? readRect(stackBoundsObj) : new int[]{0, 0, 0, 0};
                int[] taskIds = (int[]) infoClass.getField("taskIds").get(info);
                if (taskIds == null || taskIds.length == 0) {
                    continue;
                }
                String[] taskNames = (String[]) infoClass.getField("taskNames").get(info);
                Object[] taskBounds = null;
                try {
                    taskBounds = (Object[]) infoClass.getField("taskBounds").get(info);
                } catch (Throwable ignored) {
                }
                boolean visible = false;
                try {
                    visible = infoClass.getField("visible").getBoolean(info);
                } catch (Throwable ignored) {
                }
                Object topActivity = null;
                try {
                    topActivity = infoClass.getField("topActivity").get(info);
                } catch (Throwable ignored) {
                }
                String mode = readWindowingMode(infoClass, info);

                for (int j = 0; j < taskIds.length; j++) {
                    int tid = taskIds[j];
                    String rawName = (taskNames != null && j < taskNames.length) ? taskNames[j] : null;
                    String pkg = "unknown";
                    String act = "unknown";
                    if (rawName != null && !rawName.isEmpty()) {
                        int slash = rawName.indexOf('/');
                        if (slash > 0) {
                            pkg = rawName.substring(0, slash);
                            act = slash + 1 < rawName.length() ? rawName.substring(slash + 1) : "unknown";
                        } else {
                            pkg = rawName;
                        }
                    } else if (topActivity != null) {
                        pkg = String.valueOf(topActivity.getClass().getMethod("getPackageName").invoke(topActivity));
                        act = String.valueOf(topActivity.getClass().getMethod("getClassName").invoke(topActivity));
                    }
                    int[] tb = (taskBounds != null && j < taskBounds.length && taskBounds[j] != null)
                            ? readRect(taskBounds[j])
                            : sb;
                    tasks.add(new WindowTask(
                            tid,
                            stackId,
                            pkg,
                            act,
                            mode,
                            tb[0], tb[1], tb[2], tb[3],
                            sb[0], sb[1], sb[2], sb[3],
                            visible));
                }
            }
            return tasks;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<?> fetchAllStackInfos(String serviceClass) {
        try {
            Object svc = getService(serviceClass);
            if (svc == null) {
                return null;
            }
            Method m = svc.getClass().getMethod("getAllStackInfos");
            Object result = m.invoke(svc);
            if (result instanceof List) {
                return (List<?>) result;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readWindowingMode(Class<?> infoClass, Object info) {
        try {
            Object config = infoClass.getField("configuration").get(info);
            if (config == null) {
                return "unknown";
            }
            Object winConfig = config.getClass().getField("windowConfiguration").get(config);
            if (winConfig == null) {
                return "unknown";
            }
            int wm = ((Integer) winConfig.getClass().getMethod("getWindowingMode").invoke(winConfig)).intValue();
            return windowingModeName(wm);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String windowingModeName(int wm) {
        switch (wm) {
            case 0:
                return "undefined";
            case 1:
                return "fullscreen";
            case 2:
                return "pinned";
            case 3:
                return "split-screen-primary";
            case 4:
                return "split-screen-secondary";
            case 5:
                return "freeform";
            case 11:
                return "extend";
            default:
                return "mode_" + wm;
        }
    }

    private static Object getService(String serviceClass) {
        try {
            Class<?> cls = Class.forName(serviceClass);
            return cls.getMethod("getService").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object newRect(int left, int top, int right, int bottom) throws Exception {
        Constructor<?> ctor = cachedRectCtor;
        if (ctor == null) {
            Class<?> rectClass = Class.forName("android.graphics.Rect");
            ctor = rectClass.getConstructor(int.class, int.class, int.class, int.class);
            cachedRectCtor = ctor;
        }
        return ctor.newInstance(left, top, right, bottom);
    }

    private static int[] readRect(Object rect) throws Exception {
        Class<?> rc = rect.getClass();
        return new int[]{
                rc.getField("left").getInt(rect),
                rc.getField("top").getInt(rect),
                rc.getField("right").getInt(rect),
                rc.getField("bottom").getInt(rect)
        };
    }
}
