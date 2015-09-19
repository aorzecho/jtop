JT JavaThread top
=================

This is a simple java top (yet another...), using [java management api](http://docs.oracle.com/javase/6/docs/api/java/lang/management/package-summary.html)

It's a toy rather than a serious tool, but may be useful sometimes to identify long running threads (eg. busy loops).

Attached bash script makes it easier to use with remote servers. In remote mode it connects through ssh first and then 
executes the script locally on remote host. Creating an ssh tunnel instead of sending the executable would be simpler, 
but less fun ;) Besides, I had a very slow ssh connection to the server and it saved a lot of bandwidth...


Usage
-----
    $ ./jtop --help
    Show top threads of a java process, accessing the process through JMX. By default connects to localhost:8686
    Usage: ./jtop <options> [[user@]host:port]
          --top N          show top N threads, default 10
          --interval N     refresh every N milliseconds, default 10000
          --stackFilter rgxp         regexp to filter stack trace - shows first entry matching the regexp (increase maxStack if not found)
          --maxStack N         number of stack trace elements to load/filter, default 50 if stackFilter is set, 1 otherwise, use less for less overhead
          --remote             execute remotely, connecting to the host using ssh (useful when JMX port is firewalled or only exposed on localhost)
    
    Examples:
      ./jtop --maxStack 0
        connects to localhost:8686, disables stack
      ./jtop --stackFilter 'glassfish|org.felix|org.aorzecho'
        connects to localhost:8686, looks for given keywords in stacktraces
      ./jtop --top 8 192.168.1.25:9824
        connects to process via JMX on 192.168.1.25:9824, top 8 threads  



