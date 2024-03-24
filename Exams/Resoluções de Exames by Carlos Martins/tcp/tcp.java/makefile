# commands to build and clean programs in this directory using Microsoft "nmake"

all: TcpEchoClient.class StTcpEchoServer.class MtTcpEchoServer.class
	
TcpEchoClient.class : TcpEchoClient.java
	javac TcpEchoClient.java

StTcpEchoServer.class : StTcpEchoServer.java
	javac StTcpEchoServer.java

MtTcpEchoServer.class : MtTcpEchoServer.java
	javac MtTcpEchoServer.java

clean:
	@del *.class 2>NUL | (exit 0)
