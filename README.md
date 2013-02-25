JT JavaThread top
=================

This is a simple java top (yet another...), using [java management api](http://docs.oracle.com/javase/6/docs/api/java/lang/management/package-summary.html)

It's a toy rather than a serious tool, but may be useful sometimes to identify long running threads (eg. busy loops).

Attached bash script makes it easier to use with remote servers. In remote mode it connects through ssh first and then 
executes the script locally on remote host. Creating an ssh tunnel instead of sending the executable would be simpler, 
but less fun ;) Besides, I had a very slow ssh connection to the server and it saved a lot of bandwidth...
