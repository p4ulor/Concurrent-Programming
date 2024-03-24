# commands to build and clean programs in this directory using NMAKE

all: client-echo-async.exe mt-server-echo-tap.exe
	
client-echo-async.exe : client-echo-async.cs
	csc /debug+ /out:client-echo-async.exe client-echo-async.cs

mt-server-echo-tap.exe : mt-server-echo-tap.cs
	csc /debug+ /out:mt-server-echo-tap.exe mt-server-echo-tap.cs

clean:
	@del *.exe *.pdb 2>NUL

	