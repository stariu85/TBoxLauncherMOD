package vad.dashing.dclwm;

import java.util.List;

/**
 * One text command from the launcher. The caller has already checked that the
 * socket peer is root or the launcher uid.
 */
public final class Session {
    private final Am am;

    public Session(Am am) {
        this.am = am;
    }

    public String handle(String line, int peerUid, int launcherUid) {
        if (!allowed(peerUid, launcherUid)) {
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
            if ("MINIMIZE".equals(op) && parts.length == 2) {
                return minimize(parseId(parts[1]));
            }
            if ("DISMISS".equals(op) && parts.length == 2) {
                return dismiss(parseId(parts[1]));
            }
            if ("FRONT_A".equals(op) && (parts.length == 1 || parts.length == 2)) {
                int taskId = parts.length == 2 ? parseId(parts[1]) : -1;
                return frontA(taskId);
            }
            if ("FRONT_B".equals(op) && (parts.length == 1 || parts.length == 2)) {
                int taskId = parts.length == 2 ? parseId(parts[1]) : -1;
                return frontB(taskId);
            }
            if ("FRONT_C".equals(op) && (parts.length == 1 || parts.length == 2)) {
                return frontC();
            }
            return "ERR unknown\n";
        } catch (NumberFormatException bad) {
            return "ERR bad_args\n";
        } catch (AmFailure failure) {
            return "ERR am " + failure.getMessage() + "\n";
        }
    }

    private static boolean allowed(int peerUid, int launcherUid) {
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
        am.run("am", "task", "resize", Integer.toString(taskId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
        WindowTask after = StackListParser.find(read(), taskId);
        if (after == null
                || after.left != left || after.top != top
                || after.right != right || after.bottom != bottom) {
            return "ERR am bounds\n";
        }
        return "OK\n";
    }

    private String minimize(int taskId) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        moveStack(task.stackId, Protocol.OFF_LEFT, Protocol.OFF_TOP, Protocol.OFF_RIGHT, Protocol.OFF_BOTTOM);
        WindowTask after = StackListParser.find(read(), taskId);
        if (after == null
                || after.stackLeft != Protocol.OFF_LEFT
                || after.stackTop != Protocol.OFF_TOP
                || after.stackRight != Protocol.OFF_RIGHT
                || after.stackBottom != Protocol.OFF_BOTTOM) {
            return "ERR am bounds\n";
        }
        return "OK\n";
    }

    private String dismiss(int taskId) throws AmFailure {
        WindowTask task = StackListParser.find(read(), taskId);
        String rejected = reject(task);
        if (rejected != null) {
            return rejected;
        }
        am.run("am", "stack", "remove", Integer.toString(task.stackId));
        if (StackListParser.find(read(), taskId) != null) {
            return "ERR am still_there\n";
        }
        return "OK\n";
    }

    private String frontA(int taskId) throws AmFailure {
        int targetTask = taskId;
        if (targetTask <= 0) {
            List<WindowTask> tasks = read();
            for (int i = 0; i < tasks.size(); i++) {
                WindowTask t = tasks.get(i);
                if (Protocol.LAUNCHER_PACKAGE.equals(t.packageName)) {
                    targetTask = t.id;
                    break;
                }
            }
        }
        if (targetTask > 0) {
            am.run("am", "task", "to-top", Integer.toString(targetTask));
        }
        am.run("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME", "-f", "0x10200000");
        return "OK\n";
    }

    private String frontB(int taskId) {
        try {
            Class<?> amClass = Class.forName("android.app.ActivityManager");
            Object iam = amClass.getMethod("getService").invoke(null);
            if (iam == null) {
                return "ERR no_service\n";
            }
            int targetTask = taskId;
            int targetStack = -1;
            if (targetTask <= 0) {
                try {
                    List<WindowTask> tasks = read();
                    for (int i = 0; i < tasks.size(); i++) {
                        WindowTask t = tasks.get(i);
                        if (Protocol.LAUNCHER_PACKAGE.equals(t.packageName)) {
                            targetTask = t.id;
                            targetStack = t.stackId;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            if (targetTask > 0) {
                boolean moved = false;
                for (java.lang.reflect.Method m : iam.getClass().getMethods()) {
                    if ("moveTaskToFront".equals(m.getName())) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 3 && p[0] == int.class && p[1] == int.class) {
                            m.invoke(iam, targetTask, 1 /* MOVE_TASK_WITH_HOME */, null);
                            moved = true;
                            break;
                        }
                    }
                }
                if (!moved) {
                    for (java.lang.reflect.Method m : iam.getClass().getMethods()) {
                        if ("moveTaskToFront".equals(m.getName())) {
                            Class<?>[] p = m.getParameterTypes();
                            if (p.length == 2 && p[0] == int.class && p[1] == int.class) {
                                m.invoke(iam, targetTask, 1 /* MOVE_TASK_WITH_HOME */);
                                moved = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (targetStack >= 0) {
                for (java.lang.reflect.Method m : iam.getClass().getMethods()) {
                    if ("setFocusedStack".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
                        m.invoke(iam, targetStack);
                        break;
                    }
                }
            }
            return "OK\n";
        } catch (Throwable t) {
            return "ERR " + t.getMessage() + "\n";
        }
    }

    private String frontC() throws AmFailure {
        am.run("input", "keyevent", "KEYCODE_HOME");
        return "OK\n";
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
        am.run("am", "stack", "resize", Integer.toString(stackId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
    }

    private List<WindowTask> read() throws AmFailure {
        return StackListParser.parse(am.run("am", "stack", "list"));
    }
}
