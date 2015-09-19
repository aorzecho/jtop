package org.aorzecho.tools.jt;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * Simple JavaTopThreads
 */
public class JT implements Runnable {

    private static class Stats {

        final Long cpu;
        final Long userCpu;

        public Stats(long cpu, long userCpu) {
            this.cpu = cpu;
            this.userCpu = userCpu;
        }

        public String toString(String separator) {
            StringBuilder sb = new StringBuilder();
            sb.append(cpu);
            sb.append(separator);
            sb.append(userCpu);
            return sb.toString();
        }
    }

    private static class StatsComparator implements Comparator<Long> {

        private final Map<Long, Stats> diff;

        public StatsComparator(Map<Long, Stats> diff) {
            this.diff = diff;
        }

        @Override
        public int compare(Long t1Id, Long t2Id) {
            int res = diff.get(t2Id).cpu.compareTo(diff.get(t1Id).cpu);
            return res == 0
                    ? Long.valueOf(t1Id).compareTo(t2Id)
                    : res;
        }
    }
    @SuppressWarnings("unchecked")
    private Map<Long, Stats>[] infos = new HashMap[2];
    private int interval = 5000;
    private String mUrl;
    private int top = 20;
    private int maxStack = 1;
    private Pattern stackFilter = null;
    private int columns = 150;
    private String fmtLine;
    private String fmtHeader;
    private String fmtRow;
    private ThreadMXBean tb;

    private JT() throws IOException {
        infos[0] = new HashMap<Long, Stats>();
        infos[1] = new HashMap<Long, Stats>();
        setColumns(this.columns);
    }

    public void run() {
        int curr = 0;
        JMXConnector connector = null;
        try {
            connector = JMXConnectorFactory.connect(new JMXServiceURL(mUrl));
            tb = ManagementFactory.newPlatformMXBeanProxy(connector.getMBeanServerConnection(),
                    ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class);
            System.out.println("Initial dump...");
            for (Long tId : tb.getAllThreadIds()) {
                infos[0].put(tId, getStats(tb, tId));
            }
            for (;;) {
                int prev = curr;
                curr = (curr + 1) % 2;

                Thread.sleep(interval);

                for (Long tId : tb.getAllThreadIds()) {
                    infos[curr].put(tId, getStats(tb, tId));
                }
                printDiff(this.infos[curr], this.infos[prev]);
            }
        } catch (Exception ex) {
            Logger.getLogger(JT.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (connector != null) {
                try {
                    connector.close();
                } catch (IOException ex) {
                    Logger.getLogger(JT.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public void setColumns(int columns) {
        this.columns = columns;
        StringBuilder b = new StringBuilder("\n\n=== %s ");
        for (int i = 0; i < columns - 25; i++) {
            b.append('=');
        }
        this.fmtLine = b.toString();
        int stackS = 40;
        int colsLeft = columns - 142;
        if (colsLeft > 0) {
            stackS += colsLeft;
        }
        String stackF = new StringBuilder().append("%-").append(stackS).append(".").append(stackS).append("s").toString();
        this.fmtRow = new StringBuilder().append("%-10d %-40.40s %-15.15s %-10d %-5d %-10d %-5d ").append(stackF).toString();
        this.fmtHeader = new StringBuilder().append("%-10s %-40s %-15s %-10s %-5s %-10s %-5s ").append(stackF).toString();
    }

    public void setMaxStack(int maxStack) {
        this.maxStack = maxStack;
    }

    public void setmUrl(String mUrl) {
        this.mUrl = mUrl;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public void setStackFilter(String stackFilter) {
        this.stackFilter = Pattern.compile(stackFilter);
    }

    public void setTop(int top) {
        this.top = top;
    }

    private static Stats getStats(ThreadMXBean tb, Long threadId) {
        return new Stats(tb.getThreadCpuTime(threadId), tb.getThreadUserTime(threadId));
    }

    private static Stats diffStats(Stats curr, Stats prev) {
        return new Stats(
                curr.cpu - prev.cpu,
                curr.userCpu - prev.userCpu);
    }

    private void printDiff(Map<Long, Stats> curr, Map<Long, Stats> prev) {
        System.out.println(String.format(this.fmtLine, new Object[]{new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())}));

        System.out.println(String.format(this.fmtHeader, new Object[]{
            "TID", "TNAME", "TSTATE", "CPUms", "CPU%", "UCPUms", "UCPU%", 
            new StringBuilder().append("Trace[").append(this.stackFilter == null ? "0" : this.stackFilter).append("]").toString()}));

        Map<Long, Stats> diff = new HashMap<Long, Stats>(curr.size());

        for (Map.Entry<Long, Stats> entry : curr.entrySet()) {
            Stats currStats = entry.getValue();
            Stats prevStats = prev.get(entry.getKey());
            if (prevStats != null) {
                diff.put(entry.getKey(), diffStats(currStats, prevStats));
            }
        }

        SortedMap<Long, Stats> sortedThreads = new TreeMap<Long, Stats>(new StatsComparator(diff));

        sortedThreads.putAll(diff);

        int count = 0;
        for (Map.Entry<Long, Stats> entry : sortedThreads.entrySet()) {
            ThreadInfo ti = tb.getThreadInfo(entry.getKey(), maxStack);
            if (ti != null) {
                Stats stats = (Stats) entry.getValue();
                System.out.println(String.format(this.fmtRow,
                        new Object[]{Long.valueOf(ti.getThreadId()),
                            ti.getThreadName(),
                            ti.getThreadState(),
                            Long.valueOf(stats.cpu.longValue() / 1000000L),
                            Long.valueOf(stats.cpu.longValue() / this.interval / 10000L),
                            Long.valueOf(stats.userCpu.longValue() / 1000000L),
                            Long.valueOf(stats.userCpu.longValue() / this.interval / 10000L),
                            findTrace(ti, this.stackFilter)}));

                count++;
                if (count == top) {
                    break;
                }
            }
        }


    }

    private static String findTrace(ThreadInfo ti, Pattern stackFilter) {
        String res = ti.getStackTrace().length > 0 ? ti.getStackTrace()[0].toString() : "--";

        if (stackFilter != null) {
            for (StackTraceElement elem : ti.getStackTrace()) {
                String e = elem.toString();
                if (stackFilter.matcher(e).find()) {
                    res = e;
                    break;
                }
            }
        }
        return res;
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        JT jt = new JT();
        int i = 0;
        for (; (i < args.length) && (args[i].startsWith("-")); i++) {
            if ("--interval".equals(args[i])) {
                jt.setInterval(Integer.parseInt(args[(++i)]));
            } else if ("--top".equals(args[i])) {
                jt.setTop(Integer.parseInt(args[(++i)]));
            } else if ("--maxStack".equals(args[i])) {
                jt.setMaxStack(Integer.parseInt(args[(++i)]));
            } else if ("--columns".equals(args[i])) {
                jt.setColumns(Integer.parseInt(args[(++i)]));
            } else if ("--stackFilter".equals(args[i])) {
                jt.setStackFilter(args[(++i)]);
                if (jt.maxStack == 1) {
                    jt.setMaxStack(50);
                }
            } else {
		System.out.println("Usage: jtop [--interval N] [--top N] [--maxStack N] [--stackFilter regex] <JMX url>");
		System.out.println("");
		System.out.println("Example: jtop --interval 5000 --top 8 192.168.1.20:5401");
		System.exit(1);
            }
        }
        String mUrl;
        if (i == args.length) {
           mUrl = "localhost";
        } else {
            mUrl = args[i];
	}
        if (!mUrl.startsWith("service:")) {
            mUrl = new StringBuilder().append("service:jmx:rmi://").append(mUrl).append("/jndi/rmi://").append(mUrl).append("/jmxrmi").toString();
            System.out.println(new StringBuilder().append("Using url ").append(mUrl).toString());
        }

        jt.setmUrl(mUrl);

        System.out.println(String.format("Connecting to url %s, interval=%d, top=%d, maxStack=%d, stackFilter=%s", new Object[]{jt.mUrl, Integer.valueOf(jt.interval), Integer.valueOf(jt.top), Integer.valueOf(jt.maxStack), jt.stackFilter}));

        Thread poller = new Thread(jt);

        poller.start();

    }
}
